package com.ezaigc.hr.talent.application.manager;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.api.ResumeLifecycleService;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleTiming;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import java.util.Map;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.enums.ResumeLifecyclePhase;
import com.ezaigc.hr.talent.enums.ResumeLifecycleWarning;
import com.ezaigc.hr.talent.enums.ResumeLifecycleAdvanceResult;
import com.ezaigc.hr.talent.enums.ResumeLifecycleRouteReason;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;
/** 仅依据真实入库、分流和已发布日历推进周期，不产生人工筛选或招聘结论。 */
@Service
public class ResumeLifecycleApplicationManager implements ResumeLifecycleService {
  @Resource private ResumeLifecyclePersistenceService cycles;
  @Resource private CandidatePersistenceService candidates;
  @Resource private TalentIntakePersistenceService resumes;
  @Resource private CandidateResumeVersionService routing;
  @Resource private CandidateRecruitmentStateQuery recruitment;
  @Resource private CandidateScreeningStateQuery screening;
  @Resource private WorkCalendarCalculationManager calendar;
  @Resource private TalentProcessEventPersistenceService events;
  @Resource private ResumeLifecycleProperties properties;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private ObjectMapper json;
  @Resource private ResumeSearchProjectionRecorder searchProjection;

  @Override @Transactional(isolation = Isolation.READ_COMMITTED)
  public void entered(String candidateId, String resumeVersionId) {
    initialize(candidateId, resumeVersionId, clock.instant());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void initialize(String candidateId, String resumeVersionId, Instant enteredAt) {
    if (!properties.isEnabled()) return;
    routing.lockForRouting(candidateId, resumeVersionId);
    if (cycles.lastCycle(resumeVersionId) != 0 || !eligible(candidateId,resumeVersionId)) return;
    create(candidateId,resumeVersionId,enteredAt);
  }

  @Override @Transactional(isolation = Isolation.READ_COMMITTED)
  public void routed(String candidateId, String resumeVersionId, String reason) {
    if (!properties.isEnabled()) return;
    routing.lockForRouting(candidateId, resumeVersionId);
    var cycle = cycles.lockCurrent(resumeVersionId);
    if (cycle != null && ResumeLifecyclePhase.SHARED.matches(cycle.getPhase()) && ResumeLifecycleRouteReason.SCREENING_ADMITTED.matches(reason))
      throw PlatformExceptions.conflict("RESUME_ALREADY_AUTO_SHARED","简历已超时转入共享库，请刷新后从共享库操作");
    if (cycle != null && !ResumeLifecyclePhase.SHARED.matches(cycle.getPhase())) close(cycle,reason);
  }

  @Override @Transactional(isolation = Isolation.READ_COMMITTED)
  public void returned(String candidateId, String resumeVersionId) {
    if (!properties.isEnabled()) return;
    routing.lockForRouting(candidateId,resumeVersionId);
    if (!eligible(candidateId,resumeVersionId)) return;
    if (cycles.lockCurrent(resumeVersionId) != null) return;
    create(candidateId,resumeVersionId,clock.instant());
  }

  /** 共享库被明确领取后关闭旧共享周期并开启新的人工处理周期。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void reclaimed(String candidateId, String resumeVersionId) {
    if (!properties.isEnabled()) return;
    routing.lockForRouting(candidateId, resumeVersionId);
    var current = cycles.lockCurrent(resumeVersionId);
    if (current != null) {
      if (!ResumeLifecyclePhase.SHARED.matches(current.getPhase())) throw conflict();
      close(current, "SHARED_RECLAIMED");
    }
    create(candidateId, resumeVersionId, clock.instant());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public String advance(String id, LocalDate settlementDate, boolean correctionOnly) {
    if (!properties.isEnabled()) return ResumeLifecycleAdvanceResult.SKIPPED.getCode();
    var snapshot = cycles.getById(id);
    if (snapshot == null) return ResumeLifecycleAdvanceResult.SKIPPED.getCode();
    routing.lockForRouting(snapshot.getCandidateId(), snapshot.getResumeVersionId());
    var row = cycles.lockCurrent(snapshot.getResumeVersionId());
    if (row == null || !id.equals(row.getId()) || ResumeLifecyclePhase.SHARED.matches(row.getPhase())) return ResumeLifecycleAdvanceResult.SKIPPED.getCode();
    if (row.getSettledThrough()!=null && settlementDate.isBefore(row.getSettledThrough())) return ResumeLifecycleAdvanceResult.SKIPPED.getCode();
    if (!eligible(row.getCandidateId(),row.getResumeVersionId())) {
      close(row,"ROUTED_OR_VERSION_CHANGED"); return ResumeLifecycleAdvanceResult.CLOSED.getCode();
    }
    var result = calendar.calculate(row.getEnteredAt().atZone(ResumeLifecycleTiming.ZONE).toLocalDate(),
        settlementDate,true);
    String before = row.getPhase()+"/"+row.getWarningLevel();
    if (!result.isSettlementComplete()) {
      row.setPauseReason("CALENDAR_MISSING");
      row.setNextCheckAt(clock.instant().plusSeconds(3600));
      save(row);
      return ResumeLifecycleAdvanceResult.BLOCKED.getCode();
    }
    // 运行领取后仍可能发布日历；以年度锁内读取的版本时间封闭该并发窗口。
    // 本轮 01:00 边界之后发布的规则只能先纠正投影和降级，下一正常边界才允许升级。
    correctionOnly = correctionOnly || (result.getLatestPublishedAt() != null
        && result.getLatestPublishedAt().isAfter(ResumeLifecycleTiming.transitionAt(settlementDate)));
    int days = result.getElapsedWorkdays();
    String phase = ResumeLifecycleTiming.phase(days);
    boolean deferred = correctionOnly && rank(phase,ResumeLifecycleTiming.warning(days)) >
        rank(row.getPhase(),row.getWarningLevel());
    String previousWarning=row.getWarningLevel();
    if (deferred) phase=row.getPhase();
    row.setElapsedWorkdays(days);
    row.setSettledThrough(settlementDate);
    row.setCalendarRevisions(json.writeValueAsString(result.getRevisionVector()));
    row.setPauseReason(null);
    row.setPhase(phase);
    row.setWarningLevel(deferred ? previousWarning : ResumeLifecyclePhase.SHARED.matches(phase) ? ResumeLifecycleWarning.NORMAL.getCode() : ResumeLifecycleTiming.warning(days));
    var thresholds = result.getThresholdDates();
    row.setShareDueAt(thresholds.size() >= 4 ? ResumeLifecycleTiming.transitionAt(thresholds.get(3)) : null);
    row.setNextCheckAt(days < 4 && thresholds.size() > days
        ? ResumeLifecycleTiming.transitionAt(thresholds.get(days)) : null);
    if (ResumeLifecyclePhase.SHARED.matches(phase)) row.setSharedAt(clock.instant());
    if (deferred) row.setNextCheckAt(clock.instant().atZone(ResumeLifecycleTiming.ZONE).toLocalDate()
        .plusDays(1).atTime(1,0).atZone(ResumeLifecycleTiming.ZONE).toInstant());
    if (days < 4 && row.getNextCheckAt() == null) {
      row.setPauseReason("CALENDAR_MISSING");
      row.setNextCheckAt(clock.instant().plusSeconds(86400));
    }
    save(row);
    if (ResumeLifecyclePhase.SHARED.matches(phase)) {
      var candidate=candidates.findCandidate(row.getCandidateId());
      searchProjection.candidateChanged(candidate.getId(),row.getId(),candidate.getOwnerUserId(),
          candidate.getOwnerUserId(),candidate.getOwnerDepartmentId());
    }
    String after = row.getPhase()+"/"+row.getWarningLevel();
    if (!before.equals(after)) {
      var event=new TalentProcessEvent();
      event.setId(ids.newId()); event.setEventType("RESUME_LIFECYCLE_CHANGED");
      event.setActorUserId(PlatformActorConstants.SYSTEM_ACTOR_ID); event.setTargetType("CANDIDATE");
      event.setTargetId(row.getCandidateId()); event.setOccurredAt(clock.instant());
      event.setReason(before+"->"+after+";reason="+(correctionOnly ? "CALENDAR_CORRECTION" : "AUTO_TIMEOUT"));
      event.setExt(json.writeValueAsString(Map.of("cycleId",row.getId(),"settlementDate",settlementDate.toString(),
          "calendarRevisions",result.getRevisionVector(),"ownerUserId",candidates.findCandidate(row.getCandidateId()).getOwnerUserId())));
      events.save(event);
    }
    return deferred ? ResumeLifecycleAdvanceResult.DEFERRED.getCode() : ResumeLifecyclePhase.SHARED.matches(phase) ? ResumeLifecycleAdvanceResult.SHARED.getCode() : ResumeLifecycleAdvanceResult.SUCCEEDED.getCode();
  }

  private boolean eligible(String candidateId,String resumeVersionId) {
    var candidate = candidates.findCandidate(candidateId);
    if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) return false;
    var currentVersions=resumes.findCurrentCandidateResumes(List.of(candidateId)).stream()
        .map(r -> r.getResumeVersionId()).collect(Collectors.toSet());
    if (!currentVersions.contains(resumeVersionId)) return false;
    if (screening.activeResumeVersionIds(List.of(candidateId)).stream().anyMatch(currentVersions::contains)
        || currentVersions.stream().anyMatch(recruitment::hasActiveRecruitment)) return false;
    if (recruitment.states(List.of(candidateId)).getOrDefault(candidateId,List.of()).stream()
        .anyMatch(CandidateRecruitmentStateQuery.State::isBlocksLibrary)) return false;
    var ext = candidate.getExt();
    return ext == null || !resumeVersionId.equals(json.readTree(ext).path("screenedOutResumeVersionId").asString());
  }

  private void create(String candidateId,String resumeVersionId,Instant enteredAt) {
    var row = new ResumeLifecycle();
    row.setId(ids.newId()); row.setCandidateId(candidateId); row.setResumeVersionId(resumeVersionId);
    row.setCycleNo(cycles.lastCycle(resumeVersionId)+1); row.setActiveMarker("Y"); row.setEnteredAt(enteredAt);
    row.setPhase(ResumeLifecyclePhase.PENDING_SCREENING.getCode()); row.setWarningLevel(ResumeLifecycleWarning.NORMAL.getCode());
    row.setNextCheckAt(enteredAt.atZone(ResumeLifecycleTiming.ZONE).toLocalDate().plusDays(2)
        .atTime(1,0).atZone(ResumeLifecycleTiming.ZONE).toInstant());
    row.setCreateTime(clock.instant()); row.setUpdateTime(clock.instant());
    cycles.save(row);
    events.insertEvent(ids.newId(),"RESUME_LIFECYCLE_ENTERED",PlatformActorConstants.SYSTEM_ACTOR_ID,"CANDIDATE",candidateId,
        "cycle="+row.getId()+";resumeVersion="+resumeVersionId,clock.instant());
  }

  private void close(ResumeLifecycle row,String reason) {
    // 显式清空活动标记；MyBatis-Plus 默认忽略 null，不能只依赖实体更新。
    if (!cycles.update(Wrappers.<ResumeLifecycle>lambdaUpdate().eq(ResumeLifecycle::getId,row.getId())
        .eq(ResumeLifecycle::getVersion,row.getVersion()).set(ResumeLifecycle::getActiveMarker,null)
        .set(ResumeLifecycle::getPhase,ResumeLifecyclePhase.CLOSED.getCode()).set(ResumeLifecycle::getClosedReason,reason)
        .set(ResumeLifecycle::getNextCheckAt,null).set(ResumeLifecycle::getUpdateTime,clock.instant())
        .setIncrBy(ResumeLifecycle::getVersion,1))) throw conflict();
    events.insertEvent(ids.newId(),"RESUME_LIFECYCLE_CLOSED",PlatformActorConstants.SYSTEM_ACTOR_ID,"CANDIDATE",row.getCandidateId(),
        "cycle="+row.getId()+";reason="+reason,clock.instant());
  }
  private void save(ResumeLifecycle row) {
    var update = Wrappers.<ResumeLifecycle>lambdaUpdate().eq(ResumeLifecycle::getId,row.getId())
        .eq(ResumeLifecycle::getVersion,row.getVersion())
        .set(ResumeLifecycle::getPhase,row.getPhase()).set(ResumeLifecycle::getWarningLevel,row.getWarningLevel())
        .set(ResumeLifecycle::getElapsedWorkdays,row.getElapsedWorkdays())
        .set(ResumeLifecycle::getSettledThrough,row.getSettledThrough())
        .set(ResumeLifecycle::getCalendarRevisions,row.getCalendarRevisions())
        .set(ResumeLifecycle::getPauseReason,row.getPauseReason()).set(ResumeLifecycle::getNextCheckAt,row.getNextCheckAt())
        .set(ResumeLifecycle::getShareDueAt,row.getShareDueAt()).set(ResumeLifecycle::getSharedAt,row.getSharedAt())
        .set(ResumeLifecycle::getUpdateTime,clock.instant()).setIncrBy(ResumeLifecycle::getVersion,1);
    if (!cycles.update(update)) throw conflict();
  }
  public static int rank(String phase,String warning) {
    if (ResumeLifecyclePhase.SHARED.matches(phase)) return 4;
    if (ResumeLifecycleWarning.RED.matches(warning)) return 3;
    if (ResumeLifecycleWarning.YELLOW.matches(warning)) return 2;
    return ResumeLifecyclePhase.PENDING_DISPOSAL.matches(phase) ? 1 : 0;
  }
  private static RuntimeException conflict() {
    return PlatformExceptions.conflict("RESUME_LIFECYCLE_CONFLICT","简历已处理，请刷新后重试");
  }
}
