package com.ezaigc.hr.recruiting.interview.application.support;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.OUTBOX;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewExternalCalendarApplicationManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "hr.wecom.collaboration.worker-enabled", havingValue = "true", matchIfMissing = false)
@ManagedScheduledWorker
public class InterviewExternalCalendarWorker {
    @Resource private InterviewExternalCalendarApplicationManager manager;
    @Resource private ScheduledTaskMonitor monitor;
    @Resource(name = OUTBOX) private TaskExecutor executor;
    private final CoalescingTaskDispatcher dispatcher = new CoalescingTaskDispatcher();
    private volatile boolean stopping;

    @PostConstruct
    void observe() {
        dispatcher.observe(monitor);
    }

    @Scheduled(initialDelayString = "${hr.wecom.collaboration.initial-delay:5000}",
            fixedDelayString = "${hr.wecom.collaboration.poll-delay:2000}")
    @ScheduledTaskInfo(name = "企业微信面试日程投影", module = "面试管理",
            channels = {"wecom-interview-calendar"},
            enabledProperty = "hr.wecom.collaboration.worker-enabled", enabledByDefault = false)
    public void poll() {
        if (shouldStop()) return;
        dispatcher.dispatchPolling("wecom-interview-calendar", executor, () -> {
            if (shouldStop()) return 0;
            var due = manager.due(10);
            int invoked = 0;
            for (var value : due) {
                if (shouldStop()) break;
                manager.process(value.getId());
                invoked++;
            }
            // 已调用处理方法的条数，不代表实际领取或远端同步成功。
            return invoked;
        });
    }

    /** 保留在途处理完成，停止排队查询及后续记录领取。 */
    @PreDestroy
    public void stop() {
        stopping = true;
    }

    private boolean shouldStop() {
        return stopping || Thread.currentThread().isInterrupted();
    }
}
