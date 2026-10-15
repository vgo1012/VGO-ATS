package com.ezaigc.hr.reporting.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskEntity;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskLeaseRow;
import com.ezaigc.hr.reporting.mapper.AsyncTaskMapper;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 异步任务业务服务实现。
 */
@Service
public class AsyncTaskServiceImpl extends ServiceImpl<AsyncTaskMapper, AsyncTaskEntity>
        implements AsyncTaskService {

    private static final int MAXIMUM_QUERY_ROWS = 200;

    @Override
    public PersistentTaskBacklogSnapshot findReadyBacklog(
            List<String> taskTypes, Instant now) {
        if (taskTypes == null || taskTypes.isEmpty()) return PersistentTaskBacklogSnapshot.empty();
        return baseMapper.findReadyBacklog(taskTypes.stream().distinct().toList(), now);
    }

    @Override
    public String lockTask(String id) { return baseMapper.lockTask(id); }

    /**
     * 新增忽略。
     */
    @Override
    public int insertIgnore(AsyncTaskEntity task) {
        return baseMapper.insertIgnore(task);
    }

    /**
     * 查询任务。
     */
    @Override
    public TaskSnapshot findTask(String id) {
        return snapshot(getById(id));
    }

    @Override
    public TaskSnapshot findTaskForUpdate(String id) {
        return snapshot(findEntityByIdForUpdate(id));
    }

    @Override
    public List<TaskSnapshot> findTasksByResourceIdempotencyForUpdate(
            String taskType, String resourceType, String resourceId, String key) {
        return list(Wrappers.<AsyncTaskEntity>lambdaQuery()
                .eq(AsyncTaskEntity::getTaskType, taskType)
                .eq(AsyncTaskEntity::getResourceType, resourceType)
                .eq(AsyncTaskEntity::getResourceId, resourceId)
                .eq(AsyncTaskEntity::getIdempotencyKey, key)
                .last("LIMIT 2 FOR UPDATE"))
                .stream().map(AsyncTaskServiceImpl::snapshot).toList();
    }

    /**
     * 查询任务操作人幂等。
     */
    @Override
    public TaskSnapshot findTaskByIdempotency(String actorUserId, String key) {
        return snapshot(
                getOne(
                        Wrappers.<AsyncTaskEntity>lambdaQuery()
                                .eq(AsyncTaskEntity::getRequestedByUserId, actorUserId)
                                .eq(AsyncTaskEntity::getIdempotencyKey, key)
                                .last("LIMIT 1")));
    }

    @Override
    public TaskSnapshot findTaskByIdempotencyForUpdate(String actorUserId, String key) {
        return snapshot(
                getOne(
                        Wrappers.<AsyncTaskEntity>lambdaQuery()
                                .eq(AsyncTaskEntity::getRequestedByUserId, actorUserId)
                                .eq(AsyncTaskEntity::getIdempotencyKey, key)
                                .last("FOR UPDATE")));
    }

    @Override
    public AsyncTaskEntity findEntityByIdForUpdate(String id) {
        return getOne(
                Wrappers.<AsyncTaskEntity>lambdaQuery()
                        .eq(AsyncTaskEntity::getId, id)
                        .last("FOR UPDATE"));
    }

    @Override
    public List<TaskSnapshot> findRecentTasks(
            String status,
            String taskType,
            String ownerUserId,
            Instant cursorTime,
            String cursorId,
            int limit) {
        return findRecentTasks(status, taskType, ownerUserId, null, null, cursorTime, cursorId, limit);
    }

    @Override
    public List<TaskSnapshot> findRecentTasks(
            String status, String taskType, String ownerUserId, String resourceType, String resourceId,
            Instant cursorTime, String cursorId, int limit) {
        var query =
                Wrappers.<AsyncTaskEntity>lambdaQuery()
                        .eq(status != null, AsyncTaskEntity::getStatus, status)
                        .eq(taskType != null, AsyncTaskEntity::getTaskType, taskType)
                        .eq(ownerUserId != null, AsyncTaskEntity::getOwnerUserId, ownerUserId)
                        .eq(resourceType != null, AsyncTaskEntity::getResourceType, resourceType)
                        .eq(resourceId != null, AsyncTaskEntity::getResourceId, resourceId);
        if (cursorTime != null) {
            query.and(
                    cursor ->
                            cursor
                                    .lt(AsyncTaskEntity::getCreateTime, cursorTime)
                                    .or(
                                            sameTime ->
                                                    sameTime
                                                            .eq(AsyncTaskEntity::getCreateTime, cursorTime)
                                                            .lt(AsyncTaskEntity::getId, cursorId)));
        }
        return baseMapper
                .selectPage(
                        new Page<>(1, boundedLimit(limit), false),
                        query
                                .orderByDesc(AsyncTaskEntity::getCreateTime)
                                .orderByDesc(AsyncTaskEntity::getId))
                .getRecords()
                .stream()
                .map(AsyncTaskServiceImpl::snapshot)
                .toList();
    }

    @Override
    public List<String> findQueuedIdsForUpdate(String taskType, Instant now, int limit) {
        return baseMapper.findQueuedIdsForUpdate(taskType, now, limit);
    }

    @Override
    public int scheduleRetry(TaskSnapshot failed, Instant retryAt, Instant now, String actorId) {
        return baseMapper.scheduleRetry(failed.id(), failed.attemptCount(), failed.version(), retryAt, now, actorId);
    }

    @Override
    public List<AsyncTaskLeaseRow> findExpiredLeasesForUpdate(
            String taskType, Instant now, int limit) {
        return baseMapper.findExpiredLeasesForUpdate(taskType, now, limit);
    }

    @Override
    public int markRunning(String id, Instant now, Instant leaseUntil, String actorId) {
        return baseMapper.markRunning(id, now, leaseUntil, actorId);
    }

    @Override
    public boolean hasActiveLease(String id, int attemptNo, Instant now) {
        return baseMapper.countActiveLease(id, attemptNo, now) == 1;
    }

    @Override
    public int renewLease(
            String id, int attemptNo, Instant now, Instant leaseUntil, String actorId) {
        return baseMapper.renewLease(id, attemptNo, now, leaseUntil, actorId);
    }

    /**
     * 更新进度。
     */
    @Override
    public int updateProgress(
            String id,
            int attemptNo,
            int progress,
            Instant now,
            Instant leaseUntil,
            String actorId) {
        return baseMapper.updateProgress(id, attemptNo, progress, now, leaseUntil, actorId);
    }

    /**
     * 标记成功。
     */
    @Override
    public int markSucceeded(
            String id, int attemptNo, String resultId, Instant now, String actorId) {
        return baseMapper.markSucceeded(id, attemptNo, resultId, now, actorId);
    }

    /**
     * 标记失败。
     */
    @Override
    public int markFailed(
            String id,
            int attemptNo,
            String code,
            String detail,
            boolean retryable,
            Instant now,
            String actorId) {
        return baseMapper.update(
                null,
                Wrappers.<AsyncTaskEntity>lambdaUpdate()
                        .set(AsyncTaskEntity::getStatus, AsyncTaskStatus.FAILED.getCode())
                        .set(AsyncTaskEntity::getErrorCode, code)
                        .set(AsyncTaskEntity::getSafeErrorDetail, detail)
                        .set(AsyncTaskEntity::isRetryable, retryable)
                        .set(AsyncTaskEntity::getLeaseUntil, null)
                        .set(AsyncTaskEntity::getNextRetryAt, null)
                        .set(AsyncTaskEntity::getFinishedAt, now)
                        .setIncrBy(AsyncTaskEntity::getVersion, 1)
                        .set(AsyncTaskEntity::getUpdateId, actorId)
                        .set(AsyncTaskEntity::getUpdateTime, now)
                        .eq(AsyncTaskEntity::getId, id)
                        // 取消请求已经胜出时，迟到失败不得把 CANCELLING 覆盖成 FAILED。
                        .eq(AsyncTaskEntity::getStatus, AsyncTaskStatus.RUNNING.getCode())
                        // 尝试号与租约共同构成栅栏，阻止已失效执行者覆盖新一轮任务。
                        .eq(AsyncTaskEntity::getAttemptCount, attemptNo)
                        .gt(AsyncTaskEntity::getLeaseUntil, now));
    }

    /**
     * 标记已取消。
     */
    @Override
    public int markCancelled(String id, int attemptNo, Instant now, String actorId) {
        return baseMapper.markCancelled(id, attemptNo, now, actorId);
    }

    @Override
    public int requeueExpiredLease(String id, int attemptNo, Instant now, String actorId) {
        return baseMapper.requeueExpiredLease(id, attemptNo, now, actorId);
    }

    @Override
    public int failExpiredLease(
            String id,
            int attemptNo,
            String code,
            String detail,
            Instant now,
            String actorId) {
        return baseMapper.failExpiredLease(id, attemptNo, code, detail, now, actorId);
    }

    @Override
    public int cancelExpiredLease(String id, int attemptNo, Instant now, String actorId) {
        return baseMapper.cancelExpiredLease(id, attemptNo, now, actorId);
    }

    @Override
    public int requestCancel(String id, String reason, Instant now, String actorId) {
        return baseMapper.requestCancel(id, reason, now, actorId);
    }

    /**
     * 重试失败。
     */
    @Override
    public int retryFailed(String id, Instant now, String actorId) {
        return baseMapper.retryFailed(id, now, actorId);
    }

    private static TaskSnapshot snapshot(AsyncTaskEntity task) {
        if (task == null) {
            return null;
        }
        return new TaskSnapshot(
                task.getId(),
                task.getTaskType(),
                task.getResourceType(),
                task.getResourceId(),
                task.getRequestedByUserId(),
                task.getOwnerUserId(),
                task.getOwnerDepartmentId(),
                task.getViewPermission(),
                task.getManagePermission(),
                task.getStatus(),
                task.getAttemptCount(),
                task.getMaxAttempts(),
                task.getProgressPercent(),
                task.getErrorCode(),
                task.getSafeErrorDetail(),
                task.isRetryable(),
                task.getResultResourceId(),
                task.getVersion(),
                task.getCreateTime(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getUpdateTime(),
                task.getTraceId());
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, MAXIMUM_QUERY_ROWS));
    }
}
