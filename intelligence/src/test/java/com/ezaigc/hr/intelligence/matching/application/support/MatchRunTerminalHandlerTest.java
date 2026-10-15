package com.ezaigc.hr.intelligence.matching.application.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunAttemptStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunItemOutcomeStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunItemOutcomePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class MatchRunTerminalHandlerTest {
  private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
  private final MatchRunPersistenceService runs = mock(MatchRunPersistenceService.class);
  private final MatchRunAttemptPersistenceService attempts = mock(MatchRunAttemptPersistenceService.class);
  private final MatchRunItemOutcomePersistenceService outcomes = mock(MatchRunItemOutcomePersistenceService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final MatchRunTerminalHandler handler = new MatchRunTerminalHandler();

  MatchRunTerminalHandlerTest() {
    ReflectionTestUtils.setField(handler, "runs", runs);
    ReflectionTestUtils.setField(handler, "attempts", attempts);
    ReflectionTestUtils.setField(handler, "outcomes", outcomes);
    ReflectionTestUtils.setField(handler, "tasks", tasks);
  }

  @Test
  void ignoresOtherTaskTypesAndMissingBindings() {
    handler.terminalWithoutWorker("OTHER", "task", "run", AsyncTaskStatus.FAILED, "TASK_LEASE_EXPIRED", NOW, "actor");
    verifyNoInteractions(runs, attempts, outcomes, tasks);
    handler.terminalWithoutWorker("MATCH_RUN", "task", "run", AsyncTaskStatus.FAILED, "TASK_LEASE_EXPIRED", NOW, "actor");
    verify(runs).findBoundRunForUpdate("run", "task");
    verifyNoInteractions(attempts, outcomes, tasks);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCEEDED", "CANCELLED"})
  void preservesCompletedBusinessRecords(String status) {
    var run = new MatchRun();
    run.setStatus(status);
    when(runs.findBoundRunForUpdate("run", "task")).thenReturn(run);
    handler.terminalWithoutWorker("MATCH_RUN", "task", "run", AsyncTaskStatus.CANCELLED, null, NOW, "actor");
    verifyNoInteractions(attempts, outcomes, tasks);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void derivesTerminalCountsFromCommittedOutcomes(boolean cancelled) {
    var run = new MatchRun();
    run.setStatus("RUNNING");
    run.setTotalCount(10);
    when(runs.findBoundRunForUpdate("run", "task")).thenReturn(run);
    when(outcomes.lockOutcomeStatuses("run")).thenReturn(List.of(MatchRunItemOutcomeStatus.SUCCEEDED,
        MatchRunItemOutcomeStatus.SUCCEEDED, MatchRunItemOutcomeStatus.SUCCEEDED,
        MatchRunItemOutcomeStatus.FAILED, MatchRunItemOutcomeStatus.FAILED));
    when(runs.terminateWithoutWorker(anyString(), anyString(), any(), anyInt(), anyInt(), any(), any(), anyString()))
        .thenReturn(1);
    var task = mock(AsyncTaskPort.TaskSnapshot.class);
    when(task.attemptCount()).thenReturn(2);
    when(tasks.get("task")).thenReturn(task);

    handler.terminalWithoutWorker("MATCH_RUN", "task", "run",
        cancelled ? AsyncTaskStatus.CANCELLED : AsyncTaskStatus.FAILED, "TASK_LEASE_EXPIRED", NOW, "actor");

    verify(runs).terminateWithoutWorker("run", "task", cancelled ? MatchRunStatus.CANCELLED : MatchRunStatus.PARTIAL,
        3, cancelled ? 2 : 7, cancelled ? null : "TASK_LEASE_EXPIRED", NOW, "actor");
    verify(attempts).terminateWithoutWorker("run", 2,
        cancelled ? MatchRunAttemptStatus.CANCELLED : MatchRunAttemptStatus.FAILED, cancelled ? null : "TASK_LEASE_EXPIRED", NOW, "actor");
  }

  @Test
  void rejectsLostRunUpdateSoTheCallingTransactionCanRollBack() {
    var run = new MatchRun();
    run.setStatus("QUEUED");
    when(runs.findBoundRunForUpdate("run", "task")).thenReturn(run);
    assertThatThrownBy(() -> handler.terminalWithoutWorker("MATCH_RUN", "task", "run",
        AsyncTaskStatus.CANCELLED, null, NOW, "actor")).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(attempts, tasks);
  }

  @Test
  void rejectsNonterminalSignals() {
    assertThatThrownBy(() -> handler.terminalWithoutWorker("MATCH_RUN", "task", "run",
        AsyncTaskStatus.RUNNING, null, NOW, "actor")).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(runs, attempts, outcomes, tasks);
  }
}
