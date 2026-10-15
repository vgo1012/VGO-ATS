package com.ezaigc.hr.intelligence.matching.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import org.springframework.transaction.event.TransactionalEventListener;
import jakarta.annotation.PostConstruct;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MATCHING;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "hr.matching.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
/** 匹配运行后台任务处理器，负责轮询或消费异步任务。 */
@ManagedScheduledWorker
public class MatchRunWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;
  private CoalescingTaskDispatcher dispatcher;
  private int concurrency;
  private final AtomicBoolean stopping = new AtomicBoolean();

  @Resource private MatchRunApplicationManager manager;
  @Resource private PlatformExecutorProperties executorProperties;
  @Resource private AsyncTaskBatchRunner taskBatchRunner;
  @Resource(name = MATCHING)
  private TaskExecutor taskExecutor;

  @PostConstruct
  void initializeConcurrency() {
    concurrency = executorProperties.getMatching().getCoreSize();
    dispatcher = new CoalescingTaskDispatcher(concurrency);
    dispatcher.observe(taskMonitor);
  }

  @Scheduled(
      initialDelayString = "${hr.matching.initial-delay:5000}",
      fixedDelayString = "${hr.matching.poll-delay:2000}")
  @ScheduledTaskInfo(name = "匹配任务轮询", module = "智能匹配", channels = {"MATCH_RUN"}, businessTypes = {"MATCH_RUN"}, idleBackoffMaxMillis = 60000)
  public void poll() {
    if (shouldStop()) return;
    for (int slot = 0; slot < concurrency; slot++) {
      dispatcher.dispatchPolling(MatchRunApplicationManager.TASK_TYPE, taskExecutor, this::drain);
    }
  }

  @PreDestroy
  public void stop() {
    stopping.set(true);
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void available(AsyncTaskAvailable event) {
    if (MatchRunApplicationManager.TASK_TYPE.equals(event.getTaskType())) {
      dispatcher.wakeUp();
      poll();
    }
  }

  private int drain() {
    int processed = 0;
    while (!shouldStop()) {
      int count = taskBatchRunner.run(MatchRunApplicationManager.TASK_TYPE, 1, manager::process);
      if (count == 0) break;
      processed += count;
    }
    return processed;
  }

  private boolean shouldStop() {
    return stopping.get() || Thread.currentThread().isInterrupted();
  }
}
