package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static com.ezaigc.hr.common.web.request.RequiredText.required;
import static com.ezaigc.hr.common.web.request.RequiredText.optional;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorPages;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlanHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.response.FollowUpPlanHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanHistoryPersistenceService;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.pagination.VisibleCursorPageCollector;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.demand.enums.RecruitmentType;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.FollowUpPlanAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.FollowUpPlanDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import com.ezaigc.hr.recruiting.recruitment.domain.response.FollowUpPlanResponse;
import com.ezaigc.hr.recruiting.recruitment.enums.CommunicationChannel;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentIdempotencyPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.ezaigc.hr.recruiting.recruitment.enums.FollowUpPlanStatus;
import org.springframework.transaction.annotation.Transactional;

/** 预约不代表实际沟通；完成时根据 HR 录入结果同步沟通事实。 */
@Service
public class FollowUpPlanApplicationManager {
  @Resource private CompanyBlacklistQueryService blacklist;
  private static final String CREATE_ACTION = "FOLLOW_UP_PLAN_CREATE";
  @Resource private RecruitmentApplicationManager applications;
  @Resource private FollowUpPlanPersistenceService plans;
  @Resource private FollowUpPlanLifecycleManager lifecycle;
  @Resource private ApplicationPersistenceService applicationPersistence;
  @Resource private RecruitmentIdempotencyPersistenceService idempotency;
  @Resource private AuthorizationService authorization;
  @Resource private DemandQueryService demands;
  @Resource private FollowUpPlanAssembler assembler;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private CursorCodec cursorCodec;
  @Resource private FollowUpPlanHistoryPersistenceService histories;

  @Transactional(readOnly = true)
  public CursorPage<FollowUpPlanHistoryResponse> history(String applicationId, String cursor, Integer requestedLimit) {
    applications.requireFollowUpApplication(applicationId);
    var actor = authorization.currentActor();
    String scope = CursorScopes.scope("follow-up-plan-history", applicationId, actor.getUserId(), actor.getPermissions(),
        actor.hasCompanyWideScope(), actor.getOperationalManagedUserIds(), actor.visibleDepartmentIds());
    var decoded = StableCursor.decode(cursorCodec, scope, cursor);
    int limit = PageLimits.resolve(requestedLimit);
    var rows = histories.findHistory(applicationId, decoded == null ? null : decoded.getSortTime(),
        decoded == null ? null : decoded.getId(), limit + 1);
    return CursorPages.page(rows, limit, cursorCodec, scope, FollowUpPlanHistory::getCreateTime, FollowUpPlanHistory::getId)
        .map(assembler::toHistoryResponse);
  }

  @Transactional
  public FollowUpPlanResponse create(String applicationId, FollowUpPlanDTO command, String rawKey) {
    var app = applications.requireLockedFollowUpApplication(applicationId);
    String actor = authorization.currentActor().getUserId();
    String key = IdempotencyKey.fromHttpHeader(rawKey);
    String fingerprint = Sha256Utils.fingerprintObjects(31, applicationId, command.getStartsAt(), command.getEndsAt(), command.getChannel(), command.getNote(), command.getVersion());
    String planId = ids.newId();
    var replay = idempotency.findIdempotency(actor, key);
    if (replay == null && applicationPersistence.insertIdempotency(actor, key, CREATE_ACTION, fingerprint, planId, clock.instant()) != 1) {
      replay = idempotency.findIdempotencyForUpdate(actor, key);
      if (replay == null) throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "预约正在保存，请重试");
    }
    if (replay != null) {
      if (!CREATE_ACTION.equals(replay.getActionCode()) || !fingerprint.equals(replay.getRequestFingerprint()))
        throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "幂等键不能用于不同预约");
      return response(requirePlan(applicationId, replay.getTargetId()), app);
    }
    if (app.getVersion() != command.getVersion()) throw PlatformExceptions.conflict("APPLICATION_STATE_CHANGED", "招聘活动已变化，请刷新");
    blacklist.requireRecruitmentAllowed(app.getCandidateId());
    validate(command);
    var plan = new FollowUpPlan(); plan.setId(planId); plan.setApplicationId(applicationId);
    plan.setStatus(FollowUpPlanStatus.SCHEDULED.getCode()); apply(plan, command);
    plan.setCreateId(actor); plan.setCreateTime(clock.instant());
    if (!plans.save(plan)) throw new IllegalStateException("预约写入失败");
    lifecycle.audit(plan, null, "CREATED", plan.getNote(), actor, clock.instant());
    return response(plan, app);
  }

  @Transactional
  public FollowUpPlanResponse update(String applicationId, String planId, FollowUpPlanDTO command) {
    var app = applications.requireLockedFollowUpApplication(applicationId);
    blacklist.requireRecruitmentAllowed(app.getCandidateId());
    var plan = requirePlan(applicationId, planId);
    requireScheduledVersion(plan, command.getVersion()); validate(command);
    String before = JsonText.write(plan); apply(plan, command);
    if (!plans.updateById(plan)) throw PlatformExceptions.conflict("FOLLOW_UP_PLAN_CHANGED", "预约已变化，请刷新");
    lifecycle.audit(plan, before, "RESCHEDULED", plan.getNote(), authorization.currentActor().getUserId(), clock.instant());
    return response(plan, app);
  }

  @Transactional
  public FollowUpPlanResponse complete(String applicationId, String planId, long version, String reason,
      String result, String nextPlan, String stepId, Long stepVersion, String rawKey) {
    String key = IdempotencyKey.fromHttpHeader(rawKey);
    var app = applications.requireLockedFollowUpAccess(applicationId);
    String actor = authorization.currentActor().getUserId();
    String action = "FOLLOW_UP_PLAN_COMPLETE";
    String fingerprint = Sha256Utils.fingerprintObjects(31, applicationId, planId, version,
        reason, result, nextPlan, stepId, stepVersion);
    // 首查不锁空键的间隙，避免不同活动的并发新键插入相互等待；唯一键失败后再当前读。
    var replay = idempotency.findIdempotency(actor, key);
    if (replay == null && applicationPersistence.insertIdempotency(
        actor, key, action, fingerprint, planId, clock.instant()) != 1) {
      replay = idempotency.findIdempotencyForUpdate(actor, key);
      if (replay == null) throw PlatformExceptions.conflict("IDEMPOTENCY_RESERVATION_FAILED", "预约完成正在保存，请重试");
    }
    if (replay != null) {
      if (!action.equals(replay.getActionCode()) || !fingerprint.equals(replay.getRequestFingerprint())
          || !planId.equals(replay.getTargetId())) {
        throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "幂等键不能用于不同预约完成命令");
      }
      return response(requirePlan(applicationId, replay.getTargetId()), app);
    }
    applications.requireLockedFollowUpApplication(applicationId);
    blacklist.requireRecruitmentAllowed(app.getCandidateId());
    var plan = requirePlan(applicationId, planId);
    requireScheduledVersion(plan, version);
    if (plan.getStartsAt().isAfter(clock.instant()))
      throw PlatformExceptions.unprocessable("FOLLOW_UP_NOT_STARTED", "预约尚未到开始时间，请在实际沟通后标记完成");
    String clean = Objects.requireNonNullElse(optional(reason, "reason", 500), "未填写沟通备注");
    applications.recordPlannedCommunication(applicationId, plan.getChannel(), clean, result, nextPlan, stepId, stepVersion);
    return finish(applicationId, planId, version, clean, true);
  }

  @Transactional
  public FollowUpPlanResponse finish(String applicationId, String planId, long version, String reason, boolean completed) {
    var app = completed ? applications.requireLockedFollowUpApplication(applicationId)
        : applications.requireLockedFollowUpCancellation(applicationId);
    if (completed) blacklist.requireRecruitmentAllowed(app.getCandidateId());
    var plan = requirePlan(applicationId, planId); requireScheduledVersion(plan, version);
    if (completed && plan.getStartsAt().isAfter(clock.instant()))
      throw PlatformExceptions.unprocessable("FOLLOW_UP_NOT_STARTED", "预约尚未到开始时间，请在实际沟通后标记完成");
    String clean = Objects.requireNonNullElse(optional(reason, "reason", 500), completed ? "未填写沟通备注" : "HR 取消沟通预约"); String before = JsonText.write(plan);
    plan.setStatus(completed ? FollowUpPlanStatus.COMPLETED.getCode() : FollowUpPlanStatus.CANCELLED.getCode()); plan.setResultReason(clean);
    plan.setUpdateId(authorization.currentActor().getUserId()); plan.setUpdateTime(clock.instant());
    if (!plans.updateById(plan)) throw PlatformExceptions.conflict("FOLLOW_UP_PLAN_CHANGED", "预约已变化，请刷新");
    lifecycle.audit(plan, before, plan.getStatus(), clean, plan.getUpdateId(), clock.instant());
    return response(plan, app);
  }

  @Transactional(readOnly = true)
  public CursorPage<FollowUpPlanResponse> list(Instant from, Instant to, String applicationId,
      String jdId, String jdVersionId, String recruitmentType, String clientId, String cursor, Integer requestedLimit) {
    return list(from,to,applicationId,jdId,jdVersionId,recruitmentType,clientId,null,cursor,requestedLimit);
  }

  @Transactional(readOnly = true)
  public CursorPage<FollowUpPlanResponse> list(Instant from, Instant to, String applicationId,
      String jdId, String jdVersionId, String recruitmentType, String clientId, String status, String cursor, Integer requestedLimit) {
    if (applicationId != null) applications.requireFollowUpApplication(applicationId);
    else authorization.currentActor();
    if ((from == null || to == null) && !(from == null && to == null && applicationId != null)
        || from != null && (!from.isBefore(to) || to.isAfter(from.plusSeconds(366L * 86400))))
      throw PlatformExceptions.unprocessable("FOLLOW_UP_RANGE_INVALID", "日历必须指定不超过366天的起止日期；活动详情可不传日期");
    if (status != null && !Set.of(FollowUpPlanStatus.SCHEDULED.getCode(), FollowUpPlanStatus.COMPLETED.getCode(), FollowUpPlanStatus.CANCELLED.getCode()).contains(status))
      throw PlatformExceptions.unprocessable("FOLLOW_UP_STATUS_INVALID", "预约状态无效");
    if (recruitmentType != null && !RecruitmentType.supports(recruitmentType))
      throw PlatformExceptions.unprocessable("RECRUITMENT_TYPE_INVALID", "招聘类型无效");
    var actor = authorization.currentActor();
    String scope = CursorScopes.scope("follow-up-plans", actor.getUserId(), actor.hasCompanyWideScope(), actor.getOperationalManagedUserIds(),
        actor.visibleDepartmentIds(), actor.getPermissions(), from, to, applicationId, jdId, jdVersionId, recruitmentType, clientId, status);
    var page = VisibleCursorPageCollector.collect(StableCursor.decode(cursorCodec, scope, cursor), PageLimits.resolve(requestedLimit),
        200, cursorCodec, scope, scan -> plans.findRange(from, to, applicationId, jdId, jdVersionId, recruitmentType, clientId, status,
            scan == null ? null : scan.getSortTime(), scan == null ? null : scan.getId(), 200),
        this::visiblePlanIds, FollowUpPlan::getStartsAt, FollowUpPlan::getId);
    Map<String, Application> authorizedApps = page.getItems().isEmpty() ? Map.of()
        : applications.requireFollowUpApplications(page.getItems().stream()
            .map(FollowUpPlan::getApplicationId).distinct().toList());
    Map<String, JdClientSummary> clients = authorizedApps.isEmpty() ? Map.of()
        : demands.clientSummariesForVersions(authorizedApps.values().stream()
            .map(Application::getJdVersionId).collect(Collectors.toSet()));
    return page.map(plan -> {
      var app = authorizedApps.get(plan.getApplicationId());
      return assembler.toResponse(plan, app, clients.get(app.getJdVersionId()));
    });
  }

  private Set<String> visiblePlanIds(List<FollowUpPlan> rows) {
    if (rows.isEmpty()) return Set.of();
    var apps = applicationPersistence.listByIds(rows.stream().map(FollowUpPlan::getApplicationId).distinct().toList());
    var visible = applications.visibleFollowUpApplicationIds(apps);
    return rows.stream().filter(plan -> visible.contains(plan.getApplicationId())).map(FollowUpPlan::getId).collect(Collectors.toSet());
  }
  private FollowUpPlan requirePlan(String applicationId, String planId) {
    var plan = plans.getById(planId);
    if (plan == null || !applicationId.equals(plan.getApplicationId())) throw PlatformExceptions.notFound("FOLLOW_UP_PLAN_NOT_FOUND", "沟通预约不存在");
    return plan;
  }
  private FollowUpPlanResponse response(FollowUpPlan plan, Application app) {
    return assembler.toResponse(plan, app, demands.requireJdVersion(app.getJdVersionId()));
  }
  private void apply(FollowUpPlan plan, FollowUpPlanDTO command) {
    plan.setStartsAt(command.getStartsAt()); plan.setEndsAt(command.getEndsAt()); plan.setChannel(command.getChannel());
    plan.setNote(Objects.requireNonNullElse(optional(command.getNote(), "note", 500), "")); plan.setUpdateId(authorization.currentActor().getUserId()); plan.setUpdateTime(clock.instant());
  }
  static void requireScheduledVersion(FollowUpPlan plan, long version) {
    if (!FollowUpPlanStatus.SCHEDULED.matches(plan.getStatus()) || version != plan.getVersion())
      throw PlatformExceptions.conflict("FOLLOW_UP_PLAN_CHANGED", "只能修改当前版本的待沟通预约");
  }
  static void validate(FollowUpPlanDTO command) {
    if (command.getStartsAt() == null || command.getEndsAt() == null || !command.getStartsAt().isBefore(command.getEndsAt())
        || Duration.between(command.getStartsAt(), command.getEndsAt()).compareTo(Duration.ofHours(24)) > 0)
      throw PlatformExceptions.unprocessable("FOLLOW_UP_TIME_INVALID", "沟通预约结束时间必须晚于开始时间且不超过24小时");
    if (!CommunicationChannel.supports(command.getChannel())) throw PlatformExceptions.unprocessable("COMMUNICATION_CHANNEL_INVALID", "沟通渠道无效");
    optional(command.getNote(), "note", 500);
  }
}
