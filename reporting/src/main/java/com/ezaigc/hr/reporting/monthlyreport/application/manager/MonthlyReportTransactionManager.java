package com.ezaigc.hr.reporting.monthlyreport.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportAccess;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportDiagnosticSeverity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportFileKind;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportPhase;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportStatus;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportService;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportFileService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.reporting.service.AuditService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 所有方法仅执行短数据库事务；文件 IO 和渲染在外层执行。 */
@Component
public class MonthlyReportTransactionManager {
    @Resource private MonthlyReportService reports;
    @Resource private MonthlyReportFileService files;
    @Resource private AsyncTaskPort tasks;
    @Resource private AsyncTaskService taskService;
    @Resource private AuditService audits;
    @Resource private MonthlyReportAccess access;
    @Resource private MonthlyReportProperties properties;
    @Resource private PlatformIdGenerator ids;
    @Resource private Clock clock;
    @Resource private ObjectMapper json;

    @Transactional
    public MonthlyReportEntity reserveUpload(MonthlyReportEntity candidate, List<MonthlyReportFileEntity> inputs) {
        CurrentActorSnapshot actor = access.require();
        // 当前读锁定活动范围，再核对幂等请求，唯一键仍是最终并发防线。
        long active = reports.activeCount();
        MonthlyReportEntity existing = reports.findRequest(actor.getUserId(), candidate.getIdempotencyKey());
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(candidate.getRequestFingerprint()))
                throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一上传标识不能用于不同文件或月份");
            return existing;
        }
        if (active >= properties.getMaxActiveReports()) throw PlatformExceptions.conflict("MONTHLY_REPORT_QUEUE_FULL", "月报处理队列已满，请稍后上传");
        if (!reports.save(candidate)) throw new IllegalStateException("月报创建失败");
        for (MonthlyReportFileEntity input : inputs) if (!files.save(input)) throw new IllegalStateException("月报文件预留失败");
        audit(candidate, "MONTHLY_REPORT_UPLOAD", actor.getUserId(), "预留月报输入");
        return candidate;
    }

    @Transactional
    public void uploaded(String id) {
        CurrentActorSnapshot actor = access.require();
        MonthlyReportEntity row = requireLocked(id);
        if (!MonthlyReportStatus.UPLOADING.matches(row.getStatus())) return;
        unexpired(row);
        TaskSnapshot task = enqueue(row, MonthlyReportConstants.VALIDATE_TASK, "validate:" + row.getId(), row.getRequestFingerprint(), actor);
        row.setTaskId(task.id()); row.setStatus(MonthlyReportStatus.VALIDATING.getCode()); row.setPhase(MonthlyReportPhase.VALIDATE.getCode());
        save(row, actor.getUserId());
    }

    @Transactional
    public void uploadFailed(String id) {
        MonthlyReportEntity row = requireLocked(id);
        if (!MonthlyReportStatus.UPLOADING.matches(row.getStatus())) return;
        row.setStatus(MonthlyReportStatus.FAILED.getCode()); row.setErrorCode("MONTHLY_REPORT_UPLOAD_FAILED");
        row.setErrorDetail("文件上传未完成，请重新上传两份 Excel"); save(row, row.getOwnerUserId());
    }

    @Transactional
    public void recoverAbandonedUploads() {
        Instant before = clock.instant().minusSeconds(1800);
        for (MonthlyReportEntity candidate : reports.staleUploads(before, 20)) {
            MonthlyReportEntity row = requireLocked(candidate.getId());
            if (MonthlyReportStatus.UPLOADING.matches(row.getStatus()) && row.getUpdateTime().isBefore(before)) {
                row.setStatus(MonthlyReportStatus.FAILED.getCode()); row.setErrorCode("MONTHLY_REPORT_UPLOAD_INTERRUPTED");
                row.setErrorDetail("上传中断，请重新上传两份 Excel"); save(row, row.getOwnerUserId());
            }
        }
    }

    @Transactional
    public void generate(String id, long version, String key, String fingerprint, String remarks, boolean warningsAccepted) {
        CurrentActorSnapshot actor = access.require();
        MonthlyReportEntity row = requireLocked(id);
        unexpired(row);
        if (row.getGenerationKey() != null) {
            if (row.getGenerationKey().equals(key) && Objects.equals(row.getGenerationFingerprint(), fingerprint)) return;
            throw PlatformExceptions.conflict("MONTHLY_REPORT_ALREADY_GENERATED", "此批次已生成或正在生成，修改内容请新建汇报");
        }
        version(row, version);
        if (!MonthlyReportStatus.READY.matches(row.getStatus())) throw PlatformExceptions.conflict("MONTHLY_REPORT_NOT_READY", "请先完成数据校验");
        MonthlyReportSnapshotDTO snapshot = json.readValue(row.getSnapshotJson(), MonthlyReportSnapshotDTO.class);
        if (!snapshot.ready()) throw PlatformExceptions.conflict("MONTHLY_REPORT_INVALID", "校验错误尚未解决");
        if (snapshot.offerSummary().totalCount() == 0)
            throw PlatformExceptions.conflict("MONTHLY_REPORT_NO_OFFERS", "所选报告月份没有有效Offer记录，请重新选择月份并上传校验");
        boolean warnings = snapshot.diagnostics().stream().anyMatch(d -> d.severity() == MonthlyReportDiagnosticSeverity.WARNING);
        if (warnings && !warningsAccepted) throw PlatformExceptions.conflict("MONTHLY_REPORT_WARNINGS_REQUIRED", "请确认校验提示后生成");
        TaskSnapshot task = enqueue(row, MonthlyReportConstants.GENERATE_TASK, "generate:" + row.getId(), fingerprint, actor);
        row.setGenerationKey(key); row.setGenerationFingerprint(fingerprint); row.setManagementRemarks(remarks);
        row.setTaskId(task.id()); row.setPhase(MonthlyReportPhase.GENERATE.getCode()); row.setStatus(MonthlyReportStatus.QUEUED.getCode());
        save(row, actor.getUserId()); audit(row, "MONTHLY_REPORT_GENERATE", actor.getUserId(), "冻结数据及备注并生成汇报");
    }

    @Transactional(readOnly = true)
    public MonthlyReportEntity require(String id) {
        MonthlyReportEntity row = reports.getById(id);
        if (row == null || MonthlyReportStatus.DELETED.matches(row.getStatus()))
            throw PlatformExceptions.notFound("MONTHLY_REPORT_NOT_FOUND", "汇报不存在");
        return row;
    }

    @Transactional
    public boolean validated(TaskSnapshot task, MonthlyReportSnapshotDTO snapshot) {
        if (!MonthlyReportConstants.VALIDATE_TASK.equals(task.taskType())) return false;
        if (!tasks.renewLease(task)) return false;
        MonthlyReportEntity row = requireLocked(task.resourceId());
        if (!current(row, task)) return false;
        row.setSnapshotJson(json.writeValueAsString(snapshot));
        row.setOfferCount(snapshot.offerSummary().totalCount()); row.setTotalScore(snapshot.offerSummary().totalScore());
        row.setStatus(snapshot.ready() ? MonthlyReportStatus.READY.getCode() : MonthlyReportStatus.INVALID.getCode());
        tasks.succeed(task, row.getId()); save(row, row.getOwnerUserId());
        return true;
    }

    @Transactional
    public boolean reserveOutput(TaskSnapshot task, MonthlyReportFileEntity output) {
        if (!MonthlyReportConstants.GENERATE_TASK.equals(task.taskType())) return false;
        if (!tasks.renewLease(task)) return false;
        MonthlyReportEntity row = requireLocked(task.resourceId());
        if (!current(row, task)) return false;
        if (!files.save(output)) throw new IllegalStateException("月报产物预留失败");
        return true;
    }

    @Transactional
    public boolean publish(TaskSnapshot task, String fileId) {
        if (!MonthlyReportConstants.GENERATE_TASK.equals(task.taskType())) return false;
        if (!tasks.renewLease(task)) return false;
        MonthlyReportEntity row = requireLocked(task.resourceId());
        if (!current(row, task)) return false;
        unexpired(row);
        MonthlyReportFileEntity output = files.getById(fileId);
        if (output == null || !row.getId().equals(output.getReportId()) || !MonthlyReportFileKind.REPORT_TEMP.matches(output.getFileKind()))
            throw new IllegalStateException("月报产物关联无效");
        output.setFileKind(MonthlyReportFileKind.REPORT.getCode()); output.setExpiresAt(row.getExpiresAt());
        if (!files.updateById(output)) throw PlatformExceptions.versionConflict();
        tasks.succeed(task, row.getId()); row.setStatus(MonthlyReportStatus.SUCCEEDED.getCode()); save(row, row.getOwnerUserId());
        audit(row, "MONTHLY_REPORT_PUBLISHED", row.getOwnerUserId(), "固定汇报生成完成");
        return true;
    }

    @Transactional
    public void cancel(String id, long expectedVersion, String reason, String key) {
        CurrentActorSnapshot actor = access.require();
        MonthlyReportEntity initial = require(id);
        if (initial.getTaskId() != null) taskService.lockTask(initial.getTaskId());
        MonthlyReportEntity row = requireLocked(id);
        String receipt = "cancel:" + expectedVersion + ":" + reason;
        if (replayed(row, actor.getUserId(), key, receipt)) return;
        if (MonthlyReportStatus.CANCELLED.matches(row.getStatus())) return;
        version(row, expectedVersion);
        if (row.getTaskId() == null || !(MonthlyReportStatus.VALIDATING.matches(row.getStatus())
                || MonthlyReportStatus.QUEUED.matches(row.getStatus())))
            throw PlatformExceptions.conflict("MONTHLY_REPORT_NOT_CANCELLABLE", "当前汇报不能取消");
        tasks.cancel(row.getTaskId(), reason);
        remember(requireLocked(id), actor.getUserId(), key, receipt);
        audit(row, "MONTHLY_REPORT_CANCEL", actor.getUserId(), reason);
    }

    @Transactional
    public void retry(String id, long expectedVersion, String reason, String key) {
        CurrentActorSnapshot actor = access.require();
        MonthlyReportEntity initial = require(id);
        if (initial.getTaskId() == null) throw PlatformExceptions.conflict("MONTHLY_REPORT_NOT_RETRYABLE", "上传失败请重新上传文件");
        taskService.lockTask(initial.getTaskId());
        MonthlyReportEntity row = requireLocked(id);
        String receipt = "retry:" + expectedVersion + ":" + reason;
        if (replayed(row, actor.getUserId(), key, receipt)) return;
        version(row, expectedVersion); unexpired(row);
        tasks.retryFailed(row.getTaskId(), reason);
        remember(requireLocked(id), actor.getUserId(), key, receipt);
        audit(row, "MONTHLY_REPORT_RETRY", actor.getUserId(), reason);
    }

    /** 保留幂等标识及审计，清除快照；文件回收由现有过期清理任务重试。 */
    @Transactional
    public void delete(String id, long expectedVersion, String reason) {
        CurrentActorSnapshot actor = access.require();
        MonthlyReportEntity initial = reports.getById(id);
        if (initial == null) throw PlatformExceptions.notFound("MONTHLY_REPORT_NOT_FOUND", "汇报不存在");
        // 与任务重试、取消和工作器保持 task -> report 的锁顺序。
        if (initial.getTaskId() != null) taskService.lockTask(initial.getTaskId());
        MonthlyReportEntity row = reports.lock(id);
        if (row == null) throw PlatformExceptions.notFound("MONTHLY_REPORT_NOT_FOUND", "汇报不存在");
        if (MonthlyReportStatus.DELETED.matches(row.getStatus())) return;
        version(row, expectedVersion);
        if (MonthlyReportStatus.isActive(row.getStatus()) || MonthlyReportStatus.RUNNING.matches(row.getStatus())
                || MonthlyReportStatus.CANCELLING.matches(row.getStatus()))
            throw PlatformExceptions.conflict("MONTHLY_REPORT_PROCESSING", "汇报正在处理中，请等待结束或取消后再删除");
        Instant now = clock.instant();
        for (MonthlyReportFileEntity file : files.forReport(id)) {
            if (file.isDeleted()) continue;
            file.setExpiresAt(now);
            file.setUpdateId(actor.getUserId());
            file.setUpdateTime(now);
            if (!files.updateById(file)) throw PlatformExceptions.versionConflict();
        }
        row.setStatus(MonthlyReportStatus.DELETED.getCode());
        row.setSnapshotJson(null);
        row.setManagementRemarks(null);
        row.setErrorCode(null);
        row.setErrorDetail(null);
        row.setExpiresAt(now);
        save(row, actor.getUserId());
        audit(row, "MONTHLY_REPORT_DELETE", actor.getUserId(), reason);
    }

    @Transactional
    public void downloadAudit(String id) {
        CurrentActorSnapshot actor = access.require();
        MonthlyReportEntity row = requireLocked(id); unexpired(row);
        if (!MonthlyReportStatus.SUCCEEDED.matches(row.getStatus())) throw PlatformExceptions.conflict("MONTHLY_REPORT_NOT_COMPLETE", "报告尚未生成完成");
        audit(row, "MONTHLY_REPORT_DOWNLOAD", actor.getUserId(), "下载月报文件");
    }

    @Transactional
    public void stopped(TaskSnapshot task) {
        TaskSnapshot latest = tasks.get(task.id());
        if (latest.attemptCount() != task.attemptCount() || !AsyncTaskStatus.CANCELLING.matches(latest.status())) return;
        tasks.cancelled(task);
        MonthlyReportEntity row = requireLocked(task.resourceId());
        if (current(row, task)) { row.setStatus(MonthlyReportStatus.CANCELLED.getCode()); save(row, row.getOwnerUserId()); }
    }

    private TaskSnapshot enqueue(MonthlyReportEntity row, String type, String key, String fingerprint, CurrentActorSnapshot actor) {
        return tasks.enqueue(new EnqueueTask(ids.newId(), type, MonthlyReportConstants.RESOURCE, row.getId(), actor.getUserId(),
                row.getOwnerUserId(), row.getOwnerDepartmentId(), MonthlyReportConstants.PERMISSION,
                MonthlyReportConstants.PERMISSION, key, fingerprint, properties.getMaxAttempts(), clock.instant()));
    }
    private MonthlyReportEntity requireLocked(String id) {
        MonthlyReportEntity row = reports.lock(id);
        if (row == null || MonthlyReportStatus.DELETED.matches(row.getStatus()))
            throw PlatformExceptions.notFound("MONTHLY_REPORT_NOT_FOUND", "汇报不存在");
        return row;
    }
    private static void version(MonthlyReportEntity row, long expected) {
        if (row.getVersion().longValue() != expected) throw PlatformExceptions.versionConflict();
    }
    private void unexpired(MonthlyReportEntity row) {
        if (!row.getExpiresAt().isAfter(clock.instant())) throw PlatformExceptions.conflict("MONTHLY_REPORT_EXPIRED", "文件已过保留期，请重新上传");
    }
    private static boolean current(MonthlyReportEntity row, TaskSnapshot task) {
        return task.id().equals(row.getTaskId()) && MonthlyReportConstants.RESOURCE.equals(task.resourceType())
                && ((MonthlyReportConstants.VALIDATE_TASK.equals(task.taskType()) && MonthlyReportPhase.VALIDATE.matches(row.getPhase())
                        && MonthlyReportStatus.VALIDATING.matches(row.getStatus()))
                || (MonthlyReportConstants.GENERATE_TASK.equals(task.taskType()) && MonthlyReportPhase.GENERATE.matches(row.getPhase())
                        && MonthlyReportStatus.QUEUED.matches(row.getStatus())));
    }
    private boolean replayed(MonthlyReportEntity row, String actorId, String key, String fingerprint) {
        String existing = receipts(row).get(actorId + ":" + key);
        if (existing == null) return false;
        if (!existing.equals(fingerprint)) throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED", "同一操作标识不能用于不同请求");
        return true;
    }
    private void remember(MonthlyReportEntity row, String actorId, String key, String fingerprint) {
        Map<String, String> receipts = receipts(row);
        if (receipts.size() >= 20) throw PlatformExceptions.conflict("MONTHLY_REPORT_ACTION_LIMIT", "此批次操作次数过多，请新建汇报");
        receipts.put(actorId + ":" + key, fingerprint); row.setExt(json.writeValueAsString(receipts)); save(row, actorId);
    }
    private Map<String, String> receipts(MonthlyReportEntity row) {
        if (row.getExt() == null) return new LinkedHashMap<>();
        return json.readValue(row.getExt(), json.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
    }
    private void save(MonthlyReportEntity row, String actorId) {
        row.setUpdateId(actorId); row.setUpdateTime(clock.instant());
        if (!reports.updateById(row)) throw PlatformExceptions.versionConflict();
    }
    private void audit(MonthlyReportEntity row, String type, String actor, String reason) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(ids.newId()); event.setEventType(type); event.setActorUserId(actor);
        event.setTargetType(MonthlyReportConstants.RESOURCE); event.setTargetId(row.getId());
        event.setOwnerUserId(row.getOwnerUserId()); event.setDepartmentId(row.getOwnerDepartmentId());
        event.setReason(reason); event.setSourceModule("reporting"); event.setSourceEventId(event.getId());
        event.setOccurredAt(clock.instant()); event.setCapturedAt(clock.instant());
        if (!audits.save(event)) throw new IllegalStateException("月报审计写入失败");
    }
}
