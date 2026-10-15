package com.ezaigc.hr.common.infrastructure.concurrent;

import com.ezaigc.hr.common.infrastructure.logging.RepeatedFailureLogger;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/**
 * 将周期触发安全提交到专用线程池，并合并同一任务尚未完成时的重复触发。
 *
 * <p>该对象必须由每个独立周期任务单独持有，不能在不同业务任务之间共享。
 */
public final class CoalescingTaskDispatcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(CoalescingTaskDispatcher.class);

  private final Semaphore slots;
  private final PollingBackoff polling;
  private final RepeatedFailureLogger failures = new RepeatedFailureLogger(LOGGER);
  private ScheduledTaskMonitor monitor;

  public void observe(ScheduledTaskMonitor monitor) {
    this.monitor = monitor;
  }

  public void wakeUp() { polling.wakeUp(); }

  public boolean dispatchPolling(String taskName, TaskExecutor executor, IntSupplier work) {
    if (!polling.ready()) return false;
    long generation = polling.generation();
    return dispatch(taskName, executor, () -> {
      boolean worked = false;
      try { worked = work.getAsInt() > 0; }
      finally { polling.completed(generation, worked); }
    });
  }

  public CoalescingTaskDispatcher() {
    this(1);
  }

  public CoalescingTaskDispatcher(int maximumInFlight) {
    this(maximumInFlight, System::nanoTime);
  }

  public CoalescingTaskDispatcher(int maximumInFlight, LongSupplier ticker) {
    if (maximumInFlight < 1) throw new IllegalArgumentException("maximumInFlight must be positive");
    this.slots = new Semaphore(maximumInFlight);
    this.polling = new PollingBackoff(Objects.requireNonNull(ticker, "ticker"));
  }

  /**
   * 尝试提交任务。当前实例已有任务运行或排队时，本次触发会被安全合并。
   *
   * @return 已提交返回 {@code true}；已合并或线程池拒绝返回 {@code false}
   */
  public boolean dispatch(String taskName, TaskExecutor executor, Runnable task) {
    String requiredTaskName = requireTaskName(taskName);
    Objects.requireNonNull(executor, "executor");
    Objects.requireNonNull(task, "task");
    if (monitor != null) monitor.triggered(requiredTaskName);
    if (!slots.tryAcquire()) {
      if (monitor != null) monitor.coalesced(requiredTaskName);
      return false;
    }
    String runId = monitor == null ? null : monitor.queued(requiredTaskName);
    try {
      executor.execute(() -> run(requiredTaskName, task, runId));
      return true;
    } catch (TaskRejectedException exception) {
      slots.release();
      if (monitor != null) monitor.finished(requiredTaskName, runId, "DISPATCH_REJECTED");
      failures.failed(requiredTaskName, exception);
      return false;
    } catch (RuntimeException exception) {
      slots.release();
      if (monitor != null) monitor.finished(requiredTaskName, runId, "DISPATCH_FAILED");
      failures.failed(requiredTaskName, exception);
      return false;
    }
  }

  private void run(String taskName, Runnable task, String runId) {
    String errorCode = null;
    if (monitor != null) monitor.started(taskName, runId);
    try {
      task.run();
      failures.recovered(taskName);
    } catch (RuntimeException exception) {
      errorCode = "SCAN_FAILED";
      failures.failed(taskName, exception);
    } catch (Error error) {
      errorCode = "SCAN_ABORTED";
      throw error;
    } finally {
      if (monitor != null) monitor.finished(taskName, runId, errorCode);
      slots.release();
    }
  }

  private static String requireTaskName(String taskName) {
    if (taskName == null || taskName.isBlank() || taskName.length() > 100) {
      throw new IllegalArgumentException("taskName must contain 1 to 100 characters");
    }
    return taskName;
  }
}
