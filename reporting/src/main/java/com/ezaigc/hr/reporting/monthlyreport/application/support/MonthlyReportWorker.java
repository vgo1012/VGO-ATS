package com.ezaigc.hr.reporting.monthlyreport.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import jakarta.annotation.PostConstruct;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportProcessingManager;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ManagedScheduledWorker
public class MonthlyReportWorker {
    @Resource private ScheduledTaskMonitor taskMonitor;
    @PostConstruct
    void observeScheduledTasks() {
        dispatcher.observe(taskMonitor);
        cleanupDispatcher.observe(taskMonitor);
        recoveryDispatcher.observe(taskMonitor);
    }
    private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
    private final CoalescingTaskDispatcher cleanupDispatcher = new CoalescingTaskDispatcher();
    private final CoalescingTaskDispatcher recoveryDispatcher = new CoalescingTaskDispatcher();
    private volatile boolean stopping;
    @Resource private MonthlyReportProcessingManager manager;
    @Resource private AsyncTaskBatchRunner runner;
    @Resource private MonthlyReportProperties properties;
    @Resource private MonthlyReportTransactionManager transactions;
    @Resource(name = ExecutorNames.REPORTING) private TaskExecutor executor;
    @Scheduled(initialDelayString = "${hr.reporting.monthly-report.initial-delay:5000}", fixedDelayString = "${hr.reporting.monthly-report.poll-delay:5000}")
    @ScheduledTaskInfo(name = "月报处理轮询", module = "月度汇报", channels = {"monthly-report"}, businessTypes = {"MONTHLY_REPORT_VALIDATE", "MONTHLY_RECRUITMENT_REPORT"}, enabledProperty = "hr.reporting.monthly-report.enabled", idleBackoffMaxMillis = 60000)
    public void poll() {
        if (shouldStop() || !properties.isEnabled()) return;
        dispatcher.dispatchPolling("monthly-report", executor, () -> {
            if (shouldStop()) return 0;
            int count = runner.run(MonthlyReportConstants.VALIDATE_TASK, 1, manager::process);
            if (!shouldStop()) count += runner.run(MonthlyReportConstants.GENERATE_TASK, 1, manager::process);
            return count;
        });
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void available(AsyncTaskAvailable event) {
        if (MonthlyReportConstants.VALIDATE_TASK.equals(event.getTaskType())
                || MonthlyReportConstants.GENERATE_TASK.equals(event.getTaskType())) {
            dispatcher.wakeUp();
            poll();
        }
    }

    @Scheduled(initialDelayString = "${hr.reporting.monthly-report.recovery-initial-delay:1m}", fixedDelayString = "${hr.reporting.monthly-report.recovery-delay:5m}")
    @ScheduledTaskInfo(name = "月报中断上传恢复", module = "月度汇报", channels = {"monthly-report-upload-recovery"})
    public void recoverUploads() {
        if (shouldStop()) return;
        recoveryDispatcher.dispatch("monthly-report-upload-recovery", executor, () -> {
            if (!shouldStop()) transactions.recoverAbandonedUploads();
        });
    }

    @Scheduled(initialDelayString = "${hr.reporting.monthly-report.cleanup-initial-delay:5m}", fixedDelayString = "${hr.reporting.monthly-report.cleanup-delay:1h}")
    @ScheduledTaskInfo(name = "月报过期文件清理", module = "月度汇报", channels = {"monthly-report-cleanup"})
    public void cleanup() {
        if (shouldStop()) return;
        cleanupDispatcher.dispatch("monthly-report-cleanup", executor, () -> {
            if (!shouldStop()) manager.cleanup();
        });
    }
    private boolean shouldStop() { return stopping || Thread.currentThread().isInterrupted(); }
    @PreDestroy public void stop() { stopping = true; }
}
