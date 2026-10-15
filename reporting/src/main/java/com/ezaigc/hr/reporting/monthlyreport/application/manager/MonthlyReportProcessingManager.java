package com.ezaigc.hr.reporting.monthlyreport.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.monthlyreport.application.provider.MonthlyReportFileProvider;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportLimitsDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportFileKind;
import com.ezaigc.hr.reporting.monthlyreport.infrastructure.MonthlyReportDocxRenderer;
import com.ezaigc.hr.reporting.monthlyreport.infrastructure.MonthlyReportEngine;
import com.ezaigc.hr.reporting.monthlyreport.infrastructure.MonthlyReportInterruptedException;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportFileService;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 私有文件解析与渲染只在独立有界报表线程池运行。 */
@Component
public class MonthlyReportProcessingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MonthlyReportProcessingManager.class);
    private final MonthlyReportEngine engine = new MonthlyReportEngine();
    private final MonthlyReportDocxRenderer renderer = new MonthlyReportDocxRenderer();
    @Resource private MonthlyReportTransactionManager transactions;
    @Resource private MonthlyReportFileProvider fileProvider;
    @Resource private MonthlyReportFileService files;
    @Resource private MalwareScanPort scanner;
    @Resource private AsyncTaskPort tasks;
    @Resource private MonthlyReportProperties properties;
    @Resource private PlatformIdGenerator ids;
    @Resource private ObjectMapper json;
    @Resource private Clock clock;
    // cleanupDispatcher 串行运行；游标跨轮推进并在尾部回绕，让失败文件保留重试资格而不阻塞后续文件。
    private Instant cleanupAfterExpiry;
    private String cleanupAfterId;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(TaskSnapshot task) {
        long started = System.nanoTime();
        long deadline = started + 180_000_000_000L;
        BooleanSupplier cancelled = () -> System.nanoTime() >= deadline || tasks.shouldStopProcessing(task);
        try {
            if (cancelled.getAsBoolean()) { transactions.stopped(task); return; }
            MonthlyReportEntity row = transactions.require(task.resourceId());
            if (!task.id().equals(row.getTaskId())) return;
            if (!row.getExpiresAt().isAfter(clock.instant())) {
                tasks.failUnhandled(task, "MONTHLY_REPORT_EXPIRED", "输入文件已过保留期，请重新上传", false); return;
            }
            if (MonthlyReportConstants.VALIDATE_TASK.equals(task.taskType())) validate(task, row, cancelled);
            else generate(task, row, cancelled);
        } catch (MonthlyReportInterruptedException stopped) {
            if (tasks.shouldStopProcessing(task)) transactions.stopped(task);
            else tasks.failUnhandled(task, "MONTHLY_REPORT_TIME_LIMIT", "报表处理达到时限，请缩小数据范围后重试", true);
        } catch (IOException failure) {
            tasks.failUnhandled(task, "MONTHLY_REPORT_FILE_IO_FAILED", "文件读取或保存失败，请稍后重试", true);
        } finally {
            LOGGER.info("monthly-report-phase-completed taskId={}, taskType={}, durationMillis={}", task.id(), task.taskType(), (System.nanoTime() - started) / 1_000_000);
        }
    }

    private void validate(TaskSnapshot task, MonthlyReportEntity row, BooleanSupplier cancelled) throws IOException {
        List<MonthlyReportFileEntity> inputs = files.forReport(row.getId());
        MonthlyReportFileEntity offer = source(inputs, MonthlyReportFileKind.OFFER);
        MonthlyReportFileEntity demand = source(inputs, MonthlyReportFileKind.DEMAND);
        Path offerPath = null; Path demandPath = null;
        try {
            offerPath = fileProvider.fetch(offer.getObjectKey(), offer.getContentHash(), offer.getSizeBytes());
            demandPath = fileProvider.fetch(demand.getObjectKey(), demand.getContentHash(), demand.getSizeBytes());
            if (!clean(task, offer.getFileName(), offerPath) || !clean(task, demand.getFileName(), demandPath)) return;
            if (cancelled.getAsBoolean()) throw new MonthlyReportInterruptedException();
            tasks.updateProgress(task, Math.max(task.progressPercent(), 20));
            MonthlyReportLimitsDTO defaults = MonthlyReportLimitsDTO.defaults();
            MonthlyReportLimitsDTO limits = new MonthlyReportLimitsDTO(properties.getMaxFileBytes(), defaults.maxUncompressedBytes(),
                    properties.getMaxRows(), defaults.maxColumns(), defaults.maxCellChars(), properties.getMaxGroups(),
                    defaults.maxZipEntries(), defaults.maxCompressionRatio(), 2000);
            MonthlyReportSnapshotDTO result = engine.parseAndCalculate(offerPath, demandPath, YearMonth.parse(row.getReportMonth()), row.getDataAsOf(), limits, cancelled);
            if (!transactions.validated(task, result)) transactions.stopped(task);
        } finally { removeTemp(offerPath); removeTemp(demandPath); }
    }

    private boolean clean(TaskSnapshot task, String name, Path path) throws IOException {
        MalwareScanVerdict verdict;
        try (InputStream in = Files.newInputStream(path)) { verdict = scanner.scan(name, in); }
        if (verdict == MalwareScanVerdict.CLEAN) return true;
        tasks.failUnhandled(task, verdict == MalwareScanVerdict.MALICIOUS ? "MONTHLY_REPORT_FILE_UNSAFE" : "MONTHLY_REPORT_SCAN_UNAVAILABLE",
                verdict == MalwareScanVerdict.MALICIOUS ? "文件格式或安全检查未通过，请重新导出 xlsx" : "文件扫描服务暂不可用，请稍后重试",
                verdict != MalwareScanVerdict.MALICIOUS);
        return false;
    }

    private void generate(TaskSnapshot task, MonthlyReportEntity row, BooleanSupplier cancelled) throws IOException {
        MonthlyReportSnapshotDTO snapshot = json.readValue(row.getSnapshotJson(), MonthlyReportSnapshotDTO.class);
        if (!MonthlyReportEngine.TEMPLATE_VERSION.equals(snapshot.templateVersion())) {
            tasks.failUnhandled(task, "MONTHLY_REPORT_TEMPLATE_CHANGED", "历史模板版本已变更，请新建汇报", false); return;
        }
        tasks.updateProgress(task, Math.max(task.progressPercent(), 20));
        byte[] bytes = renderer.render(snapshot, row.getManagementRemarks(), cancelled);
        if (bytes.length > 32L * 1024 * 1024) {
            tasks.failUnhandled(task, "MONTHLY_REPORT_OUTPUT_TOO_LARGE", "报告内容过多，请缩小范围重新生成", false); return;
        }
        MonthlyReportFileEntity output = new MonthlyReportFileEntity();
        output.setId(ids.newId()); output.setReportId(row.getId()); output.setFileKind(MonthlyReportFileKind.REPORT_TEMP.getCode());
        output.setFileName(row.getReportMonth().replace("-", "年") + "月招聘月度汇报（国内）.docx");
        output.setObjectKey("monthly-report/results/" + row.getId() + "/" + output.getId() + ".docx");
        output.setContentHash(MonthlyReportFileProvider.hash(bytes)); output.setSizeBytes(bytes.length);
        output.setExpiresAt(clock.instant().plus(1, ChronoUnit.DAYS)); output.setCreateId(row.getOwnerUserId()); output.setUpdateId(row.getOwnerUserId());
        if (!transactions.reserveOutput(task, output)) { transactions.stopped(task); return; }
        Path path = Files.createTempFile("hr-monthly-output-", ".docx");
        try {
            Files.write(path, bytes); fileProvider.put(output.getObjectKey(), path);
            if (!transactions.publish(task, output.getId())) transactions.stopped(task);
        } finally { removeTemp(path); }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void cleanup() {
        Instant now = clock.instant();
        List<MonthlyReportFileEntity> batch = files.expiredAfter(now, cleanupAfterExpiry, cleanupAfterId, 20);
        if (batch.isEmpty() && cleanupAfterId != null) {
            cleanupAfterExpiry = null;
            cleanupAfterId = null;
            batch = files.expiredAfter(now, null, null, 20);
        }
        for (MonthlyReportFileEntity file : batch) {
            try {
                fileProvider.delete(file.getObjectKey()); file.setDeleted(true);
                if (!files.updateById(file)) throw new IllegalStateException("月报文件清理标记未保存");
            } catch (IOException | RuntimeException failure) {
                file.setDeleted(false);
                LOGGER.warn("monthly-report-cleanup-deferred fileId={}", file.getId());
            } finally {
                cleanupAfterExpiry = file.getExpiresAt();
                cleanupAfterId = file.getId();
            }
        }
    }
    private static MonthlyReportFileEntity source(List<MonthlyReportFileEntity> inputs, MonthlyReportFileKind kind) {
        return inputs.stream().filter(f -> kind.matches(f.getFileKind()) && !f.isDeleted()).findFirst()
                .orElseThrow(() -> new IllegalStateException("月报输入元数据不完整"));
    }
    private static void removeTemp(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (IOException failure) { LOGGER.warn("monthly-report-local-temp-cleanup-deferred"); }
    }
}
