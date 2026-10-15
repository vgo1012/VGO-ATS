package com.ezaigc.hr.reporting.outbox.application.manager;

import static com.ezaigc.hr.reporting.outbox.constant.OutboxConstants.PUBLISH_TASK_TYPE;
import static com.ezaigc.hr.talent.api.ResumeFactProjectionSource.TASK_TYPE;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxDeliveryPort;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import com.ezaigc.hr.reporting.outbox.application.assembler.SearchTaskRecoveryAssembler;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.domain.entity.SearchTaskRecoveryEntity;
import com.ezaigc.hr.reporting.outbox.domain.response.SearchTaskRecoveryResponse;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import com.ezaigc.hr.reporting.outbox.service.SearchTaskRecoveryService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.reporting.service.AuditService;
import com.ezaigc.hr.talent.api.enums.ResumeSearchResourceType;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 管理员创建有独立尝试预算的恢复任务；原失败任务、尝试及信封保持不变。 */
@Component
public class SearchTaskRecoveryApplicationManager {
  private static final String TASK_RESOURCE_TYPE = "ASYNC_TASK";
  private static final int MAX_ATTEMPTS = 5;
  @Resource private AuthorizationService authorization;
  @Resource private AsyncTaskService taskService;
  @Resource private AsyncTaskPort tasks;
  @Resource private OutboxTaskPort outbox;
  @Resource private OutboxDeliveryPort deliveries;
  @Resource private OutboxEventService events;
  @Resource private SearchTaskRecoveryService recoveries;
  @Resource private AuditService audits;
  @Resource private SearchTaskRecoveryAssembler assembler;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private ObjectMapper json;

  /** 先锁原任务，RC 保证等待竞争者提交后能读取其关联记录；整个事务不调用 Broker 或索引。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public SearchTaskRecoveryResponse create(String originalTaskId, String reason, String idempotencyKey) {
    String actorId = requireAdmin();
    validateId(originalTaskId);
    String normalizedReason = CommonUtils.stripToNull(reason);
    if (normalizedReason == null || normalizedReason.length() > 500) {
      throw PlatformExceptions.badRequest("SEARCH_RECOVERY_REASON_INVALID", "恢复原因必须为 1～500 字符");
    }
    String key = IdempotencyKey.fromHttpHeader(idempotencyKey);
    if (taskService.lockTask(originalTaskId) == null) {
      throw PlatformExceptions.notFound("TASK_NOT_FOUND", "原任务不存在");
    }
    var original = tasks.get(originalTaskId);
    var replay = tasks.getByIdempotency(actorId, key);
    if (replay != null) {
      var record = recoveries.getById(replay.id());
      if (record == null || !originalTaskId.equals(record.getOriginalTaskId())
          || !normalizedReason.equals(record.getReason()) || !actorId.equals(record.getRequestedByUserId())) {
        throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一 Idempotency-Key 不能用于不同恢复请求");
      }
      return assembler.toResponse(record, replay);
    }
    if (!AsyncTaskStatus.FAILED.matches(original.status()) || original.maxAttempts() != MAX_ATTEMPTS
        || original.attemptCount() != MAX_ATTEMPTS) {
      throw PlatformExceptions.conflict("SEARCH_TASK_NOT_EXHAUSTED", "仅达到 5 次上限的失败搜索任务可以新建恢复任务");
    }
    if (recoveries.findByOriginalTaskId(originalTaskId) != null) {
      throw PlatformExceptions.conflict("SEARCH_TASK_ALREADY_RECOVERED", "原任务已创建恢复任务，请查询并处理该恢复任务");
    }
    boolean publication = PUBLISH_TASK_TYPE.equals(original.taskType());
    if (!publication && !TASK_TYPE.equals(original.taskType())) {
      throw PlatformExceptions.badRequest("SEARCH_TASK_TYPE_UNSUPPORTED", "仅支持搜索投递或索引任务的恢复");
    }
    OutboxEventEntity sourceEvent = publication ? publicationEvent(original.id()) : events.findByWorkTaskId(original.id());
    if (sourceEvent == null || !TASK_TYPE.equals(sourceEvent.getWorkTaskType())
        || !ResumeSearchResourceType.CANDIDATE.matches(sourceEvent.getAggregateType())) throw invalidBinding();
    var work = original.id().equals(sourceEvent.getWorkTaskId())
        ? original : tasks.get(sourceEvent.getWorkTaskId());
    if (work == null || (publication && (!TASK_RESOURCE_TYPE.equals(original.resourceType())
        || !work.id().equals(original.resourceId()))) || (!publication && !work.id().equals(original.id()))) throw invalidBinding();
    try {
      if (!sourceEvent.getId().equals(deliveries.forTask(work).getEventId())) throw invalidBinding();
    } catch (IllegalArgumentException invalid) {
      throw invalidBinding();
    }
    String taskId = ids.newId();
    var command = new EnqueueTask(taskId, original.taskType(), original.resourceType(), original.resourceId(),
        actorId, original.ownerUserId(), original.ownerDepartmentId(), original.viewPermission(), original.managePermission(),
        key, Sha256Utils.hashUtf8("SEARCH_RECOVERY:" + originalTaskId + ':' + normalizedReason), MAX_ATTEMPTS, clock.instant());
    var created = publication ? tasks.enqueue(command) : outbox.enqueue(command);
    if (!taskId.equals(created.id())) {
      throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "恢复请求的幂等键已被其他操作使用");
    }
    var event = publication ? sourceEvent : events.findByWorkTaskId(created.id());
    if (event == null) throw new IllegalStateException("recovery outbox event is missing");
    var record = new SearchTaskRecoveryEntity();
    record.setId(created.id());
    record.setOriginalTaskId(originalTaskId);
    record.setOutboxEventId(event.getId());
    record.setReason(normalizedReason);
    record.setRequestedByUserId(actorId);
    record.setCreateId(actorId);
    record.setUpdateId(actorId);
    record.setCreateTime(command.now());
    record.setUpdateTime(command.now());
    if (!recoveries.save(record)) throw new IllegalStateException("search recovery was not persisted");
    saveAudit(record, original);
    return assembler.toResponse(record, created);
  }

  /** 以原失败任务查询唯一直接恢复记录；调用者必须仍为管理员。 */
  @Transactional(readOnly = true)
  public SearchTaskRecoveryResponse get(String originalTaskId) {
    requireAdmin();
    validateId(originalTaskId);
    var record = recoveries.findByOriginalTaskId(originalTaskId);
    if (record == null) throw PlatformExceptions.notFound("SEARCH_RECOVERY_NOT_FOUND", "该任务尚无恢复记录");
    return assembler.toResponse(record, tasks.get(record.getId()));
  }

  private OutboxEventEntity publicationEvent(String taskId) {
    var event = events.getById(taskId);
    return event == null ? events.findForRecoveryTask(taskId) : event;
  }

  private String requireAdmin() {
    var actor = authorization.currentActor();
    if (!actor.isSuperAdmin()) throw PlatformExceptions.forbidden("SEARCH_RECOVERY_ADMIN_REQUIRED", "仅超级管理员可以创建或查看搜索恢复记录");
    return actor.getUserId();
  }

  private static void validateId(String id) {
    if (id == null || id.isBlank() || id.length() > 26) {
      throw PlatformExceptions.badRequest("TASK_ID_INVALID", "任务 ID 无效");
    }
  }

  private static PlatformException invalidBinding() {
    return PlatformExceptions.conflict("SEARCH_TASK_BINDING_INVALID", "原搜索任务与持久事件绑定不完整，请先核对记录");
  }

  private void saveAudit(SearchTaskRecoveryEntity record, TaskSnapshot original) {
    var audit = new AuditEventEntity();
    audit.setId(ids.newId());
    audit.setEventType("SEARCH_TASK_RECOVERED");
    audit.setActorUserId(record.getRequestedByUserId());
    audit.setTargetType(TASK_RESOURCE_TYPE);
    audit.setTargetId(original.id());
    audit.setOwnerUserId(original.ownerUserId());
    audit.setDepartmentId(original.ownerDepartmentId());
    audit.setReason(record.getReason());
    audit.setBeforeSnapshot(json.writeValueAsString(Map.of("taskId", original.id(), "status", original.status(), "attemptCount", original.attemptCount())));
    audit.setAfterSnapshot(json.writeValueAsString(Map.of("recoveryTaskId", record.getId(), "outboxEventId", record.getOutboxEventId(), "maxAttempts", MAX_ATTEMPTS)));
    audit.setSourceModule("REPORTING_SEARCH_RECOVERY");
    audit.setSourceEventId(record.getId());
    audit.setOccurredAt(record.getCreateTime());
    audit.setCapturedAt(record.getCreateTime());
    audit.setCreateId(record.getRequestedByUserId());
    audit.setUpdateId(record.getRequestedByUserId());
    audit.setCreateTime(record.getCreateTime());
    audit.setUpdateTime(record.getCreateTime());
    if (!audits.save(audit)) throw new IllegalStateException("search recovery audit was not persisted");
  }
}
