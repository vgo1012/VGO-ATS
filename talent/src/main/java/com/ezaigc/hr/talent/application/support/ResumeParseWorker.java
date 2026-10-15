package com.ezaigc.hr.talent.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.AI;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_PARSE_TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "hr.resume-parsing.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
/** 简历解析后台任务处理器，负责轮询或消费异步任务。 */
@ManagedScheduledWorker
public class ResumeParseWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;

  private final AtomicBoolean stopping = new AtomicBoolean();
  private CoalescingTaskDispatcher dispatcher;
  private int concurrency;

  @Resource private PlatformExecutorProperties executorProperties;

  @Resource private ResumeParsingApplicationManager parsing;
  @Resource private AsyncTaskBatchRunner taskBatchRunner;
  @Resource(name = AI)
  private TaskExecutor taskExecutor;

  @PostConstruct
  void initializeConcurrency() {
    concurrency = executorProperties.getAiWorkloadSlots().getResumeParse();
    dispatcher = new CoalescingTaskDispatcher(concurrency);
    dispatcher.observe(taskMonitor);
  }

  @Scheduled(
      initialDelayString = "${hr.resume-parsing.initial-delay:5000}",
      fixedDelayString = "${hr.resume-parsing.poll-delay:2000}")
  @ScheduledTaskInfo(name = "简历解析任务轮询", module = "简历解析", channels = {"RESUME_PARSE"}, businessTypes = {"RESUME_PARSE"}, idleBackoffMaxMillis = 60000)
  public void poll() {
    if (stopping.get()) return;
    for (int slot = 0; slot < concurrency; slot++) dispatcher.dispatchPolling(
        RESUME_PARSE_TASK_TYPE,
        taskExecutor,
        this::drainAvailableTasks);
  }

  /** 每个槽只保持一个在途任务；当前任务返回后立即补位，不等待其他槽或下次轮询。 */
  private int drainAvailableTasks() {
    int processed = 0;
    while (!stopping.get() && !Thread.currentThread().isInterrupted()) {
      int claimed = taskBatchRunner.run(RESUME_PARSE_TASK_TYPE, 1, parsing::process);
      if (claimed == 0) break;
      processed += claimed;
    }
    return processed;
  }

  @PreDestroy
  public void stop() {
    stopping.set(true);
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void available(AsyncTaskAvailable event) {
    if (RESUME_PARSE_TASK_TYPE.equals(event.getTaskType())) {
      dispatcher.wakeUp();
      poll();
    }
  }
}
