package com.ezaigc.hr.talent.application.support;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MAINTENANCE;
import static com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager.INTAKE_ADOPTION_TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalEvent;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.application.manager.ResumeIntakeCompletionManager;
import com.ezaigc.hr.talent.constant.ResumeIntakeWorkerConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在独立 AI 线程池中推动共享解析结果的业务完成与评分恢复。 */
@Component
@ConditionalOnProperty(prefix = "hr.resume-intake", name = "worker-enabled",
    havingValue = "true", matchIfMissing = true)
@ManagedScheduledWorker
public class ResumeIntakeCompletionWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;
  @PostConstruct
  void observeScheduledTasks() { dispatcher.observe(taskMonitor); }
  @Resource private ResumeIntakeCompletionManager manager;
  @Resource(name = MAINTENANCE) private TaskExecutor executor;
  @Resource private TaskScheduler scheduler;
  @Resource private Clock clock;
  private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();

  @Scheduled(initialDelayString = "${hr.resume-intake.initial-delay:5000}",
      fixedDelayString = "${hr.resume-intake.completion-delay:30s}")
  @ScheduledTaskInfo(name = "录入完成与恢复检查", module = "简历录入", channels = {"resume-intake-completion"})
  public void trigger() {
    dispatcher.dispatch("resume-intake-completion", executor, manager::completeAvailable);
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void available(AsyncTaskAvailable event) {
    if (ResumeIntakeWorkerConstants.COMPLETE.equals(event.getTaskType())) trigger();
  }

  /** 事实采纳和匹配终态提交后精准推进收件，不再等待 30 秒恢复扫描。 */
  @TransactionalEventListener(fallbackExecution = true)
  public void terminal(AsyncTaskTerminalEvent event) {
    if (INTAKE_ADOPTION_TASK_TYPE.equals(event.getTaskType())
        || ResumeIntakeScoringService.TASK_TYPE.equals(event.getTaskType())) {
      scheduler.schedule(this::trigger, clock.instant().plus(Duration.ofMillis(100)));
    }
  }
}
