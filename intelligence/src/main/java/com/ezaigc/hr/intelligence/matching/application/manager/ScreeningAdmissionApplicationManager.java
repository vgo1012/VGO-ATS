package com.ezaigc.hr.intelligence.matching.application.manager;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.require;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_RUN_RESUMES;
import static com.ezaigc.hr.recruiting.demand.api.JdFactQueryService.MAX_READ_ACCESS_BATCH_SIZE;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.pagination.CursorPages;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.common.web.version.ResourceVersion;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningAdmission;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.domain.dto.ScreeningSelectionQuery;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningSelectionCandidateResponse;
import com.ezaigc.hr.intelligence.matching.domain.request.ScreeningAdmissionItemRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.ScreeningReturnItemRequest;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningReturnBatchResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.DefaultScreeningResultResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningAdmissionBatchResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.ScreeningCandidateResponse;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunActionCode;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingIdempotencyPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.ResumeFactBatch;
import com.ezaigc.hr.talent.api.ResumeLifecycleService;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** 编排“我的简历”到“智能筛选”的显式、无重评准入。 */
@Service
public class ScreeningAdmissionApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private ResumeFactQueryService resumeFacts;
  @Resource private CandidateResumeVersionService resumeVersions;
  @Resource private CandidateDirectoryService candidates;
  @Resource private CandidateRecruitmentStateQuery recruitmentStates;
  @Resource private CompanyBlacklistQueryService blacklist;
  @Resource private JdFactQueryService jdFacts;
  @Resource private ScreeningAdmissionPersistenceService admissions;
  @Resource private MatchingIdempotencyPersistenceService idempotency;
  @Resource private MatchingAuditEventPersistenceService audit;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private ResumeLifecycleService lifecycle;
  @Resource private CursorCodec cursorCodec;

  @Transactional(readOnly = true)
  public CursorPage<ScreeningSelectionCandidateResponse> selectionCandidates(
      String jdVersionId, String requestedQuery, String cursor, int requestedLimit) {
    CurrentActorSnapshot actor = requireActor();
    String jd = require(jdVersionId, "jdVersionId");
    jdFacts.requireCurrentFactsReadAccess(jd);
    String keyword = requestedQuery == null || requestedQuery.isBlank() ? null : requestedQuery.strip();
    if (keyword != null && keyword.length() > 128) {
      throw PlatformExceptions.badRequest("SCREENING_QUERY_TOO_LONG", "姓名搜索最多 128 字");
    }
    int limit = PageLimits.resolve(requestedLimit);
    var query = new ScreeningSelectionQuery();
    query.setJdVersionId(jd);
    query.setQuery(keyword);
    query.setActorId(actor.getUserId());
    query.setScopeAll(actor.hasCompanyWideScope());
    query.setManagedUserIds(actor.getOperationalManagedUserIds());
    query.setVisibleDepartmentIds(actor.visibleDepartmentIdsFor("MATCH_RUN"));
    query.setLimit(limit + 1);
    String scope = CursorScopes.scope("matching.screening.selection", query.getActorId(), jd, keyword,
        query.isScopeAll(), query.getManagedUserIds(), query.getVisibleDepartmentIds());
    StableCursor position = StableCursor.decode(cursorCodec, scope, cursor);
    if (position != null) {
      query.setBeforeTime(position.getSortTime());
      query.setBeforeId(position.getId());
    }
    List<ScreeningCandidateRow> rows = admissions.findSelectionCandidates(query);
    verifyCurrentResumeAccess(rows);
    return CursorPages.page(rows, limit, cursorCodec, scope,
        row -> new ScreeningSelectionCandidateResponse(row.getCandidateId(), row.getCandidateName(),
            row.getResumeVersionId(), row.getResumeVersionNo(), row.getOriginalFileName(),
            row.getJdVersionId(), row.getDefaultMatchRunId(),
            row.getAdmissionId(), row.getProfessionalScore(), row.getReferenceScore()),
        ScreeningCandidateRow::getAdmittedAt, ScreeningCandidateRow::getAdmissionId);
  }

  @Transactional(readOnly = true)
  public CursorCount countSelectionCandidates(
      String jdVersionId, String requestedQuery, String cursor) {
    CursorPage<ScreeningSelectionCandidateResponse> page =
        selectionCandidates(jdVersionId, requestedQuery, cursor, 100);
    return new CursorCount(page.getItems().size(), page.getNextCursor(), page.isHasMore());
  }

  @Transactional(readOnly = true)
  public List<ScreeningCandidateResponse> candidates(List<String> requestedCandidateIds) {
    CurrentActorSnapshot actor = requireActor();
    boolean admittedOnly = requestedCandidateIds == null || requestedCandidateIds.isEmpty();
    List<String> candidateIds = admittedOnly ? List.of() : requestedCandidateIds.stream()
        .map(value -> require(value, "candidateIds")).distinct().toList();
    if (candidateIds.size() > MAX_MATCH_RUN_RESUMES) {
      throw PlatformExceptions.unprocessable("SCREENING_CANDIDATE_LIMIT_EXCEEDED", "候选人最多 1000 项");
    }
    List<ScreeningCandidateRow> rows = findCandidates(candidateIds, actor, admittedOnly);
    if (rows.size() > MAX_MATCH_RUN_RESUMES) {
      throw PlatformExceptions.unprocessable("SCREENING_CANDIDATE_LIMIT_EXCEEDED", "智能筛选结果超过 1000 项，请缩小范围");
    }
    verifyCurrentReadAccess(rows);
    Set<String> owned = ownedCandidates(rows.stream().map(ScreeningCandidateRow::getCandidateId).toList(), actor);
    return rows.stream().map(row -> response(row, owned)).toList();
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ScreeningAdmissionBatchResponse admit(
      List<ScreeningAdmissionItemRequest> requestedItems, String idempotencyKey) {
    CurrentActorSnapshot actor = requireActor();
    List<AdmissionItem> items = normalize(requestedItems);
    var authorizedFacts = resumeFacts.currentFactBatch(items.stream().map(AdmissionItem::resumeVersionId).distinct().toList());
    for (AdmissionItem item : items) {
      var facts = authorizedFacts.get(item.resumeVersionId());
      if (facts == null || !item.candidateId().equals(facts.getCandidateId())) {
        throw PlatformExceptions.conflict("SCREENING_IDENTITY_MISMATCH", "候选人与简历版本不匹配");
      }
    }
    // 路由先锁候选人再锁版本；跨批次也按相同顺序，避免不同历史版本反向锁候选人。
    items.stream().sorted(Comparator.comparing(AdmissionItem::candidateId)
        .thenComparing(AdmissionItem::resumeVersionId))
        .forEach(item -> {
          resumeVersions.lockForRouting(item.candidateId(), item.resumeVersionId());
          requireNotRecruited(item.resumeVersionId());
          resumeVersions.requireCurrentScore(item.candidateId(), item.resumeVersionId(),
              item.jdVersionId(), item.defaultMatchRunId());
        });
    Map<String, ScreeningCandidateRow> ready = index(findCandidates(
        items.stream().map(AdmissionItem::candidateId).distinct().toList(), actor, false));
    List<ScreeningCandidateRow> verifiedRows = new ArrayList<>(items.size());
    for (AdmissionItem item : items) {
      ScreeningCandidateRow row = ready.get(item.identityKey());
      if (row == null) {
        throw PlatformExceptions.conflict("SCREENING_DEFAULT_SCORE_REQUIRED",
            "只有公司默认评分已完成的精确简历和岗位版本才能进入智能筛选");
      }
      if (!item.defaultMatchRunId().equals(row.getDefaultMatchRunId())) {
        throw PlatformExceptions.conflict("SCREENING_DEFAULT_RUN_MISMATCH", "默认评分运行已变化，请刷新后重试");
      }
      verifiedRows.add(row);
    }
    verifyCurrentNewUseAccess(verifiedRows);
    String fingerprint = fingerprint(items);
    var replay = idempotency.findIdempotency(actor.getUserId(), idempotencyKey);
    if (replay != null) {
      requireSameCommand(replay.getActionCode(), replay.getRequestFingerprint(), fingerprint);
      return responseFor(items, actor, 0);
    }
    Instant now = clock.instant();
    if (idempotency.insertIgnore(actor.getUserId(), idempotencyKey,
        MatchRunActionCode.ADMIT_SCREENING_CANDIDATES.getCode(), fingerprint, ids.newId(), now) == 0) {
      replay = idempotency.findIdempotency(actor.getUserId(), idempotencyKey);
      if (replay == null) throw new IllegalStateException("screening admission idempotency reservation missing");
      requireSameCommand(replay.getActionCode(), replay.getRequestFingerprint(), fingerprint);
      return responseFor(items, actor, 0);
    }
    items.stream().map(AdmissionItem::candidateId).distinct().forEach(blacklist::requireRecruitmentAllowed);
    List<ScreeningAdmission> rows = items.stream()
        .map(item -> admission(item, actor, now)).toList();
    int created = admissions.insertBatchIgnore(rows);
    // 全部新建时 INSERT 已持有记录锁，审计可使用本次写入的 ID；有重复则仍锁读最新状态。
    boolean allInserted = created == rows.size();
    for (ScreeningAdmission row : rows) {
      ScreeningAdmission persisted = allInserted ? row : admissions.findByIdentity(
          row.getCandidateId(), row.getResumeVersionId(), row.getJdVersionId());
      boolean readmitted = persisted != null && persisted.getReturnedAt() != null;
      if (readmitted) {
        if (admissions.readmit(persisted.getId(), persisted.getVersion(), row.getDefaultMatchRunId(),
            actor.getUserId(), actor.getHomeDepartmentId(), now) != 1) throw stateConflict();
        created++;
      }
      if (persisted != null && (row.getId().equals(persisted.getId()) || readmitted)) {
        String detail = readmitted ? "{\"previousDefaultMatchRunId\":\"" + persisted.getDefaultMatchRunId()
            + "\",\"defaultMatchRunId\":\"" + row.getDefaultMatchRunId() + "\",\"previousVersion\":"
            + persisted.getVersion() + "}" : null;
        audit.insertAudit(ids.newId(), "SCREENING_CANDIDATE_ADMITTED", actor.getUserId(),
            "SCREENING_ADMISSION", persisted.getId(), detail, now);
      }
    }
    for (AdmissionItem item : items) lifecycle.routed(item.candidateId(),item.resumeVersionId(),"SCREENING_ADMITTED");
    return responseFor(items, actor, created);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ScreeningReturnBatchResponse returnToResumes(
      List<ScreeningReturnItemRequest> requestedItems, String idempotencyKey) {
    CurrentActorSnapshot actor = requireActor();
    if (requestedItems == null || requestedItems.isEmpty() || requestedItems.size() > MAX_MATCH_RUN_RESUMES) {
      throw PlatformExceptions.unprocessable("SCREENING_RETURN_ITEMS_INVALID", "退回项必须为 1～1000 项");
    }
    Map<String, Long> expected = new LinkedHashMap<>();
    for (ScreeningReturnItemRequest item : requestedItems) {
      if (item == null || !ResourceVersion.isValid(item.getExpectedVersion())) {
        throw PlatformExceptions.unprocessable("SCREENING_RETURN_ITEM_INVALID", "退回项必须包含有效版本");
      }
      String id = require(item.getAdmissionId(), "admissionId");
      long expectedVersion = ResourceVersion.parse(item.getExpectedVersion());
      Long previous = expected.putIfAbsent(id, expectedVersion);
      if (previous != null && previous != expectedVersion) throw stateConflict();
    }
    List<ScreeningAdmission> initial = expected.keySet().stream().map(id -> {
      ScreeningAdmission admission = admissions.getById(id);
      if (admission == null) throw PlatformExceptions.notFound("SCREENING_ADMISSION_NOT_FOUND", "准入记录不存在");
      return admission;
    }).sorted(Comparator.comparing(ScreeningAdmission::getCandidateId)
        .thenComparing(ScreeningAdmission::getResumeVersionId)
        .thenComparing(ScreeningAdmission::getId)).toList();
    // 事实与 JD 权限一次批量预装载：逐条调用批量接口会按项重复整批查询，1000 项将放大为数万次查询。
    ResumeFactBatch returnFacts = resumeFacts.currentFactBatch(initial.stream()
        .map(ScreeningAdmission::getResumeVersionId).distinct().toList());
    Set<String> owned = ownedCandidates(initial.stream().map(ScreeningAdmission::getCandidateId).toList(), actor);
    for (ScreeningAdmission row : initial) requireReturnAccess(row, returnFacts, owned);
    requireCurrentJdReadAccess(initial.stream()
        .filter(row -> !owned.contains(row.getCandidateId()))
        .map(ScreeningAdmission::getJdVersionId).distinct().toList());
    String fingerprint = Sha256Utils.fingerprintStrings("null", 0, expected.entrySet().stream()
        .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + ":" + entry.getValue()).toArray(String[]::new));
    var replay = idempotency.findIdempotency(actor.getUserId(), idempotencyKey);
    if (replay != null) {
      requireSameReturn(replay.getActionCode(), replay.getRequestFingerprint(), fingerprint);
      return new ScreeningReturnBatchResponse(expected.size(), 0, expected.size());
    }
    Instant now = clock.instant();
    if (idempotency.insertIgnore(actor.getUserId(), idempotencyKey,
        MatchRunActionCode.RETURN_SCREENING_CANDIDATES.getCode(), fingerprint, ids.newId(), now) == 0) {
      replay = idempotency.findIdempotency(actor.getUserId(), idempotencyKey);
      if (replay == null) throw new IllegalStateException("screening return idempotency reservation missing");
      requireSameReturn(replay.getActionCode(), replay.getRequestFingerprint(), fingerprint);
      return new ScreeningReturnBatchResponse(expected.size(), 0, expected.size());
    }
    List<ScreeningAdmission> locked = new ArrayList<>(initial.size());
    for (ScreeningAdmission row : initial) {
      resumeVersions.lockForRouting(row.getCandidateId(), row.getResumeVersionId());
      requireNotRecruited(row.getResumeVersionId());
      locked.add(admissions.lockAdmission(row.getId()));
    }
    Set<String> currentOwned = ownedCandidates(locked.stream().map(ScreeningAdmission::getCandidateId).toList(), actor);
    requireCurrentJdReadAccess(locked.stream()
        .filter(row -> owned.contains(row.getCandidateId()) && !currentOwned.contains(row.getCandidateId()))
        .map(ScreeningAdmission::getJdVersionId).distinct().toList());
    for (ScreeningAdmission current : locked) {
      requireReturnAccess(current, returnFacts, currentOwned);
      if (current.getReturnedAt() != null || current.getVersion().longValue() != expected.get(current.getId())) throw stateConflict();
      if (admissions.returnToResumes(current.getId(), expected.get(current.getId()), actor.getUserId(), now) != 1) {
        throw stateConflict();
      }
      audit.insertAudit(ids.newId(), "SCREENING_CANDIDATE_RETURNED", actor.getUserId(),
          "SCREENING_ADMISSION", current.getId(), null, now);
    }
    for (ScreeningAdmission row : initial) lifecycle.returned(row.getCandidateId(),row.getResumeVersionId());
    return new ScreeningReturnBatchResponse(expected.size(), expected.size(), 0);
  }

  private void requireReturnAccess(ScreeningAdmission admission, ResumeFactBatch facts, Set<String> owned) {
    if (!owned.contains(admission.getCandidateId())) {
      authorization.requireAccess("matching.run", new AuthorizationTarget("MATCH_RUN", admission.getDefaultMatchRunId(),
          admission.getAdmittedByUserId(), admission.getOwnerDepartmentId(), Set.of()));
    }
    var resume = facts.get(admission.getResumeVersionId());
    if (resume == null || !admission.getCandidateId().equals(resume.getCandidateId())) {
      throw PlatformExceptions.conflict("SCREENING_IDENTITY_MISMATCH", "候选人与简历版本不匹配");
    }
  }

  private void requireNotRecruited(String resumeVersionId) {
    if (recruitmentStates.hasActiveRecruitment(resumeVersionId)) {
      throw PlatformExceptions.conflict("SCREENING_RECRUITMENT_ACTIVE", "该简历仍在招聘流程中，不能重新准入");
    }
  }

  private Set<String> ownedCandidates(List<String> candidateIds, CurrentActorSnapshot actor) {
    return candidates.ownedCandidateIds(candidateIds.stream().distinct().toList(), actor.getUserId());
  }

  private static RuntimeException stateConflict() {
    return PlatformExceptions.conflict("SCREENING_ADMISSION_VERSION_CONFLICT", "准入状态已变化，请刷新后重试");
  }

  private static void requireSameReturn(String action, String storedFingerprint, String fingerprint) {
    if (!MatchRunActionCode.RETURN_SCREENING_CANDIDATES.matches(action) || !fingerprint.equals(storedFingerprint)) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同退回请求");
    }
  }

  private CurrentActorSnapshot requireActor() {
    authorization.requirePermission("talent.read");
    authorization.requirePermission("matching.run");
    return authorization.currentActor();
  }

  private void verifyCurrentReadAccess(Collection<ScreeningCandidateRow> rows) {
    verifyCurrentResumeAccess(rows);
    Set<String> owned = ownedCandidates(rows.stream().map(ScreeningCandidateRow::getCandidateId).toList(),
        authorization.currentActor());
    requireCurrentJdReadAccess(
        rows.stream().filter(row -> row.getAdmissionId() == null || !owned.contains(row.getCandidateId()))
            .map(ScreeningCandidateRow::getJdVersionId).distinct().toList());
  }

  /** 批量复查当前 JD 事实读权限；上游接口每批最多 200 项。 */
  private void requireCurrentJdReadAccess(List<String> jdVersionIds) {
    for (int offset = 0; offset < jdVersionIds.size(); offset += MAX_READ_ACCESS_BATCH_SIZE) {
      jdFacts.requireCurrentFactsReadAccessBatch(jdVersionIds.subList(
          offset, Math.min(offset + MAX_READ_ACCESS_BATCH_SIZE, jdVersionIds.size())));
    }
  }

  private void verifyCurrentNewUseAccess(Collection<ScreeningCandidateRow> rows) {
    verifyCurrentResumeAccess(rows);
    rows.stream().map(ScreeningCandidateRow::getJdVersionId).distinct()
        .forEach(jdFacts::currentFacts);
  }

  private void verifyCurrentResumeAccess(Collection<ScreeningCandidateRow> rows) {
    if (rows.isEmpty()) return;
    var facts = resumeFacts.currentFactBatch(rows.stream()
        .map(ScreeningCandidateRow::getResumeVersionId).distinct().toList());
    for (ScreeningCandidateRow row : rows) {
      var resume = facts.get(row.getResumeVersionId());
      if (resume == null || !row.getCandidateId().equals(resume.getCandidateId())) {
        throw PlatformExceptions.conflict("SCREENING_IDENTITY_MISMATCH", "候选人与简历版本不匹配");
      }
    }
  }

  private ScreeningAdmission admission(AdmissionItem item, CurrentActorSnapshot actor, Instant now) {
    ScreeningAdmission row = new ScreeningAdmission();
    row.setId(ids.newId());
    row.setCandidateId(item.candidateId());
    row.setResumeVersionId(item.resumeVersionId());
    row.setJdVersionId(item.jdVersionId());
    row.setDefaultMatchRunId(item.defaultMatchRunId());
    row.setAdmittedByUserId(actor.getUserId());
    row.setOwnerDepartmentId(actor.getHomeDepartmentId());
    row.setAdmittedAt(now);
    row.setCreateId(actor.getUserId());
    row.setCreateTime(now);
    row.setUpdateId(actor.getUserId());
    row.setUpdateTime(now);
    return row;
  }

  private ScreeningAdmissionBatchResponse responseFor(
      List<AdmissionItem> items, CurrentActorSnapshot actor, int created) {
    Map<String, ScreeningCandidateRow> current = index(findCandidates(
        items.stream().map(AdmissionItem::candidateId).distinct().toList(), actor, false));
    Set<String> owned = ownedCandidates(items.stream().map(AdmissionItem::candidateId).toList(), actor);
    List<ScreeningCandidateResponse> responses = items.stream()
        .map(item -> current.get(item.identityKey()))
        .map(row -> {
          if (row == null || row.getAdmissionId() == null) {
            throw stateConflict();
          }
          return response(row, owned);
        }).toList();
    return new ScreeningAdmissionBatchResponse(items.size(), created, items.size() - created, responses);
  }

  private List<ScreeningCandidateRow> findCandidates(
      List<String> candidateIds, CurrentActorSnapshot actor, boolean admittedOnly) {
    return admissions.findScreeningCandidates(
        candidateIds,
        actor.getUserId(),
        actor.hasCompanyWideScope(),
        actor.getOperationalManagedUserIds(),
        actor.visibleDepartmentIdsFor("MATCH_RUN"),
        admittedOnly);
  }

  private static ScreeningCandidateResponse response(ScreeningCandidateRow row, Set<String> owned) {
    return new ScreeningCandidateResponse(
        row.getCandidateId(), row.getResumeVersionId(), row.getJdVersionId(),
        row.getDefaultMatchRunId(), new DefaultScreeningResultResponse(
            row.getProfessionalScore(), row.getBonusScore(), row.getReferenceScore(), row.getMustTier()),
        row.getAdmissionId() == null ? "READY" : "ADMITTED", row.getAdmissionId(), row.getAdmittedAt(),
        row.getAdmissionVersion() == null ? null : ResourceVersion.format(row.getAdmissionVersion()),
        owned.contains(row.getCandidateId()));
  }

  private static List<AdmissionItem> normalize(List<ScreeningAdmissionItemRequest> values) {
    if (values == null || values.isEmpty() || values.size() > MAX_MATCH_RUN_RESUMES) {
      throw PlatformExceptions.unprocessable("SCREENING_ADMISSION_ITEMS_INVALID", "准入项必须为 1～1000 项");
    }
    Map<String, AdmissionItem> unique = new LinkedHashMap<>();
    for (ScreeningAdmissionItemRequest value : values) {
      if (value == null) {
        throw PlatformExceptions.unprocessable("SCREENING_ADMISSION_ITEM_INVALID", "准入项不能为空");
      }
      AdmissionItem item = new AdmissionItem(
          require(value.getCandidateId(), "candidateId"),
          require(value.getResumeVersionId(), "resumeVersionId"),
          require(value.getJdVersionId(), "jdVersionId"),
          require(value.getDefaultMatchRunId(), "defaultMatchRunId"));
      AdmissionItem previous = unique.putIfAbsent(item.identityKey(), item);
      if (previous != null && !previous.defaultMatchRunId().equals(item.defaultMatchRunId())) {
        throw PlatformExceptions.conflict("SCREENING_ADMISSION_DUPLICATE_CONFLICT", "同一准入身份不能引用不同默认评分运行");
      }
    }
    return List.copyOf(unique.values());
  }

  private static Map<String, ScreeningCandidateRow> index(List<ScreeningCandidateRow> rows) {
    Map<String, ScreeningCandidateRow> result = new LinkedHashMap<>();
    rows.forEach(row -> result.put(identityKey(
        row.getCandidateId(), row.getResumeVersionId(), row.getJdVersionId()), row));
    return result;
  }

  private static String fingerprint(List<AdmissionItem> items) {
    String[] parts = items.stream().sorted(Comparator.comparing(AdmissionItem::identityKey))
        .map(item -> item.identityKey() + ":" + item.defaultMatchRunId()).toArray(String[]::new);
    return Sha256Utils.fingerprintStrings("null", 0, parts);
  }

  private static void requireSameCommand(String action, String storedFingerprint, String fingerprint) {
    if (!MatchRunActionCode.ADMIT_SCREENING_CANDIDATES.matches(action)
        || !fingerprint.equals(storedFingerprint)) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同准入请求");
    }
  }

  private static String identityKey(String candidateId, String resumeVersionId, String jdVersionId) {
    return candidateId + ":" + resumeVersionId + ":" + jdVersionId;
  }

  @Getter(AccessLevel.PRIVATE)
  @Accessors(fluent = true)
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class AdmissionItem {
    private final String candidateId;
    private final String resumeVersionId;
    private final String jdVersionId;
    private final String defaultMatchRunId;

    private String identityKey() {
      return ScreeningAdmissionApplicationManager.identityKey(candidateId, resumeVersionId, jdVersionId);
    }
  }
}
