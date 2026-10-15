package com.ezaigc.hr.reporting.outbox.application.support;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublicationSource;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.reporting.outbox.constant.OutboxConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;

/** 每次只领取一个发布任务，避免前项等待 Broker 确认时后项租约提前耗尽。 */
@ManagedScheduledWorker
public class OutboxPublicationWorker {
    @Resource private ScheduledTaskMonitor taskMonitor;
    @Resource private PlatformExecutorProperties executorProperties;
    private CoalescingTaskDispatcher dispatcher;
    private int concurrency;

    @PostConstruct
    void initializeConcurrency() {
        concurrency = executorProperties.getOutbox().getCoreSize();
        dispatcher = new CoalescingTaskDispatcher(concurrency);
        dispatcher.observe(taskMonitor);
    }
    private final AtomicBoolean stopping = new AtomicBoolean();
    @Resource private ObjectProvider<OutboxPublicationSource> sourceProvider;
    @Resource(name = ExecutorNames.OUTBOX) private TaskExecutor executor;
    private final AtomicInteger nextSource = new AtomicInteger();

    @Scheduled(initialDelayString = "${hr.messaging.initial-delay:3000}", fixedDelayString = "${hr.messaging.poll-delay:1000}")
    @ScheduledTaskInfo(name = "Outbox 消息发布", module = "可靠消息", channels = {"OUTBOX_PUBLISH"}, businessTypes = {"OUTBOX_PUBLISH"}, idleBackoffMaxMillis = 60000)
    public void poll() {
        if (stopping.get()) return;
        for (int slot = 0; slot < concurrency; slot++) {
            dispatcher.dispatchPolling(OutboxConstants.PUBLISH_TASK_TYPE, executor, this::drain);
        }
    }

    @PreDestroy
    public void stop() {
        stopping.set(true);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void available(AsyncTaskAvailable event) {
        if (OutboxConstants.PUBLISH_TASK_TYPE.equals(event.getTaskType())) {
            dispatcher.wakeUp();
            poll();
        }
    }

    private int drain() {
        int processed = 0;
        for (int batch = 0; batch < 50 && !stopping.get() && !Thread.currentThread().isInterrupted(); batch++) {
            int count = publishNext();
            if (count == 0) break;
            processed += count;
        }
        return processed;
    }

    private int publishNext() {
        List<OutboxPublicationSource> sources = sourceProvider.orderedStream().toList();
        if (sources.isEmpty()) return 0;
        int start = Math.floorMod(nextSource.getAndIncrement(), sources.size());
        for (int offset = 0; offset < sources.size(); offset++) {
            if (stopping.get() || Thread.currentThread().isInterrupted()) return 0;
            int index = (start + offset) % sources.size();
            if (sources.get(index).publishOne() > 0) return 1;
        }
        return 0;
    }
}
