package com.ezaigc.hr.integration.mail.application;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MAIL;
import static com.ezaigc.hr.integration.mail.constant.MailResumeConstants.TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.integration.mail.application.manager.MailResumeApplicationManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hr.mail-resume.enabled", havingValue = "true")
@ManagedScheduledWorker
public class MailResumeWorker {

  @Resource private MailResumeApplicationManager manager;
  @Resource private AsyncTaskBatchRunner runner;
  @Resource private ScheduledTaskMonitor monitor;
  @Resource(name = MAIL) private TaskExecutor executor;
  private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
  private volatile boolean stopping;

  @PostConstruct
  void observe() {
    dispatcher.observe(monitor);
  }

  @PreDestroy
  void stop() {
    stopping = true;
  }

  @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
  @ScheduledTaskInfo(
      name = "邮件简历采集",
      module = "简历收件",
      channels = {"mail-resume-ingest"},
      businessTypes = {TASK_TYPE},
      enabledProperty = "hr.mail-resume.enabled",
      enabledByDefault = false)
  public void poll() {
    if (shouldStop()) return;
    dispatcher.dispatch("mail-resume-ingest", executor, () -> {
      if (shouldStop()) return;
      manager.schedule();
      for (int index = 0; index < 5 && !shouldStop(); index++) {
        if (runner.run(TASK_TYPE, 1, manager::process) == 0) break;
      }
    });
  }

  private boolean shouldStop() {
    return stopping || Thread.currentThread().isInterrupted();
  }
}
