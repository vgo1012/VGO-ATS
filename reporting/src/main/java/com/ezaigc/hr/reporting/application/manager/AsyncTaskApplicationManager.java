package com.ezaigc.hr.reporting.application.manager;

import static com.ezaigc.hr.reporting.outbox.constant.OutboxConstants.EVENT_TYPE;
import static com.ezaigc.hr.reporting.outbox.constant.OutboxConstants.EVENT_VERSION;
import static com.ezaigc.hr.reporting.outbox.constant.OutboxConstants.PUBLISH_TASK_TYPE;

import com.ezaigc.hr.common.infrastructure.logging.TraceIds;
import org.slf4j.MDC;

import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalHandler;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskRetryGuard;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskErrorCode;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.VisibleCursorPageCollector;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.AsyncTaskAssembler;
import com.ezaigc.hr.reporting.config.AsyncTaskProperties;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskEntity;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskLeaseRow;
import com.ezaigc.hr.reporting.domain.response.AsyncTaskResponse;
import com.ezaigc.hr.reporting.service.AsyncTaskAttemptService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportAccess;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 异步任务应用服务，负责用例编排、权限校验和事务边界。
 */
@Component
public class AsyncTaskApplicationManager implements AsyncTaskPort, OutboxTaskPort {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AsyncTaskApplicationManager.class);
    private static final int MAXIMUM_CLAIM_LIMIT = 50;
    private static final int LEASE_RECOVERY_BATCH_SIZE = 50;
    private static final String LEASE_EXPIRED_CODE = "TASK_LEASE_EXPIRED";
    private static final String RETIRED_EXPORT_TASK_TYPE = "REPORT_EXPORT";
    private static final String LEASE_EXPIRED_DETAIL = "任务处理租约已过期，系统将按重试策略恢复";
    private static final String LEASE_EXHAUSTED_DETAIL = "任务处理租约已过期且已达到最大尝试次数，请重新发起任务";

    @Resource
    private Clock clock;
    @Resource
    private AuthorizationService authorization;
    @Resource
    private CurrentActorProvider currentActorProvider;
    @Resource
    private AsyncTaskService service;
    @Resource
    private AsyncTaskAttemptService attemptService;
    @Resource
    private PlatformIdGenerator ids;
    @Resource
    private CursorCodec cursorCodec;
    @Resource
    private AsyncTaskAssembler assembler;
    @Resource
    private AsyncTaskProperties properties;
    @Resource
    private ObjectProvider<AsyncTaskTerminalHandler> terminalHandlers;

    @Resource
    private ObjectProvider<AsyncTaskRetryGuard> retryGuards;
    @Resource private ApplicationEventPublisher events;
    @Resource private OutboxEventService outboxEvents;

    /**
     * 列出响应列表。
     */
    @Transactional(readOnly = true)
    public List<AsyncTaskResponse> listResponses(
            String status, String taskType, String ownerUserId, int limit) {
        return list(status, taskType, ownerUserId, limit).stream().map(assembler::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AsyncTaskResponse> listResponses(
            String status, String taskType, String ownerUserId, String resourceType, String resourceId, int limit) {
        return list(status, taskType, ownerUserId, resourceType, resourceId, limit).stream().map(assembler::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AsyncTaskResponse getResponse(String id) {
        TaskSnapshot task = require(id);
        requireTaskRole(task);
        authorization.requireAccess(task.viewPermission(), target(task));
        return assembler.toResponse(task);
    }

    @Transactional
    public AsyncTaskResponse cancelResponse(String id, String reason) {
        TaskSnapshot task = require(id);
        requireTaskRole(task);
        authorization.requireAccess(task.managePermission(), target(task));
        return assembler.toResponse(cancel(id, reason));
    }

    /**
     * 重试失败响应。
     */
    @Transactional
    public AsyncTaskResponse retryFailedResponse(String id, String reason) {
        TaskSnapshot task = require(id);
        requireTaskRole(task);
        authorization.requireAccess(task.managePermission(), target(task));
        return assembler.toResponse(retryFailed(id, reason));
    }

    @Transactional(readOnly = true)
    public List<TaskSnapshot> list(String status, String taskType, String ownerUserId, int limit) {
        return list(status, taskType, ownerUserId, null, null, limit);
    }

    @Transactional(readOnly = true)
    public List<TaskSnapshot> list(String status, String taskType, String ownerUserId,
            String resourceType, String resourceId, int limit) {
        authorization.requirePermission("reporting.read-own");
        var actor = authorization.currentActor();
        String normalizedStatus = CommonUtils.stripToNull(status);
        String normalizedTaskType = CommonUtils.stripToNull(taskType);
        String normalizedOwnerUserId = CommonUtils.stripToNull(ownerUserId);
        String normalizedResourceType = CommonUtils.stripToNull(resourceType);
        String normalizedResourceId = CommonUtils.stripToNull(resourceId);
        int boundedLimit = PageLimits.resolve(limit);
        String scope =
                CursorScopes.scope(
                        "reporting.async-task.list",
                        actor.getUserId(),
                        actor.hasCompanyWideScope(),
                        actor.getManagedUserIds(),
                        Set.of(),
                        normalizedStatus,
                        normalizedTaskType,
                        normalizedOwnerUserId, normalizedResourceType, normalizedResourceId);
        return VisibleCursorPageCollector.collect(
                        null,
                        boundedLimit,
                        200,
                        cursorCodec,
                        scope,
                        cursor ->
                                service.findRecentTasks(
                                        normalizedStatus,
                                        normalizedTaskType,
                                        normalizedOwnerUserId,
                                        normalizedResourceType,
                                        normalizedResourceId,
                                        cursor == null ? null : cursor.getSortTime(),
                                        cursor == null ? null : cursor.getId(),
                                        200),
                        tasks -> visibleTaskIds(actor, tasks),
                        TaskSnapshot::createTime,
                        TaskSnapshot::id)
                .getItems();
    }

    private Set<String> visibleTaskIds(
            CurrentActorSnapshot actor, List<TaskSnapshot> tasks) {
        Map<String, List<AuthorizationTarget>> targetsByPermission = new LinkedHashMap<>();
        for (TaskSnapshot task : tasks) {
            if (MonthlyReportConstants.isMonthlyTask(task.taskType()) && !MonthlyReportAccess.allowed(actor)) continue;
            targetsByPermission
                    .computeIfAbsent(task.viewPermission(), ignored -> new ArrayList<>())
                    .add(target(task));
        }
        Set<String> visible = new LinkedHashSet<>();
        targetsByPermission.forEach(
                (permission, targets) ->
                        visible.addAll(authorization.accessibleObjectIds(actor, permission, targets)));
        return Set.copyOf(visible);
    }

    @Override
    @Transactional
    public TaskSnapshot enqueue(EnqueueTask command) {
        Objects.requireNonNull(command, "task is required");
        if (PUBLISH_TASK_TYPE.equals(command.taskType())
                || RETIRED_EXPORT_TASK_TYPE.equals(command.taskType())) {
            return enqueueTask(command);
        }
        TaskSnapshot work = enqueueTask(command);
        TaskSnapshot publication = enqueueTask(new EnqueueTask(
                ids.newId(),
                PUBLISH_TASK_TYPE,
                "ASYNC_TASK",
                work.id(),
                work.requestedByUserId(),
                work.ownerUserId(),
                work.ownerDepartmentId(),
                work.viewPermission(),
                work.managePermission(),
                "outbox:" + work.id(),
                Sha256Utils.hashUtf8(EVENT_TYPE + ":" + work.id()),
                5,
                command.now()));
        OutboxEventEntity event = outboxEvent(work, publication, command.now());
        OutboxEventEntity saved = outboxEvents.saveIfAbsent(event);
        verifyOutboxBinding(work, saved);
        return work;
    }

    private TaskSnapshot enqueueTask(EnqueueTask command) {
        String key = IdempotencyKey.fromHttpHeader(command.idempotencyKey());
        validate(command);
        TaskSnapshot existing = service.findTaskByIdempotency(command.requestedByUserId(), key);
        if (existing != null) {
            return verifyExisting(command, existing);
        }
        AsyncTaskEntity task = task(command, key);
        if (service.insertIgnore(task) == 0) {
            // INSERT IGNORE 已等待唯一键赢家提交；锁定读必须跳过本事务先前建立的 RR 旧快照。
            existing = service.findTaskByIdempotencyForUpdate(command.requestedByUserId(), key);
            if (existing == null) {
                throw PlatformExceptions.conflict("TASK_IDEMPOTENCY_RACE", "任务正在创建，请稍后重试");
            }
            return verifyExisting(
                    command, existing, service.findEntityByIdForUpdate(existing.id()));
        }
        events.publishEvent(new AsyncTaskAvailable(command.taskType(), command.taskId(), null));
        return require(command.taskId());
    }

    private static OutboxEventEntity outboxEvent(
            TaskSnapshot work, TaskSnapshot publication, Instant now) {
        var event = new OutboxEventEntity();
        event.setId(publication.id());
        event.setWorkTaskId(work.id());
        event.setWorkTaskType(work.taskType());
        event.setEventType(EVENT_TYPE);
        event.setEventVersion(EVENT_VERSION);
        event.setAggregateType(work.resourceType());
        event.setAggregateId(work.resourceId());
        event.setOccurredAt(work.createTime());
        event.setTraceId(TraceIds.acceptOrCreate(MDC.get("traceId")));
        event.setActorUserId(work.requestedByUserId());
        event.setOwnerUserId(work.ownerUserId());
        event.setOwnerDepartmentId(work.ownerDepartmentId());
        event.setCreateId(work.requestedByUserId());
        event.setUpdateId(work.requestedByUserId());
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    private static void verifyOutboxBinding(TaskSnapshot work, OutboxEventEntity saved) {
        if (saved == null
                || !work.id().equals(saved.getWorkTaskId())
                || !work.taskType().equals(saved.getWorkTaskType())
                || !work.resourceId().equals(saved.getAggregateId())
                || !work.resourceType().equals(saved.getAggregateType())
                || !EVENT_TYPE.equals(saved.getEventType())
                || EVENT_VERSION != saved.getEventVersion()) {
            throw new IllegalStateException("outbox event binding changed");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TaskSnapshot get(String taskId) {
        return require(taskId);
    }

    /**
     * 获取操作人幂等。
     */
    @Override
    @Transactional(readOnly = true)
    public TaskSnapshot getByIdempotency(String actorUserId, String key) {
        return service.findTaskByIdempotency(actorUserId, IdempotencyKey.fromHttpHeader(key));
    }

    @Override
    @Transactional
    public TaskSnapshot getByResourceIdempotency(
            String taskType, String resourceType, String resourceId, String key) {
        List<TaskSnapshot> matches = service.findTasksByResourceIdempotencyForUpdate(
                required(taskType, 80), required(resourceType, 60), required(resourceId, 26),
                IdempotencyKey.fromHttpHeader(key));
        if (matches.size() > 1) {
            throw PlatformExceptions.conflict("TASK_RESOURCE_IDEMPOTENCY_AMBIGUOUS",
                    "同一业务关联存在多个任务，请核对原任务记录");
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    @Override
    @Transactional
    public List<TaskSnapshot> claim(String taskType, int limit) {
        String normalizedTaskType = required(taskType, 80);
        if (limit < 1 || limit > MAXIMUM_CLAIM_LIMIT) {
            throw new IllegalArgumentException("claim limit must be 1..50");
        }
        Instant now = clock.instant();
        String actorId = actorId();
        recoverExpiredLeases(normalizedTaskType, now, actorId);
        Instant leaseUntil = properties.leaseDeadline(now);
        List<TaskSnapshot> claimed = new ArrayList<>();
        for (String id : service.findQueuedIdsForUpdate(normalizedTaskType, now, limit)) {
            if (service.markRunning(id, now, leaseUntil, actorId) == 1) {
                requireSingleWrite(
                        attemptService.insertAttempt(ids.newId(), id, now),
                        "创建任务尝试记录",
                        id);
                claimed.add(require(id));
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean shouldStopProcessing(TaskSnapshot task) {
        validateClaimedTask(task);
        return !service.hasActiveLease(task.id(), task.attemptCount(), clock.instant());
    }

    @Override
    @Transactional
    public boolean renewLease(TaskSnapshot task) {
        validateClaimedTask(task);
        Instant now = clock.instant();
        return service.renewLease(
                        task.id(),
                        task.attemptCount(),
                        now,
                        properties.leaseDeadline(now),
                        actorId())
                == 1;
    }

    /**
     * 更新进度。
     */
    @Override
    @Transactional
    public TaskSnapshot updateProgress(TaskSnapshot task, int progressPercent) {
        validateClaimedTask(task);
        if (progressPercent < 1 || progressPercent > 99) {
            throw new IllegalArgumentException("progress must be 1..99");
        }
        Instant now = clock.instant();
        if (service.updateProgress(
                        task.id(),
                        task.attemptCount(),
                        progressPercent,
                        now,
                        properties.leaseDeadline(now),
                        actorId())
                == 0) {
            throw stateConflict();
        }
        return require(task.id());
    }

    @Override
    @Transactional
    public TaskSnapshot succeed(TaskSnapshot task, String resultResourceId) {
        validateClaimedTask(task);
        String normalizedResultResourceId = required(resultResourceId, 26);
        Instant now = clock.instant();
        if (service.markSucceeded(
                        task.id(), task.attemptCount(), normalizedResultResourceId, now, actorId())
                == 0) {
            throw stateConflict();
        }
        requireSingleWrite(
                attemptService.markSucceeded(task.id(), task.attemptCount(), now),
                "完成任务尝试记录",
                task.id());
        events.publishEvent(new AsyncTaskTerminalEvent(
                task.taskType(), task.id(), normalizedResultResourceId,
                AsyncTaskStatus.Codes.SUCCEEDED));
        return require(task.id());
    }

    @Override
    @Transactional
    public TaskSnapshot fail(
            TaskSnapshot task,
            String errorCode,
            String safeErrorDetail,
            boolean retryable) {
        validateClaimedTask(task);
        Instant now = clock.instant();
        String code = required(errorCode, 100);
        String detail = required(safeErrorDetail, 500);
        // 错误类型可恢复不等于任务仍可重试；耗尽尝试次数后必须暴露明确的最终失败语义。
        boolean retryAvailable = retryable && task.attemptCount() < task.maxAttempts();
        if (service.markFailed(
                        task.id(),
                        task.attemptCount(),
                        code,
                        detail,
                        retryAvailable,
                        now,
                        actorId())
                == 0) {
            return completeCancellation(task, now, false);
        }
        requireSingleWrite(
                attemptService.markFailed(task.id(), task.attemptCount(), code, detail, now),
                "结束失败任务尝试记录",
                task.id());
        TaskSnapshot outcome = require(task.id());
        events.publishEvent(new AsyncTaskTerminalEvent(
                outcome.taskType(), outcome.id(), outcome.resourceId(), outcome.status()));
        return outcome;
    }

    @Override
    @Transactional
    public TaskSnapshot failAndRetryAt(TaskSnapshot task, String errorCode, String safeErrorDetail, Instant retryAt) {
        Instant now = clock.instant();
        if (retryAt == null || !retryAt.isAfter(now) || retryAt.isAfter(now.plusSeconds(86400))) {
            throw new IllegalArgumentException("retry time must be in the next 24 hours");
        }
        // 本方法事务覆盖失败记录与重新排队；内部调用不依赖额外代理传播。
        TaskSnapshot failed = fail(task, errorCode, safeErrorDetail, true);
        if (!AsyncTaskStatus.FAILED.matches(failed.status()) || !failed.retryable()) {
            return failed;
        }
        requireSingleWrite(service.scheduleRetry(failed, retryAt, now, actorId()), "安排任务延迟重试", task.id());
        return require(task.id());
    }

    @Override
    @Transactional
    public TaskSnapshot failUnhandled(TaskSnapshot task, String errorCode, String safeErrorDetail,
            boolean retryable) {
        TaskSnapshot outcome = fail(task, errorCode, safeErrorDetail, retryable);
        completeTerminalWithoutWorker(outcome.taskType(), outcome.id(), outcome.resourceId(),
                AsyncTaskStatus.fromCode(outcome.status()), outcome.errorCode(),
                outcome.finishedAt(), actorId());
        return outcome;
    }

    @Override
    @Transactional
    public TaskSnapshot cancelled(TaskSnapshot task) {
        validateClaimedTask(task);
        return completeCancellation(task, clock.instant(), true);
    }

    @Override
    @Transactional
    public TaskSnapshot cancel(String taskId, String reason) {
        TaskSnapshot task = service.findTaskForUpdate(taskId);
        if (task == null) {
            throw PlatformExceptions.notFound("TASK_NOT_FOUND", "任务不存在");
        }
        if (AsyncTaskStatus.CANCELLING.matches(task.status())
                || AsyncTaskStatus.fromCode(task.status()).isTerminal()) {
            return task;
        }
        Instant now = clock.instant();
        String actorId = actorId();
        if (service.requestCancel(taskId, required(reason, 500), now, actorId) == 0) {
            throw stateConflict();
        }
        TaskSnapshot outcome = require(taskId);
        // 以锁内更新后的状态判断；并发领取后只能请求取消，不能抢先终结仍在工作的业务记录。
        if (AsyncTaskStatus.CANCELLED.matches(outcome.status())) {
            completeTerminalWithoutWorker(outcome.taskType(), outcome.id(), outcome.resourceId(),
                    AsyncTaskStatus.CANCELLED, null, now, actorId);
        }
        return outcome;
    }

    /**
     * 重试失败。
     */
    @Override
    @Transactional
    public TaskSnapshot retryFailed(String taskId, String reason) {
        required(reason, 500);
        TaskSnapshot task = require(taskId);
        if (RETIRED_EXPORT_TASK_TYPE.equals(task.taskType())) {
            throw PlatformExceptions.conflict("TASK_TYPE_RETIRED", "候选人导出已停用，历史任务不能重试");
        }
        retryGuards.orderedStream().forEach(guard -> guard.requireRetryAllowed(task));
        if (service.retryFailed(taskId, clock.instant(), actorId()) == 0) {
            throw PlatformExceptions.conflict("TASK_NOT_RETRYABLE", "任务不可重试或已达到最大尝试次数");
        }
        events.publishEvent(new AsyncTaskAvailable(task.taskType(), taskId, null));
        return require(taskId);
    }

    private TaskSnapshot verifyExisting(EnqueueTask command, TaskSnapshot existing) {
        return verifyExisting(command, existing, service.getById(existing.id()));
    }

    private TaskSnapshot verifyExisting(
            EnqueueTask command, TaskSnapshot existing, AsyncTaskEntity persisted) {
        if (persisted == null
                || !command.taskType().equals(existing.taskType())
                || !command.requestFingerprint().equals(persisted.getRequestFingerprint())
                || !command.resourceId().equals(existing.resourceId())) {
            throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同任务请求");
        }
        return existing;
    }

    private TaskSnapshot require(String id) {
        TaskSnapshot task = service.findTask(id);
        if (task == null) {
            throw new PlatformException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在", false);
        }
        return task;
    }

    private void requireTaskRole(TaskSnapshot task) {
        if (MonthlyReportConstants.isMonthlyTask(task.taskType()) && !MonthlyReportAccess.allowed(authorization.currentActor()))
            throw PlatformExceptions.forbidden("MONTHLY_REPORT_FORBIDDEN", "仅人力负责人和管理员可以访问月报任务");
    }

    /**
     * 完成取消。
     */
    private TaskSnapshot completeCancellation(
            TaskSnapshot claimedTask, Instant now, boolean allowRunningSource) {
        TaskSnapshot task = require(claimedTask.id());
        if (task.attemptCount() != claimedTask.attemptCount()) {
            throw stateConflict();
        }
        AsyncTaskStatus status = AsyncTaskStatus.fromCode(task.status());
        if (status == AsyncTaskStatus.CANCELLED) {
            return task;
        }
        if (!status.canCompleteCancellation(allowRunningSource)) {
            throw stateConflict();
        }
        if (service.markCancelled(
                        task.id(), claimedTask.attemptCount(), now, actorId())
                == 0) {
            TaskSnapshot latest = require(task.id());
            if (latest.attemptCount() == claimedTask.attemptCount()
                    && AsyncTaskStatus.CANCELLED.matches(latest.status())) {
                return latest;
            }
            throw stateConflict();
        }
        requireSingleWrite(
                attemptService.markCancelled(task.id(), claimedTask.attemptCount(), now),
                "取消任务尝试记录",
                task.id());
        return require(task.id());
    }

    /**
     * 恢复同类型的过期租约。任务行由数据库锁定，任务状态与尝试审计在同一事务内收敛。
     */
    private void recoverExpiredLeases(String taskType, Instant now, String actorId) {
        for (AsyncTaskLeaseRow task :
                service.findExpiredLeasesForUpdate(
                        taskType, now, LEASE_RECOVERY_BATCH_SIZE)) {
            int attemptNo = task.getAttemptCount();
            AsyncTaskStatus status = AsyncTaskStatus.fromCode(task.getStatus());
            if (status == AsyncTaskStatus.CANCELLING) {
                requireSingleWrite(
                        attemptService.markCancelled(task.getId(), attemptNo, now),
                        "恢复取消任务尝试记录",
                        task.getId());
                requireSingleWrite(
                        service.cancelExpiredLease(task.getId(), attemptNo, now, actorId),
                        "恢复取消任务",
                        task.getId());
                completeTerminalWithoutWorker(task.getTaskType(), task.getId(), task.getResourceId(),
                        AsyncTaskStatus.CANCELLED, null, now, actorId);
                logLeaseRecovery(task, "CANCELLED");
                continue;
            }
            if (status != AsyncTaskStatus.RUNNING) {
                throw new IllegalStateException("租约恢复查询返回非运行态任务: " + task.getId());
            }
            String detail = attemptNo < task.getMaxAttempts() ? LEASE_EXPIRED_DETAIL : LEASE_EXHAUSTED_DETAIL;
            requireSingleWrite(
                    attemptService.markFailed(
                            task.getId(),
                            attemptNo,
                            LEASE_EXPIRED_CODE,
                            detail,
                            now),
                    "记录过期任务尝试",
                    task.getId());
            if (attemptNo < task.getMaxAttempts()) {
                requireSingleWrite(
                        service.requeueExpiredLease(task.getId(), attemptNo, now, actorId),
                        "重新排队过期任务",
                        task.getId());
                logLeaseRecovery(task, "QUEUED");
            } else {
                requireSingleWrite(
                        service.failExpiredLease(
                                task.getId(),
                                attemptNo,
                                LEASE_EXPIRED_CODE,
                                detail,
                                now,
                                actorId),
                        "终结耗尽重试次数的过期任务",
                        task.getId());
                completeTerminalWithoutWorker(task.getTaskType(), task.getId(), task.getResourceId(),
                        AsyncTaskStatus.FAILED, LEASE_EXPIRED_CODE, now, actorId);
                logLeaseRecovery(task, "FAILED");
            }
        }
    }

    private void completeTerminalWithoutWorker(String taskType, String taskId, String resourceId,
            AsyncTaskStatus status, String errorCode, Instant now, String actorId) {
        // 任务行仍由当前事务锁定；业务模块只能通过窄接口参与同一次终态提交。
        terminalHandlers.orderedStream().forEach(handler -> handler.terminalWithoutWorker(
                taskType, taskId, resourceId, status, errorCode, now, actorId));
    }

    private static void logLeaseRecovery(AsyncTaskLeaseRow task, String targetStatus) {
        LOGGER.warn(
                "async-task-lease-recovered taskType={}, taskId={}, attemptNo={}, targetStatus={}",
                task.getTaskType(),
                task.getId(),
                task.getAttemptCount(),
                targetStatus);
    }

    private static void validateClaimedTask(TaskSnapshot task) {
        if (task == null || task.id() == null || task.id().isBlank() || task.id().length() > 26) {
            throw new IllegalArgumentException("claimed task identity is invalid");
        }
        if (task.attemptCount() < 1 || task.attemptCount() > 20) {
            throw new IllegalArgumentException("claimed task attempt number is invalid");
        }
        if (task.maxAttempts() < task.attemptCount() || task.maxAttempts() > 20) {
            throw new IllegalArgumentException("claimed task maximum attempts is invalid");
        }
    }

    private static void requireSingleWrite(int affectedRows, String operation, String taskId) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    operation + "未影响唯一记录, taskId=" + taskId + ", affectedRows=" + affectedRows);
        }
    }

    private String actorId() {
        return currentActorProvider
                .currentActor()
                .map(CurrentActor::getUserId)
                .filter(value -> !value.isBlank())
                .orElse(PlatformActorConstants.SYSTEM_ACTOR_ID);
    }

    private static AsyncTaskEntity task(EnqueueTask command, String key) {
        AsyncTaskEntity task = new AsyncTaskEntity();
        task.setId(command.taskId());
        task.setTraceId(TraceIds.acceptOrCreate(MDC.get("traceId")));
        task.setTaskType(command.taskType());
        task.setResourceType(command.resourceType());
        task.setResourceId(command.resourceId());
        task.setRequestedByUserId(command.requestedByUserId());
        task.setOwnerUserId(command.ownerUserId());
        task.setOwnerDepartmentId(command.ownerDepartmentId());
        task.setViewPermission(command.viewPermission());
        task.setManagePermission(command.managePermission());
        task.setStatus(AsyncTaskStatus.QUEUED.getCode());
        task.setMaxAttempts(command.maxAttempts());
        task.setIdempotencyKey(key);
        task.setRequestFingerprint(command.requestFingerprint());
        task.setCreateId(command.requestedByUserId());
        task.setCreateTime(command.now());
        task.setUpdateId(command.requestedByUserId());
        task.setUpdateTime(command.now());
        return task;
    }

    private static AuthorizationTarget target(TaskSnapshot task) {
        return new AuthorizationTarget(
                "ASYNC_TASK",
                task.id(),
                task.ownerUserId(),
                task.ownerDepartmentId(),
                Set.of(task.requestedByUserId()));
    }

    private static void validate(EnqueueTask command) {
        required(command.taskId(), 26);
        required(command.taskType(), 80);
        required(command.resourceType(), 60);
        required(command.resourceId(), 26);
        required(command.requestedByUserId(), 26);
        required(command.ownerUserId(), 26);
        required(command.ownerDepartmentId(), 26);
        required(command.viewPermission(), 100);
        required(command.managePermission(), 100);
        required(command.requestFingerprint(), 64);
        if (command.now() == null) {
            throw new IllegalArgumentException("task timestamp is required");
        }
        if (command.maxAttempts() < 1 || command.maxAttempts() > 20) {
            throw new IllegalArgumentException("maxAttempts must be 1..20");
        }
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("required task field invalid");
        }
        return value;
    }

    private static PlatformException stateConflict() {
        return PlatformExceptions.conflict(AsyncTaskErrorCode.TASK_STATE_CONFLICT.getCode(), "任务状态已变化，请刷新后重试");
    }
}
