package com.ezaigc.hr.intelligence.search.application.support;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.REBUILD_TASK_TYPE;
import static com.ezaigc.hr.talent.api.ResumeFactProjectionSource.TASK_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.intelligence.search.application.manager.SearchProjectionApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchTaskApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchStateReplayApplicationManager;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

/** 真实轮询分发器和任务批处理器的故障隔离；持久化及投影端口由测试控制。 */
class SearchProjectionWorkerTest {
  private final SearchProjectionApplicationManager manager = mock(SearchProjectionApplicationManager.class);
  private final SearchTaskApplicationManager taskManager = mock(SearchTaskApplicationManager.class);
  private final SearchStateReplayApplicationManager stateReplay = mock(SearchStateReplayApplicationManager.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final TaskSnapshot rebuild = mock(TaskSnapshot.class);
  private final SearchProjectionWorker worker = new SearchProjectionWorker();
  private final AtomicLong now = new AtomicLong();

  SearchProjectionWorkerTest() {
    ReflectionTestUtils.setField(worker, "dispatcher", new CoalescingTaskDispatcher(1, now::get));
    ReflectionTestUtils.setField(worker, "rebuildDispatcher", new CoalescingTaskDispatcher(1, now::get));
    when(rebuild.id()).thenReturn("synthetic-rebuild-task");
    when(tasks.claim(REBUILD_TASK_TYPE, 1)).thenReturn(List.of(rebuild));
    ReflectionTestUtils.setField(worker, "manager", manager);
    ReflectionTestUtils.setField(worker, "taskManager", taskManager);
    ReflectionTestUtils.setField(worker, "stateReplay", stateReplay);
    ReflectionTestUtils.setField(worker, "taskBatchRunner",
        new AsyncTaskBatchRunner(tasks, mock(TaskScheduler.class)));
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) Runnable::run);
  }

  @Test
  void replayRemainsBoundedAndFailureCanResumeNextPollIndependentlyOfRebuild() {
    when(stateReplay.replayNext(200)).thenReturn(200);
    worker.poll();
    verify(stateReplay, times(5)).replayNext(200);
    when(stateReplay.replayNext(200)).thenThrow(new IllegalStateException("Synthetic replay failure"));
    worker.poll();
    verify(manager, times(2)).process(rebuild);
    doReturn(0).when(stateReplay).replayNext(200);
    now.addAndGet(TimeUnit.MINUTES.toNanos(1));
    worker.poll();
    verify(stateReplay, times(7)).replayNext(200);
    verify(manager, times(3)).process(rebuild);
  }

  @Test
  void notificationOnlySchedulesBoundedSqlClaimsAndShutdownPreventsMoreWork() {
    var projection = mock(TaskSnapshot.class);
    when(projection.id()).thenReturn("projection");
    when(tasks.claim(TASK_TYPE, 1)).thenReturn(List.of(projection));
    var pending = new ArrayList<Runnable>();
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) pending::add);
    worker.available(new AsyncTaskAvailable(TASK_TYPE, "ignored-unclaimed-id", "trace"));
    worker.available(new AsyncTaskAvailable(TASK_TYPE, "ignored-unclaimed-id", "trace"));
    verifyNoInteractions(taskManager);
    assertThat(pending).hasSize(2);
    pending.removeFirst().run();
    pending.removeFirst().run();
    verify(tasks, times(50)).claim(TASK_TYPE, 1);
    verify(taskManager, times(50)).process(projection);
    verify(manager).process(rebuild);
    worker.stop();
    worker.poll();
    assertThat(pending).isEmpty();
  }

  @Test
  void durableClaimFailureDoesNotBlockProjectionOrRebuild() {
    when(tasks.claim(TASK_TYPE, 1)).thenThrow(new IllegalStateException("synthetic failure"));
    worker.poll();
    verify(manager).projectNext(200);
    verify(manager).process(rebuild);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void failedProjectionStillRunsRebuildAndAllowsTheNextPoll(int failingPage) {
    AtomicInteger pages = new AtomicInteger();
    when(manager.projectNext(200)).thenAnswer(ignored -> {
      int page = pages.incrementAndGet();
      if (page == failingPage) throw new IllegalStateException("Synthetic index write failure");
      return page < failingPage ? 200 : 0;
    });

    worker.poll();
    verify(tasks).claim(REBUILD_TASK_TYPE, 1);
    verify(manager).process(rebuild);
    assertThat(pages.get()).isEqualTo(failingPage);

    now.addAndGet(TimeUnit.MINUTES.toNanos(1));
    worker.poll();
    verify(tasks, times(2)).claim(REBUILD_TASK_TYPE, 1);
    verify(manager, times(2)).process(rebuild);
    assertThat(pages.get()).isEqualTo(failingPage + 1);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 200})
  void successfulProjectionRemainsBoundedBeforeRunningRebuild(int pageSize) {
    when(manager.projectNext(200)).thenReturn(pageSize);

    worker.poll();

    verify(manager, times(pageSize == 0 ? 1 : 5)).projectNext(200);
    verify(tasks).claim(REBUILD_TASK_TYPE, 1);
    verify(manager).process(rebuild);
  }

  @Test
  void bothFailuresAreLoggedWithoutSensitiveMessagesAndDoNotBlockLaterPolls() {
    when(manager.projectNext(200)).thenThrow(new IllegalStateException("synthetic-secret-projection"));
    when(tasks.claim(REBUILD_TASK_TYPE, 1)).thenThrow(new IllegalArgumentException("synthetic-secret-claim"));
    Logger projectionLog = (Logger) LoggerFactory.getLogger(SearchProjectionWorker.class);
    Logger dispatcherLog = (Logger) LoggerFactory.getLogger(CoalescingTaskDispatcher.class);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    projectionLog.addAppender(captured);
    dispatcherLog.addAppender(captured);
    try {
      worker.poll();

      verify(tasks).claim(REBUILD_TASK_TYPE, 1);
      var failures = captured.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
      assertThat(failures).hasSize(2);
      assertThat(failures).allSatisfy(event -> {
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getFormattedMessage() + ThrowableProxyUtil.asString(event.getThrowableProxy()))
            .doesNotContain("synthetic-secret");
      });
      worker.poll();
      verify(tasks).claim(REBUILD_TASK_TYPE, 1);
      now.addAndGet(TimeUnit.MINUTES.toNanos(1));
      worker.poll();
      verify(tasks, times(2)).claim(REBUILD_TASK_TYPE, 1);
      verify(manager, times(2)).projectNext(200);
    } finally {
      projectionLog.detachAppender(captured);
      dispatcherLog.detachAppender(captured);
      captured.stop();
    }
  }

  @Test
  void blockedRebuildDoesNotPreventIncrementalProjectionAndReplay() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(ignored -> {
      entered.countDown();
      assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
      throw new IllegalStateException("Synthetic blocked rebuild failure");
    }).when(manager).process(rebuild);
    var executor = Executors.newFixedThreadPool(2);
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) executor::execute);
    try {
      worker.poll();
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
      now.addAndGet(TimeUnit.MINUTES.toNanos(1));
      worker.poll();
      executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
      verify(manager, times(2)).projectNext(200);
      verify(stateReplay, times(2)).replayNext(200);
      verify(tasks).claim(REBUILD_TASK_TYPE, 1);
      verify(manager).process(rebuild);
      worker.stop();
      worker.poll();
      release.countDown();
      executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
      verify(manager, times(2)).projectNext(200);
      verify(manager).process(rebuild);
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void shutdownPreventsQueuedProjectionAndRebuildFromClaimingTasks() {
    var pending = new ArrayList<Runnable>();
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) pending::add);
    worker.poll();
    worker.stop();
    pending.forEach(Runnable::run);
    worker.poll();
    assertThat(pending).hasSize(2);
    verifyNoInteractions(manager, taskManager, stateReplay);
    verify(tasks, times(0)).claim(REBUILD_TASK_TYPE, 1);
    verify(tasks, times(0)).claim(TASK_TYPE, 1);
  }
}
