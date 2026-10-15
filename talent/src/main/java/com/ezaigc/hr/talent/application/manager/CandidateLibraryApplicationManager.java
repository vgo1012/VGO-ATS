package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.response.ResumeLifecycleResponse;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.RiskPersistenceService;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateApplicationAccessQuery;
import com.ezaigc.hr.talent.api.CandidateEngagementClaimQuery;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningTransitionService;
import com.ezaigc.hr.talent.api.ScoreRunCompletionQuery;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery.State;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import com.ezaigc.hr.talent.domain.request.SharedCandidateReclaimItemRequest;
import com.ezaigc.hr.talent.domain.request.SharedCandidateReclaimRequest;
import com.ezaigc.hr.talent.domain.response.CandidatePoolStatusResponse;
import com.ezaigc.hr.talent.domain.response.SharedCandidateReclaimResponse;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.enums.ResumeLifecyclePhase;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 候选人处理状态与共享库准入，基于当前简历及实时招聘状态。 */
@Component
public class CandidateLibraryApplicationManager implements CandidateLibraryQueryService {
  private static final ObjectMapper JSON = new ObjectMapper();
  @Resource private AuthorizationService authorization;
  @Resource private ResumeLifecyclePersistenceService lifecycle;
  @Resource private ResumeLifecycleApplicationManager lifecycleManager;
  @Resource private CandidateResumeVersionService routing;
  @Resource private CandidatePersistenceService candidates;
  @Resource private TalentIntakePersistenceService resumes;
  @Resource private IdentityConflictPersistenceService identityConflicts;
  @Resource private CandidateRecruitmentStateQuery recruitment;
  @Resource private CandidateScreeningStateQuery screening;
  @Resource private CandidateScreeningTransitionService screeningTransitions;
  @Resource private TalentProcessEventPersistenceService events;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private CandidateJobContextApplicationManager jobContexts;
  @Resource private JobContextQuery jobs;
  @Resource private CandidateApplicationAccessQuery applicationAccess;
  @Resource private RiskPersistenceService risks;
  @Resource private CandidateEngagementClaimQuery engagementClaims;
  @Resource private ResumeJdScoreBindingPersistenceService scoreBindings;
  @Resource private ScoreRunCompletionQuery scoreRuns;
  @Resource private TalentIdempotencyPersistenceService idempotencies;
  @Resource private ResumeSearchProjectionRecorder searchProjection;

  @Override
  @Transactional(readOnly = true)
  public Set<String> eligibleCandidateIds(Collection<String> candidateIds) {
    return statusesInternal(candidateIds).stream()
        .filter(CandidatePoolStatusResponse::isLibraryEligible)
        .map(CandidatePoolStatusResponse::getCandidateId)
        .collect(Collectors.toSet());
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> personalPoolCandidateIds(Collection<String> candidateIds) {
    authorization.requirePermission("talent.read");
    if (candidateIds == null || candidateIds.isEmpty()) return Set.of();
    if (candidateIds.size() > 1000)
      throw PlatformExceptions.unprocessable("TOO_MANY_CANDIDATES", "候选人最多1000项");
    var actor = authorization.currentActor();
    String actorId = actor.getUserId();
    var ownerRows =
        candidates.listByIds(candidateIds).stream()
            .filter(row -> CandidateStatus.ACTIVE.matches(row.getStatus()))
            .filter(row -> actor.hasCompanyWideScope()
                || actorId.equals(row.getOwnerUserId())
                || actor.getOperationalManagedUserIds().contains(row.getOwnerUserId()))
            .toList();
    Set<String> ownerScoped = ownerRows.stream().map(Candidate::getId).collect(Collectors.toSet());
    if (ownerScoped.isEmpty()) return Set.of();
    var snapshot = statusSnapshot(ownerScoped, ownerRows);
    return personalPoolCandidateIds(snapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasPersonalPoolResumes(Collection<String> jdVersionIds) {
    if (jdVersionIds == null || jdVersionIds.isEmpty()) return false;
    Set<String> versions = Set.copyOf(jdVersionIds);
    for (String jdVersionId : versions) {
      String afterId = null;
      while (true) {
        var candidateIds = resumes.findCandidateIdsForJdVersion(jdVersionId, afterId, 1000);
        if (candidateIds.isEmpty()) break;
        var snapshot = statusSnapshot(candidateIds);
        var personal = personalPoolCandidateIds(snapshot);
        if (!personal.isEmpty()) {
          // 岗位上下文保留领取绑定语义，不能把旧收件或旧评分绑定当作本轮个人池关联。
          if (jobContexts.hasCurrentPersonalPoolAssociation(List.copyOf(personal), versions)) return true;
        }
        if (candidateIds.size() < 1000) break;
        afterId = candidateIds.getLast();
      }
    }
    return false;
  }

  private Set<String> personalPoolCandidateIds(StatusSnapshot snapshot) {
    Set<String> shared = snapshot.statuses().stream()
        .filter(CandidatePoolStatusResponse::isLibraryEligible)
        .map(CandidatePoolStatusResponse::getCandidateId)
        .collect(Collectors.toSet());
    Map<String, List<CandidateRecruitmentStateQuery.State>> states = snapshot.recruitmentStates();
    var currentVersions = snapshot.currentResumeVersions();
    Set<String> admittedVersions = snapshot.admittedResumeVersionIds();
    var candidateById = snapshot.candidates().stream().collect(Collectors.toMap(Candidate::getId, row -> row));
    return snapshot.candidates().stream()
        .filter(candidate -> CandidateStatus.ACTIVE.matches(candidate.getStatus()))
        .map(Candidate::getId)
        .filter(candidateId -> !shared.contains(candidateId))
        .filter(candidateId -> !snapshot.blacklistedCandidateIds().contains(candidateId))
        .filter(currentVersions::containsKey)
        .filter(candidateId -> currentVersions.get(candidateId).stream().noneMatch(admittedVersions::contains))
        .filter(candidateId -> states.getOrDefault(candidateId, List.of()).stream()
            .noneMatch(state -> currentVersions.get(candidateId).contains(state.getResumeVersionId())
                && (state.isBlocksLibrary() || unclaimedTermination(state,
                    candidateById.get(candidateId)))))
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> personalPoolCandidateIds(Collection<String> candidateIds, String jdId) {
    return filterByJob(personalPoolCandidateIds(candidateIds), jdId);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> eligibleCandidateIds(Collection<String> candidateIds, String jdId) {
    var shared = eligibleCandidateIds(candidateIds);
    return filterByJob(shared, jdId, shared);
  }

  private Set<String> filterByJob(Set<String> eligible, String jdId) {
    return filterByJob(eligible, jdId, Set.of());
  }

  private Set<String> filterByJob(Set<String> eligible, String jdId, Set<String> shared) {
    if (jdId == null || eligible.isEmpty()) return eligible;
    var currentVersions = resumes.findCurrentCandidateResumes(List.copyOf(eligible)).stream()
        .collect(Collectors.groupingBy(CandidateResumeRow::getCandidateId,
            Collectors.mapping(CandidateResumeRow::getResumeVersionId, Collectors.toSet())));
    var contexts = shared.isEmpty() ? jobContexts.contexts(List.copyOf(eligible), recruitment.states(eligible))
        : jobContexts.contexts(List.copyOf(eligible), recruitment.states(eligible), shared);
    return eligible.stream()
        .filter(id -> contexts.getOrDefault(id, List.of()).stream()
            .anyMatch(context -> jdId.equals(context.getJdId())
                && (shared.contains(id) || currentVersions.getOrDefault(id, Set.of()).contains(context.getResumeVersionId()))))
        .collect(Collectors.toUnmodifiableSet());
  }

  @Transactional(readOnly = true)
  public List<CandidatePoolStatusResponse> statuses(Collection<String> candidateIds) {
    var snapshot = statusSnapshot(candidateIds);
    var base = snapshot.statuses();
    var actor = authorization.currentActor();
    var ownerScoped =
        snapshot.candidates().stream()
            .filter(row -> actor.hasCompanyWideScope()
                || actor.getUserId().equals(row.getOwnerUserId())
                || actor.getOperationalManagedUserIds().contains(row.getOwnerUserId()))
            .map(Candidate::getId)
            .collect(Collectors.toSet());
    var related = applicationAccess.accessibleResumeVersionIds(candidateIds, actor.getUserId());
    var visible =
        base.stream()
            .filter(row -> ownerScoped.contains(row.getCandidateId())
                || row.isLibraryEligible() || related.containsKey(row.getCandidateId()))
            .toList();
    var visibleIds = visible.stream().map(CandidatePoolStatusResponse::getCandidateId).toList();
    if (visibleIds.isEmpty()) return List.of();
    Map<String, List<State>> visibleRecruitmentStates = new HashMap<>();
    visibleIds.forEach(
        id ->
            visibleRecruitmentStates.put(
                id, snapshot.recruitmentStates().getOrDefault(id, List.of())));
    var sharedIds = visible.stream().filter(CandidatePoolStatusResponse::isLibraryEligible)
        .map(CandidatePoolStatusResponse::getCandidateId).collect(Collectors.toSet());
    var contexts = sharedIds.isEmpty() ? jobContexts.contexts(visibleIds, visibleRecruitmentStates)
        : jobContexts.contexts(visibleIds, visibleRecruitmentStates, sharedIds);
    return visible.stream()
        .map(
            row ->
                new CandidatePoolStatusResponse(
                    row.getCandidateId(),
                    row.getCandidateName(),
                    row.getResumeVersionId(),
                    row.isLibraryEligible(),
                    row.isScreenedOut(),
                    row.isCanScreenOut(),
                    row.getOpenIdentityConflictCount(),
                    row.getStages(),
                    contexts.getOrDefault(row.getCandidateId(), List.of()), row.getLifecycle()))
        .toList();
  }

  private List<CandidatePoolStatusResponse> statusesInternal(Collection<String> candidateIds) {
    return statusSnapshot(candidateIds).statuses();
  }

  private StatusSnapshot statusSnapshot(Collection<String> candidateIds) {
    return statusSnapshot(candidateIds, null);
  }

  private StatusSnapshot statusSnapshot(Collection<String> candidateIds, List<Candidate> candidateRows) {
    authorization.currentActor();
    if (candidateIds.size() > 1000)
      throw PlatformExceptions.unprocessable("TOO_MANY_CANDIDATES", "候选人最多1000项");
    if (candidateIds.isEmpty()) {
      return new StatusSnapshot(List.of(), List.of(), Map.of(), Map.of(), Set.of(), Set.of());
    }
    var actor = authorization.currentActor();
    var rows = candidateRows == null ? candidates.listByIds(candidateIds) : candidateRows;
    Map<String, String> versions = new HashMap<>();
    var currentResumes = resumes.findCurrentCandidateResumes(List.copyOf(candidateIds));
    for (var version : currentResumes) {
      versions.put(version.getCandidateId(), version.getResumeVersionId());
    }
    var applications = recruitment.states(candidateIds);
    var admitted = screening.activeResumeVersionIds(candidateIds);
    Set<String> blacklisted = Set.copyOf(risks.blacklistTags(List.copyOf(candidateIds), clock.instant()));
    var currentResumeVersions = currentResumes.stream()
        .collect(Collectors.groupingBy(CandidateResumeRow::getCandidateId,
            Collectors.mapping(CandidateResumeRow::getResumeVersionId, Collectors.toSet())));
    Map<String, ResumeLifecycle> cycleRows = lifecycle.list(Wrappers.<ResumeLifecycle>lambdaQuery()
        .in(ResumeLifecycle::getCandidateId,candidateIds).eq(ResumeLifecycle::getActiveMarker,"Y"))
        .stream().collect(Collectors.toMap(ResumeLifecycle::getResumeVersionId,r -> r));
    var conflictCounts = identityConflicts
        .countOpenConflictsByCandidateIds(candidateIds, actor.getUserId(), actor.hasCompanyWideScope(),
            actor.getOperationalManagedUserIds(), Set.of()).stream()
        .collect(Collectors.toMap(
            value -> value.getCandidateId(), value -> value.getOpenIdentityConflictCount()));

    List<CandidatePoolStatusResponse> result = new ArrayList<>();
    for (var row : rows) {
      if (!CandidateStatus.ACTIVE.matches(row.getStatus())) continue;
      var version = versions.get(row.getId());
      var states = applications.getOrDefault(row.getId(), List.of());
      boolean blacklistedCandidate = blacklisted.contains(row.getId());
      boolean blocked = states.stream().anyMatch(CandidateRecruitmentStateQuery.State::isBlocksLibrary);
      var cycle = cycleRows.get(version);
      boolean autoShared = cycle != null && ResumeLifecyclePhase.SHARED.matches(cycle.getPhase())
          && !admitted.contains(version) && unclaimedAutomaticSharing(cycle, row);
      boolean recruitmentShared = states.stream().anyMatch(state -> version != null
          && version.equals(state.getResumeVersionId())
          && row.getOwnerUserId().equals(state.getOwnerUserId())
          && unclaimedTermination(state, row));
      boolean screenedOut =
          version != null
              && (version.equals(
                      metadata(row.getExt()).path("screenedOutResumeVersionId").asString())
                  || states.stream()
                      .anyMatch(
                          state ->
                              state.isScreenedOut()
                                  && unclaimedTermination(state, row)
                                  && (row.getOwnerUserId().equals(state.getOwnerUserId()) || state.isBlacklistRecorded())
                                  && version.equals(state.getResumeVersionId())));
      result.add(
          new CandidatePoolStatusResponse(
              row.getId(),
              row.getFullName(),
              version,
              version != null && (screenedOut || autoShared || recruitmentShared || blacklistedCandidate) && !blocked,
              screenedOut,
              version != null
                  && !blocked
                  && !blacklistedCandidate
                  && !screenedOut && !autoShared && !recruitmentShared
                  && actor.hasPermission("talent.manage")
                  && actor.getUserId().equals(row.getOwnerUserId()),
              conflictCounts.getOrDefault(row.getId(), 0),
              states.stream()
                  .map(CandidateRecruitmentStateQuery.State::getStage)
                  .distinct()
                  .toList(),
              List.of(), lifecycleResponse(cycle)));
    }
    return new StatusSnapshot(result, rows, applications, currentResumeVersions, admitted, blacklisted);
  }

  private static ResumeLifecycleResponse lifecycleResponse(ResumeLifecycle cycle) {
    if (cycle == null) return null;
    return new ResumeLifecycleResponse(cycle.getId(),cycle.getPhase(),cycle.getWarningLevel(),
        cycle.getEnteredAt(),cycle.getElapsedWorkdays(),cycle.getNextCheckAt(),cycle.getShareDueAt(),
        cycle.getPauseReason(),cycle.getSharedAt(),cycle.getSharedAt()==null ? null : "AUTO_TIMEOUT",
        String.valueOf(cycle.getVersion()));
  }

  private static boolean unclaimedTermination(State state, Candidate candidate) {
    if (state.isBlocksLibrary()) return false;
    var ext = metadata(candidate.getExt());
    if (!state.getResumeVersionId().equals(ext.path("reclaimedResumeVersionId").asString())) return true;
    Instant reclaimedAt = timestamp(ext, "reclaimedAt");
    return reclaimedAt == null || (state.getTerminalAt() != null
        && !state.getTerminalAt().isBefore(reclaimedAt));
  }

  private static boolean unclaimedAutomaticSharing(ResumeLifecycle cycle, Candidate candidate) {
    var ext = metadata(candidate.getExt());
    if (!cycle.getResumeVersionId().equals(ext.path("reclaimedResumeVersionId").asString())) return true;
    Instant reclaimedAt = timestamp(ext, "reclaimedAt");
    Instant sharedAt = cycle.getSharedAt() == null ? cycle.getEnteredAt() : cycle.getSharedAt();
    return reclaimedAt == null || sharedAt == null || !sharedAt.isBefore(reclaimedAt);
  }

  private static Instant timestamp(ObjectNode ext, String key) {
    String value = ext.path(key).asString();
    return value == null || value.isBlank() ? null : Instant.parse(value);
  }

  @Override
  public Set<String> personalPoolCandidateIds(Collection<String> candidateIds,String jdId,String poolStage) {
    var eligible = personalPoolCandidateIds(candidateIds,jdId);
    if (poolStage == null || eligible.isEmpty()) return eligible;
    if (!ResumeLifecyclePhase.isPersonalPool(poolStage))
      throw PlatformExceptions.badRequest("INVALID_POOL_STAGE","简历分类无效");
    return statusesInternal(eligible).stream().filter(r -> poolStage.equals(r.getLifecycle()==null
        ? ResumeLifecyclePhase.PENDING_SCREENING.getCode() : r.getLifecycle().getPhase()))
        .map(CandidatePoolStatusResponse::getCandidateId).collect(Collectors.toSet());
  }

  @Getter(AccessLevel.PRIVATE)
  @Accessors(fluent = true)
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class StatusSnapshot {
    private final List<CandidatePoolStatusResponse> statuses;
    private final List<Candidate> candidates;
    private final Map<String, List<State>> recruitmentStates;
    private final Map<String, Set<String>> currentResumeVersions;
    private final Set<String> admittedResumeVersionIds;
    private final Set<String> blacklistedCandidateIds;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CandidatePoolStatusResponse screenOut(String candidateId, TalentVersionReasonDTO command) {
    authorization.requirePermission("talent.manage");
    var actor = authorization.currentActor();
    var candidate = candidates.findCandidateForUpdate(candidateId);
    if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) {
      throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在或已停用");
    }
    if (!actor.getUserId().equals(candidate.getOwnerUserId())) {
      throw PlatformExceptions.forbidden("CANDIDATE_OWNER_REQUIRED", "请由候选人归属人完成筛选处理");
    }
    var currentResume = resumes.findCurrentCandidateResumes(List.of(candidateId));
    if (!currentResume.isEmpty()) routing.lockForRouting(candidateId,currentResume.getFirst().getResumeVersionId());
    var status = statusesInternal(List.of(candidateId)).getFirst();
    if (command.getResumeVersionId() != null
        && !command.getResumeVersionId().equals(status.getResumeVersionId())) {
      throw PlatformExceptions.conflict("RESUME_VERSION_CHANGED", "当前简历已更新，请核对最新简历后重新处理");
    }
    if (!status.isCanScreenOut())
      throw PlatformExceptions.conflict(
          "CANDIDATE_NOT_READY_FOR_LIBRARY", "当前简历已处理、缺少简历或仍有招聘流程，不能转入共享库");
    String reason = command.getReason() == null ? "" : command.getReason().strip();
    if (reason.isEmpty() || reason.length() > 500)
      throw PlatformExceptions.unprocessable("REASON_REQUIRED", "请填写500字以内的筛选原因");
    Instant now = clock.instant();
    var ext = metadata(candidate.getExt());
    ext.put("screenedOutResumeVersionId", status.getResumeVersionId());
    ext.put("screenedOutByUserId", actor.getUserId());
    ext.put("screenedOutReason", reason);
    ext.put("screenedOutAt", now.toString());
    if (candidates.updateLibraryReview(
            candidateId, command.getVersion(), actor.getUserId(), ext.toString(), now)
        != 1) {
      throw PlatformExceptions.conflict("RESOURCE_VERSION_CONFLICT", "候选人资料已更新，请刷新后重新处理");
    }
    screeningTransitions.complete(candidateId, status.getResumeVersionId(), actor.getUserId(), now);
    events.insertEvent(
        ids.newId(),
        "CANDIDATE_SCREENED_OUT",
        actor.getUserId(),
        "CANDIDATE",
        candidateId,
        "简历版本=" + status.getResumeVersionId() + "；" + reason,
        now);
    lifecycleManager.routed(candidateId,status.getResumeVersionId(),"MANUAL_SHARED");
    return statusesInternal(List.of(candidateId)).getFirst();
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SharedCandidateReclaimResponse reclaim(
      SharedCandidateReclaimRequest request, String rawIdempotencyKey) {
    authorization.requirePermission("talent.manage");
    var actor = authorization.currentActor();
    String key = IdempotencyKey.fromHttpHeader(rawIdempotencyKey);
    var items = normalizeReclaims(request.getItems());
    var bindings = scoreBindings.listByIds(
        items.stream().map(SharedCandidateReclaimItemRequest::getScoreBindingId).toList()).stream()
        .collect(Collectors.toMap(value -> value.getId(), value -> value));
    var successfulRuns = scoreRuns.successfulRunIds(bindings.values().stream()
        .map(value -> value.getDefaultMatchRunId()).toList());
    String fingerprint = Sha256Utils.fingerprintStrings(
        "SHARED_CANDIDATE_RECLAIM", 0, items.stream()
            .map(item -> item.getCandidateId() + ":" + item.getResumeVersionId() + ":"
                + item.getCandidateVersion() + ":" + item.getScoreBindingId())
            .toArray(String[]::new));
    var replay = idempotencies.findIdempotency(actor.getUserId(), key);
    if (replay != null) {
      requireReplay(replay.getActionCode(), replay.getRequestFingerprint(),
          "SHARED_CANDIDATE_RECLAIM", fingerprint);
      return new SharedCandidateReclaimResponse(items.size(),
          items.stream().map(SharedCandidateReclaimItemRequest::getCandidateId).toList());
    }
    String operationId = ids.newId();
    if (resumes.insertIdempotency(actor.getUserId(), key, "SHARED_CANDIDATE_RECLAIM",
        fingerprint, operationId, clock.instant()) != 1) {
      replay = idempotencies.findIdempotency(actor.getUserId(), key);
      if (replay == null) throw PlatformExceptions.conflict("IDEMPOTENCY_IN_PROGRESS", "领取请求正在处理中");
      requireReplay(replay.getActionCode(), replay.getRequestFingerprint(),
          "SHARED_CANDIDATE_RECLAIM", fingerprint);
      return new SharedCandidateReclaimResponse(items.size(),
          items.stream().map(SharedCandidateReclaimItemRequest::getCandidateId).toList());
    }
    Instant now = clock.instant();
    for (var item : items) {
      candidates.lockCandidate(item.getCandidateId());
      routing.lockForRouting(item.getCandidateId(), item.getResumeVersionId());
      Candidate candidate = candidates.findCandidateForUpdate(item.getCandidateId());
      if (candidate == null || !CandidateStatus.ACTIVE.matches(candidate.getStatus())) {
        throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在或已停用");
      }
      if (candidate.getVersion() != item.getCandidateVersion()) {
        throw PlatformExceptions.conflict("RESOURCE_VERSION_CONFLICT", "候选人资料已更新，请刷新后重新领取");
      }
      Set<String> currentVersions = resumes.findCurrentCandidateResumes(List.of(candidate.getId())).stream()
          .map(CandidateResumeRow::getResumeVersionId).collect(Collectors.toSet());
      if (!currentVersions.contains(item.getResumeVersionId())) {
        throw PlatformExceptions.conflict("RESUME_VERSION_CHANGED", "当前简历已更新，请刷新后重新领取");
      }
      var sharedSnapshot = statusSnapshot(List.of(candidate.getId()));
      var status = sharedSnapshot.statuses().getFirst();
      if (!status.isLibraryEligible() || !item.getResumeVersionId().equals(status.getResumeVersionId())) {
        throw PlatformExceptions.conflict("CANDIDATE_NOT_IN_SHARED_LIBRARY", "候选人已离开共享库，请刷新后重试");
      }
      var binding = bindings.get(item.getScoreBindingId());
      if (binding == null
          || !candidate.getId().equals(binding.getCandidateId())
          || !item.getResumeVersionId().equals(binding.getResumeVersionId())
          || !actor.getUserId().equals(binding.getSelectedByUserId())) {
        throw PlatformExceptions.conflict(
            "SHARED_RECLAIM_SCORE_BINDING_INVALID", "当前转入请求没有匹配的 JD 评分记录，请重新选择 JD 评分");
      }
      var lifecycle = status.getLifecycle();
      Instant currentSharedAt = lifecycle == null ? null
          : lifecycle.getSharedAt() == null ? lifecycle.getEnteredAt() : lifecycle.getSharedAt();
      for (var state : sharedSnapshot.recruitmentStates().getOrDefault(candidate.getId(), List.of())) {
        if (item.getResumeVersionId().equals(state.getResumeVersionId())
            && unclaimedTermination(state, candidate) && state.getTerminalAt() != null
            && (currentSharedAt == null || state.getTerminalAt().isAfter(currentSharedAt))) {
          currentSharedAt = state.getTerminalAt();
        }
      }
      Instant manualSharedAt = timestamp(metadata(candidate.getExt()), "screenedOutAt");
      if (manualSharedAt != null && (currentSharedAt == null || manualSharedAt.isAfter(currentSharedAt))) {
        currentSharedAt = manualSharedAt;
      }
      if (currentSharedAt != null && binding.getSelectedAt().isBefore(currentSharedAt)) {
        throw PlatformExceptions.conflict(
            "SHARED_RECLAIM_SCORE_STALE", "该评分早于本次进入共享库，请重新选择 JD 评分");
      }
      if (!successfulRuns.contains(binding.getDefaultMatchRunId())) {
        throw PlatformExceptions.conflict(
            "SHARED_RECLAIM_SCORE_NOT_SUCCEEDED", "AI 评分尚未成功，简历仍保留在共享库");
      }
      if (risks.blacklistTags(List.of(candidate.getId()), now).contains(candidate.getId())) {
        throw PlatformExceptions.conflict("CANDIDATE_BLACKLISTED", "黑名单候选人不能领取到我的简历");
      }
      if (!engagementClaims.claimableBy(candidate.getId(), actor.getUserId())) {
        throw PlatformExceptions.conflict("CANDIDATE_ENGAGEMENT_LOCKED", "候选人正由其他招聘人员跟进");
      }
      jobs.requireVersionForNewUse(binding.getJdVersionId());
      ObjectNode ext = metadata(candidate.getExt());
      ext.remove("screenedOutResumeVersionId");
      ext.remove("screenedOutByUserId");
      ext.remove("screenedOutReason");
      ext.remove("screenedOutAt");
      ext.put("reclaimedResumeVersionId", item.getResumeVersionId());
      ext.put("reclaimedScoreBindingId", binding.getId());
      ext.put("reclaimedAt", now.toString());
      ext.put("reclaimedFromOwnerUserId", candidate.getOwnerUserId());
      String encodedExt = ext.isEmpty() ? null : ext.toString();
      if (candidates.reclaimFromLibrary(candidate.getId(), candidate.getVersion(), actor.getUserId(),
          actor.getHomeDepartmentId(), encodedExt, actor.getUserId(), now) != 1) {
        throw PlatformExceptions.conflict("RESOURCE_VERSION_CONFLICT", "候选人资料已更新，请刷新后重新领取");
      }
      lifecycleManager.reclaimed(candidate.getId(), item.getResumeVersionId());
      var event = new TalentProcessEvent();
      event.setId(ids.newId());
      event.setEventType("SHARED_CANDIDATE_RECLAIMED");
      event.setActorUserId(actor.getUserId());
      event.setTargetType("CANDIDATE");
      event.setTargetId(candidate.getId());
      event.setOccurredAt(now);
      event.setReason("共享简历领取到我的简历");
      event.setExt(JSON.writeValueAsString(Map.of(
          "operationId", operationId,
          "resumeVersionId", item.getResumeVersionId(),
          "jdVersionId", binding.getJdVersionId(),
          "defaultMatchRunId", binding.getDefaultMatchRunId(),
          "fromOwnerUserId", candidate.getOwnerUserId(),
          "toOwnerUserId", actor.getUserId())));
      events.save(event);
      searchProjection.candidateChanged(candidate.getId(), "reclaim:" + operationId,
          actor.getUserId(), actor.getUserId(), actor.getHomeDepartmentId());
    }
    return new SharedCandidateReclaimResponse(items.size(),
        items.stream().map(SharedCandidateReclaimItemRequest::getCandidateId).toList());
  }

  private static List<SharedCandidateReclaimItemRequest> normalizeReclaims(
      List<SharedCandidateReclaimItemRequest> values) {
    Map<String, SharedCandidateReclaimItemRequest> unique = new LinkedHashMap<>();
    for (var value : values) unique.putIfAbsent(value.getCandidateId() + ":" + value.getResumeVersionId(), value);
    if (unique.size() != values.size()) {
      throw PlatformExceptions.unprocessable("DUPLICATE_RECLAIM_ITEM", "领取列表包含重复候选人");
    }
    return unique.values().stream().sorted(Comparator.comparing(SharedCandidateReclaimItemRequest::getCandidateId)
        .thenComparing(SharedCandidateReclaimItemRequest::getResumeVersionId)).toList();
  }

  private static void requireReplay(String action, String storedFingerprint, String expectedAction,
      String fingerprint) {
    if (!expectedAction.equals(action) || !fingerprint.equals(storedFingerprint)) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同请求");
    }
  }

  private static ObjectNode metadata(String ext) {
    if (ext == null || ext.isBlank()) return JSON.createObjectNode();
    try {
      return (ObjectNode) JSON.readTree(ext);
    } catch (Exception error) {
      throw new IllegalStateException("候选人扩展字段格式错误", error);
    }
  }
}
