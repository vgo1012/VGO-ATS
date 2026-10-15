package com.ezaigc.hr.identity.application.support;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.identity.application.manager.WecomSyncApplicationManager;
import com.ezaigc.hr.identity.application.manager.WecomSyncExecutionManager;
import com.ezaigc.hr.identity.config.WecomSyncProperties;
import com.ezaigc.hr.identity.domain.event.WecomSyncRequested;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ManagedScheduledWorker
public class WecomSyncWorker {
    @Resource private WecomSyncApplicationManager manager;
    @Resource private WecomSyncExecutionManager execution;
    @Resource private WecomSyncProperties properties;
    @Resource private ScheduledTaskMonitor monitor;
    @Resource(name = ExecutorNames.DIRECTORY) private TaskExecutor executor;
    private final CoalescingTaskDispatcher daily = new CoalescingTaskDispatcher();
    private final CoalescingTaskDispatcher recovery = new CoalescingTaskDispatcher();

    @PostConstruct
    void observe() {
        daily.observe(monitor);
        recovery.observe(monitor);
    }

    @Scheduled(cron = "${hr.wecom.sync.cron:0 0 2 * * *}", zone = "Asia/Shanghai")
    @ScheduledTaskInfo(name = "企业微信每日通讯录同步", module = "组织目录", channels = {"wecom-daily-sync"},
            enabledProperty = "hr.wecom.sync.enabled", enabledByDefault = false)
    public void daily() {
        if (properties.isEnabled()) daily.dispatch("wecom-daily-sync", executor, manager::scheduleDaily);
    }

    @Scheduled(initialDelayString = "${hr.wecom.sync.recovery-delay:600000}",
            fixedDelayString = "${hr.wecom.sync.recovery-delay:600000}")
    @ScheduledTaskInfo(name = "企业微信同步批次恢复", module = "组织目录", channels = {"wecom-sync-recovery"},
            enabledProperty = "hr.wecom.sync.enabled", enabledByDefault = false)
    public void recover() {
        if (properties.isEnabled()) recovery.dispatch("wecom-sync-recovery", executor,
                () -> execution.execute(manager.configuredCorp()));
    }

    @TransactionalEventListener
    public void requested(WecomSyncRequested event) {
        if (properties.isEnabled()) recovery.dispatch("wecom-sync-recovery", executor,
                () -> execution.execute(event.getCorpId()));
    }
}
