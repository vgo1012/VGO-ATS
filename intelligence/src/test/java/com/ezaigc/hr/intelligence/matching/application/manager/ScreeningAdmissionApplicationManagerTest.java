package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningAdmission;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningCandidateRow;
import com.ezaigc.hr.intelligence.matching.domain.request.ScreeningAdmissionItemRequest;
import com.ezaigc.hr.intelligence.matching.domain.request.ScreeningReturnItemRequest;
import com.ezaigc.hr.intelligence.matching.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingIdempotencyPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ScreeningAdmissionPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeLifecycleService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ScreeningAdmissionApplicationManagerTest {
  private static final String ACTOR = "00000000000000000000000001";
  private static final String DEPARTMENT = "00000000000000000000000002";
  private static final String CANDIDATE = "00000000000000000000000003";
  private static final String RESUME = "00000000000000000000000004";
  private static final String JD = "00000000000000000000000005";
  private static final String RUN = "00000000000000000000000006";

  private final ScreeningAdmissionApplicationManager manager =
      new ScreeningAdmissionApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
  private final JdFactQueryService jdFacts = mock(JdFactQueryService.class);
  private final ScreeningAdmissionPersistenceService admissions =
      mock(ScreeningAdmissionPersistenceService.class);
  private final MatchingIdempotencyPersistenceService idempotency =
      mock(MatchingIdempotencyPersistenceService.class);
  private final MatchingAuditEventPersistenceService audit =
      mock(MatchingAuditEventPersistenceService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final CandidateResumeVersionService resumeVersions = mock(CandidateResumeVersionService.class);
  private final CandidateDirectoryService candidates = mock(CandidateDirectoryService.class);
  private final CandidateRecruitmentStateQuery recruitmentStates = mock(CandidateRecruitmentStateQuery.class);
  private final CompanyBlacklistQueryService blacklist = mock(CompanyBlacklistQueryService.class);

  @BeforeEach
  void setUp() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId(ACTOR);
    actor.setHomeDepartmentId(DEPARTMENT);
    actor.setManagedUserIds(Set.of());
    actor.setExplicitlyGrantedDepartmentIds(Set.of());
    when(authorization.currentActor()).thenReturn(actor);
    ResumeFactSnapshotData resume = new ResumeFactSnapshotData();
    resume.setCandidateId(CANDIDATE);
    resume.setResumeVersionId(RESUME);
    when(resumeFacts.currentFactBatch(List.of(RESUME))).thenReturn(id -> resume);
    when(jdFacts.currentFacts(JD)).thenReturn(mock());
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "resumeFacts", resumeFacts);
    ReflectionTestUtils.setField(manager, "resumeVersions", resumeVersions);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager,"lifecycle",mock(ResumeLifecycleService.class));
    ReflectionTestUtils.setField(manager, "recruitmentStates", recruitmentStates);
    ReflectionTestUtils.setField(manager, "blacklist", blacklist);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "admissions", admissions);
    ReflectionTestUtils.setField(manager, "idempotency", idempotency);
    ReflectionTestUtils.setField(manager, "audit", audit);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-10T08:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void cancelledRecruitmentCannotReuseItsOldDefaultScoreForAdmission() {
    doThrow(PlatformExceptions.conflict("CURRENT_RECRUITMENT_SCORE_REQUIRED", "请重新选择 JD 评分"))
        .when(resumeVersions).requireCurrentScore(CANDIDATE, RESUME, JD, RUN);
    assertThatThrownBy(() -> manager.admit(List.of(request(RUN)), "cancelled-old-score"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURRENT_RECRUITMENT_SCORE_REQUIRED"));
    verify(admissions, never()).insertBatchIgnore(anyList());
    verifyNoInteractions(idempotency, audit);
  }

  @Test
  void admissionVersionUsesAStringOnTheWire() {
    ScreeningCandidateRow admitted = row("00000000000000000000000007");
    admitted.setAdmissionVersion(2L);
    when(admissions.findScreeningCandidates(List.of(), ACTOR, false, Set.of(), Set.of(), true))
        .thenReturn(List.of(admitted));
    var response = manager.candidates(null).getFirst();
    assertThat(response.getAdmissionVersion()).isEqualTo("2");
    assertThat(new ObjectMapper().writeValueAsString(response)).contains("\"admissionVersion\":\"2\"");
  }

  @Test
  void returnKeepsDefaultScoreAndAppendsAuditWithResumeLock() {
    ScreeningAdmission admitted = returnedAdmission(null, 0);
    stubReturn(admitted);
    when(admissions.returnToResumes(eq(admitted.getId()), eq(0L), eq(ACTOR), any())).thenReturn(1);

    var result = manager.returnToResumes(List.of(returnRequest(0)), "return-key");

    assertThat(result.getReturnedCount()).isEqualTo(1);
    var order = inOrder(resumeVersions, recruitmentStates, admissions);
    order.verify(admissions).getById(admitted.getId());
    order.verify(resumeVersions).lockForRouting(CANDIDATE, RESUME);
    order.verify(recruitmentStates).hasActiveRecruitment(RESUME);
    order.verify(admissions).lockAdmission(admitted.getId());
    order.verify(admissions).returnToResumes(eq(admitted.getId()), eq(0L), eq(ACTOR), any());
    verify(audit).insertAudit(anyString(), eq("SCREENING_CANDIDATE_RETURNED"), eq(ACTOR),
        eq("SCREENING_ADMISSION"), eq(admitted.getId()), eq(null), any());
    verify(admissions, never()).readmit(anyString(), anyLong(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void candidateResponseMarksOnlyTheCurrentOwnerForRowActions() {
    ScreeningCandidateRow admitted = row("00000000000000000000000007");
    when(admissions.findScreeningCandidates(List.of(), ACTOR, false, Set.of(), Set.of(), true))
        .thenReturn(List.of(admitted));
    when(candidates.ownedCandidateIds(List.of(CANDIDATE), ACTOR)).thenReturn(Set.of(CANDIDATE));

    assertThat(manager.candidates(null).getFirst().isOwnedByCurrentActor()).isTrue();
    when(candidates.ownedCandidateIds(List.of(CANDIDATE), ACTOR)).thenReturn(Set.of());
    assertThat(manager.candidates(null).getFirst().isOwnedByCurrentActor()).isFalse();
  }

  @Test
  void currentCandidateOwnerCanReturnAnAdmissionCreatedByTheirLeader() {
    ScreeningAdmission admitted = returnedAdmission(null, 0);
    admitted.setAdmittedByUserId("00000000000000000000000009");
    stubReturn(admitted);
    when(candidates.ownedCandidateIds(List.of(CANDIDATE), ACTOR)).thenReturn(Set.of(CANDIDATE));
    when(admissions.returnToResumes(eq(admitted.getId()), eq(0L), eq(ACTOR), any())).thenReturn(1);

    assertThat(manager.returnToResumes(List.of(returnRequest(0)), "owner-return").getReturnedCount()).isEqualTo(1);
    verify(authorization, never()).requireAccess(eq("matching.run"), any());
    verify(jdFacts, never()).requireCurrentFactsReadAccessBatch(anyList());
    verify(audit).insertAudit(anyString(), eq("SCREENING_CANDIDATE_RETURNED"), eq(ACTOR),
        eq("SCREENING_ADMISSION"), eq(admitted.getId()), eq(null), any());
  }

  @Test
  void admissionLocksCandidatesBeforeComparingTheirDifferentResumeVersions() {
    var first = request(RUN);
    first.setResumeVersionId("00000000000000000000000024");
    var second = request(RUN);
    second.setCandidateId("00000000000000000000000013");
    second.setResumeVersionId("00000000000000000000000014");
    var requested = List.of(second, first);
    when(resumeFacts.currentFactBatch(anyList())).thenReturn(id -> {
      var item = requested.stream().filter(value -> value.getResumeVersionId().equals(id)).findFirst().orElseThrow();
      var facts = new ResumeFactSnapshotData();
      facts.setCandidateId(item.getCandidateId()); facts.setResumeVersionId(id);
      return facts;
    });
    // 缺省评分校验发生在两把路由锁之后；只验证进入数据库写入前的统一锁序。
    assertThatThrownBy(() -> manager.admit(requested, "lock-order"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_DEFAULT_SCORE_REQUIRED"));
    var order = inOrder(resumeVersions);
    order.verify(resumeVersions).lockForRouting(first.getCandidateId(), first.getResumeVersionId());
    order.verify(resumeVersions).lockForRouting(second.getCandidateId(), second.getResumeVersionId());
    verify(admissions, never()).insertBatchIgnore(anyList());
  }

  @Test
  void returnsUseTheSameCandidateThenVersionLockOrderAsAdmission() {
    var first = returnedAdmission(null, 0);
    first.setResumeVersionId("00000000000000000000000024");
    var second = returnedAdmission(null, 0);
    second.setId("00000000000000000000000017");
    second.setCandidateId("00000000000000000000000013");
    second.setResumeVersionId("00000000000000000000000014");
    stubReturn(first); stubReturn(second);
    when(resumeFacts.currentFactBatch(anyList())).thenReturn(id -> {
      var item = id.equals(first.getResumeVersionId()) ? first : second;
      var facts = new ResumeFactSnapshotData();
      facts.setCandidateId(item.getCandidateId()); facts.setResumeVersionId(id);
      return facts;
    });
    when(admissions.returnToResumes(anyString(), eq(0L), eq(ACTOR), any())).thenReturn(1);
    var firstRequest = returnRequest(0);
    var secondRequest = returnRequest(0); secondRequest.setAdmissionId(second.getId());
    assertThat(manager.returnToResumes(List.of(secondRequest, firstRequest), "return-order").getReturnedCount())
        .isEqualTo(2);
    var order = inOrder(resumeVersions);
    order.verify(resumeVersions).lockForRouting(first.getCandidateId(), first.getResumeVersionId());
    order.verify(resumeVersions).lockForRouting(second.getCandidateId(), second.getResumeVersionId());
  }

  @Test
  void returnLoadsResumeFactsAndJdAccessOnceInsteadOfPerAdmission() {
    var first = returnedAdmission(null, 0);
    first.setResumeVersionId("00000000000000000000000024");
    var second = returnedAdmission(null, 0);
    second.setId("00000000000000000000000017");
    second.setCandidateId("00000000000000000000000013");
    second.setResumeVersionId("00000000000000000000000014");
    stubReturn(first); stubReturn(second);
    when(resumeFacts.currentFactBatch(anyList())).thenReturn(id -> {
      var item = id.equals(first.getResumeVersionId()) ? first : second;
      var facts = new ResumeFactSnapshotData();
      facts.setCandidateId(item.getCandidateId()); facts.setResumeVersionId(id);
      return facts;
    });
    when(admissions.returnToResumes(anyString(), eq(0L), eq(ACTOR), any())).thenReturn(1);
    var firstRequest = returnRequest(0);
    var secondRequest = returnRequest(0); secondRequest.setAdmissionId(second.getId());

    assertThat(manager.returnToResumes(List.of(secondRequest, firstRequest), "return-batch").getReturnedCount())
        .isEqualTo(2);

    // 事实与 JD 权限必须整批预装载：逐条调用会按项重复整批查询，1000 项将放大为数万次查询。
    verify(resumeFacts, times(1)).currentFactBatch(
        List.of(first.getResumeVersionId(), second.getResumeVersionId()));
    verify(jdFacts, times(1)).requireCurrentFactsReadAccessBatch(List.of(JD));
    verify(jdFacts, never()).requireCurrentFactsReadAccess(anyString());
  }

  @Test
  void activeRecruitmentPreventsReturn() {
    stubReturn(returnedAdmission(null, 0));
    when(recruitmentStates.hasActiveRecruitment(RESUME)).thenReturn(true);

    assertThatThrownBy(() -> manager.returnToResumes(List.of(returnRequest(0)), "return-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_RECRUITMENT_ACTIVE"));
    verify(admissions, never()).returnToResumes(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void oldVersionCannotReturnAReadmittedCandidate() {
    stubReturn(returnedAdmission(null, 2));

    assertThatThrownBy(() -> manager.returnToResumes(List.of(returnRequest(0)), "new-return-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_ADMISSION_VERSION_CONFLICT"));
    verify(admissions, never()).returnToResumes(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void replayOfOldReturnNeverWithdrawsReadmission() {
    ScreeningAdmission readmitted = returnedAdmission(null, 2);
    when(admissions.getById(readmitted.getId())).thenReturn(readmitted);
    String fingerprint = Sha256Utils.fingerprintStrings("null", 0, readmitted.getId() + ":0");
    when(idempotency.findIdempotency(ACTOR, "return-key")).thenReturn(new IdempotencyRecord(
        ACTOR, "return-key", "RETURN_SCREENING_CANDIDATES", fingerprint, "target"));

    var result = manager.returnToResumes(List.of(returnRequest(0)), "return-key");

    assertThat(result.getAlreadyReturnedCount()).isEqualTo(1);
    assertThat(result.getReturnedCount()).isZero();
    verify(resumeVersions, never()).lockForRouting(anyString(), anyString());
    verify(admissions, never()).returnToResumes(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void returnChecksObjectPermissionBeforeMutating() {
    ScreeningAdmission admitted = returnedAdmission(null, 0);
    when(admissions.getById(admitted.getId())).thenReturn(admitted);
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权处理"))
        .when(authorization).requireAccess(eq("matching.run"), any());
    assertThatThrownBy(() -> manager.returnToResumes(List.of(returnRequest(0)), "return-key"))
        .isInstanceOf(PlatformException.class);
    var target = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization).requireAccess(eq("matching.run"), target.capture());
    assertThat(target.getValue().getObjectType()).isEqualTo("MATCH_RUN");
    assertThat(target.getValue().getObjectId()).isEqualTo(RUN);
    assertThat(target.getValue().getOwnerUserId()).isEqualTo(ACTOR);
    assertThat(target.getValue().getDepartmentId()).isEqualTo(DEPARTMENT);
    verify(admissions, never()).returnToResumes(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void replayOfOriginalAdmissionAfterReturnCannotReactivateCandidate() {
    when(admissions.findScreeningCandidates(List.of(CANDIDATE), ACTOR, false, Set.of(), Set.of(), false))
        .thenReturn(List.of(row(null)));
    String fingerprint = Sha256Utils.fingerprintStrings("null", 0,
        CANDIDATE + ":" + RESUME + ":" + JD + ":" + RUN);
    when(idempotency.findIdempotency(ACTOR, "old-admit-key")).thenReturn(new IdempotencyRecord(
        ACTOR, "old-admit-key", "ADMIT_SCREENING_CANDIDATES", fingerprint, "target"));

    assertThatThrownBy(() -> manager.admit(List.of(request(RUN)), "old-admit-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_ADMISSION_VERSION_CONFLICT"));
    verify(admissions, never()).insertBatchIgnore(anyList());
    verify(admissions, never()).readmit(anyString(), anyLong(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void readmissionReactivatesTheExistingIdentityAndKeepsItsAuditTarget() {
    ScreeningAdmission prior = returnedAdmission(Instant.parse("2026-09-10T07:00:00Z"), 1);
    when(admissions.findScreeningCandidates(List.of(CANDIDATE), ACTOR, false, Set.of(), Set.of(), false))
        .thenReturn(List.of(row(null)), List.of(row(prior.getId())));
    when(ids.newId()).thenReturn("00000000000000000000000008");
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    when(admissions.findByIdentity(CANDIDATE, RESUME, JD)).thenReturn(prior);
    when(admissions.readmit(eq(prior.getId()), eq(1L), eq(RUN), eq(ACTOR), eq(DEPARTMENT), any())).thenReturn(1);

    assertThat(manager.admit(List.of(request(RUN)), "readmit-key").getAdmittedCount()).isEqualTo(1);
    verify(audit).insertAudit(anyString(), eq("SCREENING_CANDIDATE_ADMITTED"), eq(ACTOR),
        eq("SCREENING_ADMISSION"), eq(prior.getId()),
        eq("{\"previousDefaultMatchRunId\":\"" + RUN + "\",\"defaultMatchRunId\":\"" + RUN + "\",\"previousVersion\":1}"), any());
  }

  @Test
  void activelyRecruitedResumeCannotBeAdmittedAgain() {
    when(recruitmentStates.hasActiveRecruitment(RESUME)).thenReturn(true);
    assertThatThrownBy(() -> manager.admit(List.of(request(RUN)), "readmit-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_RECRUITMENT_ACTIVE"));
    verify(admissions, never()).insertBatchIgnore(anyList());
  }

  private ScreeningAdmission returnedAdmission(Instant returnedAt, int version) {
    ScreeningAdmission row = new ScreeningAdmission();
    row.setId("00000000000000000000000007");
    row.setCandidateId(CANDIDATE);
    row.setResumeVersionId(RESUME);
    row.setJdVersionId(JD);
    row.setDefaultMatchRunId(RUN);
    row.setAdmittedByUserId(ACTOR);
    row.setOwnerDepartmentId(DEPARTMENT);
    row.setVersion(version);
    row.setReturnedAt(returnedAt);
    return row;
  }

  private ScreeningReturnItemRequest returnRequest(long version) {
    ScreeningReturnItemRequest request = new ScreeningReturnItemRequest();
    request.setAdmissionId("00000000000000000000000007");
    request.setExpectedVersion(Long.toString(version));
    return request;
  }

  private void stubReturn(ScreeningAdmission row) {
    when(ids.newId()).thenReturn("00000000000000000000000008");
    when(admissions.getById(row.getId())).thenReturn(row);
    when(admissions.lockAdmission(row.getId())).thenReturn(row);
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void admitsExactDefaultResultWithoutStartingAnotherScore() {
    ScreeningCandidateRow ready = row(null);
    ScreeningCandidateRow admitted = row("00000000000000000000000007");
    when(admissions.findScreeningCandidates(
        List.of(CANDIDATE), ACTOR, false, Set.of(), Set.of(), false))
        .thenReturn(List.of(ready), List.of(admitted));
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn(
        "00000000000000000000000008",
        "00000000000000000000000007",
        "00000000000000000000000009");
    when(admissions.insertBatchIgnore(anyList())).thenReturn(1);

    var response = manager.admit(List.of(request(RUN)), "admission-key");

    assertThat(response.getRequestedCount()).isEqualTo(1);
    assertThat(response.getAdmittedCount()).isEqualTo(1);
    assertThat(response.getItems()).singleElement().satisfies(item -> {
      assertThat(item.getAdmissionStatus()).isEqualTo("ADMITTED");
      assertThat(item.getDefaultMatchRunId()).isEqualTo(RUN);
      assertThat(item.getDefaultResult().getReferenceScore()).isEqualByComparingTo("91.50");
    });
    ArgumentCaptor<List<ScreeningAdmission>> saved = ArgumentCaptor.forClass(List.class);
    verify(admissions).insertBatchIgnore(saved.capture());
    assertThat(saved.getValue()).singleElement().satisfies(item -> {
      assertThat(item.getCandidateId()).isEqualTo(CANDIDATE);
      assertThat(item.getResumeVersionId()).isEqualTo(RESUME);
      assertThat(item.getJdVersionId()).isEqualTo(JD);
      assertThat(item.getDefaultMatchRunId()).isEqualTo(RUN);
    });
    verify(audit).insertAudit("00000000000000000000000009",
        "SCREENING_CANDIDATE_ADMITTED", ACTOR, "SCREENING_ADMISSION",
        "00000000000000000000000007", null, Instant.parse("2026-09-10T08:00:00Z"));
    verify(jdFacts).currentFacts(JD);
    verify(jdFacts, never()).requireCurrentFactsReadAccess(anyString());
    verify(admissions, never()).findByIdentity(anyString(), anyString(), anyString());
  }

  @Test
  void companyBlacklistBlocksNewScreeningAdmission() {
    CompanyBlacklistQueryService blocked = mock(CompanyBlacklistQueryService.class, call -> {
      if (call.getMethod().getName().equals("isCompanyBlacklisted")) return true;
      if (call.getMethod().getName().equals("requireRecruitmentAllowed")) {
        throw PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单阻断筛选准入");
      }
      return Mockito.RETURNS_DEFAULTS.answer(call);
    });
    ReflectionTestUtils.setField(manager, "blacklist", blocked);
    when(admissions.findScreeningCandidates(
        List.of(CANDIDATE), ACTOR, false, Set.of(), Set.of(), false))
        .thenReturn(List.of(row(null)), List.of(row("00000000000000000000000007")));
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn(
        "00000000000000000000000008",
        "00000000000000000000000007",
        "00000000000000000000000009");
    when(admissions.insertBatchIgnore(anyList())).thenReturn(1);

    assertThatThrownBy(() -> manager.admit(List.of(request(RUN)), "blacklisted-admission"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verify(admissions, never()).insertBatchIgnore(anyList());
    verifyNoInteractions(audit);
  }

  @Test
  void successfulAdmissionReplaysAfterCompanyBlacklistActivationWithoutNewFacts() {
    when(admissions.findScreeningCandidates(
        List.of(CANDIDATE), ACTOR, false, Set.of(), Set.of(), false))
        .thenReturn(List.of(row(null)), List.of(row("00000000000000000000000007")));
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn(
        "00000000000000000000000008",
        "00000000000000000000000007",
        "00000000000000000000000009");
    when(admissions.insertBatchIgnore(anyList())).thenReturn(1);
    assertThat(manager.admit(List.of(request(RUN)), "admit-before-blacklist").getAdmittedCount()).isEqualTo(1);
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(idempotency).insertIgnore(eq(ACTOR), eq("admit-before-blacklist"),
        eq("ADMIT_SCREENING_CANDIDATES"), fingerprint.capture(), anyString(), any());
    when(idempotency.findIdempotency(ACTOR, "admit-before-blacklist"))
        .thenReturn(new IdempotencyRecord(ACTOR, "admit-before-blacklist", "ADMIT_SCREENING_CANDIDATES",
            fingerprint.getValue(), "00000000000000000000000008"));
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "成功后生效的公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(anyString());

    assertThat(manager.admit(List.of(request(RUN)), "admit-before-blacklist").getAdmittedCount()).isZero();
    verify(blacklist).requireRecruitmentAllowed(CANDIDATE);
    verify(admissions).insertBatchIgnore(anyList());
    verify(audit).insertAudit(anyString(), eq("SCREENING_CANDIDATE_ADMITTED"), eq(ACTOR),
        eq("SCREENING_ADMISSION"), anyString(), any(), any());
    verify(idempotency).insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void rejectsAClientRunThatDoesNotMatchTheCanonicalDefaultResult() {
    when(admissions.findScreeningCandidates(
        List.of(CANDIDATE), ACTOR, false, Set.of(), Set.of(), false))
        .thenReturn(List.of(row(null)));

    assertThatThrownBy(() -> manager.admit(
        List.of(request("00000000000000000000000010")), "admission-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_DEFAULT_RUN_MISMATCH"));
    verify(idempotency, never()).insertIgnore(anyString(), anyString(), anyString(), anyString(),
        anyString(), any());
  }

  @Test
  void admitsItemsFromDifferentImmutableJdVersionsWithTheirOwnDefaultRuns() {
    List<ScreeningAdmissionItemRequest> requests = mixedJobRequests();
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn("reservation", "admission-0", "admission-1", "audit-0", "audit-1");
    when(admissions.insertBatchIgnore(anyList())).thenReturn(2);

    var response = manager.admit(requests, "admission-key");

    assertThat(response.getRequestedCount()).isEqualTo(2);
    assertThat(response.getAdmittedCount()).isEqualTo(2);
    assertThat(response.getAlreadyAdmittedCount()).isZero();
    verify(admissions, never()).findByIdentity(anyString(), anyString(), anyString());
    ArgumentCaptor<List<ScreeningAdmission>> saved = ArgumentCaptor.forClass(List.class);
    verify(admissions).insertBatchIgnore(saved.capture());
    for (int i = 0; i < requests.size(); i++) {
      var expected = requests.get(i);
      var actual = saved.getValue().get(i);
      assertThat(actual.getCandidateId()).isEqualTo(expected.getCandidateId());
      assertThat(actual.getResumeVersionId()).isEqualTo(expected.getResumeVersionId());
      assertThat(actual.getJdVersionId()).isEqualTo(expected.getJdVersionId());
      assertThat(actual.getDefaultMatchRunId()).isEqualTo(expected.getDefaultMatchRunId());
      assertThat(response.getItems().get(i).getJdVersionId()).isEqualTo(expected.getJdVersionId());
      assertThat(response.getItems().get(i).getDefaultMatchRunId()).isEqualTo(expected.getDefaultMatchRunId());
      verify(jdFacts).currentFacts(expected.getJdVersionId());
      verify(resumeVersions).lockForRouting(expected.getCandidateId(), expected.getResumeVersionId());
      verify(audit).insertAudit(anyString(), eq("SCREENING_CANDIDATE_ADMITTED"), eq(ACTOR),
          eq("SCREENING_ADMISSION"), eq("admission-" + i), eq(null), any());
    }
  }

  @Test
  void rejectsTheWholeMixedJobBatchWhenOneJobIsForbidden() {
    var requests = mixedJobRequests();
    when(jdFacts.currentFacts(requests.getLast().getJdVersionId()))
        .thenThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权使用岗位"));
    assertThatThrownBy(() -> manager.admit(requests, "admission-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));
    verify(admissions, never()).insertBatchIgnore(anyList());
    verify(idempotency, never()).insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void rejectsTheWholeMixedJobBatchWhenOneDefaultRunIsStale() {
    var requests = mixedJobRequests();
    requests.getLast().setDefaultMatchRunId("00000000000000000000000015");
    assertThatThrownBy(() -> manager.admit(requests, "admission-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_DEFAULT_RUN_MISMATCH"));
    verify(admissions, never()).insertBatchIgnore(anyList());
  }

  @Test
  void mixedNewAndReturnedItemsStillLockReadAndAuditTheExistingIdentity() {
    List<ScreeningAdmissionItemRequest> requests = mixedJobRequests();
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn("reservation", "admission-0", "unused-new-id", "audit-0", "audit-1");
    when(admissions.insertBatchIgnore(anyList())).thenReturn(1);
    ScreeningAdmission inserted = returnedAdmission(null, 0);
    inserted.setId("admission-0");
    ScreeningAdmission prior = returnedAdmission(Instant.parse("2026-09-10T07:00:00Z"), 3);
    prior.setId("admission-1");
    var second = requests.get(1);
    when(admissions.findByIdentity(CANDIDATE, RESUME, JD)).thenReturn(inserted);
    when(admissions.findByIdentity(second.getCandidateId(), second.getResumeVersionId(), second.getJdVersionId()))
        .thenReturn(prior);
    when(admissions.readmit(eq(prior.getId()), eq(3L), eq(second.getDefaultMatchRunId()),
        eq(ACTOR), eq(DEPARTMENT), any())).thenReturn(1);

    var result = manager.admit(requests, "mixed-key");

    assertThat(result.getAdmittedCount()).isEqualTo(2);
    assertThat(result.getAlreadyAdmittedCount()).isZero();
    var order = inOrder(admissions, audit);
    order.verify(admissions).insertBatchIgnore(anyList());
    order.verify(admissions).findByIdentity(CANDIDATE, RESUME, JD);
    order.verify(audit).insertAudit(eq("audit-0"), eq("SCREENING_CANDIDATE_ADMITTED"), eq(ACTOR),
        eq("SCREENING_ADMISSION"), eq("admission-0"), eq(null), any());
    order.verify(admissions).findByIdentity(second.getCandidateId(), second.getResumeVersionId(), second.getJdVersionId());
    order.verify(admissions).readmit(eq("admission-1"), eq(3L), eq(second.getDefaultMatchRunId()),
        eq(ACTOR), eq(DEPARTMENT), any());
    order.verify(audit).insertAudit(eq("audit-1"), eq("SCREENING_CANDIDATE_ADMITTED"), eq(ACTOR),
        eq("SCREENING_ADMISSION"), eq("admission-1"),
        eq("{\"previousDefaultMatchRunId\":\"" + RUN + "\",\"defaultMatchRunId\":\""
            + second.getDefaultMatchRunId() + "\",\"previousVersion\":3}"), any());
  }

  @Test
  void repeatedActiveItemsStillReadExistingRowsWithoutCreatingAuditEvents() {
    List<ScreeningAdmissionItemRequest> requests = mixedJobRequests();
    when(idempotency.insertIgnore(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn("reservation", "unused-0", "unused-1");
    when(admissions.insertBatchIgnore(anyList())).thenReturn(0);
    for (int i = 0; i < requests.size(); i++) {
      var item = requests.get(i);
      var prior = returnedAdmission(null, 2);
      prior.setId("admission-" + i);
      when(admissions.findByIdentity(item.getCandidateId(), item.getResumeVersionId(), item.getJdVersionId()))
          .thenReturn(prior);
    }

    var result = manager.admit(requests, "duplicate-key");

    assertThat(result.getAdmittedCount()).isZero();
    assertThat(result.getAlreadyAdmittedCount()).isEqualTo(2);
    verify(admissions, times(2)).findByIdentity(anyString(), anyString(), anyString());
    verify(admissions, never()).readmit(anyString(), anyLong(), anyString(), anyString(), anyString(), any());
    verifyNoInteractions(audit);
  }

  private List<ScreeningAdmissionItemRequest> mixedJobRequests() {
    ScreeningAdmissionItemRequest first = request(RUN);
    ScreeningAdmissionItemRequest second = request("00000000000000000000000014");
    second.setCandidateId("00000000000000000000000011");
    second.setResumeVersionId("00000000000000000000000012");
    second.setJdVersionId("00000000000000000000000013");

    List<ScreeningAdmissionItemRequest> requests = List.of(first, second);
    when(resumeFacts.currentFactBatch(List.of(RESUME, second.getResumeVersionId())))
        .thenReturn(id -> {
          var request = requests.stream().filter(item -> item.getResumeVersionId().equals(id)).findFirst().orElseThrow();
          ResumeFactSnapshotData facts = new ResumeFactSnapshotData();
          facts.setCandidateId(request.getCandidateId());
          facts.setResumeVersionId(id);
          return facts;
        });
    var rows = requests.stream().map(item -> {
      ScreeningCandidateRow candidate = row(null);
      candidate.setCandidateId(item.getCandidateId());
      candidate.setResumeVersionId(item.getResumeVersionId());
      candidate.setJdVersionId(item.getJdVersionId());
      candidate.setDefaultMatchRunId(item.getDefaultMatchRunId());
      candidate.setAdmissionId(item == first ? "admission-0" : "admission-1");
      return candidate;
    }).toList();
    when(admissions.findScreeningCandidates(List.of(CANDIDATE, second.getCandidateId()), ACTOR, false, Set.of(), Set.of(), false))
        .thenReturn(rows);
    return requests;
  }

  @Test
  void listWithoutCandidateIdsReturnsOnlyThePersistedAdmissionProjection() {
    when(admissions.findScreeningCandidates(
        List.of(), ACTOR, false, Set.of(), Set.of(), true))
        .thenReturn(List.of(row("00000000000000000000000007")));

    assertThat(manager.candidates(null)).singleElement()
        .satisfies(item -> assertThat(item.getAdmissionStatus()).isEqualTo("ADMITTED"));
    verify(admissions).findScreeningCandidates(
        List.of(), ACTOR, false, Set.of(), Set.of(), true);
    verify(jdFacts).requireCurrentFactsReadAccessBatch(List.of(JD));
    verify(jdFacts, never()).requireCurrentFactsReadAccess(anyString());
    verify(jdFacts, never()).currentFacts(anyString());
  }

  @Test
  void companyWideActorCanReadVisibleAdmissionsWithoutOwnerRestriction() {
    CurrentActorSnapshot companyActor = new CurrentActorSnapshot();
    companyActor.setUserId(ACTOR);
    companyActor.setHomeDepartmentId(DEPARTMENT);
    companyActor.setManagedUserIds(Set.of());
    companyActor.setExplicitlyGrantedDepartmentIds(Set.of());
    companyActor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(companyActor);
    when(admissions.findScreeningCandidates(
        List.of(), ACTOR, true, Set.of(), Set.of(), true))
        .thenReturn(List.of(row("00000000000000000000000007")));

    assertThat(manager.candidates(null)).singleElement()
        .satisfies(item -> assertThat(item.getAdmissionStatus()).isEqualTo("ADMITTED"));
    verify(admissions).findScreeningCandidates(
        List.of(), ACTOR, true, Set.of(), Set.of(), true);
  }

  @Test
  void batchesDistinctJobReadAccessWithoutChangingCandidateOrder() {
    var rows = IntStream.range(0, 202).mapToObj(index -> {
      var value = row("admission-" + index);
      value.setJdVersionId("jd-" + (index % 201));
      return value;
    }).toList();
    when(admissions.findScreeningCandidates(
        List.of(), ACTOR, false, Set.of(), Set.of(), true)).thenReturn(rows);

    var response = manager.candidates(null);

    assertThat(response).extracting(item -> item.getJdVersionId())
        .containsExactlyElementsOf(rows.stream().map(ScreeningCandidateRow::getJdVersionId).toList());
    verify(jdFacts).requireCurrentFactsReadAccessBatch(
        IntStream.range(0, 200).mapToObj(index -> "jd-" + index).toList());
    verify(jdFacts).requireCurrentFactsReadAccessBatch(List.of("jd-200"));
    verify(jdFacts, times(2)).requireCurrentFactsReadAccessBatch(anyList());
    verify(jdFacts, never()).requireCurrentFactsReadAccess(anyString());
    verify(jdFacts, never()).currentFacts(anyString());
  }

  @Test
  void emptyCandidateListDoesNotQueryJobOrResumeFacts() {
    when(admissions.findScreeningCandidates(
        List.of(), ACTOR, false, Set.of(), Set.of(), true)).thenReturn(List.of());

    assertThat(manager.candidates(null)).isEmpty();

    verifyNoInteractions(jdFacts, resumeFacts);
  }

  @Test
  void rejectedBatchDoesNotReturnPartiallyAuthorizedCandidates() {
    when(admissions.findScreeningCandidates(
        List.of(), ACTOR, false, Set.of(), Set.of(), true)).thenReturn(List.of(row("admission")));
    doThrow(PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权访问该业务对象"))
        .when(jdFacts).requireCurrentFactsReadAccessBatch(List.of(JD));

    assertThatThrownBy(() -> manager.candidates(null)).isInstanceOfSatisfying(
        PlatformException.class, exception -> assertThat(exception.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
  }

  private static ScreeningAdmissionItemRequest request(String runId) {
    ScreeningAdmissionItemRequest request = new ScreeningAdmissionItemRequest();
    request.setCandidateId(CANDIDATE);
    request.setResumeVersionId(RESUME);
    request.setJdVersionId(JD);
    request.setDefaultMatchRunId(runId);
    return request;
  }

  private static ScreeningCandidateRow row(String admissionId) {
    ScreeningCandidateRow row = new ScreeningCandidateRow();
    row.setCandidateId(CANDIDATE);
    row.setResumeVersionId(RESUME);
    row.setJdVersionId(JD);
    row.setDefaultMatchRunId(RUN);
    row.setProfessionalScore(new BigDecimal("86.50"));
    row.setBonusScore(5);
    row.setReferenceScore(new BigDecimal("91.50"));
    row.setMustTier("PASS");
    row.setAdmissionId(admissionId);
    row.setAdmittedAt(admissionId == null ? null : Instant.parse("2026-09-10T08:00:00Z"));
    return row;
  }
}
