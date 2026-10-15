package com.ezaigc.hr.reporting.monthlyreport.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.request.IdempotencyKey;
import com.ezaigc.hr.common.web.version.ResourceVersion;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.monthlyreport.application.assembler.MonthlyReportAssembler;
import com.ezaigc.hr.reporting.monthlyreport.application.provider.MonthlyReportFileProvider;
import com.ezaigc.hr.reporting.monthlyreport.application.provider.MonthlyReportFileProvider.StagedFile;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportAccess;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.request.GenerateMonthlyReportRequest;
import com.ezaigc.hr.reporting.monthlyreport.domain.request.MonthlyReportActionRequest;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportDetailResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportDownloadResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportPageResponse;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportFileKind;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportPhase;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportStatus;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportService;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportFileService;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MonthlyReportApplicationManager {
    @Resource private MonthlyReportAccess access;
    @Resource private MonthlyReportProperties properties;
    @Resource private MonthlyReportFileProvider fileProvider;
    @Resource private MonthlyReportTransactionManager transactions;
    @Resource private MonthlyReportService reports;
    @Resource private MonthlyReportFileService files;
    @Resource private MonthlyReportAssembler assembler;
    @Resource private AsyncTaskPort tasks;
    @Resource private PlatformIdGenerator ids;
    @Resource private Clock clock;
    @Resource private CursorCodec cursors;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MonthlyReportDetailResponse upload(MultipartFile offerFile, MultipartFile demandFile, String month, String asOf, String headerKey) {
        CurrentActorSnapshot actor = access.require(); enabled();
        String key = IdempotencyKey.fromHttpHeader(headerKey);
        YearMonth period = period(month); LocalDate date = date(asOf);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        if (date.isBefore(period.atDay(1)) || date.isAfter(today))
            throw PlatformExceptions.badRequest("MONTHLY_REPORT_DATE_INVALID", "数据截至日期不能早于汇报月首日或晚于今天");
        try (StagedFile offer = fileProvider.stage(offerFile); StagedFile demand = fileProvider.stage(demandFile)) {
            String fingerprint = hash(month + "\n" + asOf + "\n" + offer.hash() + "\n" + demand.hash());
            MonthlyReportEntity candidate = new MonthlyReportEntity();
            candidate.setId(ids.newId()); candidate.setReportMonth(period.toString()); candidate.setDataAsOf(date);
            candidate.setStatus(MonthlyReportStatus.UPLOADING.getCode()); candidate.setPhase(MonthlyReportPhase.UPLOAD.getCode());
            candidate.setOwnerUserId(actor.getUserId()); candidate.setOwnerDepartmentId(actor.getHomeDepartmentId());
            candidate.setIdempotencyKey(key); candidate.setRequestFingerprint(fingerprint);
            candidate.setCreateId(actor.getUserId()); candidate.setCreateName(actor.getDisplayName());
            candidate.setUpdateId(actor.getUserId()); candidate.setCreateTime(clock.instant()); candidate.setUpdateTime(clock.instant());
            candidate.setExpiresAt(clock.instant().plus(properties.getRetentionDays(), ChronoUnit.DAYS));
            MonthlyReportEntity reserved = transactions.reserveUpload(candidate, List.of(
                    input(candidate, MonthlyReportFileKind.OFFER, offer), input(candidate, MonthlyReportFileKind.DEMAND, demand)));
            if (!reserved.getId().equals(candidate.getId())) return detail(reserved.getId());
            try {
                for (MonthlyReportFileEntity input : files.forReport(reserved.getId()))
                    fileProvider.put(input.getObjectKey(), MonthlyReportFileKind.OFFER.matches(input.getFileKind()) ? offer.path() : demand.path());
                transactions.uploaded(reserved.getId());
            } catch (IOException failure) {
                transactions.uploadFailed(reserved.getId());
                throw PlatformExceptions.unavailable("MONTHLY_REPORT_STORAGE_UNAVAILABLE", "文件保存失败，请重新上传");
            }
            return detail(reserved.getId());
        } catch (IOException failure) {
            throw PlatformExceptions.unavailable("MONTHLY_REPORT_UPLOAD_UNAVAILABLE", "文件读取失败，请重新上传");
        }
    }

    public MonthlyReportPageResponse list(String month, String cursor, int limit) {
        CurrentActorSnapshot actor = access.require();
        if (month != null) period(month);
        int bounded = PageLimits.resolve(limit);
        String scope = "monthly-report:" + actor.getUserId() + ":" + (month == null ? "all" : month);
        Instant before = null; String beforeId = null;
        if (cursor != null) {
            List<String> decoded = cursors.decode(cursor, scope, 2);
            try { before = Instant.parse(decoded.get(0)); beforeId = decoded.get(1); }
            catch (DateTimeParseException failure) { throw PlatformExceptions.badRequest("CURSOR_INVALID", "分页位置无效，请刷新"); }
        }
        List<MonthlyReportEntity> found = reports.recent(month, before, beforeId, bounded + 1);
        boolean more = found.size() > bounded;
        List<MonthlyReportEntity> page = more ? found.subList(0, bounded) : found;
        String next = null;
        if (more) { MonthlyReportEntity last = page.getLast(); next = cursors.encode(scope, List.of(last.getCreateTime().toString(), last.getId())); }
        return new MonthlyReportPageResponse(page.stream().map(r -> assembler.summary(r, clock.instant())).toList(), next);
    }

    public MonthlyReportDetailResponse detail(String id) {
        access.require(); MonthlyReportEntity row = transactions.require(id);
        return assembler.detail(row, files.forReport(id), row.getTaskId() == null ? null : tasks.get(row.getTaskId()), clock.instant());
    }
    public MonthlyReportDetailResponse generate(String id, GenerateMonthlyReportRequest request, String key) {
        access.require(); enabled(); String normalizedKey = IdempotencyKey.fromHttpHeader(key);
        String remarks = request.getRemarks() == null ? "" : request.getRemarks().strip();
        String fingerprint = hash(id + "\n" + request.getVersion() + "\n" + remarks + "\n" + request.isAcknowledgeWarnings());
        transactions.generate(id, ResourceVersion.parse(request.getVersion()), normalizedKey, fingerprint, remarks, request.isAcknowledgeWarnings());
        return detail(id);
    }
    public MonthlyReportDetailResponse retry(String id, MonthlyReportActionRequest request, String key) {
        access.require(); enabled(); IdempotencyKey.fromHttpHeader(key);
        transactions.retry(id, ResourceVersion.parse(request.getVersion()), request.getReason(), key); return detail(id);
    }
    public MonthlyReportDetailResponse cancel(String id, MonthlyReportActionRequest request, String key) {
        access.require(); IdempotencyKey.fromHttpHeader(key);
        transactions.cancel(id, ResourceVersion.parse(request.getVersion()), request.getReason(), key); return detail(id);
    }
    public void delete(String id, MonthlyReportActionRequest request) {
        access.require();
        transactions.delete(id, ResourceVersion.parse(request.getVersion()), request.getReason().strip());
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MonthlyReportDownloadResponse download(String id) {
        access.require(); MonthlyReportEntity row = transactions.require(id);
        if (!MonthlyReportStatus.SUCCEEDED.matches(row.getStatus()) || !row.getExpiresAt().isAfter(clock.instant()))
            throw PlatformExceptions.conflict("MONTHLY_REPORT_DOWNLOAD_UNAVAILABLE", "汇报尚未完成或已过保留期");
        MonthlyReportFileEntity file = files.forReport(id).stream()
                .filter(f -> MonthlyReportFileKind.REPORT.matches(f.getFileKind()) && !f.isDeleted()).findFirst()
                .orElseThrow(() -> PlatformExceptions.notFound("MONTHLY_REPORT_FILE_MISSING", "汇报文件不可用"));
        try {
            InputStream stream = fileProvider.open(file.getObjectKey());
            try { transactions.downloadAudit(id); }
            catch (RuntimeException failure) {
                try { stream.close(); }
                catch (IOException | RuntimeException cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
                throw failure;
            }
            return new MonthlyReportDownloadResponse(file.getFileName(), file.getSizeBytes(), stream);
        } catch (IOException failure) { throw PlatformExceptions.unavailable("MONTHLY_REPORT_STORAGE_UNAVAILABLE", "文件暂时无法读取，请稍后重试"); }
    }
    private MonthlyReportFileEntity input(MonthlyReportEntity report, MonthlyReportFileKind kind, StagedFile file) {
        MonthlyReportFileEntity row = new MonthlyReportFileEntity(); row.setId(ids.newId()); row.setReportId(report.getId());
        row.setFileKind(kind.getCode()); row.setFileName(file.fileName()); row.setSizeBytes(file.sizeBytes()); row.setContentHash(file.hash());
        row.setObjectKey("monthly-report/quarantine/" + report.getId() + "/" + row.getId() + ".xlsx");
        row.setExpiresAt(report.getExpiresAt()); row.setCreateId(report.getOwnerUserId()); row.setUpdateId(report.getOwnerUserId());
        return row;
    }
    private void enabled() {
        if (!properties.isEnabled()) throw PlatformExceptions.unavailable("MONTHLY_REPORT_DISABLED", "月报生成暂未开放，已有汇报仍可查看");
    }
    private static YearMonth period(String value) {
        try { return YearMonth.parse(value); }
        catch (DateTimeParseException failure) { throw PlatformExceptions.badRequest("MONTHLY_REPORT_MONTH_INVALID", "汇报月份格式应为 yyyy-MM"); }
    }
    private static LocalDate date(String value) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException failure) { throw PlatformExceptions.badRequest("MONTHLY_REPORT_DATE_INVALID", "数据截至日期格式应为 yyyy-MM-dd"); }
    }
    private static String hash(String value) { return MonthlyReportFileProvider.hash(value.getBytes(StandardCharsets.UTF_8)); }
}
