package com.ezaigc.hr.intelligence.matching.application.support;

import static com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager.TASK_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties.Pool;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

class MatchRunWorkerTest {
  private final MatchRunApplicationManager manager = mock(MatchRunApplicationManager.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final MatchRunWorker worker = new MatchRunWorker();

  MatchRunWorkerTest() {
    ReflectionTestUtils.setField(worker, "manager", manager);
    ReflectionTestUtils.setField(worker, "taskBatchRunner",
        new AsyncTaskBatchRunner(tasks, mock(TaskScheduler.class)));
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) Runnable::run);
    ReflectionTestUtils.setField(worker, "taskMonitor", mock(ScheduledTaskMonitor.class));
    ReflectionTestUtils.setField(worker, "executorProperties", new PlatformExecutorProperties());
    worker.initializeConcurrency();
  }

  @Test
  void submitsAllFiftyConfiguredScoringSlots() {
    var concurrentWorker = new MatchRunWorker();
    var properties = new PlatformExecutorProperties();
    properties.setMatching(new Pool(50, 50, 50));
    var submitted = new ArrayList<Runnable>();
    ReflectionTestUtils.setField(concurrentWorker, "manager", manager);
    ReflectionTestUtils.setField(concurrentWorker, "taskBatchRunner",
        new AsyncTaskBatchRunner(tasks, mock(TaskScheduler.class)));
    ReflectionTestUtils.setField(concurrentWorker, "taskExecutor", (TaskExecutor) submitted::add);
    ReflectionTestUtils.setField(concurrentWorker, "taskMonitor", mock(ScheduledTaskMonitor.class));
    ReflectionTestUtils.setField(concurrentWorker, "executorProperties", properties);
    concurrentWorker.initializeConcurrency();

    concurrentWorker.poll();

    assertThat(submitted).hasSize(50);
    verifyNoInteractions(tasks, manager);
  }

  @Test
  void claimsNextTaskOnlyAfterCurrentTaskCompletesAndKeepsThreeItemBound() {
    var task = task("task");
    when(tasks.claim(TASK_TYPE, 1))
        .thenReturn(List.of(task), List.of(task), List.of(task), List.of());
    worker.poll();
    var order = inOrder(tasks, manager);
    for (int i = 0; i < 3; i++) {
      order.verify(tasks).claim(TASK_TYPE, 1);
      order.verify(manager).process(task);
    }
    verify(tasks, times(4)).claim(TASK_TYPE, 1);
    verify(tasks, never()).claim(TASK_TYPE, 3);
  }

  @Test
  void emptyQueueEndsDrainImmediately() {
    when(tasks.claim(TASK_TYPE, 1)).thenReturn(List.of());
    worker.poll();
    verify(tasks).claim(TASK_TYPE, 1);
    verifyNoInteractions(manager);
  }

  @Test
  void failedTaskIsFinalizedBeforeNextTaskIsClaimed() {
    var first = task("first");
    var next = task("next");
    when(tasks.claim(TASK_TYPE, 1)).thenReturn(List.of(first), List.of(next), List.of());
    doThrow(new IllegalStateException("synthetic failure")).when(manager).process(first);
    worker.poll();
    var order = inOrder(tasks, manager);
    order.verify(tasks).claim(TASK_TYPE, 1);
    order.verify(manager).process(first);
    order.verify(tasks).failUnhandled(eq(first), anyString(), anyString(), eq(true));
    order.verify(tasks).claim(TASK_TYPE, 1);
    order.verify(manager).process(next);
  }

  @Test
  void queuedWorkDoesNotClaimAfterShutdown() {
    var queued = new ArrayList<Runnable>();
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) queued::add);
    worker.poll();
    worker.poll();
    worker.stop();
    queued.getFirst().run();
    worker.poll();
    assertThat(queued).hasSize(1);
    verifyNoInteractions(tasks, manager);
  }

  @Test
  void blockedTaskDoesNotPreclaimAndConcurrentPollsStayCoalesced() throws Exception {
    var task = task("blocked");
    when(tasks.claim(TASK_TYPE, 1)).thenReturn(List.of(task));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(invocation -> {
      entered.countDown();
      assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
      return null;
    }).when(manager).process(task);
    var executor = Executors.newSingleThreadExecutor();
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) executor::execute);
    try {
      worker.poll();
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      worker.poll();
      verify(tasks).claim(TASK_TYPE, 1);
      worker.stop();
      release.countDown();
      executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
      verify(tasks).claim(TASK_TYPE, 1);
      verify(manager).process(task);
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void interruptionBetweenTasksStopsFurtherClaims() {
    var task = task("interrupted");
    when(tasks.claim(TASK_TYPE, 1)).thenReturn(List.of(task));
    doAnswer(invocation -> {
      Thread.currentThread().interrupt();
      return null;
    }).when(manager).process(any());
    try {
      worker.poll();
      verify(tasks).claim(TASK_TYPE, 1);
    } finally {
      Thread.interrupted();
    }
  }

  private TaskSnapshot task(String id) {
    var task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn(id);
    return task;
  }
}
