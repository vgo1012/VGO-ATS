package com.ezaigc.hr.recruiting.recruitment.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import jakarta.annotation.PostConstruct;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MAINTENANCE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.recruiting.recruitment.application.manager.EngagementLockApplicationManager;
import jakarta.annotation.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 跟进归属锁定过期后台任务处理器，负责轮询或消费异步任务。 */
@Component
@ManagedScheduledWorker
public class EngagementLockExpiryWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;
  @PostConstruct
  void observeScheduledTasks() { dispatcher.observe(taskMonitor); }

  private static final String TASK_NAME = "ENGAGEMENT_LOCK_EXPIRY";

  private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();

  @Resource private EngagementLockApplicationManager manager;
  @Resource(name = MAINTENANCE)
  private TaskExecutor taskExecutor;

  @Scheduled(
      initialDelayString = "${hr.recruitment.engagement-expiry-initial-delay:60000}",
      fixedDelayString = "${hr.recruitment.engagement-expiry-delay:60000}")
  @ScheduledTaskInfo(name = "沟通锁到期检查", module = "招聘流程", channels = {"ENGAGEMENT_LOCK_EXPIRY"})
  public void expire() {
    dispatcher.dispatch(TASK_NAME, taskExecutor, () -> manager.expireDue(200));
  }
}
