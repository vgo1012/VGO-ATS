package com.ezaigc.hr.common.infrastructure.task;

import java.time.Instant;
import java.util.List;
import lombok.Value;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 异步任务模块能力端口。
 */
public interface AsyncTaskPort {

    TaskSnapshot enqueue(EnqueueTask command);

    TaskSnapshot get(String taskId);

    /**
     * 获取操作人幂等。
     */
    TaskSnapshot getByIdempotency(String actorUserId, String idempotencyKey);

    /**
     * 按完整业务关联当前锁读原任务，不以本次操作人代替原请求人定位；无记录返回 null，
     * 多条记录拒绝歧义。调用方必须先完成当前业务授权；用于入队去重时，
     * 必须在同一事务持有业务资源锁，串行化查询与创建。
     */
    TaskSnapshot getByResourceIdempotency(
            String taskType, String resourceType, String resourceId, String idempotencyKey);

    List<TaskSnapshot> claim(String taskType, int limit);

    /**
     * 判断当前领取批次是否应停止。任务被取消、租约过期或已被下一次领取取代时均返回 true。
     */
    boolean shouldStopProcessing(TaskSnapshot task);

    /**
     * 为仍由当前尝试持有的运行任务续租；失租或取消时返回 false。
     * 加入调用方事务，成功后任务行锁保留到事务结束，可保护同一短事务内的业务写入。
     */
    boolean renewLease(TaskSnapshot task);

    /**
     * 更新进度。
     */
    TaskSnapshot updateProgress(TaskSnapshot task, int progressPercent);

    TaskSnapshot succeed(TaskSnapshot task, String resultResourceId);

    TaskSnapshot fail(
            TaskSnapshot task, String errorCode, String safeErrorDetail, boolean retryable);

    /**
     * 同事务记录失败尝试并安排延迟重试。等待期间保持可取消的排队状态；
     * 次数耗尽或取消已胜出时不再排队。重试时间必须在未来 24 小时内。
     */
    TaskSnapshot failAndRetryAt(
            TaskSnapshot task, String errorCode, String safeErrorDetail, Instant retryAt);

    /** 处理器异常退出时，同事务结束共享任务、尝试并执行已注册的业务终态回调。 */
    TaskSnapshot failUnhandled(
            TaskSnapshot task, String errorCode, String safeErrorDetail, boolean retryable);

    TaskSnapshot cancelled(TaskSnapshot task);

    TaskSnapshot cancel(String taskId, String reason);

    /**
     * 重试失败。
     */
    TaskSnapshot retryFailed(String taskId, String reason);

    @Value
    @Accessors(fluent = true)
    class EnqueueTask {

        String taskId;
        String taskType;
        String resourceType;
        String resourceId;
        String requestedByUserId;
        String ownerUserId;
        String ownerDepartmentId;
        String viewPermission;
        String managePermission;
        String idempotencyKey;
        String requestFingerprint;
        int maxAttempts;
        Instant now;
    }

    @Value
    @Accessors(fluent = true)
    @AllArgsConstructor
    class TaskSnapshot {

        String id;
        String taskType;
        String resourceType;
        String resourceId;
        String requestedByUserId;
        String ownerUserId;
        String ownerDepartmentId;
        String viewPermission;
        String managePermission;
        String status;
        int attemptCount;
        int maxAttempts;
        int progressPercent;
        String errorCode;
        String safeErrorDetail;
        boolean retryable;
        String resultResourceId;
        long version;
        Instant createTime;
        Instant startedAt;
        Instant finishedAt;
        Instant updateTime;
        /** 原始请求关联 ID；历史任务可能为空，不能从执行线程推测。 */
        String traceId;

        /** 兼容没有持久追踪字段的快照生产者，明确表示原请求关联信息未知。 */
        public TaskSnapshot(String id, String taskType, String resourceType, String resourceId,
                String requestedByUserId, String ownerUserId, String ownerDepartmentId,
                String viewPermission, String managePermission, String status, int attemptCount,
                int maxAttempts, int progressPercent, String errorCode, String safeErrorDetail,
                boolean retryable, String resultResourceId, long version, Instant createTime,
                Instant startedAt, Instant finishedAt, Instant updateTime) {
            this(id, taskType, resourceType, resourceId, requestedByUserId, ownerUserId,
                    ownerDepartmentId, viewPermission, managePermission, status, attemptCount,
                    maxAttempts, progressPercent, errorCode, safeErrorDetail, retryable,
                    resultResourceId, version, createTime, startedAt, finishedAt, updateTime, null);
        }
    }
}
