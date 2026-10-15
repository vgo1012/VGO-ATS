package com.ezaigc.hr.common.infrastructure.task;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.logging.TraceIds;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.TaskScheduler;

/**
 * 统一隔离持久化异步任务的单项故障，避免一项异常中断整个已认领批次。
 */
@RequiredArgsConstructor
public class AsyncTaskBatchRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncTaskBatchRunner.class);
    /**
     * 失败编码。
     */
    private static final String FAILURE_CODE = "TASK_EXECUTION_FAILED";
    /**
     * 可安全展示的失败详情。
     */
    private static final String SAFE_FAILURE_DETAIL = "任务处理发生未预期错误，请稍后重试";
    /** 心跳间隔必须短于异步任务允许配置的最小租约。 */
    private static final Duration LEASE_HEARTBEAT_INTERVAL = Duration.ofMinutes(1);

    /**
     * 任务列表。
     */
    private final AsyncTaskPort tasks;
    /** 复用 Spring 管理的调度线程池，不为单个任务创建线程。 */
    private final TaskScheduler taskScheduler;

    /**
     * 执行当前任务。
     */
    public int run(String taskType, int limit, Consumer<TaskSnapshot> processor) {
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(processor, "processor");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<TaskSnapshot> claimed =
                Objects.requireNonNull(tasks.claim(taskType, limit), "claim result");
        for (TaskSnapshot task : claimed) {
            processOne(taskType, Objects.requireNonNull(task, "claimed task"), processor);
        }
        return claimed.size();
    }

    private void processOne(
            String taskType, TaskSnapshot task, Consumer<TaskSnapshot> processor) {
        try (TaskLogScope ignored = new TaskLogScope(taskType, task)) {
            processWithContext(taskType, task, processor);
        }
    }

    private void processWithContext(
            String taskType, TaskSnapshot task, Consumer<TaskSnapshot> processor) {
        ScheduledFuture<?> heartbeat = scheduleHeartbeat(taskType, task);
        try {
            processor.accept(task);
        } catch (RuntimeException processingFailure) {
            LOGGER.error(
                    "async-task-processing-failed taskType={}, taskId={}, errorType={}",
                    taskType,
                    task.id(),
                    processingFailure.getClass().getName(),
                    SafeLogException.from(processingFailure));
            try {
                // 单项异常必须先收敛为可重试失败，再继续处理同一批次的后续任务。
                tasks.failUnhandled(task, FAILURE_CODE, SAFE_FAILURE_DETAIL, true);
            } catch (RuntimeException finalizationFailure) {
                // 数据库不可用时无法强行制造终态；保留安全元数据，等待租约恢复机制接管。
                LOGGER.error(
                        "async-task-finalization-failed taskType={}, taskId={}, errorType={}",
                        taskType,
                        task.id(),
                        finalizationFailure.getClass().getName(),
                        SafeLogException.from(finalizationFailure));
            }
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
        }
    }

    private ScheduledFuture<?> scheduleHeartbeat(String taskType, TaskSnapshot task) {
        try {
            return taskScheduler.scheduleAtFixedRate(
                    () -> renewLease(taskType, task),
                    Instant.now().plus(LEASE_HEARTBEAT_INTERVAL),
                    LEASE_HEARTBEAT_INTERVAL);
        } catch (RuntimeException schedulingFailure) {
            LOGGER.error(
                    "async-task-heartbeat-scheduling-failed taskType={}, taskId={}, errorType={}",
                    taskType,
                    task.id(),
                    schedulingFailure.getClass().getName(),
                    SafeLogException.from(schedulingFailure));
            return null;
        }
    }

    private void renewLease(String taskType, TaskSnapshot task) {
        try (TaskLogScope ignored = new TaskLogScope(taskType, task)) {
            renewLeaseWithContext(taskType, task);
        }
    }

    private void renewLeaseWithContext(String taskType, TaskSnapshot task) {
        try {
            if (!tasks.renewLease(task)) {
                LOGGER.warn(
                        "async-task-lease-lost taskType={}, taskId={}, attemptNo={}",
                        taskType,
                        task.id(),
                        task.attemptCount());
            }
        } catch (RuntimeException renewalFailure) {
            LOGGER.error(
                    "async-task-heartbeat-failed taskType={}, taskId={}, attemptNo={}, errorType={}",
                    taskType,
                    task.id(),
                    task.attemptCount(),
                    renewalFailure.getClass().getName(),
                    SafeLogException.from(renewalFailure));
        }
    }

    /** 任务和续租各自绑定身份；恢复整个原上下文，避免任务新增字段污染复用线程。 */
    private static final class TaskLogScope implements AutoCloseable {
        private final Map<String, String> previous = MDC.getCopyOfContextMap();

        private TaskLogScope(String taskType, TaskSnapshot task) {
            if (TraceIds.isSafe(task.traceId())) {
                MDC.put("traceId", task.traceId());
            } else {
                // 旧任务没有原始关联信息；清除无关线程的 trace，仍保留 taskId 供定位。
                MDC.remove("traceId");
            }
            MDC.put("taskType", taskType);
            MDC.put("taskId", task.id());
            MDC.put("attemptNo", Integer.toString(task.attemptCount()));
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
