package com.ezaigc.hr.recruiting.demand.application.support;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import jakarta.annotation.PostConstruct;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.AI;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import org.springframework.transaction.event.TransactionalEventListener;
import com.ezaigc.hr.recruiting.demand.application.manager.JdParsingApplicationManager;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "hr.jd-parsing.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
@ManagedScheduledWorker
public class JdParseWorker {
    @Resource private ScheduledTaskMonitor taskMonitor;

    private CoalescingTaskDispatcher dispatcher;
    private int concurrency;

    @Resource
    private PlatformExecutorProperties executorProperties;

    @Resource
    private JdParsingApplicationManager manager;

    @Resource
    private AsyncTaskBatchRunner taskBatchRunner;

    @Resource(name = AI)
    private TaskExecutor taskExecutor;

    @PostConstruct
    void initializeConcurrency() {
        concurrency = executorProperties.getAiWorkloadSlots().getJdParse();
        dispatcher = new CoalescingTaskDispatcher(concurrency);
        dispatcher.observe(taskMonitor);
    }

    @Scheduled(
            initialDelayString = "${hr.jd-parsing.initial-delay:5000}",
            fixedDelayString = "${hr.jd-parsing.poll-delay:2000}")
    @ScheduledTaskInfo(name = "JD 解析轮询", module = "招聘需求", channels = {"JD_PARSE"}, businessTypes = {"JD_PARSE"}, idleBackoffMaxMillis = 60000)
    public void poll() {
        for (int slot = 0; slot < concurrency; slot++) {
            dispatcher.dispatchPolling(
                    JD_PARSE_TASK_TYPE,
                    taskExecutor,
                    // 在执行线程开始时再认领一份，避免慢模型让后续任务空耗租约。
                    () -> taskBatchRunner.run(JD_PARSE_TASK_TYPE, 1, manager::process));
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void available(AsyncTaskAvailable event) {
        if (JD_PARSE_TASK_TYPE.equals(event.getTaskType())) {
            dispatcher.wakeUp();
            poll();
        }
    }
}
