package com.ezaigc.hr.common.infrastructure.concurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class CoalescingTaskDispatcherTest {

  @Test
  void boundsQueuedAndRunningWorkAndReleasesExactlyOneSlotOnCompletion() {
    CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher(2);
    List<Runnable> submitted = new ArrayList<>();
    TaskExecutor executor = submitted::add;
    assertThat(dispatcher.dispatch("resume-parse", executor, () -> {})).isTrue();
    assertThat(dispatcher.dispatch("resume-parse", executor, () -> {})).isTrue();
    assertThat(dispatcher.dispatch("resume-parse", executor, () -> {})).isFalse();
    submitted.getFirst().run();
    assertThat(dispatcher.dispatch("resume-parse", executor, () -> {})).isTrue();
    assertThat(dispatcher.dispatch("resume-parse", executor, () -> {})).isFalse();
    assertThat(submitted).hasSize(3);
  }

  @Test
  void coalescesTriggersUntilSubmittedTaskFinishes() {
    CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
    AtomicReference<Runnable> submitted = new AtomicReference<>();
    AtomicInteger executions = new AtomicInteger();
    TaskExecutor executor = submitted::set;

    assertThat(dispatcher.dispatch("resume-parse", executor, executions::incrementAndGet)).isTrue();
    assertThat(dispatcher.dispatch("resume-parse", executor, executions::incrementAndGet)).isFalse();
    assertThat(executions).hasValue(0);

    submitted.get().run();

    assertThat(executions).hasValue(1);
    assertThat(dispatcher.dispatch("resume-parse", executor, executions::incrementAndGet)).isTrue();
  }

  @Test
  void releasesTaskAfterExecutionFailure() {
    CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
    AtomicReference<Runnable> submitted = new AtomicReference<>();
    TaskExecutor executor = submitted::set;

    assertThat(
            dispatcher.dispatch(
                "search-projection",
                executor,
                () -> {
                  throw new IllegalStateException("dependency unavailable");
                }))
        .isTrue();

    assertThatCode(() -> submitted.get().run()).doesNotThrowAnyException();
    assertThat(dispatcher.dispatch("search-projection", executor, () -> {})).isTrue();
  }

  @Test
  void releasesTaskAfterExecutorRejection() {
    CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
    TaskExecutor rejectingExecutor =
        task -> {
          throw new TaskRejectedException("queue full");
        };
    TaskExecutor directExecutor = Runnable::run;

    assertThat(dispatcher.dispatch("audit-capture", rejectingExecutor, () -> {})).isFalse();
    assertThat(dispatcher.dispatch("audit-capture", directExecutor, () -> {})).isTrue();
  }
}
