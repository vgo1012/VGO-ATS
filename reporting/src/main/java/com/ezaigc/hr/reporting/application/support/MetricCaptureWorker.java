package com.ezaigc.hr.reporting.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import jakarta.annotation.PostConstruct;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MAINTENANCE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.reporting.application.manager.MetricCaptureApplicationManager;
import com.ezaigc.hr.reporting.config.MetricCaptureProperties;
import com.ezaigc.hr.reporting.enums.MetricCaptureLane;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.locks.LockSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 指标投影后台采集任务。 */
@Component
@ConditionalOnProperty(
    name = "hr.reporting.metric-capture.enabled",
    havingValue = "true",
    matchIfMissing = true)
@ManagedScheduledWorker
public class MetricCaptureWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;
  @PostConstruct
  void observeScheduledTasks() {
    incrementalDispatcher.observe(taskMonitor);
    reconciliationDispatcher.observe(taskMonitor);
  }

  private static final String INCREMENTAL_TASK_NAME = "METRIC_CAPTURE_INCREMENTAL";
  private static final String RECONCILIATION_TASK_NAME = "METRIC_CAPTURE_RECONCILIATION";

  private final CoalescingTaskDispatcher incrementalDispatcher =
      new CoalescingTaskDispatcher();
  private final CoalescingTaskDispatcher reconciliationDispatcher =
      new CoalescingTaskDispatcher();
  private volatile boolean stopping;

  @Resource private MetricCaptureApplicationManager manager;
  @Resource private MetricCaptureProperties properties;
  @Resource(name = MAINTENANCE)
  private TaskExecutor taskExecutor;

  @Scheduled(
      initialDelayString = "${hr.reporting.metric-capture.initial-delay:5s}",
      fixedDelayString = "${hr.reporting.metric-capture.poll-delay:30s}")
  @ScheduledTaskInfo(name = "指标增量采集", module = "运营指标", channels = {"METRIC_CAPTURE_INCREMENTAL"})
  public void pollIncremental() {
    if (shouldStop()) return;
    incrementalDispatcher.dispatch(
        INCREMENTAL_TASK_NAME,
        taskExecutor,
        () -> captureAll(MetricCaptureLane.INCREMENTAL, properties.getBatchSize()));
  }

  @Scheduled(
      initialDelayString = "${hr.reporting.metric-capture.reconciliation-initial-delay:1m}",
      fixedDelayString = "${hr.reporting.metric-capture.reconciliation-delay:10m}")
  @ScheduledTaskInfo(name = "指标补偿核对", module = "运营指标", channels = {"METRIC_CAPTURE_RECONCILIATION"})
  public void reconcile() {
    if (shouldStop()) return;
    reconciliationDispatcher.dispatch(
        RECONCILIATION_TASK_NAME,
        taskExecutor,
        () -> captureAll(MetricCaptureLane.RECONCILIATION, properties.getReconciliationBatchSize()));
  }

  private void captureAll(MetricCaptureLane lane, int limit) {
    if (shouldStop()) return;
    RuntimeException failure = null;
    for (MetricEventType source : MetricEventType.values()) {
      if (shouldStop()) break;
      try {
        captureWithTransientRetry(source, lane, limit);
      } catch (RuntimeException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private void captureWithTransientRetry(MetricEventType source, MetricCaptureLane lane, int limit) {
    for (int attempt = 1; ; attempt++) {
      if (shouldStop()) return;
      try {
        manager.captureNext(source, lane, limit);
        return;
      } catch (TransientDataAccessException exception) {
        if (shouldStop()) return;
        if (attempt == 3) {
          throw exception;
        }
        // INSERT ... SELECT 可能与在线业务写入短暂死锁；事务已回滚，短退避后安全重试。
        LockSupport.parkNanos(attempt * 50_000_000L);
      }
    }
  }

  /** 已进入的短事务由其自身完成；停止排队回调、后续来源及重试进入数据库。 */
  @PreDestroy
  public void stop() {
    stopping = true;
  }

  private boolean shouldStop() {
    return stopping || Thread.currentThread().isInterrupted();
  }
}
