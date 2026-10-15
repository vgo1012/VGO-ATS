package com.ezaigc.hr.recruiting.interview.application.manager;

import static com.ezaigc.hr.common.web.request.RequiredText.required;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.WecomCollaborationDirectoryService;
import com.ezaigc.hr.recruiting.interview.api.InterviewExternalCalendarPort;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarReconciliationDTO;
import com.ezaigc.hr.common.web.version.ResourceVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarAttempt;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewExternalCalendarResponse;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarAttemptStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarMode;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarOperation;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarReconciliationAction;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarRemoteStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewExternalCalendarLegacyCreateError;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarAttemptPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarProjectionPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 本地面试到企业微信日程/会议的持久化投影编排。 */
@Service
public class InterviewExternalCalendarApplicationManager {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final int MAX_AUTOMATIC_ATTEMPTS = 8;
    private static final Set<Integer> REMINDER_MINUTES = Set.of(0, 5, 15, 60, 1440);

    @Resource private InterviewExternalCalendarProjectionPersistenceService projections;
    @Resource private InterviewExternalCalendarAttemptPersistenceService attempts;
    @Resource private WecomCollaborationDirectoryService directory;
    @Resource private InterviewExternalCalendarPort port;
    @Resource private PlatformIdGenerator ids;
    @Resource private Clock clock;
    @Resource private ObjectMapper json;
    @Resource private TransactionTemplate transactions;

    @Transactional
    public void configure(Interview interview, List<String> localParticipantIds,
            InterviewExternalCalendarDTO requested, String actorId) {
        InterviewExternalCalendarProjection existing = projections.findByInterviewIdForUpdate(interview.getId());
        if (requested == null && existing == null) return;
        InterviewExternalCalendarDTO effective = requested == null ? from(existing) : requested;
        String mode = normalizeMode(effective.getMode());
        ProjectionValues values = values(interview, localParticipantIds, effective, actorId, mode);
        Instant now = clock.instant();
        if (existing == null) {
            InterviewExternalCalendarProjection created = new InterviewExternalCalendarProjection();
            created.setId(ids.newId());
            created.setInterviewId(interview.getId());
            created.setProvider("WECOM");
            created.setOperationRevision(1);
            created.setActiveType(null);
            created.setAttemptCount(0);
            created.setVersion(0);
            created.setCreateId(actorId);
            created.setCreateTime(now);
            apply(created, values);
            created.setStatus(InterviewExternalCalendarMode.localOnly(mode)
                    ? InterviewExternalCalendarStatus.CANCELLED.name()
                    : InterviewExternalCalendarStatus.PENDING.name());
            created.setNextAttemptAt(now);
            created.setUpdateId(actorId);
            created.setUpdateTime(now);
            if (!projections.save(created)) throw new IllegalStateException("企业微信面试投影写入失败");
            return;
        }
        boolean preserveCreate = preserveCreate(existing);
        apply(existing, values);
        existing.setOperationRevision(existing.getOperationRevision() + 1);
        existing.setAttemptCount(0);
        if (!preserveCreate) {
            existing.setCreateIntentId(null);
            existing.setStatus(InterviewExternalCalendarMode.localOnly(mode) && existing.getActiveType() == null
                    ? InterviewExternalCalendarStatus.CANCELLED.name()
                    : InterviewExternalCalendarStatus.PENDING.name());
            existing.setNextAttemptAt(now);
            existing.setLastErrorCode(null);
            existing.setLastErrorDetail(null);
        }
        existing.setUpdateId(actorId);
        existing.setUpdateTime(now);
        if (!projections.updateById(existing)) throw PlatformExceptions.conflict(
                "WECOM_PROJECTION_CHANGED", "企业微信日程状态已变化，请刷新后重试");
    }

    @Transactional
    public void cancel(String interviewId, String actorId) {
        InterviewExternalCalendarProjection value = projections.findByInterviewIdForUpdate(interviewId);
        if (value == null) return;
        Instant now = clock.instant();
        boolean preserveCreate = preserveCreate(value);
        value.setDesiredType(InterviewExternalCalendarMode.LOCAL_ONLY.name());
        value.setOperationRevision(value.getOperationRevision() + 1);
        value.setAttemptCount(0);
        if (!preserveCreate) {
            value.setCreateIntentId(null);
            value.setStatus(value.getActiveType() == null
                    ? InterviewExternalCalendarStatus.CANCELLED.name()
                    : InterviewExternalCalendarStatus.PENDING.name());
            value.setNextAttemptAt(now);
            value.setLastErrorCode(null);
            value.setLastErrorDetail(null);
        }
        value.setUpdateId(actorId);
        value.setUpdateTime(now);
        if (!projections.updateById(value)) throw PlatformExceptions.conflict(
                "WECOM_PROJECTION_CHANGED", "企业微信日程状态已变化，请刷新后重试");
    }

    @Transactional(readOnly = true)
    public InterviewExternalCalendarResponse response(String interviewId) {
        InterviewExternalCalendarProjection value = projections.findByInterviewId(interviewId);
        if (value == null) return null;
        return new InterviewExternalCalendarResponse(
                value.getDesiredType(), value.getActiveType(), value.getStatus(), value.getTitle(),
                value.getDescription(), value.getAdminUserId(), readIds(value.getInviteeUserIdsJson()),
                value.getReminderSeconds() / 60, value.isWaitingRoomEnabled(),
                value.isAllowEnterBeforeHost(), value.isEnterMuteEnabled(), value.getExternalObjectId(),
                value.getMeetingCode(), value.getMeetingLink(), value.getRemoteStatus(),
                value.getLastErrorCode(), value.getLastErrorDetail(), value.getAttemptCount(),
                value.getLastSyncedAt(), ResourceVersion.format(value.getVersion()), value.getCreateIntentId());
    }

    @Transactional
    public void retry(String interviewId, String actorId) {
        InterviewExternalCalendarProjection value = projections.findByInterviewIdForUpdate(interviewId);
        if (value != null && (InterviewExternalCalendarStatus.UNKNOWN.name().equals(value.getStatus())
                || legacyAmbiguousCreate(value))) throw reconciliationRequired();
        if (value != null && value.getCreateIntentId() != null
                && !InterviewExternalCalendarAttemptStatus.FAILED.name().equals(requireIntent(value).getStatus())) {
            throw reconciliationRequired();
        }
        if (value == null || !InterviewExternalCalendarStatus.FAILED.name().equals(value.getStatus())) {
            throw PlatformExceptions.conflict("WECOM_PROJECTION_NOT_RETRYABLE", "当前企业微信日程状态无需重试");
        }
        Instant now = clock.instant();
        value.setStatus(InterviewExternalCalendarStatus.PENDING.name());
        value.setCreateIntentId(null);
        value.setAttemptCount(0);
        value.setNextAttemptAt(now);
        value.setLeaseToken(null);
        value.setLeaseUntil(null);
        value.setLastErrorCode(null);
        value.setLastErrorDetail(null);
        value.setUpdateId(actorId);
        value.setUpdateTime(now);
        if (!projections.updateById(value)) throw PlatformExceptions.conflict(
                "WECOM_PROJECTION_CHANGED", "企业微信日程状态已变化，请刷新后重试");
    }

    public List<InterviewExternalCalendarProjection> due(int limit) {
        return projections.findDue(clock.instant(), limit);
    }

    /** 授权由面试用例完成；只接受对当前未知意图的带版本、理由核对。 */
    @Transactional
    public void reconcile(String interviewId, InterviewExternalCalendarReconciliationDTO command, String actorId) {
        InterviewExternalCalendarProjection value = projections.findByInterviewIdForUpdate(interviewId);
        if (value == null) throw PlatformExceptions.notFound("WECOM_PROJECTION_NOT_FOUND", "企业微信日程投影不存在");
        if (command == null || value.getVersion() != command.getVersion()
                || !Objects.equals(value.getCreateIntentId(), command.getIntentId())) throw changed();
        if (!InterviewExternalCalendarStatus.UNKNOWN.name().equals(value.getStatus())
                || value.getCreateIntentId() == null) throw reconciliationRequired();
        InterviewExternalCalendarAttempt intent = requireIntent(value);
        String reason = required(command.getReason(), "reason", 500);
        String action = required(command.getAction(), "action", 32).toUpperCase(Locale.ROOT);
        boolean adopt = InterviewExternalCalendarReconciliationAction.ADOPT_EXISTING.matches(action);
        String externalId = null;
        if (adopt) {
            externalId = required(command.getExternalObjectId(), "externalObjectId", 200);
            if (InterviewExternalCalendarAttemptStatus.SUCCEEDED.name().equals(intent.getStatus())
                    && !Objects.equals(intent.getExternalObjectId(), externalId)) throw changed();
            if (value.getActiveType() != null) throw changed();
            value.setActiveType(intent.getExternalType());
            value.setExternalObjectId(externalId);
            value.setRemoteStatus(InterviewExternalCalendarRemoteStatus.ACTIVE.name());
            // 依据最新本地 desired 更新或取消同一对象，不再 CREATE。
            value.setStatus(InterviewExternalCalendarStatus.PENDING.name());
        } else if (InterviewExternalCalendarReconciliationAction.CONFIRM_NOT_CREATED.matches(action)) {
            if (!Boolean.TRUE.equals(command.getOriginalRequestTerminated())) {
                throw PlatformExceptions.unprocessable("WECOM_REQUEST_TERMINATION_REQUIRED", "必须明确确认原创建请求已终止，租约过期不能证明未创建");
            }
            if (InterviewExternalCalendarAttemptStatus.SUCCEEDED.name().equals(intent.getStatus())) throw changed();
            value.setStatus(InterviewExternalCalendarMode.localOnly(value.getDesiredType())
                    ? InterviewExternalCalendarStatus.CANCELLED.name() : InterviewExternalCalendarStatus.PENDING.name());
        } else throw PlatformExceptions.unprocessable("WECOM_RECONCILIATION_ACTION_INVALID", "仅允许采纳现有对象或确认未创建");
        InterviewExternalCalendarAttempt audit = attempt(value, InterviewExternalCalendarOperation.CREATE, ids.newId(),
                adopt ? InterviewExternalCalendarAttemptStatus.SUCCEEDED.name() : InterviewExternalCalendarAttemptStatus.FAILED.name(), clock.instant());
        audit.setOperationRevision(intent.getOperationRevision());
        audit.setExternalType(intent.getExternalType());
        audit.setExternalObjectId(externalId);
        audit.setErrorCode(adopt ? "WECOM_MANUAL_CREATE_ADOPT" : "WECOM_MANUAL_CREATE_NOT_CREATED");
        audit.setErrorDetail(reason);
        audit.setRemark("原创建意图 " + intent.getId());
        audit.setCreateId(actorId);
        audit.setUpdateId(actorId);
        requireSave(audit);
        value.setCreateIntentId(null);
        clearLease(value);
        value.setLastErrorCode(null);
        value.setLastErrorDetail(null);
        value.setNextAttemptAt(clock.instant());
        value.setUpdateId(actorId);
        value.setUpdateTime(clock.instant());
        requireUpdate(value);
    }

    public void process(String projectionId) {
        String leaseToken = ids.newId();
        TransactionTemplate localTransactions = independentTransactions();
        InterviewExternalCalendarProjection claimed = localTransactions.execute(status -> claim(projectionId, leaseToken));
        if (claimed == null) return;
        InterviewExternalCalendarOperation operation = operation(claimed);
        if (operation == InterviewExternalCalendarOperation.NONE) {
            localTransactions.executeWithoutResult(status -> finishWithoutRemote(claimed, leaseToken));
            return;
        }
        InterviewExternalCalendarPort.Result result;
        try {
            InterviewExternalCalendarPort.Command command = command(claimed,
                    operation == InterviewExternalCalendarOperation.CANCEL ? claimed.getActiveType() : claimed.getDesiredType());
            result = switch (operation) {
                case CREATE -> port.create(command);
                case UPDATE -> port.update(command);
                case CANCEL -> port.cancel(command);
                case NONE -> throw new IllegalStateException("unreachable operation");
            };
            if (result == null) result = operation == InterviewExternalCalendarOperation.CREATE
                    ? InterviewExternalCalendarPort.Result.unknownCreate("INVALID_ADAPTER_RESULT", "企业微信创建结果未确认")
                    : InterviewExternalCalendarPort.Result.failed(true, "INVALID_ADAPTER_RESULT", "企业微信适配器未返回有效结果");
        } catch (RuntimeException failure) {
            result = operation == InterviewExternalCalendarOperation.CREATE
                    ? InterviewExternalCalendarPort.Result.unknownCreate("WECOM_CALL_EXCEPTION", "企业微信创建结果未确认，请人工核对")
                    : InterviewExternalCalendarPort.Result.failed(true, "WECOM_CALL_EXCEPTION", "企业微信调用异常，本地面试不受影响");
        }
        if (operation == InterviewExternalCalendarOperation.CREATE && result.succeeded()
                && (result.externalObjectId() == null || result.externalObjectId().isBlank())) {
            result = InterviewExternalCalendarPort.Result.unknownCreate("WECOM_INVALID_RESPONSE", "企业微信成功响应缺少创建对象 ID");
        }
        InterviewExternalCalendarPort.Result outcome = result;
        if (operation == InterviewExternalCalendarOperation.CREATE) {
            // 回执先独立提交；收尾事务失败不会遗失已知 ID。任何阶段失败都不能重新 CREATE。
            localTransactions.executeWithoutResult(status -> recordCreateOutcome(claimed, outcome));
            localTransactions.executeWithoutResult(status -> completeCreate(claimed, outcome));
        } else {
            localTransactions.executeWithoutResult(status -> complete(claimed, leaseToken, operation, outcome));
        }
    }

    private InterviewExternalCalendarProjection claim(String id, String leaseToken) {
        InterviewExternalCalendarProjection value = projections.findByIdForUpdate(id);
        if (value == null) return null;
        Instant now = clock.instant();
        if (InterviewExternalCalendarStatus.PROCESSING.name().equals(value.getStatus())
                && value.getLeaseUntil() != null && value.getLeaseUntil().isAfter(now)) return null;
        if (value.getCreateIntentId() != null) {
            InterviewExternalCalendarAttempt intent = requireIntent(value);
            if (InterviewExternalCalendarAttemptStatus.SUCCEEDED.name().equals(intent.getStatus())) {
                applyCreated(value, intent, null);
                requireUpdate(value);
                return null;
            }
            if (!InterviewExternalCalendarAttemptStatus.FAILED.name().equals(intent.getStatus())) {
                quarantine(value, intent);
                return null;
            }
            if (InterviewExternalCalendarStatus.PROCESSING.name().equals(value.getStatus())
                    || InterviewExternalCalendarStatus.UNKNOWN.name().equals(value.getStatus())) {
                applyRejected(value, intent, null);
                requireUpdate(value);
                return null;
            }
        } else if (legacyAmbiguousCreate(value) || legacyInFlightCreate(value)) {
            InterviewExternalCalendarAttempt intent = legacyIntent(value);
            quarantine(value, intent);
            return null;
        }
        if (value == null || (!InterviewExternalCalendarStatus.PENDING.name().equals(value.getStatus())
                && !InterviewExternalCalendarStatus.FAILED.name().equals(value.getStatus())
                && !(InterviewExternalCalendarStatus.PROCESSING.name().equals(value.getStatus())
                        && value.getLeaseUntil() != null && !value.getLeaseUntil().isAfter(clock.instant())))) return null;
        if (value.getNextAttemptAt() != null && value.getNextAttemptAt().isAfter(now)) return null;
        if (operation(value) == InterviewExternalCalendarOperation.CREATE) {
            InterviewExternalCalendarAttempt intent = attempt(value, InterviewExternalCalendarOperation.CREATE,
                    leaseToken, InterviewExternalCalendarAttemptStatus.RUNNING.name(), now);
            requireSave(intent);
            if (projections.claimCreate(value.getId(), value.getVersion(), leaseToken, now.plus(LEASE), now,
                    value.getCreateIntentId()) != 1) throw changed();
        } else if (projections.claim(value.getId(), value.getVersion(), leaseToken, now.plus(LEASE), now) != 1) {
            return null;
        }
        InterviewExternalCalendarProjection claimed = projections.findByIdForUpdate(value.getId());
        if (claimed == null || !leaseToken.equals(claimed.getLeaseToken())) throw changed();
        return claimed;
    }

    private void finishWithoutRemote(InterviewExternalCalendarProjection claimed, String leaseToken) {
        InterviewExternalCalendarProjection current = projections.findByIdForUpdate(claimed.getId());
        if (!owned(current, claimed, leaseToken)) return;
        current.setStatus(InterviewExternalCalendarStatus.CANCELLED.name());
        clearLease(current);
        current.setLastSyncedAt(clock.instant());
        current.setUpdateTime(clock.instant());
        requireUpdate(current);
    }

    private void complete(InterviewExternalCalendarProjection claimed, String leaseToken,
            InterviewExternalCalendarOperation operation, InterviewExternalCalendarPort.Result result) {
        Instant now = clock.instant();
        InterviewExternalCalendarProjection current = projections.findByIdForUpdate(claimed.getId());
        if (!owned(current, claimed, leaseToken)) {
            if (current != null && result.succeeded() && operation == InterviewExternalCalendarOperation.CANCEL
                    && Objects.equals(current.getActiveType(), claimed.getActiveType())
                    && Objects.equals(current.getExternalObjectId(), claimed.getExternalObjectId())) {
                current.setActiveType(null);
                current.setExternalObjectId(null);
                current.setMeetingCode(null);
                current.setMeetingLink(null);
                current.setStatus(InterviewExternalCalendarMode.localOnly(current.getDesiredType())
                        ? InterviewExternalCalendarStatus.CANCELLED.name() : InterviewExternalCalendarStatus.PENDING.name());
                current.setNextAttemptAt(now);
                clearLease(current);
                current.setUpdateTime(now);
                requireUpdate(current);
            }
            recordAttempt(claimed, operation, result, now);
            return;
        }
        current.setAttemptCount(current.getAttemptCount() + 1);
        clearLease(current);
        current.setUpdateTime(now);
        if (!result.succeeded()) {
            current.setStatus(InterviewExternalCalendarStatus.FAILED.name());
            current.setLastErrorCode(safe(result.errorCode(), 80));
            current.setLastErrorDetail(safe(result.errorDetail(), 500));
            boolean retry = result.retryable() && current.getAttemptCount() < MAX_AUTOMATIC_ATTEMPTS;
            current.setNextAttemptAt(retry ? now.plusSeconds(backoffSeconds(current.getAttemptCount()))
                    : now.plus(3650, ChronoUnit.DAYS));
        } else {
            current.setLastErrorCode(null);
            current.setLastErrorDetail(null);
            current.setLastSyncedAt(now);
            if (operation == InterviewExternalCalendarOperation.UPDATE) {
                current.setMeetingCode(result.meetingCode() == null
                        ? current.getMeetingCode() : result.meetingCode());
                current.setMeetingLink(result.meetingLink() == null
                        ? current.getMeetingLink() : result.meetingLink());
                current.setRemoteStatus(result.remoteStatus());
                current.setStatus(InterviewExternalCalendarStatus.SYNCED.name());
            } else {
                current.setActiveType(null);
                current.setExternalObjectId(null);
                current.setMeetingCode(null);
                current.setMeetingLink(null);
                current.setRemoteStatus(InterviewExternalCalendarStatus.CANCELLED.name());
                if (InterviewExternalCalendarMode.localOnly(current.getDesiredType())) {
                    current.setStatus(InterviewExternalCalendarStatus.CANCELLED.name());
                } else {
                    current.setStatus(InterviewExternalCalendarStatus.PENDING.name());
                    current.setNextAttemptAt(now);
                }
            }
        }
        requireUpdate(current);
        recordAttempt(claimed, operation, result, now);
    }

    private void recordAttempt(InterviewExternalCalendarProjection projection,
            InterviewExternalCalendarOperation operation,
            InterviewExternalCalendarPort.Result result, Instant now) {
        InterviewExternalCalendarAttempt attempt = new InterviewExternalCalendarAttempt();
        attempt.setId(ids.newId());
        attempt.setProjectionId(projection.getId());
        attempt.setOperationRevision(projection.getOperationRevision());
        attempt.setOperationType(operation.name());
        attempt.setExternalType(operation == InterviewExternalCalendarOperation.CANCEL
                ? projection.getActiveType() : projection.getDesiredType());
        attempt.setStatus(result.succeeded() ? InterviewExternalCalendarAttemptStatus.SUCCEEDED.name()
                : result.unknownCreate() ? InterviewExternalCalendarAttemptStatus.UNKNOWN.name()
                : InterviewExternalCalendarAttemptStatus.FAILED.name());
        attempt.setExternalObjectId(result.succeeded()
                ? Objects.requireNonNullElse(result.externalObjectId(), projection.getExternalObjectId()) : null);
        attempt.setErrorCode(safe(result.errorCode(), 80));
        attempt.setErrorDetail(safe(result.errorDetail(), 500));
        attempt.setOccurredAt(now);
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        if (operation == InterviewExternalCalendarOperation.CREATE && projection.getCreateIntentId() != null) {
            attempt.setRemark("原创建意图 " + projection.getCreateIntentId());
        }
        requireSave(attempt);
    }

    private TransactionTemplate independentTransactions() {
        TransactionTemplate result = new TransactionTemplate(Objects.requireNonNull(transactions.getTransactionManager()));
        result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return result;
    }

    private void recordCreateOutcome(InterviewExternalCalendarProjection claimed, InterviewExternalCalendarPort.Result result) {
        InterviewExternalCalendarProjection current = projections.findByIdForUpdate(claimed.getId());
        if (current == null || !Objects.equals(current.getCreateIntentId(), claimed.getCreateIntentId())) {
            // 人工核对后迟到的结果只能留痕，不能污染新意图或绑定其他对象。
            recordAttempt(claimed, InterviewExternalCalendarOperation.CREATE, result, clock.instant());
            return;
        }
        InterviewExternalCalendarAttempt intent = requireIntent(current);
        if (InterviewExternalCalendarAttemptStatus.SUCCEEDED.name().equals(intent.getStatus())) {
            if (result.succeeded() && Objects.equals(intent.getExternalObjectId(), result.externalObjectId())) return;
            throw changed();
        }
        if (!InterviewExternalCalendarAttemptStatus.RUNNING.name().equals(intent.getStatus())
                && !InterviewExternalCalendarAttemptStatus.UNKNOWN.name().equals(intent.getStatus())) throw changed();
        intent.setStatus(result.succeeded() ? InterviewExternalCalendarAttemptStatus.SUCCEEDED.name()
                : result.unknownCreate() ? InterviewExternalCalendarAttemptStatus.UNKNOWN.name()
                : InterviewExternalCalendarAttemptStatus.FAILED.name());
        intent.setExternalObjectId(result.externalObjectId());
        intent.setErrorCode(safe(result.errorCode(), 80));
        intent.setErrorDetail(safe(result.errorDetail(), 500));
        intent.setUpdateTime(clock.instant());
        if (!attempts.updateById(intent)) throw changed();
    }

    private void completeCreate(InterviewExternalCalendarProjection claimed, InterviewExternalCalendarPort.Result result) {
        InterviewExternalCalendarProjection current = projections.findByIdForUpdate(claimed.getId());
        if (current == null || !Objects.equals(current.getCreateIntentId(), claimed.getCreateIntentId())) return;
        InterviewExternalCalendarAttempt intent = requireIntent(current);
        if (InterviewExternalCalendarAttemptStatus.SUCCEEDED.name().equals(intent.getStatus())) applyCreated(current, intent, result);
        else if (InterviewExternalCalendarAttemptStatus.FAILED.name().equals(intent.getStatus())) applyRejected(current, intent, result);
        else {
            quarantine(current, intent);
            return;
        }
        requireUpdate(current);
    }

    private void applyCreated(InterviewExternalCalendarProjection value, InterviewExternalCalendarAttempt intent,
            InterviewExternalCalendarPort.Result result) {
        if (intent.getExternalObjectId() == null || intent.getExternalObjectId().isBlank()) throw changed();
        if (value.getActiveType() != null && (!Objects.equals(value.getActiveType(), intent.getExternalType())
                || !Objects.equals(value.getExternalObjectId(), intent.getExternalObjectId()))) throw changed();
        if (InterviewExternalCalendarStatus.PROCESSING.name().equals(value.getStatus())) value.setAttemptCount(value.getAttemptCount() + 1);
        value.setActiveType(intent.getExternalType());
        value.setExternalObjectId(intent.getExternalObjectId());
        value.setMeetingCode(result == null ? null : result.meetingCode());
        value.setMeetingLink(result == null ? null : result.meetingLink());
        value.setRemoteStatus(result == null ? InterviewExternalCalendarRemoteStatus.ACTIVE.name() : result.remoteStatus());
        value.setStatus(value.getOperationRevision() == intent.getOperationRevision()
                && Objects.equals(value.getDesiredType(), intent.getExternalType())
                ? InterviewExternalCalendarStatus.SYNCED.name() : InterviewExternalCalendarStatus.PENDING.name());
        value.setCreateIntentId(null);
        clearLease(value);
        value.setLastErrorCode(null);
        value.setLastErrorDetail(null);
        value.setLastSyncedAt(clock.instant());
        value.setNextAttemptAt(clock.instant());
        value.setUpdateTime(clock.instant());
    }

    private void applyRejected(InterviewExternalCalendarProjection value, InterviewExternalCalendarAttempt intent,
            InterviewExternalCalendarPort.Result result) {
        if (InterviewExternalCalendarStatus.PROCESSING.name().equals(value.getStatus())) value.setAttemptCount(value.getAttemptCount() + 1);
        clearLease(value);
        value.setStatus(InterviewExternalCalendarStatus.FAILED.name());
        value.setLastErrorCode(intent.getErrorCode());
        value.setLastErrorDetail(intent.getErrorDetail());
        boolean retry = result != null && result.retryable() && value.getAttemptCount() < MAX_AUTOMATIC_ATTEMPTS;
        value.setNextAttemptAt(retry ? clock.instant().plusSeconds(backoffSeconds(value.getAttemptCount()))
                : clock.instant().plus(3650, ChronoUnit.DAYS));
        if (InterviewExternalCalendarMode.localOnly(value.getDesiredType()) || value.getOperationRevision() != intent.getOperationRevision()) {
            value.setCreateIntentId(null);
            value.setStatus(InterviewExternalCalendarMode.localOnly(value.getDesiredType())
                    ? InterviewExternalCalendarStatus.CANCELLED.name() : InterviewExternalCalendarStatus.PENDING.name());
            value.setNextAttemptAt(clock.instant());
        }
        value.setUpdateTime(clock.instant());
    }

    private void quarantine(InterviewExternalCalendarProjection value, InterviewExternalCalendarAttempt intent) {
        if (InterviewExternalCalendarAttemptStatus.RUNNING.name().equals(intent.getStatus())) {
            intent.setStatus(InterviewExternalCalendarAttemptStatus.UNKNOWN.name());
            intent.setErrorCode("WECOM_CREATE_RESULT_UNKNOWN");
            intent.setErrorDetail("创建请求可能已生效，必须核对远端结果及原请求终止情况");
            intent.setUpdateTime(clock.instant());
            if (!attempts.updateById(intent)) throw changed();
        }
        if (!InterviewExternalCalendarStatus.UNKNOWN.name().equals(value.getStatus())) value.setAttemptCount(value.getAttemptCount() + 1);
        value.setStatus(InterviewExternalCalendarStatus.UNKNOWN.name());
        value.setLastErrorCode(Objects.requireNonNullElse(intent.getErrorCode(), "WECOM_CREATE_RESULT_UNKNOWN"));
        value.setLastErrorDetail(Objects.requireNonNullElse(intent.getErrorDetail(), "创建结果待人工核对"));
        clearLease(value);
        value.setUpdateTime(clock.instant());
        requireUpdate(value);
    }

    private boolean preserveCreate(InterviewExternalCalendarProjection value) {
        if (value.getCreateIntentId() != null) {
            InterviewExternalCalendarAttempt intent = requireIntent(value);
            if (InterviewExternalCalendarAttemptStatus.SUCCEEDED.name().equals(intent.getStatus())) {
                applyCreated(value, intent, null);
                return false;
            }
            return !InterviewExternalCalendarAttemptStatus.FAILED.name().equals(intent.getStatus());
        }
        if (legacyInFlightCreate(value) || legacyAmbiguousCreate(value)) {
            legacyIntent(value);
            if (!InterviewExternalCalendarStatus.PROCESSING.name().equals(value.getStatus())) value.setStatus(InterviewExternalCalendarStatus.UNKNOWN.name());
            return true;
        }
        return InterviewExternalCalendarStatus.UNKNOWN.name().equals(value.getStatus());
    }

    private InterviewExternalCalendarAttempt legacyIntent(InterviewExternalCalendarProjection value) {
        InterviewExternalCalendarAttempt intent = attempt(value, InterviewExternalCalendarOperation.CREATE,
                ids.newId(), InterviewExternalCalendarAttemptStatus.UNKNOWN.name(), clock.instant());
        intent.setErrorCode("WECOM_LEGACY_CREATE_UNKNOWN");
        intent.setErrorDetail("旧版创建请求结果未确认，普通重试不能证明远端未创建");
        requireSave(intent);
        value.setCreateIntentId(intent.getId());
        return intent;
    }

    private InterviewExternalCalendarAttempt requireIntent(InterviewExternalCalendarProjection value) {
        InterviewExternalCalendarAttempt intent = attempts.findForUpdate(value.getCreateIntentId());
        if (intent == null || !Objects.equals(intent.getProjectionId(), value.getId())
                || !InterviewExternalCalendarOperation.CREATE.name().equals(intent.getOperationType())) throw changed();
        return intent;
    }

    private static boolean legacyInFlightCreate(InterviewExternalCalendarProjection value) {
        return InterviewExternalCalendarStatus.PROCESSING.name().equals(value.getStatus())
                && value.getActiveType() == null && !InterviewExternalCalendarMode.localOnly(value.getDesiredType());
    }

    private static boolean legacyAmbiguousCreate(InterviewExternalCalendarProjection value) {
        return value.getCreateIntentId() == null && value.getActiveType() == null
                && InterviewExternalCalendarStatus.FAILED.name().equals(value.getStatus())
                && InterviewExternalCalendarLegacyCreateError.ambiguous(value.getLastErrorCode());
    }

    private static InterviewExternalCalendarAttempt attempt(InterviewExternalCalendarProjection value,
            InterviewExternalCalendarOperation operation, String id, String status, Instant now) {
        InterviewExternalCalendarAttempt result = new InterviewExternalCalendarAttempt();
        result.setId(id);
        result.setProjectionId(value.getId());
        result.setOperationRevision(value.getOperationRevision());
        result.setOperationType(operation.name());
        result.setExternalType(operation == InterviewExternalCalendarOperation.CANCEL ? value.getActiveType() : value.getDesiredType());
        result.setStatus(status);
        result.setOccurredAt(now);
        result.setCreateTime(now);
        result.setUpdateTime(now);
        return result;
    }

    private void requireSave(InterviewExternalCalendarAttempt value) {
        if (!attempts.save(value)) throw changed();
    }

    private void requireUpdate(InterviewExternalCalendarProjection value) {
        if (!projections.updateById(value)) throw changed();
    }

    private static RuntimeException changed() {
        return PlatformExceptions.conflict("WECOM_PROJECTION_CHANGED", "企业微信日程或回执状态已变化，请刷新后重试");
    }

    private static RuntimeException reconciliationRequired() {
        return PlatformExceptions.conflict("WECOM_CREATE_RECONCILIATION_REQUIRED", "创建结果待核对，请管理员确认原请求及远端结果");
    }

    private ProjectionValues values(Interview interview, List<String> localParticipantIds,
            InterviewExternalCalendarDTO requested, String actorId, String mode) {
        if (InterviewExternalCalendarMode.localOnly(mode)) {
            return new ProjectionValues(mode, text(requested.getTitle(), 120),
                    text(requested.getDescription(), 500), text(requested.getAdminUserId(), 128),
                    List.of(), List.of(), interview.getScheduledStartAt(), interview.getScheduledEndAt(),
                    interview.getTimezoneId(), interview.getLocationText(), 0, false, true, true);
        }
        long duration = Duration.between(interview.getScheduledStartAt(), interview.getScheduledEndAt()).toSeconds();
        if (InterviewExternalCalendarMode.WECOM_MEETING.name().equals(mode)
                && (duration < 300 || duration > 86399)) {
            throw PlatformExceptions.unprocessable(
                    "WECOM_MEETING_DURATION_INVALID", "企业微信会议时长必须在 5 分钟到 23 小时 59 分 59 秒之间");
        }
        List<String> localIds = localParticipantIds == null ? List.of() : localParticipantIds;
        LinkedHashSet<String> invitees = new LinkedHashSet<>(directory
                .requireLinkedUserIds(Set.copyOf(localIds)).values());
        List<String> additional = normalizeIds(requested.getAdditionalInviteeUserIds());
        directory.requireMembers(Set.copyOf(additional));
        invitees.addAll(additional);
        String admin = text(requested.getAdminUserId(), 128);
        if (admin == null) {
            admin = directory.requireLinkedUserIds(Set.of(actorId)).get(actorId);
        } else {
            directory.requireMembers(Set.of(admin));
        }
        invitees.add(admin);
        String title = required(requested.getTitle(), "externalCalendar.title", 120);
        if (InterviewExternalCalendarMode.WECOM_MEETING.name().equals(mode) && title.codePointCount(0, title.length()) > 20) {
            throw PlatformExceptions.unprocessable("WECOM_MEETING_TITLE_TOO_LONG", "企业微信会议标题最多 20 个字符");
        }
        int reminder = requested.getReminderMinutes() == null ? 5 : requested.getReminderMinutes();
        if (!REMINDER_MINUTES.contains(reminder)) {
            throw PlatformExceptions.unprocessable(
                    "WECOM_REMINDER_INVALID", "企业微信提醒仅支持开始时、5 分钟、15 分钟、1 小时或1天前");
        }
        return new ProjectionValues(mode, title, text(requested.getDescription(), 500), admin,
                List.copyOf(invitees), additional, interview.getScheduledStartAt(),
                interview.getScheduledEndAt(), interview.getTimezoneId(), interview.getLocationText(),
                reminder * 60, Boolean.TRUE.equals(requested.getWaitingRoomEnabled()),
                requested.getAllowEnterBeforeHost() == null || requested.getAllowEnterBeforeHost(),
                requested.getEnterMuteEnabled() == null || requested.getEnterMuteEnabled());
    }

    private void apply(InterviewExternalCalendarProjection target, ProjectionValues values) {
        target.setDesiredType(values.mode());
        target.setTitle(values.title());
        target.setDescription(values.description());
        target.setAdminUserId(values.adminUserId());
        target.setInviteeUserIdsJson(JsonText.write(values.inviteeUserIds()));
        target.setAdditionalInviteeUserIdsJson(JsonText.write(values.additionalInviteeUserIds()));
        target.setStartAt(values.startAt());
        target.setEndAt(values.endAt());
        target.setTimezoneId(values.timezoneId());
        target.setLocationText(values.location());
        target.setReminderSeconds(values.reminderSeconds());
        target.setWaitingRoomEnabled(values.waitingRoomEnabled());
        target.setAllowEnterBeforeHost(values.allowEnterBeforeHost());
        target.setEnterMuteEnabled(values.enterMuteEnabled());
    }

    private InterviewExternalCalendarDTO from(InterviewExternalCalendarProjection value) {
        return new InterviewExternalCalendarDTO(value.getDesiredType(), value.getTitle(), value.getDescription(),
                value.getAdminUserId(), readIds(value.getAdditionalInviteeUserIdsJson()),
                value.getReminderSeconds() / 60, value.isWaitingRoomEnabled(),
                value.isAllowEnterBeforeHost(), value.isEnterMuteEnabled());
    }

    private InterviewExternalCalendarPort.Command command(
            InterviewExternalCalendarProjection value, String type) {
        return new InterviewExternalCalendarPort.Command(type, value.getExternalObjectId(), value.getTitle(),
                value.getDescription(), value.getAdminUserId(), readIds(value.getInviteeUserIdsJson()),
                value.getStartAt(), value.getEndAt(), value.getTimezoneId(), value.getLocationText(),
                value.getReminderSeconds(), value.isWaitingRoomEnabled(),
                value.isAllowEnterBeforeHost(), value.isEnterMuteEnabled());
    }

    private List<String> readIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            JsonNode node = json.readTree(value);
            if (node == null || !node.isArray()) throw new IllegalStateException("invalid invitee json");
            List<String> result = new ArrayList<>();
            node.forEach(item -> {
                if (!item.isString()) throw new IllegalStateException("invalid invitee json");
                result.add(item.stringValue());
            });
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("企业微信参与人快照损坏", failure);
        }
    }

    private static String normalizeMode(String value) {
        String normalized = value == null ? InterviewExternalCalendarMode.LOCAL_ONLY.name()
                : value.trim().toUpperCase(Locale.ROOT);
        if (!InterviewExternalCalendarMode.supports(normalized)) {
            throw PlatformExceptions.unprocessable(
                    "WECOM_CALENDAR_MODE_INVALID", "企业微信联动模式必须为本地、普通日程或会议");
        }
        return normalized;
    }

    private static List<String> normalizeIds(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = text(value, 128);
            if (normalized == null) throw PlatformExceptions.unprocessable(
                    "WECOM_USER_ID_INVALID", "企业微信成员 userid 无效");
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static String text(String value, int maximum) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maximum) throw PlatformExceptions.unprocessable(
                "WECOM_FIELD_TOO_LONG", "企业微信日程字段长度超过限制");
        return normalized;
    }

    private static String safe(String value, int maximum) {
        if (value == null) return null;
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static long backoffSeconds(int attempt) {
        return Math.min(900, 15L << Math.min(6, Math.max(0, attempt - 1)));
    }

    private static InterviewExternalCalendarOperation operation(InterviewExternalCalendarProjection value) {
        if (value.getActiveType() != null && (!value.getActiveType().equals(value.getDesiredType())
                || InterviewExternalCalendarMode.localOnly(value.getDesiredType()))) {
            return InterviewExternalCalendarOperation.CANCEL;
        }
        if (value.getActiveType() == null) {
            return InterviewExternalCalendarMode.localOnly(value.getDesiredType())
                    ? InterviewExternalCalendarOperation.NONE
                    : InterviewExternalCalendarOperation.CREATE;
        }
        return InterviewExternalCalendarOperation.UPDATE;
    }

    private static boolean owned(InterviewExternalCalendarProjection current,
            InterviewExternalCalendarProjection claimed, String leaseToken) {
        return current != null && leaseToken.equals(current.getLeaseToken())
                && current.getOperationRevision() == claimed.getOperationRevision();
    }

    private static void clearLease(InterviewExternalCalendarProjection value) {
        value.setLeaseToken(null);
        value.setLeaseUntil(null);
    }

    @Value
    @Accessors(fluent = true)
    private static class ProjectionValues {
        String mode;
        String title;
        String description;
        String adminUserId;
        List<String> inviteeUserIds;
        List<String> additionalInviteeUserIds;
        Instant startAt;
        Instant endAt;
        String timezoneId;
        String location;
        int reminderSeconds;
        boolean waitingRoomEnabled;
        boolean allowEnterBeforeHost;
        boolean enterMuteEnabled;
    }
}
