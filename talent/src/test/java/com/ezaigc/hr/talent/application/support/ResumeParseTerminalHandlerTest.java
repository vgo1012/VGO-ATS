package com.ezaigc.hr.talent.application.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeModelRunPersistenceService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeParseTerminalHandlerTest {
  private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
  private final ExtractionRunPersistenceService extractions = mock(ExtractionRunPersistenceService.class);
  private final ResumeModelRunPersistenceService models = mock(ResumeModelRunPersistenceService.class);
  private final ResumeParseTerminalHandler handler = new ResumeParseTerminalHandler();

  @BeforeEach
  void initialize() {
    ReflectionTestUtils.setField(handler, "extractions", extractions);
    ReflectionTestUtils.setField(handler, "models", models);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void usesExactTaskVersionAndActualReason(boolean cancelling) {
    handler.terminalWithoutWorker("RESUME_PARSE", "task", "version",
        cancelling ? AsyncTaskStatus.CANCELLED : AsyncTaskStatus.FAILED, "TASK_EXECUTION_FAILED", NOW, "actor");
    String code = cancelling ? "TASK_CANCELLED" : "TASK_EXECUTION_FAILED";
    verify(models).terminateWithoutWorker("task", "version", code, NOW, "actor");
    verify(extractions).terminateWithoutWorker("task", "version", code, NOW, "actor");
  }

  @Test
  void rejectsNonterminalSignalsAndIgnoresUnrelatedTasks() {
    handler.terminalWithoutWorker("OTHER", "task", "version", AsyncTaskStatus.FAILED, "ERROR", NOW, "actor");
    assertThatThrownBy(() -> handler.terminalWithoutWorker("RESUME_PARSE", "task", "version",
        AsyncTaskStatus.RUNNING, null, NOW, "actor")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(models, extractions);
  }

  @Test
  void propagatesBusinessWriteFailureToTheSharedTransaction() {
    var failure = new IllegalStateException("Synthetic model terminal failure");
    doThrow(failure).when(models).terminateWithoutWorker("task", "version", "ERROR", NOW, "actor");
    assertThatThrownBy(() -> handler.terminalWithoutWorker("RESUME_PARSE", "task", "version",
        AsyncTaskStatus.FAILED, "ERROR", NOW, "actor")).isSameAs(failure);
    verifyNoInteractions(extractions);
  }
}
