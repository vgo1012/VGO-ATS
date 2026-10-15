package com.ezaigc.hr.common.infrastructure.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.mockito.ArgumentMatchers;

class AsyncTaskBatchRunnerTest {

  private AsyncTaskPort tasks;
  private AsyncTaskBatchRunner runner;
  private Consumer<TaskSnapshot> processor;
  private ScheduledFuture<?> heartbeat;
  private TaskScheduler taskScheduler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    tasks = mock(AsyncTaskPort.class);
    taskScheduler = mock(TaskScheduler.class);
    heartbeat = mock(ScheduledFuture.class);
    doReturn(heartbeat)
        .when(taskScheduler)
        .scheduleAtFixedRate(
            ArgumentMatchers.any(Runnable.class),
            ArgumentMatchers.any(Instant.class),
            ArgumentMatchers.eq(Duration.ofMinutes(1)));
    runner = new AsyncTaskBatchRunner(tasks, taskScheduler);
    processor = mock(Consumer.class);
  }

  @Test
  void continuesBatchAfterUnexpectedTaskFailure() {
    TaskSnapshot first = task("01KCOMMONFAIL00000000000000");
    TaskSnapshot second = task("01KCOMMONNEXT00000000000000");
    when(tasks.claim("TEST_TASK", 2)).thenReturn(List.of(first, second));
    doThrow(new IllegalStateException("unexpected")).when(processor).accept(first);

    runner.run("TEST_TASK", 2, processor);

    verify(tasks)
        .failUnhandled(
            first,
            "TASK_EXECUTION_FAILED",
            "任务处理发生未预期错误，请稍后重试",
            true);
    verify(processor).accept(second);
  }

  @Test
  void continuesBatchWhenFailureStateCannotBePersisted() {
    TaskSnapshot first = task("01KFINALIZEFAIL000000000000");
    TaskSnapshot second = task("01KFINALIZENEXT000000000000");
    when(tasks.claim("TEST_TASK", 2)).thenReturn(List.of(first, second));
    doThrow(new IllegalStateException("processing failed")).when(processor).accept(first);
    when(
            tasks.failUnhandled(
                first,
                "TASK_EXECUTION_FAILED",
                "任务处理发生未预期错误，请稍后重试",
                true))
        .thenThrow(new IllegalStateException("database unavailable"));

    runner.run("TEST_TASK", 2, processor);

    verify(processor).accept(second);
  }

  @Test
  void leavesExpectedTaskResultToProcessor() {
    TaskSnapshot task = task("01KCOMMONSUCCESS000000000000");
    when(tasks.claim("TEST_TASK", 1)).thenReturn(List.of(task));

    runner.run("TEST_TASK", 1, processor);

    verify(processor).accept(task);
    verify(heartbeat).cancel(false);
    verify(tasks, never())
        .failUnhandled(
            task,
            "TASK_EXECUTION_FAILED",
            "任务处理发生未预期错误，请稍后重试",
            true);
  }

  @Test
  void scheduledHeartbeatRenewsTheClaimedAttempt() {
    TaskSnapshot task = task("01KCOMMONHEARTBEAT000000000");
    when(task.attemptCount()).thenReturn(2);
    when(tasks.claim("TEST_TASK", 1)).thenReturn(List.of(task));
    when(tasks.renewLease(task)).thenReturn(true);

    runner.run("TEST_TASK", 1, processor);

    ArgumentCaptor<Runnable> heartbeatAction = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler)
        .scheduleAtFixedRate(
            heartbeatAction.capture(),
            ArgumentMatchers.any(Instant.class),
            ArgumentMatchers.eq(Duration.ofMinutes(1)));
    heartbeatAction.getValue().run();
    verify(tasks).renewLease(task);
  }

  @Test
  void propagatesClaimFailureBeforeAnyTaskStarts() {
    IllegalStateException failure = new IllegalStateException("claim failed");
    when(tasks.claim("TEST_TASK", 1)).thenThrow(failure);

    assertThatThrownBy(() -> runner.run("TEST_TASK", 1, processor)).isSameAs(failure);

    verify(processor, never()).accept(ArgumentMatchers.any());
  }

  @Test
  void usesSlf4jFacadeForFailureLogging() throws NoSuchFieldException {
    assertThat(AsyncTaskBatchRunner.class.getDeclaredField("LOGGER").getType().getName())
        .isEqualTo("org.slf4j.Logger");
  }

  @Test
  @ResourceLock("SYSTEM_ERR")
  void failureLogsPreserveDiagnosticsWithoutLeakingExceptionPayloads() {
    TaskSnapshot first = task("01KLOGPRIVACY0000000000000");
    TaskSnapshot second = task("01KLOGNEXT0000000000000000");
    when(tasks.claim("TEST_TASK", 2)).thenReturn(List.of(first, second));
    IllegalStateException failure = new IllegalStateException("audit-person@example.invalid",
        new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new IllegalStateException("test-secret-marker"));
    doThrow(failure).when(processor).accept(first);
    when(tasks.failUnhandled(first, "TASK_EXECUTION_FAILED", "任务处理发生未预期错误，请稍后重试", true))
        .thenThrow(new IllegalStateException("audit-person@example.invalid database value"));
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream original = System.err;
    try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setErr(output);
      runner.run("TEST_TASK", 2, processor);
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8))
        .contains("async-task-processing-failed", "async-task-finalization-failed", first.id(),
            "java.lang.IllegalStateException", "java.lang.IllegalArgumentException", "AsyncTaskBatchRunnerTest")
        .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    verify(processor).accept(second);
    assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");
  }

  private static TaskSnapshot task(String id) {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn(id);
    return task;
  }
}
