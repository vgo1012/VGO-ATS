package com.ezaigc.hr.intelligence.search.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource.ProjectionPage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SearchRebuildAttemptTest {
  private static final String RUN_ID = "01J" + "R".repeat(23);
  private final SearchProjectionApplicationManager manager = new SearchProjectionApplicationManager();
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final RebuildRunService rebuilds = mock(RebuildRunService.class);
  private final SearchIndexPort index = mock(SearchIndexPort.class);
  private final ResumeFactProjectionSource source = mock(ResumeFactProjectionSource.class);
  private final RebuildRun run = new RebuildRun();
  private final TaskSnapshot first = task(1);

  @BeforeEach
  void setUp() {
    run.setId(RUN_ID);
    run.setTaskId("task-1");
    run.setTargetIndex("resume-facts-vinitial");
    run.setStatus("QUEUED");
    when(rebuilds.findByTaskId("task-1")).thenReturn(run);
    when(rebuilds.getById(RUN_ID)).thenReturn(run);
    when(rebuilds.start(anyString(), anyString(), anyString(), any())).thenReturn(true);
    when(rebuilds.finish(anyString(), any(), anyLong(), any(), anyString(), any())).thenReturn(true);
    when(rebuilds.finishApproved(anyString(), anyString(), anyString(), anyLong(), anyString(), any())).thenReturn(true);
    var authority = mock(SearchIndexAuthorityApplicationManager.class);
    when(authority.rebuildBase()).thenReturn(new SearchIndexTarget("initial-index", 0));
    var checkpoints = mock(ProjectionCheckpointService.class);
    when(checkpoints.lockApprovedIndex()).thenReturn(new SearchIndexTarget("initial-index", 0));
    when(checkpoints.approveIndex(anyString(), anyLong(), anyString(), any())).thenReturn(true);
    when(checkpoints.resetForApprovedIndex(anyString(), any(), anyString(), any())).thenReturn(true);
    var stateCheckpoints = mock(StateReplayCheckpointService.class);
    when(stateCheckpoints.resetForApprovedIndex(anyString(), anyString(), any())).thenReturn(true);
    ReflectionTestUtils.setField(manager, "authority", authority);
    ReflectionTestUtils.setField(manager, "resources", mock(SearchIndexResourceApplicationManager.class));
    ReflectionTestUtils.setField(manager, "checkpoints", checkpoints);
    ReflectionTestUtils.setField(manager, "stateCheckpoints", stateCheckpoints);
    when(tasks.renewLease(any(TaskSnapshot.class))).thenReturn(true);
    TaskSnapshot failed = mock(TaskSnapshot.class);
    when(failed.status()).thenReturn("FAILED");
    when(tasks.fail(any(TaskSnapshot.class), anyString(), anyString(), any(Boolean.class))).thenReturn(failed);
    when(source.currentFactsAfter(any(), anyString(), anyInt())).thenReturn(new ProjectionPage(List.of(), false));
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "rebuilds", rebuilds);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "source", source);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void retryUsesAFreshIndexAfterTheFirstAttemptAlreadyCreatedOne() {
    TaskSnapshot retry = task(2);
    Set<String> createdIndexes = new HashSet<>();
    doAnswer(call -> {
      if (!createdIndexes.add(call.getArgument(0, SearchRebuildAttempt.class).indexName())) {
        throw new IllegalStateException("index already exists");
      }
      return null;
    }).when(index).beginRebuild(any(SearchRebuildAttempt.class));
    when(source.currentFactsAfter(any(), anyString(), anyInt()))
        .thenThrow(new IllegalStateException("projection source temporarily unavailable"))
        .thenReturn(new ProjectionPage(List.of(), false));

    manager.process(first);
    manager.process(retry);

    verify(tasks).fail(first, "SEARCH_REBUILD_FAILED", "搜索索引重建失败，可重试", true);
    verify(tasks).succeed(retry, RUN_ID);
    assertThat(createdIndexes).hasSize(2).allSatisfy(name ->
        assertThat(name).matches("[a-z0-9][a-z0-9._-]{0,118}"));
  }

  @Test
  void rejectedStartCannotTouchTheIndex() {
    when(rebuilds.start(anyString(), anyString(), anyString(), any())).thenReturn(false);

    assertThatThrownBy(() -> manager.process(first)).isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(index, source);
    verify(tasks, never()).succeed(any(), any());
  }

  @Test
  void expiredAttemptCannotCreateAnIndexOrChangeTheRebuild() {
    when(tasks.renewLease(first)).thenReturn(false);
    PlatformException conflict = PlatformExceptions.conflict("TASK_STATE_CONFLICT", "领取批次已被替代");
    doThrow(conflict).when(tasks).cancelled(first);

    assertThatThrownBy(() -> manager.process(first)).isSameAs(conflict);

    verifyNoInteractions(index, source);
    verify(rebuilds, never()).start(anyString(), anyString(), anyString(), any());
    verify(rebuilds, never()).finish(anyString(), any(), anyLong(), any(), anyString(), any());
  }

  @Test
  void duplicateRunningAttemptDoesNotCreateOrCompleteAnything() {
    run.setTargetIndex("resume-facts-v" + RUN_ID.toLowerCase(Locale.ROOT) + "-attempt-1");
    run.setStatus("RUNNING");
    when(rebuilds.start(anyString(), anyString(), anyString(), any())).thenReturn(false);

    manager.process(first);

    verifyNoInteractions(index, source);
    verify(tasks, never()).succeed(any(), any());
    verify(tasks, never()).cancelled(any());
    verify(rebuilds, never()).finish(anyString(), any(), anyLong(), any(), anyString(), any());
  }

  @Test
  void cancellationBeforeStartingCompletesWithoutCreatingAnIndex() {
    when(tasks.renewLease(first)).thenReturn(false);

    manager.process(first);

    verifyNoInteractions(index, source);
    verify(tasks).cancelled(first);
    verify(rebuilds, never()).start(anyString(), anyString(), anyString(), any());
    verify(rebuilds).finish(eq(RUN_ID), eq(SearchRebuildStatus.CANCELLED), eq(0L), any(), anyString(), any());
  }

  @Test
  void completedCancellationKeepsItsExistingProgress() {
    when(tasks.renewLease(first)).thenReturn(false);
    run.setStatus("CANCELLED");
    run.setProjectedCount(15);

    manager.process(first);

    verifyNoInteractions(index, source);
    verify(rebuilds, never()).finish(anyString(), any(), anyLong(), any(), anyString(), any());
    assertThat(run.getProjectedCount()).isEqualTo(15);
  }

  @Test
  void cancellationObservedAfterTheLastPagePreventsAliasActivation() {
    AtomicBoolean cancelled = new AtomicBoolean();
    when(tasks.shouldStopProcessing(first)).thenAnswer(call -> cancelled.get());
    when(source.currentFactsAfter(any(), anyString(), anyInt())).thenAnswer(call -> {
      cancelled.set(true);
      return new ProjectionPage(List.of(), false);
    });

    manager.process(first);

    verify(index, never()).activateRebuild(any(SearchRebuildAttempt.class));
    verify(tasks).cancelled(first);
    verify(rebuilds).finish(anyString(), eq(SearchRebuildStatus.CANCELLED),
        anyLong(), any(), anyString(), any());
    verify(tasks, never()).succeed(any(), any());
  }

  private static TaskSnapshot task(int attempt) {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn("task-1");
    when(task.attemptCount()).thenReturn(attempt);
    return task;
  }
}
