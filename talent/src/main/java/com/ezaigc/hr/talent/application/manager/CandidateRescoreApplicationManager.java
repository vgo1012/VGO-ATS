package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningTransitionService;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.domain.request.CandidateRescoreItemRequest;
import com.ezaigc.hr.talent.domain.request.CandidateRescoreRequest;
import com.ezaigc.hr.talent.domain.response.CandidateRescoreItemResponse;
import com.ezaigc.hr.talent.domain.response.CandidateRescoreResponse;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 候选人重新评分命令编排，与候选库读取策略隔离，避免评分事实查询反向依赖候选库。 */
@Component
public class CandidateRescoreApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private CandidatePersistenceService candidates;
  @Resource private CandidateLibraryQueryService library;
  @Resource private CandidateRecruitmentStateQuery recruitment;
  @Resource private CandidateResumeVersionService routing;
  @Resource private CandidateScreeningStateQuery screening;
  @Resource private CandidateScreeningTransitionService screeningTransitions;
  @Resource private ResumeIntakeScoringService intakeScoring;
  @Resource private ResumeJdScoreBindingPersistenceService scoreBindings;
  @Resource private TalentIdempotencyPersistenceService idempotencies;
  @Resource private TalentIntakePersistenceService resumes;
  @Resource private TalentProcessEventPersistenceService events;
  @Resource private JobContextQuery jobs;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CandidateRescoreResponse rescore(CandidateRescoreRequest request, String rawIdempotencyKey) {
    authorization.requirePermission("talent.manage");
    authorization.requirePermission("matching.run");
    var actor = authorization.currentActor();
    String key = IdempotencyKey.fromHttpHeader(rawIdempotencyKey);
    var job = jobs.requireVisibleVersion(request.getJdVersionId());
    var items = normalizeRescores(request.getItems());
    String fingerprint =
        Sha256Utils.fingerprintStrings(
            job.getJdVersionId(),
            0,
            items.stream()
                .map(item -> item.getCandidateId() + ":" + item.getResumeVersionId())
                .toArray(String[]::new));
    var replay = idempotencies.findIdempotency(actor.getUserId(), key);
    if (replay != null) {
      requireReplay(
          replay.getActionCode(),
          replay.getRequestFingerprint(),
          "SHARED_RECLAIM_SCORE",
          fingerprint);
      return rescoreResponse(
          replay.getTargetId(), scoreBindings.findByOperation(replay.getTargetId()), actor.getUserId());
    }
    String operationId = ids.newId();
    if (resumes.insertIdempotency(
            actor.getUserId(),
            key,
            "SHARED_RECLAIM_SCORE",
            fingerprint,
            operationId,
            clock.instant())
        != 1) {
      replay = idempotencies.findIdempotency(actor.getUserId(), key);
      if (replay == null) {
        throw PlatformExceptions.conflict("IDEMPOTENCY_IN_PROGRESS", "重新评分请求正在处理中");
      }
      requireReplay(
          replay.getActionCode(),
          replay.getRequestFingerprint(),
          "SHARED_RECLAIM_SCORE",
          fingerprint);
      return rescoreResponse(
          replay.getTargetId(), scoreBindings.findByOperation(replay.getTargetId()), actor.getUserId());
    }
    Instant now = clock.instant();
    for (var item : items) {
      candidates.lockCandidate(item.getCandidateId());
      routing.lockForRouting(item.getCandidateId(), item.getResumeVersionId());
      Candidate candidate = candidates.findCandidateForUpdate(item.getCandidateId());
      if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) {
        throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在或已停用");
      }
      if (!library.eligibleCandidateIds(List.of(candidate.getId())).contains(candidate.getId())) {
        throw PlatformExceptions.conflict(
            "CANDIDATE_NOT_IN_SHARED_LIBRARY", "只有仍在共享简历库的候选人可以选择 JD 并评分");
      }
      Set<String> currentVersions =
          resumes.findCurrentCandidateResumes(List.of(candidate.getId())).stream()
              .map(CandidateResumeRow::getResumeVersionId)
              .collect(Collectors.toSet());
      if (!currentVersions.contains(item.getResumeVersionId())) {
        throw PlatformExceptions.conflict("RESUME_VERSION_CHANGED", "当前简历已更新，请刷新后重新评分");
      }
      if (recruitment.hasActiveRecruitment(item.getResumeVersionId())) {
        throw PlatformExceptions.conflict("RECRUITMENT_ACTIVE", "候选人仍在招聘流程中，不能重新评分");
      }
      if (screening
          .activeResumeVersionIds(List.of(candidate.getId()))
          .contains(item.getResumeVersionId())) {
        boolean archivedRecruitment = recruitment.states(List.of(candidate.getId()))
            .getOrDefault(candidate.getId(), List.of()).stream()
            .anyMatch(state -> item.getResumeVersionId().equals(state.getResumeVersionId())
                && candidate.getOwnerUserId().equals(state.getOwnerUserId()) && !state.isBlocksLibrary());
        if (!archivedRecruitment) {
          throw PlatformExceptions.conflict("SCREENING_ACTIVE", "候选人仍在智能筛选中，请先完成或退回");
        }
        // 兼容旧流程取消后仍未结束的筛选准入，保留准入历史并允许新评分周期。
        screeningTransitions.complete(candidate.getId(), item.getResumeVersionId(), actor.getUserId(), now);
      }
      var task =
          intakeScoring.enqueueRescore(
              job.getJdVersionId(), item.getResumeVersionId(), actor.getUserId(),
              operationId + ":" + candidate.getId());
      var binding = new ResumeJdScoreBinding();
      binding.setId(ids.newId());
      binding.setOperationId(operationId);
      binding.setCandidateId(candidate.getId());
      binding.setResumeVersionId(item.getResumeVersionId());
      binding.setJdId(job.getJdId());
      binding.setJdVersionId(job.getJdVersionId());
      binding.setDefaultMatchRunId(task.runId());
      binding.setSelectedByUserId(actor.getUserId());
      binding.setOwnerDepartmentId(actor.getHomeDepartmentId());
      binding.setSelectedAt(now);
      binding.setCreateId(actor.getUserId());
      binding.setCreateTime(now);
      binding.setUpdateId(actor.getUserId());
      binding.setUpdateTime(now);
      if (!scoreBindings.save(binding)) {
        throw new IllegalStateException("重新评分绑定写入失败");
      }
      events.insertEvent(
          ids.newId(),
          "SHARED_RECLAIM_SCORE_REQUESTED",
          actor.getUserId(),
          "CANDIDATE",
          candidate.getId(),
          "JD版本=" + job.getJdVersionId(),
          now);
    }
    return rescoreResponse(
        operationId, scoreBindings.findByOperation(operationId), actor.getUserId());
  }

  private static List<CandidateRescoreItemRequest> normalizeRescores(
      List<CandidateRescoreItemRequest> values) {
    Map<String, CandidateRescoreItemRequest> unique = new LinkedHashMap<>();
    for (var value : values) {
      unique.putIfAbsent(value.getCandidateId() + ":" + value.getResumeVersionId(), value);
    }
    if (unique.size() != values.size()) {
      throw PlatformExceptions.unprocessable("DUPLICATE_RESCORE_ITEM", "重新评分列表包含重复候选人");
    }
    return unique.values().stream()
        .sorted(
            Comparator.comparing(CandidateRescoreItemRequest::getCandidateId)
                .thenComparing(CandidateRescoreItemRequest::getResumeVersionId))
        .toList();
  }

  private static void requireReplay(
      String action, String storedFingerprint, String expectedAction, String fingerprint) {
    if (!expectedAction.equals(action) || !fingerprint.equals(storedFingerprint)) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同请求");
    }
  }

  private CandidateRescoreResponse rescoreResponse(
      String operationId, List<ResumeJdScoreBinding> bindings, String actorUserId) {
    return new CandidateRescoreResponse(
        operationId,
        bindings.size(),
        bindings.stream()
            .map(
                value ->
                    new CandidateRescoreItemResponse(
                        value.getId(),
                        value.getCandidateId(),
                        value.getResumeVersionId(),
                        value.getJdId(),
                        value.getJdVersionId(),
                        value.getDefaultMatchRunId(),
                        intakeScoring.get(value.getDefaultMatchRunId(), actorUserId).taskId()))
            .toList());
  }
}
