package com.ezaigc.hr.reporting.monthlyreport.application.assembler;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportDetailResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportFileResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportSnapshotResponse;
import com.ezaigc.hr.reporting.monthlyreport.domain.response.MonthlyReportSummaryResponse;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportFileKind;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportStatus;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MonthlyReportAssembler {
    @Resource private ObjectMapper json;
    public MonthlyReportSummaryResponse summary(MonthlyReportEntity row, Instant now) {
        return new MonthlyReportSummaryResponse(row.getId(), row.getReportMonth(), row.getDataAsOf(),
                state(row, null, now), row.getVersion().toString(), row.getCreateTime(), row.getCreateName(),
                row.getOfferCount(), row.getTotalScore(), row.getErrorCode(), row.getErrorDetail());
    }
    public MonthlyReportDetailResponse detail(MonthlyReportEntity row, List<MonthlyReportFileEntity> files, TaskSnapshot task, Instant now) {
        MonthlyReportSnapshotResponse snapshot = row.getSnapshotJson() == null ? null : json.readValue(row.getSnapshotJson(), MonthlyReportSnapshotResponse.class);
        return new MonthlyReportDetailResponse(row.getId(), row.getReportMonth(), row.getDataAsOf(), state(row, task, now),
                row.getVersion().toString(), row.getCreateTime(), row.getCreateName(), row.getOfferCount(), row.getTotalScore(),
                row.getErrorCode(), row.getErrorDetail(), row.getTaskId(), task == null ? 0 : task.progressPercent(),
                task != null && task.retryable() && MonthlyReportStatus.FAILED.matches(row.getStatus()), row.getManagementRemarks(),
                files.stream().filter(f -> !MonthlyReportFileKind.REPORT_TEMP.matches(f.getFileKind())).map(f -> new MonthlyReportFileResponse(f.getFileKind(), f.getFileName(), f.getSizeBytes())).toList(),
                snapshot == null ? List.of() : snapshot.diagnostics(), snapshot);
    }
    private static String state(MonthlyReportEntity row, TaskSnapshot task, Instant now) {
        if (row.getExpiresAt() != null && !row.getExpiresAt().isAfter(now)) return MonthlyReportStatus.EXPIRED.getCode();
        if (task != null && AsyncTaskStatus.CANCELLING.matches(task.status())) return MonthlyReportStatus.CANCELLING.getCode();
        if (task != null && MonthlyReportStatus.QUEUED.matches(row.getStatus()) && AsyncTaskStatus.RUNNING.matches(task.status()))
            return MonthlyReportStatus.RUNNING.getCode();
        return row.getStatus();
    }
}
