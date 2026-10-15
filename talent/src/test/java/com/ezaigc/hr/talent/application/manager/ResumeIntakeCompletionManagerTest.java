package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.ScoringTaskRef;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.PrecomputedAssessment;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.domain.dto.IntakeParsedSourceDTO;
import com.ezaigc.hr.talent.enums.ResumeIntakeStatus;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ResumeIntakeCompletionManagerTest {
  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final ResumeIntakeProcessingPersistenceService processing =
      mock(ResumeIntakeProcessingPersistenceService.class);
  private final TalentIntakeApplicationManager intakeManager = mock(TalentIntakeApplicationManager.class);
  private final ResumeParsingApplicationManager parsing = mock(ResumeParsingApplicationManager.class);
  private final ResumeIntakeScoringService scoring = mock(ResumeIntakeScoringService.class);
  private final BackgroundActorExecutor actors = mock(BackgroundActorExecutor.class);
  private final ObjectMapper json = mock(ObjectMapper.class);
  private ResumeIntakeCompletionManager manager;

  @BeforeEach
  void setUp() {
    manager = new ResumeIntakeCompletionManager();
    ReflectionTestUtils.setField(manager,"lifecycle",mock(ResumeLifecycleApplicationManager.class));
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "processing", processing);
    ReflectionTestUtils.setField(manager, "intakeManager", intakeManager);
    ReflectionTestUtils.setField(manager, "resumeParsing", parsing);
    ReflectionTestUtils.setField(manager, "scoring", scoring);
    ReflectionTestUtils.setField(manager, "backgroundActors", actors);
    ReflectionTestUtils.setField(manager, "json", json);
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-09T08:00:00Z"), ZoneOffset.UTC));
    when(intakes.findPendingIntakes(any(), anyInt())).thenReturn(List.of());
    when(intakes.findAdoptingIntakes(anyInt())).thenReturn(List.of());
    when(intakes.findScorableIntakes(anyInt())).thenReturn(List.of());
    when(actors.execute(any(), any())).thenAnswer(invocation -> {
      Supplier<?> action = invocation.getArgument(1);
      return action.get();
    });
  }

  @Test
  void parsedIdentityUsesHandlerAndOnlySuccessfulAdoptionCompletes() {
    ResumeIntake intake = intake("PARSING");
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    result.setParsedFactsJson("{}");
    ResumeFacts facts = mock(ResumeFacts.class);
    when(facts.candidate()).thenReturn(mock(CandidateIdentity.class));
    when(intakes.findPendingIntakes("SUCCEEDED", 50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    when(json.readValue("{}", ResumeFacts.class)).thenReturn(facts);
    ResumeIntake confirmed = intake("CONFIRMED");
    when(intakeManager.autoConfirm("intake-1", facts)).thenReturn(confirmed);
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.status()).thenReturn("SUCCEEDED");
    when(parsing.adoptIntakeResult("version-1", result)).thenReturn(task);

    manager.completeAvailable();

    verify(intakeManager).autoConfirm("intake-1", facts);
    verify(parsing).adoptIntakeResult("version-1", result);
    verify(intakes).advanceIntake("intake-1", ResumeIntakeStatus.CONFIRMED.getCode(),
        ResumeIntakeStatus.COMPLETED.getCode(), null, "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"));
    verify(actors).execute(eq("handler-1"), any());
  }

  @Test
  void linkedCompletedIntakeStartsScoreAsHandler() {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));
    when(scoring.enqueue("jd-1", "version-1", "handler-1"))
        .thenReturn(new ScoringTaskRef("task-1", "run-1", "QUEUED", null, false));

    manager.completeAvailable();

    verify(scoring).enqueue("jd-1", "version-1", "handler-1");
    verify(scoring, never()).get(any(), any());
    verify(intakes).updateScore("intake-1", 0, "run-1", "QUEUED", null, "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void unavailableJdFactsKeepAutomaticScoreWaitingWithoutLosingTheIntake() {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));
    when(scoring.enqueue("jd-1", "version-1", "handler-1"))
        .thenThrow(PlatformExceptions.conflict("JD_FACTS_REQUIRED", "岗位事实未就绪"));

    manager.completeAvailable();

    verify(intakes).updateScore("intake-1", 0, null, "WAITING_JD", "JD_FACTS_REQUIRED",
        "handler-1", Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void completedIntakeWithoutJdDoesNotStartScore() {
    ResumeIntake intake = intake("COMPLETED");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));

    manager.completeAvailable();

    verify(scoring, never()).enqueue(any(), any(), any());
    verify(scoring, never()).get(any(), any());
    verify(intakes, never()).updateScore(any(), anyLong(), any(), any(), any(), any(), any());
  }

  @Test
  void existingScoreRunIsSynchronizedAsHandlerWithoutEnqueueing() {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    intake.setScoreRunId("run-1");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));
    when(scoring.get("run-1", "handler-1"))
        .thenReturn(new ScoringTaskRef("task-1", "run-1", "SUCCEEDED", null, false));

    manager.completeAvailable();

    verify(scoring).get("run-1", "handler-1");
    verify(scoring, never()).enqueue(any(), any(), any());
    verify(intakes).updateScore("intake-1", 0, "run-1", "SUCCEEDED", null, "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void recalculatedRunIsPolledToSuccessWithoutRestartingTheOldRun() {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    intake.setScoreRunId("run-new");
    intake.setScoreStatus("QUEUED");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));
    when(scoring.get("run-new", "handler-1"))
        .thenReturn(new ScoringTaskRef("task-new", "run-new", "SUCCEEDED", null, false));

    manager.completeAvailable();

    verify(scoring).get("run-new", "handler-1");
    verify(scoring, never()).get(eq("run-old"), any());
    verify(scoring, never()).enqueue(any(), any(), any());
    verify(intakes).updateScore("intake-1", 0, "run-new", "SUCCEEDED", null, "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void revokedHandlerPermissionReturnsIntakeToRetryableWithHandlerAudit() {
    ResumeIntake intake = intake("PARSING");
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    result.setParsedFactsJson("{}");
    ResumeFacts facts = mock(ResumeFacts.class);
    when(intakes.findPendingIntakes("SUCCEEDED", 50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    when(json.readValue("{}", ResumeFacts.class)).thenReturn(facts);
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "权限已撤销"))
        .when(actors).execute(eq("handler-1"), any());

    manager.completeAvailable();

    verify(intakes).advanceIntake("intake-1", "PARSING", "FAILED_RETRYABLE", "FORBIDDEN",
        "handler-1", Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void inFlightHistoricalIntakeFallsBackToSourceRecruiter() {
    ResumeIntake intake = intake("PARSING");
    intake.setHandledByUserId(null);
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    result.setParsedFactsJson("{}");
    ResumeFacts facts = mock(ResumeFacts.class);
    when(intakes.findPendingIntakes("SUCCEEDED", 50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    when(json.readValue("{}", ResumeFacts.class)).thenReturn(facts);
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "历史上传人权限已撤销"))
        .when(actors).execute(eq("user-1"), any());

    manager.completeAvailable();

    verify(intakes).advanceIntake("intake-1", "PARSING", "FAILED_RETRYABLE", "FORBIDDEN",
        "user-1", Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void linkedCompletedIntakeImportsCombinedAssessmentInsteadOfRequestingSecondModelCall() {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setExtractedPagesJson("source-json");
    when(processing.findProcessing("processing-1")).thenReturn(result);
    var assessment = new PrecomputedAssessment("jd-snapshot", "prompt", "schema",
        LocalDate.of(2026, 9, 9), "provider", "model", "{}", 1, 2, 3);
    var source = new IntakeParsedSourceDTO();
    source.setPrecomputedAssessment(assessment);
    when(json.readValue("source-json", IntakeParsedSourceDTO.class)).thenReturn(source);
    when(scoring.enqueue("jd-1", "version-1", "handler-1", assessment))
        .thenReturn(new ScoringTaskRef("task-1", "run-1", "QUEUED", null, false));

    manager.completeAvailable();

    verify(scoring).enqueue("jd-1", "version-1", "handler-1", assessment);
    verify(scoring, never()).enqueue("jd-1", "version-1", "handler-1");
  }

  @Test
  void unexpectedCompletionFailureRemainsRetryable() {
    ResumeIntake intake = intake("PARSING");
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    result.setParsedFactsJson("{}");
    ResumeFacts facts = mock(ResumeFacts.class);
    when(intakes.findPendingIntakes("SUCCEEDED", 50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    when(json.readValue("{}", ResumeFacts.class)).thenReturn(facts);
    when(intakeManager.autoConfirm("intake-1", facts))
        .thenThrow(new IllegalStateException("synthetic completion failure"));
    when(intakes.advanceIntake("intake-1", "PARSING", "FAILED_RETRYABLE",
        "INTAKE_COMPLETION_FAILED", "handler-1", Instant.parse("2026-09-09T08:00:00Z")))
        .thenReturn(0);

    manager.completeAvailable();

    verify(intakes).advanceIntake("intake-1", "PARSING", "FAILED_RETRYABLE",
        "INTAKE_COMPLETION_FAILED", "handler-1", Instant.parse("2026-09-09T08:00:00Z"));
    verify(intakes).advanceIntake("intake-1", "READY_FOR_CONFIRMATION", "FAILED_RETRYABLE",
        "INTAKE_COMPLETION_FAILED", "handler-1", Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void concurrentCompletionDoesNotFailAnotherWorkersPreparedIntake() {
    ResumeIntake intake = intake("PARSING");
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    result.setParsedFactsJson("{}");
    ResumeFacts facts = mock(ResumeFacts.class);
    when(intakes.findPendingIntakes("SUCCEEDED", 50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    when(json.readValue("{}", ResumeFacts.class)).thenReturn(facts);
    when(intakeManager.autoConfirm("intake-1", facts)).thenThrow(
        PlatformExceptions.conflict("INTAKE_NOT_PARSING", "收件已由另一任务推进"));

    manager.completeAvailable();

    verify(intakes, never()).advanceIntake(eq("intake-1"), any(), any(), any(), any(), any());
  }

  @Test
  void versionConflictAfterPreparationRemainsRetryable() {
    ResumeIntake intake = intake("PARSING");
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    result.setParsedFactsJson("{}");
    ResumeFacts facts = mock(ResumeFacts.class);
    when(intakes.findPendingIntakes("SUCCEEDED", 50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    when(json.readValue("{}", ResumeFacts.class)).thenReturn(facts);
    when(intakeManager.autoConfirm("intake-1", facts)).thenThrow(
        PlatformExceptions.versionConflict());
    when(intakes.advanceIntake("intake-1", "PARSING", "FAILED_RETRYABLE",
        "VERSION_CONFLICT", "handler-1", Instant.parse("2026-09-09T08:00:00Z")))
        .thenReturn(0);

    manager.completeAvailable();

    verify(intakes).advanceIntake("intake-1", "READY_FOR_CONFIRMATION", "FAILED_RETRYABLE",
        "VERSION_CONFLICT", "handler-1", Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void preparedIdentityConflictAdvancesFromReadyForConfirmation() {
    ResumeIntake intake = intake("PARSING");
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    result.setParsedFactsJson("{}");
    ResumeFacts facts = mock(ResumeFacts.class);
    when(intakes.findPendingIntakes("SUCCEEDED", 50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    when(json.readValue("{}", ResumeFacts.class)).thenReturn(facts);
    when(intakeManager.autoConfirm("intake-1", facts)).thenThrow(
        PlatformExceptions.conflict("IDENTITY_CONFLICT_REQUIRES_REVIEW", "synthetic conflict"));
    when(intakes.advanceIntake("intake-1", "PARSING", "IDENTITY_REVIEW_REQUIRED",
        "IDENTITY_CONFLICT_REQUIRES_REVIEW", "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"))).thenReturn(0);

    manager.completeAvailable();

    verify(intakes).advanceIntake("intake-1", "READY_FOR_CONFIRMATION",
        "IDENTITY_REVIEW_REQUIRED", "IDENTITY_CONFLICT_REQUIRES_REVIEW", "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"));
  }

  @Test
  void queuedAdoptionDoesNotCompleteOrScoreEarly() {
    ResumeIntake intake = intake("CONFIRMED");
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    result.setId("processing-1");
    when(intakes.findAdoptingIntakes(50)).thenReturn(List.of(intake));
    when(processing.findProcessing("processing-1")).thenReturn(result);
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.status()).thenReturn("QUEUED");
    when(parsing.adoptIntakeResult("version-1", result)).thenReturn(task);

    manager.completeAvailable();

    verify(intakes, never()).advanceIntake(eq("intake-1"), eq("CONFIRMED"),
        eq("COMPLETED"), any(), any(), any());
    verify(scoring, never()).enqueue(any(), any(), any());
  }

  private static ResumeIntake intake(String status) {
    ResumeIntake intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setProcessingId("processing-1");
    intake.setSourceRecruiterUserId("user-1");
    intake.setHandledByUserId("handler-1");
    intake.setResumeVersionId("version-1");
    intake.setStatus(status);
    intake.setScoreStatus("NOT_APPLICABLE");
    return intake;
  }

  @ParameterizedTest
  @CsvSource({"QUEUED,true", "QUEUED,false", "RUNNING,true", "RUNNING,false"})
  void transientScoreReadFailureRemainsPollableAndConverges(String initialStatus, boolean platformError) {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    intake.setScoreRunId("run-1");
    intake.setScoreStatus(initialStatus);
    when(intakes.findScorableIntakes(50)).thenAnswer(invocation ->
        List.of("QUEUED", "RUNNING").contains(intake.getScoreStatus()) ? List.of(intake) : List.of());
    when(intakes.updateScore(any(), anyLong(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> {
          intake.setScoreStatus(invocation.getArgument(3));
          intake.setVersion(intake.getVersion() + 1);
          return 1;
        });
    RuntimeException failure = platformError
        ? PlatformExceptions.unavailable("TEMPORARY_READ_FAILURE", "状态暂不可用")
        : new IllegalStateException("temporary read failure");
    when(scoring.get("run-1", "handler-1")).thenThrow(failure)
        .thenReturn(new ScoringTaskRef("task-1", "run-1", "SUCCEEDED", null, false));

    manager.completeAvailable();
    assertThat(intake.getScoreStatus()).isEqualTo(initialStatus);
    manager.completeAvailable();

    assertThat(intake.getScoreStatus()).isEqualTo("SUCCEEDED");
    verify(scoring, times(2)).get("run-1", "handler-1");
    verify(scoring, never()).enqueue(any(), any(), any());
    verify(scoring, never()).retry(any(), any());
    verify(intakes).updateScore(eq("intake-1"), eq(0L), any(), eq(initialStatus),
        eq(platformError ? "TEMPORARY_READ_FAILURE" : "INTAKE_SCORE_STATUS_FAILED"),
        eq("handler-1"), eq(Instant.parse("2026-09-09T08:00:00Z")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"FORBIDDEN", "MATCH_RUN_NOT_FOUND"})
  void permanentScoreReadFailureStopsPolling(String code) {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    intake.setScoreRunId("run-1");
    intake.setScoreStatus("RUNNING");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));
    when(scoring.get("run-1", "handler-1")).thenThrow("FORBIDDEN".equals(code)
        ? PlatformExceptions.forbidden(code, "无权访问") : PlatformExceptions.notFound(code, "运行不存在"));

    manager.completeAvailable();

    verify(intakes).updateScore("intake-1", 0, null, "FAILED_FINAL", code, "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"));
  }

  @ParameterizedTest
  @CsvSource({"QUEUED,false,QUEUED", "RUNNING,false,RUNNING", "SUCCEEDED,false,SUCCEEDED",
      "FAILED,true,FAILED_RETRYABLE", "FAILED,false,FAILED_FINAL",
      "CANCELLED,false,FAILED_FINAL", "CANCELLING,false,RUNNING"})
  void actualScoreTaskStatesKeepTheirMeaning(String taskStatus, boolean retryable, String expectedStatus) {
    ResumeIntake intake = intake("COMPLETED");
    intake.setJdVersionId("jd-1");
    intake.setScoreRunId("run-1");
    when(intakes.findScorableIntakes(50)).thenReturn(List.of(intake));
    when(scoring.get("run-1", "handler-1"))
        .thenReturn(new ScoringTaskRef("task-1", "run-1", taskStatus, null, retryable));

    manager.completeAvailable();

    verify(intakes).updateScore("intake-1", 0, "run-1", expectedStatus, null, "handler-1",
        Instant.parse("2026-09-09T08:00:00Z"));
  }
}
