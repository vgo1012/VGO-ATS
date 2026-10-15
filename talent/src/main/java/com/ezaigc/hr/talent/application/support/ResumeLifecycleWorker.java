package com.ezaigc.hr.talent.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import jakarta.annotation.PostConstruct;
import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MAINTENANCE;
import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.talent.application.manager.ResumeLifecycleJobManager;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix="hr.resume-lifecycle",name="enabled",havingValue="true")
@ManagedScheduledWorker
public class ResumeLifecycleWorker {
  @Resource private ScheduledTaskMonitor taskMonitor;
  @PostConstruct
  void observeScheduledTasks() {
    dailyDispatcher.observe(taskMonitor);
    recoveryDispatcher.observe(taskMonitor);
  }
  @Resource private ResumeLifecycleJobManager manager;
  @Resource(name=MAINTENANCE) private TaskExecutor executor;
  private final CoalescingTaskDispatcher dailyDispatcher=new CoalescingTaskDispatcher();
  private final CoalescingTaskDispatcher recoveryDispatcher=new CoalescingTaskDispatcher();
  @Scheduled(cron="0 0 1 * * *",zone="Asia/Shanghai")
  @ScheduledTaskInfo(name = "简历生命周期每日结算", module = "简历流转", channels = {"resume-lifecycle-daily"})
  public void daily() { dailyDispatcher.dispatch("resume-lifecycle-daily",executor,manager::execute); }
  /** 启动及故障恢复只补已到达的结算边界，日历发布后执行降级重算。 */
  @Scheduled(initialDelayString="60000",fixedDelayString="300000")
  @ScheduledTaskInfo(name = "简历生命周期恢复检查", module = "简历流转", channels = {"resume-lifecycle-recovery"})
  public void recover() { recoveryDispatcher.dispatch("resume-lifecycle-recovery",executor,manager::execute); }
}
