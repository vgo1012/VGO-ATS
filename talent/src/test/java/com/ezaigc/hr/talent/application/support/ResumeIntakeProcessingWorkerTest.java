package com.ezaigc.hr.talent.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties.AiWorkloadSlots;
import com.ezaigc.hr.talent.application.manager.ResumeIntakeParsingManager;
import com.ezaigc.hr.talent.application.manager.ResumeIntakeParsingManager.ParsedIntake;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelResult;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator.ContractViolation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.task.TaskExecutor;
import tools.jackson.databind.ObjectMapper;

class ResumeIntakeProcessingWorkerTest {
  private final ResumeIntakeProcessingPersistenceService persistence = mock(ResumeIntakeProcessingPersistenceService.class);
  private final ResumeIntakeParsingManager parsing = mock(ResumeIntakeParsingManager.class);
  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final Instant now = Instant.parse("2026-09-09T08:00:00Z");
  private final List<Runnable> queued = new ArrayList<>();
  private final ResumeIntakeProcessingWorker worker = new ResumeIntakeProcessingWorker();

  ResumeIntakeProcessingWorkerTest() {
    ReflectionTestUtils.setField(worker, "persistence", persistence);
    ReflectionTestUtils.setField(worker, "parsing", parsing);
    ReflectionTestUtils.setField(worker, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ReflectionTestUtils.setField(worker, "ids", ids);
    ReflectionTestUtils.setField(worker, "executor", (TaskExecutor) queued::add);
    ReflectionTestUtils.setField(worker, "scheduler", scheduler);
    ReflectionTestUtils.setField(worker, "properties", new PlatformExecutorProperties());
    worker.initialize();
  }

  @Test
  void schedulesBoundedWorkAndClaimsOnlyWhenExecutorStarts() throws Exception {
    when(persistence.findClaimable(any(), anyInt())).thenReturn(List.of());
    worker.poll();
    worker.poll();
    assertThat(queued).hasSize(1);
    verify(persistence, never()).findClaimable(any(), anyInt());
    queued.getFirst().run();
    verify(persistence).expireExhausted(now, 100);
    verify(persistence).findClaimable(now, 1);
    verify(parsing, never()).parse(any());
  }

  @Test
  void submitsAllFiftyConfiguredIntakeParsingSlots() {
    var concurrentWorker = new ResumeIntakeProcessingWorker();
    var properties = new PlatformExecutorProperties();
    properties.setAiWorkloadSlots(new AiWorkloadSlots(6, 50, 1));
    var submitted = new ArrayList<Runnable>();
    ReflectionTestUtils.setField(concurrentWorker, "properties", properties);
    ReflectionTestUtils.setField(concurrentWorker, "executor", (TaskExecutor) submitted::add);
    concurrentWorker.initialize();

    concurrentWorker.poll();

    assertThat(submitted).hasSize(50);
    verifyNoInteractions(persistence, parsing);
  }

  @Test
  void rejectedConcurrentClaimNeverCallsModel() throws Exception {
    var row = processing();
    when(persistence.findClaimable(now, 1)).thenReturn(List.of(row));
    when(ids.newId()).thenReturn("unique-lease");
    when(persistence.claim(anyString(), anyLong(), anyString(), any(), any())).thenReturn(0);
    worker.poll();
    queued.getFirst().run();
    verify(parsing, never()).parse(any());
  }

  @Test
  void usesUniqueLeaseHeartbeatAndCancelsItAfterPublishingResult() throws Exception {
    var row = processing();
    when(persistence.findClaimable(now, 1)).thenReturn(List.of(row), List.of());
    when(ids.newId()).thenReturn("unique-lease");
    when(persistence.claim(eq("processing"), eq(0L), eq("unique-lease"), any(), eq(now))).thenReturn(1);
    when(persistence.findProcessing("processing")).thenReturn(row);
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
        .thenAnswer(invocation -> future);
    var result = new ParsedIntake("[]", "{}", null, new ModelResult("provider", "model", "{}", 10, 5, 20),
        new ArchiveResult("archive", "hash", 2, "enc"));
    when(parsing.parse(row)).thenReturn(result);
    worker.poll();
    queued.getFirst().run();
    verify(persistence).succeed("processing", "unique-lease", "[]", "{}", "provider", "model",
        "archive", "hash", 2, "enc", now);
    var heartbeat = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).scheduleAtFixedRate(heartbeat.capture(), eq(now.plusSeconds(60)), eq(Duration.ofMinutes(1)));
    heartbeat.getValue().run();
    verify(persistence).renewLease("processing", "unique-lease", now.plusSeconds(600), now);
    verify(future).cancel(false);
  }

  @Test
  void losingClaimContinuesToNextAvailableFileWithoutWaitingForPoll() throws Exception {
    var lost = processing();
    var next = processing();
    next.setId("next");
    when(persistence.findClaimable(now, 1)).thenReturn(List.of(lost), List.of(next), List.of());
    when(ids.newId()).thenReturn("lease");
    when(persistence.claim(eq("next"), anyLong(), eq("lease"), any(), eq(now))).thenReturn(1);
    when(persistence.findProcessing("next")).thenReturn(next);
    when(parsing.parse(next)).thenReturn(parsed());
    worker.poll();
    queued.getFirst().run();
    verify(parsing, never()).parse(lost);
    verify(parsing).parse(next);
    verify(persistence, times(3)).findClaimable(now, 1);
  }

  @Test
  void completedFileContinuesDrainingButRepeatedContentionIsBounded() throws Exception {
    var row = processing();
    when(persistence.findClaimable(now, 1)).thenReturn(List.of(row));
    when(ids.newId()).thenReturn("lease");
    when(persistence.claim(anyString(), anyLong(), anyString(), any(), any())).thenReturn(1, 0);
    when(persistence.findProcessing("processing")).thenReturn(row);
    when(parsing.parse(row)).thenReturn(parsed());
    worker.poll();
    queued.getFirst().run();
    verify(parsing).parse(row);
    verify(persistence, times(10)).findClaimable(now, 1);
  }

  @Test
  void failedFileDoesNotPreventNextFileFromFinishingInSameDrain() throws Exception {
    var first = processing();
    var next = processing();
    next.setId("next");
    when(persistence.findClaimable(now, 1)).thenReturn(List.of(first), List.of(next), List.of());
    when(ids.newId()).thenReturn("first-lease", "next-lease");
    when(persistence.claim(anyString(), anyLong(), anyString(), any(), any())).thenReturn(1);
    when(persistence.findProcessing("processing")).thenReturn(first);
    when(persistence.findProcessing("next")).thenReturn(next);
    when(parsing.parse(first)).thenThrow(new IOException("synthetic failure"));
    when(parsing.parse(next)).thenReturn(parsed());
    worker.poll();
    queued.getFirst().run();
    verify(persistence).fail("processing", "first-lease", "DOCUMENT_EXTRACTION_FAILED", true,
        now.plusSeconds(30), now);
    verify(persistence).succeed("next", "next-lease", "[]", "{}", "provider", "model",
        "archive", "hash", 2, "enc", now);
  }

  @Test
  void slowModelFinishesItsLeaseButDoesNotClaimAnotherFile() throws Exception {
    var row = processing();
    var clock = mock(Clock.class);
    when(clock.instant()).thenReturn(now);
    ReflectionTestUtils.setField(worker, "clock", clock);
    when(persistence.findClaimable(now, 1)).thenReturn(List.of(row));
    when(ids.newId()).thenReturn("lease");
    when(persistence.claim(anyString(), anyLong(), anyString(), any(), any())).thenReturn(1);
    when(persistence.findProcessing("processing")).thenReturn(row);
    when(parsing.parse(row)).thenAnswer(invocation -> {
      when(clock.instant()).thenReturn(now.plusSeconds(31));
      return parsed();
    });
    worker.poll();
    queued.getFirst().run();
    verify(persistence).findClaimable(now, 1);
    verify(persistence).succeed("processing", "lease", "[]", "{}", "provider", "model",
        "archive", "hash", 2, "enc", now.plusSeconds(31));
  }

  @Test
  void stoppingSkipsAlreadyQueuedWorkAndFuturePolls() {
    worker.poll();
    worker.stop();
    queued.getFirst().run();
    worker.poll();
    assertThat(queued).hasSize(1);
    verify(persistence, never()).findClaimable(any(), anyInt());
  }

  @Test
  void interruptedExecutorDoesNotClaimWork() {
    worker.poll();
    Thread.currentThread().interrupt();
    try {
      queued.getFirst().run();
      verify(persistence, never()).findClaimable(any(), anyInt());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void stopDuringParsingFinishesCurrentFileWithoutClaimingAnother() throws Exception {
    var row = processing();
    when(persistence.findClaimable(now, 1)).thenReturn(List.of(row));
    when(ids.newId()).thenReturn("lease");
    when(persistence.claim(anyString(), anyLong(), anyString(), any(), any())).thenReturn(1);
    when(persistence.findProcessing("processing")).thenReturn(row);
    when(parsing.parse(row)).thenAnswer(invocation -> {
      worker.stop();
      return parsed();
    });
    worker.poll();
    queued.getFirst().run();
    verify(persistence).findClaimable(now, 1);
    verify(persistence).succeed("processing", "lease", "[]", "{}", "provider", "model",
        "archive", "hash", 2, "enc", now);
  }

  private ParsedIntake parsed() {
    return new ParsedIntake("[]", "{}", null, new ModelResult("provider", "model", "{}", 10, 5, 20),
        new ArchiveResult("archive", "hash", 2, "enc"));
  }

  @Test
  void failedFileReceivesBoundedRetryWithoutPublishingSuccess() throws Exception {
    var row = processing();
    when(parsing.parse(row)).thenThrow(new IOException("synthetic storage failure"));
    worker.process(row, "lease");
    verify(persistence).fail("processing", "lease", "DOCUMENT_EXTRACTION_FAILED", true,
        now.plusSeconds(30), now);
    verify(persistence, never()).succeed(any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
  }

  @Test
  void permanentDocumentFailureIsNotRetried() throws Exception {
    var row = processing();
    when(parsing.parse(row)).thenThrow(new IOException("DOCUMENT_PAGE_LIMIT"));

    worker.process(row, "lease");

    verify(persistence).fail("processing", "lease", "DOCUMENT_PAGE_LIMIT", false,
        now.plusSeconds(30), now);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "DOCUMENT_PAGE_LIMIT", "DOCUMENT_TEXT_LIMIT:4", "DOCUMENT_CONTAINER_LIMIT",
      "DOCUMENT_CONTAINER_INVALID", "DOCUMENT_PAGE_RENDER_LIMIT:7", "DOCUMENT_EMPTY",
      "DOCUMENT_TEXT_QUALITY_LOW", "document exceeds extraction limit"
  })
  void deterministicDocumentFailureKeepsStableCodeAndIsNotRetried(String message)
      throws Exception {
    var row = processing();
    when(parsing.parse(row)).thenThrow(new IOException(message));

    worker.process(row, "lease");

    String expectedCode = message.startsWith("DOCUMENT_PAGE_LIMIT") ? "DOCUMENT_PAGE_LIMIT"
        : message.startsWith("DOCUMENT_TEXT_LIMIT") ? "DOCUMENT_TEXT_LIMIT"
        : message.startsWith("DOCUMENT_CONTAINER_LIMIT") ? "DOCUMENT_CONTAINER_LIMIT"
        : message.startsWith("DOCUMENT_CONTAINER_INVALID") ? "DOCUMENT_CONTAINER_INVALID"
        : message.startsWith("DOCUMENT_PAGE_RENDER_LIMIT") ? "DOCUMENT_PAGE_RENDER_LIMIT"
        : message.contains("QUALITY_LOW") || message.contains("DOCUMENT_EMPTY")
            ? "DOCUMENT_QUALITY_INVALID" : "DOCUMENT_SIZE_INVALID";
    verify(persistence).fail("processing", "lease", expectedCode, false,
        now.plusSeconds(30), now);
  }

  @Test
  void unavailablePageExtractionRetainsItsCodeAndCanRetry() throws Exception {
    var row = processing();
    when(parsing.parse(row)).thenThrow(new IOException("DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE:1"));

    worker.process(row, "lease");

    verify(persistence).fail("processing", "lease", "DOCUMENT_PAGE_EXTRACTION_UNAVAILABLE", true,
        now.plusSeconds(30), now);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MODEL_SCHEMA_INVALID", "MODEL_DATE_INVALID"})
  void modelSchemaFailureRetriesBecauseTheNextModelResponseCanBeValid(String errorCode)
      throws Exception {
    var row = processing();
    var failure = contractFailure(AiOutputValidationStatus.SCHEMA_INVALID,
        errorCode);
    when(parsing.parse(row)).thenThrow(failure);

    worker.process(row, "lease");

    verify(persistence).fail("processing", "lease", errorCode, true,
        now.plusSeconds(30), now);
    verify(scheduler).schedule(any(Runnable.class), eq(now.plusSeconds(30).plusMillis(100)));
  }

  @Test
  void evidenceContractFailureRemainsNonRetryable() throws Exception {
    var row = processing();
    var failure = contractFailure(AiOutputValidationStatus.EVIDENCE_INVALID,
        "MODEL_EVIDENCE_INVALID");
    when(parsing.parse(row)).thenThrow(failure);

    worker.process(row, "lease");

    verify(persistence).fail("processing", "lease", "MODEL_EVIDENCE_INVALID", false,
        now.plusSeconds(30), now);
  }

  private static ContractViolation contractFailure(
      AiOutputValidationStatus status, String code) throws Exception {
    var constructor = ContractViolation.class.getDeclaredConstructor(
        AiOutputValidationStatus.class, String.class, String.class);
    constructor.setAccessible(true);
    return constructor.newInstance(status, code, "safe test detail");
  }

  @Test
  @ResourceLock("SYSTEM_ERR")
  void temporaryFailureLogsSafeCauseWithoutLeakingExceptionMessages() throws Exception {
    var row = processing();
    var failure = new IOException("candidate@example.invalid", new IllegalStateException("13800000000"));
    when(parsing.parse(row)).thenThrow(failure);
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream original = System.err;
    try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setErr(output);
      worker.process(row, "lease");
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8))
        .contains("resume-intake-processing-failed", "java.io.IOException",
            "ResumeIntakeProcessingWorkerTest")
        .doesNotContain("candidate@example.invalid", "13800000000");
  }

  @Test
  @ResourceLock("SYSTEM_ERR")
  void modelSchemaFailureLogsStaticRuleWithoutModelContent() throws Exception {
    var validator = new ResumeParseContractValidator();
    ReflectionTestUtils.setField(validator, "objectMapper", new ObjectMapper());
    ReflectionTestUtils.setField(validator, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ContractViolation failure;
    try {
      validator.validateModel("{\"unexpected\":\"candidate@example.invalid\"}", List.of());
      throw new AssertionError("expected schema failure");
    } catch (ContractViolation violation) {
      failure = violation;
    }
    when(parsing.parse(any())).thenThrow(failure);
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream original = System.err;
    try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setErr(output);
      worker.process(processing(), "lease");
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8))
        .contains("code=MODEL_SCHEMA_INVALID", "validationRule=TOP_LEVEL_KEYS")
        .doesNotContain("candidate@example.invalid");
  }

  private ResumeIntakeProcessing processing() {
    var row = new ResumeIntakeProcessing();
    row.setId("processing");
    row.setAttemptCount(1);
    return row;
  }
}
