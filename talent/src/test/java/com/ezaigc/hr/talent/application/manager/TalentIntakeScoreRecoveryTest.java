package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.ScoringTaskRef;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.response.ResumeIntakeResponse;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class TalentIntakeScoreRecoveryTest {
  private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");
  private static final String ACTOR_ID = "handler-1";
  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
  private final ResumeIntakeScoringService scoring = mock(ResumeIntakeScoringService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final TalentIntakeAssembler assembler = mock(TalentIntakeAssembler.class);
  private final JobContextQuery jobContexts = mock(JobContextQuery.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateResumeVersionService routing = mock(CandidateResumeVersionService.class);
  private final CandidateRecruitmentStateQuery recruitment = mock(CandidateRecruitmentStateQuery.class);
  private final CandidateScreeningStateQuery screening = mock(CandidateScreeningStateQuery.class);
  private final ResumeParsingPersistenceService facts = mock(ResumeParsingPersistenceService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final ResumeIntakeResponse response = mock(ResumeIntakeResponse.class);
  private TalentIntakeApplicationManager manager;
  private ResumeIntake intake;

  @BeforeEach
  void setUp() {
    manager = new TalentIntakeApplicationManager();
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "talentProcessEventPersistenceService", events);
    ReflectionTestUtils.setField(manager, "intakeScoring", scoring);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "assembler", assembler);
    ReflectionTestUtils.setField(manager, "jobContexts", jobContexts);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "routing", routing);
    ReflectionTestUtils.setField(manager, "recruitment", recruitment);
    ReflectionTestUtils.setField(manager, "screening", screening);
    ReflectionTestUtils.setField(manager, "resumeFacts", facts);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setSourceRecruiterUserId("source-1");
    intake.setOwnerUserId("owner-1");
    intake.setOwnerDepartmentId("department-1");
    intake.setJdVersionId("jd-version-1");
    intake.setResumeVersionId("resume-version-1");
    intake.setScoreRunId("run-1");
    intake.setScoreStatus("FAILED_RETRYABLE");
    intake.setVersion(3);
    when(intakes.findIntake("intake-1")).thenReturn(intake);
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot(ACTOR_ID,
        "处理人", "handler@example.com", "department-1", Set.of(), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("talent.manage"), false, false));
    when(ids.newId()).thenReturn("event-1");
    when(assembler.toResponse(any(ResumeIntake.class))).thenReturn(response);
    when(jobContexts.visibleVersions(any())).thenReturn(Map.of());
    when(scoring.retry("run-1", ACTOR_ID)).thenReturn(task("QUEUED", null, false));
    when(intakes.updateScore(eq("intake-1"), eq(3L), any(), any(), any(), eq(ACTOR_ID), eq(NOW)))
        .thenReturn(1);
  }

  @ParameterizedTest
  @CsvSource({
      "QUEUED, true, QUEUED",
      "RUNNING, true, RUNNING",
      "SUCCEEDED, true, SUCCEEDED",
      "CANCELLING, true, RUNNING",
      "CANCELLED, true, FAILED_FINAL",
      "FAILED, false, FAILED_FINAL"
  })
  void synchronizesAuthoritativeTaskWithoutRetrying(String taskStatus, boolean retryable,
      String expectedStatus) {
    when(scoring.get("run-1", ACTOR_ID)).thenReturn(task(taskStatus, "TASK_DETAIL", retryable));

    assertThat(manager.retryScore("intake-1", command())).isSameAs(response);

    verify(scoring).get("run-1", ACTOR_ID);
    verify(scoring, never()).retry(any(), any());
    verify(scoring, never()).enqueue(any(), any(), any());
    verify(intakes).updateScore("intake-1", 3, "run-1", expectedStatus, "TASK_DETAIL", ACTOR_ID, NOW);
    verifyAudit();
  }

  @ParameterizedTest
  @CsvSource({"QUEUED, false, QUEUED", "FAILED, true, FAILED_RETRYABLE", "FAILED, false, FAILED_FINAL"})
  void retriesOnlyAuthoritativeRetryableFailureAndPersistsReturnedState(String returnedStatus,
      boolean returnedRetryable, String expectedStatus) {
    when(scoring.get("run-1", ACTOR_ID)).thenReturn(task("FAILED", "MODEL_TIMEOUT", true));
    when(scoring.retry("run-1", ACTOR_ID)).thenReturn(task(returnedStatus, "RESULT_DETAIL", returnedRetryable));

    manager.retryScore("intake-1", command());

    InOrder order = inOrder(authorization, scoring, intakes, events);
    order.verify(authorization).requireAccess(eq("talent.manage"), any());
    order.verify(scoring).get("run-1", ACTOR_ID);
    order.verify(scoring).retry("run-1", ACTOR_ID);
    order.verify(intakes).updateScore("intake-1", 3, "run-1", expectedStatus, "RESULT_DETAIL", ACTOR_ID, NOW);
    order.verify(events).insertEvent("event-1", "RESUME_INTAKE_SCORE_RETRIED", ACTOR_ID,
        "RESUME_INTAKE", "intake-1", "恢复评分", NOW);
    verify(scoring, never()).enqueue(any(), any(), any());
  }

  @Test
  void enqueuesWhenNoRunWasCreated() {
    intake.setScoreRunId(null);
    when(scoring.enqueue("jd-version-1", "resume-version-1", ACTOR_ID))
        .thenReturn(task("QUEUED", null, false));

    assertThat(manager.retryScore("intake-1", command())).isSameAs(response);

    verify(scoring).enqueue("jd-version-1", "resume-version-1", ACTOR_ID);
    verify(scoring, never()).get(any(), any());
    verify(scoring, never()).retry(any(), any());
    verify(intakes).updateScore("intake-1", 3, "run-1", "QUEUED", null, ACTOR_ID, NOW);
    verifyAudit();
  }

  @Test
  void missingReasonStopsBeforeReadingIntakeOrScoring() {
    assertThatThrownBy(() -> manager.retryScore("intake-1", new TalentVersionReasonDTO(3, " ")))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(intakes, scoring, events);
  }

  @Test
  void deniedHandlerCannotRecoverTaskOrWriteSuccessAudit() {
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权重试"))
        .when(authorization).requireAccess(eq("talent.manage"), any());

    assertThatThrownBy(() -> manager.retryScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));

    verifyNoInteractions(scoring, events);
    verifyNoScoreUpdate();
  }

  @Test
  void staleVersionCannotRecoverTaskOrWriteSuccessAudit() {
    assertThatThrownBy(() -> manager.retryScore("intake-1", new TalentVersionReasonDTO(2, "恢复评分")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));
    verifyNoInteractions(scoring, events);
    verifyNoScoreUpdate();
  }

  @Test
  void failedCompareAndSetDoesNotWriteSuccessAudit() {
    when(scoring.get("run-1", ACTOR_ID)).thenReturn(task("SUCCEEDED", null, false));
    when(intakes.updateScore(eq("intake-1"), eq(3L), any(), any(), any(), eq(ACTOR_ID), eq(NOW)))
        .thenReturn(0);

    assertThatThrownBy(() -> manager.retryScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));

    verifyNoInteractions(events, assembler);
    verify(scoring, never()).retry(any(), any());
  }

  @Test
  void nonRetryableIntakeCannotInitiateRecovery() {
    intake.setScoreStatus("SUCCEEDED");
    assertThatThrownBy(() -> manager.retryScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_SCORE_NOT_RETRYABLE"));
    verifyNoInteractions(scoring, events);
    verifyNoScoreUpdate();
  }

  @Test
  void recalculatesOnlyAfterFactSnapshotChangesAndRebindsTheNewRun() {
    prepareRecalculation();

    assertThat(manager.recalculateScore("intake-1", command())).isSameAs(response);

    verify(authorization).requirePermission("matching.run");
    verify(routing).lockForRouting("candidate-1", "resume-version-1");
    verify(scoring).scoredFactSnapshotId("run-1", "jd-version-1", "resume-version-1", ACTOR_ID);
    verify(scoring).enqueueRescore("jd-version-1", "resume-version-1", "processor-1",
        "intake-recalculate:intake-1:fact-2");
    verify(intakes).updateScore("intake-1", 3, "run-2", "QUEUED", null, ACTOR_ID, NOW);
    verify(events).insertEvent("event-1", "RESUME_INTAKE_SCORE_RECALCULATED_REQUESTED",
        ACTOR_ID, "RESUME_INTAKE", "intake-1", "恢复评分", NOW);
  }

  @Test
  void unchangedFactCannotCreateAnotherDefaultRun() {
    prepareRecalculation();
    when(scoring.scoredFactSnapshotId("run-1", "jd-version-1", "resume-version-1", ACTOR_ID))
        .thenReturn("fact-2");

    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_SCORE_FACT_UNCHANGED"));
    verify(scoring, never()).enqueueRescore(any(), any(), any(), any());
    verifyNoScoreUpdate();
  }

  @Test
  void activeRecruitmentOrScreeningBlocksRecalculation() {
    prepareRecalculation();
    when(recruitment.hasActiveRecruitment("resume-version-1")).thenReturn(true);
    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_ACTIVE"));

    when(recruitment.hasActiveRecruitment("resume-version-1")).thenReturn(false);
    when(screening.activeResumeVersionIds(List.of("candidate-1")))
        .thenReturn(Set.of("resume-version-1"));
    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SCREENING_ACTIVE"));
    verify(scoring, never()).enqueueRescore(any(), any(), any(), any());
  }

  @Test
  void pendingOrStaleIntakeCannotReplaceItsScore() {
    prepareRecalculation();
    intake.setScoreStatus("QUEUED");
    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_SCORE_NOT_RECALCULABLE"));
    intake.setScoreStatus("SUCCEEDED");
    assertThatThrownBy(() -> manager.recalculateScore("intake-1", new TalentVersionReasonDTO(2, "恢复评分")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));
    verify(scoring, never()).enqueueRescore(any(), any(), any(), any());
  }

  @Test
  void changedCandidateVersionOrMissingFactsCannotStartAnotherRun() {
    prepareRecalculation();
    when(intakes.findCurrentCandidateResumes(List.of("candidate-1"))).thenReturn(List.of());
    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RESUME_VERSION_CHANGED"));

    CandidateResumeRow current = new CandidateResumeRow();
    current.setResumeVersionId("resume-version-1");
    current.setJdVersionId("jd-version-1");
    current.setDefaultMatchRunId("run-1");
    when(intakes.findCurrentCandidateResumes(List.of("candidate-1"))).thenReturn(List.of(current));
    when(facts.findCurrentFactSnapshotForUpdate("resume-version-1")).thenReturn(null);
    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RESUME_FACTS_REQUIRED"));
    verify(scoring, never()).enqueueRescore(any(), any(), any(), any());
  }

  @Test
  void deniedRunPermissionStopsBeforeLockingOrScoring() {
    prepareRecalculation();
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "无评分权限"))
        .when(authorization).requirePermission("matching.run");

    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));
    verifyNoInteractions(routing, scoring);
  }

  @Test
  void compareAndSetFailureDoesNotAuditRecalculation() {
    prepareRecalculation();
    when(intakes.updateScore(eq("intake-1"), eq(3L), any(), any(), any(), eq(ACTOR_ID), eq(NOW)))
        .thenReturn(0);

    assertThatThrownBy(() -> manager.recalculateScore("intake-1", command()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));
    verifyNoInteractions(events);
  }

  private void prepareRecalculation() {
    intake.setStatus("COMPLETED");
    intake.setScoreStatus("SUCCEEDED");
    intake.setCandidateId("candidate-1");
    intake.setHandledByUserId("processor-1");
    Candidate candidate = new Candidate();
    candidate.setId("candidate-1");
    candidate.setStatus("ACTIVE");
    when(candidates.findCandidate("candidate-1")).thenReturn(candidate);
    CandidateResumeRow current = new CandidateResumeRow();
    current.setCandidateId("candidate-1");
    current.setResumeVersionId("resume-version-1");
    current.setJdVersionId("jd-version-1");
    current.setDefaultMatchRunId("run-1");
    when(intakes.findCurrentCandidateResumes(List.of("candidate-1")))
        .thenReturn(List.of(current));
    when(screening.activeResumeVersionIds(List.of("candidate-1"))).thenReturn(Set.of());
    FactSnapshot snapshot = new FactSnapshot();
    snapshot.setId("fact-2");
    when(facts.findCurrentFactSnapshotForUpdate("resume-version-1")).thenReturn(snapshot);
    when(scoring.scoredFactSnapshotId("run-1", "jd-version-1", "resume-version-1", ACTOR_ID))
        .thenReturn("fact-1");
    when(scoring.enqueueRescore("jd-version-1", "resume-version-1", "processor-1",
        "intake-recalculate:intake-1:fact-2"))
        .thenReturn(new ScoringTaskRef("task-2", "run-2", "QUEUED", null, false));
  }

  private void verifyNoScoreUpdate() {
    verify(intakes, never()).updateScore(any(), anyLong(), any(), any(), any(), any(), any());
  }

  private void verifyAudit() {
    verify(events).insertEvent("event-1", "RESUME_INTAKE_SCORE_RETRIED", ACTOR_ID,
        "RESUME_INTAKE", "intake-1", "恢复评分", NOW);
  }

  private static TalentVersionReasonDTO command() {
    return new TalentVersionReasonDTO(3, "  恢复评分  ");
  }

  private static ScoringTaskRef task(String status, String errorCode, boolean retryable) {
    return new ScoringTaskRef("task-1", "run-1", status, errorCode, retryable);
  }
}
