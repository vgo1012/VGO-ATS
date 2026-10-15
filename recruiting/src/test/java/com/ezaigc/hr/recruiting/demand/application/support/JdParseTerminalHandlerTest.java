package com.ezaigc.hr.recruiting.demand.application.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.recruiting.demand.service.JdAiModelRunPersistenceService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class JdParseTerminalHandlerTest {
  private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
  private final JdAiModelRunPersistenceService runs = mock(JdAiModelRunPersistenceService.class);
  private final JdParseTerminalHandler handler = new JdParseTerminalHandler();

  @BeforeEach
  void initialize() {
    ReflectionTestUtils.setField(handler, "runs", runs);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void usesExactTaskVersionAndActualReason(boolean cancelling) {
    handler.terminalWithoutWorker("JD_PARSE", "task", "version",
        cancelling ? AsyncTaskStatus.CANCELLED : AsyncTaskStatus.FAILED, "TASK_EXECUTION_FAILED", NOW, "actor");
    verify(runs).terminateWithoutWorker("task", "version", cancelling ? "TASK_CANCELLED" : "TASK_EXECUTION_FAILED", NOW, "actor");
  }

  @Test
  void rejectsNonterminalSignalsAndIgnoresUnrelatedTasks() {
    handler.terminalWithoutWorker("OTHER", "task", "version", AsyncTaskStatus.FAILED, "ERROR", NOW, "actor");
    assertThatThrownBy(() -> handler.terminalWithoutWorker("JD_PARSE", "task", "version",
        AsyncTaskStatus.RUNNING, null, NOW, "actor")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(runs);
  }

  @Test
  void propagatesBusinessWriteFailureToTheSharedTransaction() {
    var failure = new IllegalStateException("Synthetic model terminal failure");
    doThrow(failure).when(runs).terminateWithoutWorker("task", "version", "ERROR", NOW, "actor");
    assertThatThrownBy(() -> handler.terminalWithoutWorker("JD_PARSE", "task", "version",
        AsyncTaskStatus.FAILED, "ERROR", NOW, "actor")).isSameAs(failure);
  }
}
