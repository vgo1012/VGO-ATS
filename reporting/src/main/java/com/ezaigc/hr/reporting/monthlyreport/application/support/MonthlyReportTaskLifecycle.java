package com.ezaigc.hr.reporting.monthlyreport.application.support;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskRetryGuard;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalHandler;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportPhase;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportStatus;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 与任务终态/重试同事务，覆盖进程崩溃和通用任务入口。 */
@Component
public class MonthlyReportTaskLifecycle implements AsyncTaskTerminalHandler, AsyncTaskRetryGuard {
    @Resource private MonthlyReportService reports;
    @Resource private MonthlyReportAccess access;
    @Resource private Clock clock;
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void terminalWithoutWorker(String taskType, String taskId, String resourceId, AsyncTaskStatus status,
            String errorCode, Instant finishedAt, String actorId) {
        if (!MonthlyReportConstants.isMonthlyTask(taskType)) return;
        MonthlyReportEntity row = reports.lock(resourceId);
        if (row == null || !taskId.equals(row.getTaskId())
                || !(MonthlyReportStatus.VALIDATING.matches(row.getStatus()) || MonthlyReportStatus.QUEUED.matches(row.getStatus()))) return;
        row.setStatus(status == AsyncTaskStatus.CANCELLED ? MonthlyReportStatus.CANCELLED.getCode() : MonthlyReportStatus.FAILED.getCode());
        row.setErrorCode(errorCode);
        row.setErrorDetail(status == AsyncTaskStatus.CANCELLED ? null : "处理未完成，请查看失败原因后重试；输入问题需重新上传");
        row.setUpdateTime(finishedAt); row.setUpdateId(actorId);
        if (!reports.updateById(row)) throw PlatformExceptions.versionConflict();
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRetryAllowed(TaskSnapshot task) {
        if (!MonthlyReportConstants.isMonthlyTask(task.taskType())) return;
        access.require();
        MonthlyReportEntity row = reports.lock(task.resourceId());
        if (row == null || !task.id().equals(row.getTaskId()) || !MonthlyReportStatus.FAILED.matches(row.getStatus())
                || !row.getExpiresAt().isAfter(clock.instant()) || !task.retryable())
            throw PlatformExceptions.conflict("MONTHLY_REPORT_NOT_RETRYABLE", "此汇报不能重试，请重新上传");
        row.setStatus(MonthlyReportPhase.VALIDATE.matches(row.getPhase())
                ? MonthlyReportStatus.VALIDATING.getCode() : MonthlyReportStatus.QUEUED.getCode());
        row.setErrorCode(null); row.setErrorDetail(null); row.setUpdateTime(clock.instant());
        if (!reports.updateById(row)) throw PlatformExceptions.versionConflict();
    }
}
