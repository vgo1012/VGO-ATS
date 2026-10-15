package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportAccess;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportTaskLifecycle;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MonthlyReportLifecycleTest {
  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

  @Test
  void deletedReportsCannotBeResurrectedByRetryOrLateTerminalCallback() {
    MonthlyReportService reports = mock(MonthlyReportService.class);
    MonthlyReportAccess access = mock(MonthlyReportAccess.class);
    MonthlyReportEntity row = report("task", "DELETED", "GENERATE", NOW.plusSeconds(3600));
    when(reports.lock(row.getId())).thenReturn(row);
    when(access.require()).thenReturn(actor());
    MonthlyReportTaskLifecycle lifecycle = lifecycle(reports, access);
    lifecycle.terminalWithoutWorker(MonthlyReportConstants.GENERATE_TASK, "task", row.getId(),
        AsyncTaskStatus.FAILED, "TASK_LEASE_EXPIRED", NOW, "system");
    assertThatThrownBy(() -> lifecycle.requireRetryAllowed(
        task("task", row.getId(), MonthlyReportConstants.GENERATE_TASK, true)))
        .isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code()).isEqualTo("MONTHLY_REPORT_NOT_RETRYABLE");
    assertThat(row.getStatus()).isEqualTo("DELETED");
    verify(reports, never()).updateById(row);
  }

  @Test
  void terminalCallbackFromAnOldTaskCannotOverwriteTheCurrentTask() {
    MonthlyReportService reports = mock(MonthlyReportService.class);
    MonthlyReportEntity row = report("new-task", "VALIDATING", "VALIDATE", NOW.plusSeconds(3600));
    when(reports.lock(row.getId())).thenReturn(row);
    MonthlyReportTaskLifecycle lifecycle = lifecycle(reports, mock(MonthlyReportAccess.class));

    lifecycle.terminalWithoutWorker(MonthlyReportConstants.VALIDATE_TASK, "old-task", row.getId(),
        AsyncTaskStatus.FAILED, "TASK_LEASE_EXPIRED", NOW, "system");

    assertThat(row.getStatus()).isEqualTo("VALIDATING");
    verify(reports, never()).updateById(row);
  }

  @Test
  void retryOfFailedUnexpiredTaskRechecksAuthorizationAndRestoresTheMatchingPhase() {
    MonthlyReportService reports = mock(MonthlyReportService.class);
    MonthlyReportAccess access = mock(MonthlyReportAccess.class);
    MonthlyReportEntity row = report("task", "FAILED", "VALIDATE", NOW.plusSeconds(3600));
    when(reports.lock(row.getId())).thenReturn(row);
    when(reports.updateById(row)).thenReturn(true);
    when(access.require()).thenReturn(actor());
    MonthlyReportTaskLifecycle lifecycle = lifecycle(reports, access);

    lifecycle.requireRetryAllowed(task("task", row.getId(), MonthlyReportConstants.VALIDATE_TASK, true));

    assertThat(row.getStatus()).isEqualTo("VALIDATING");
    assertThat(row.getErrorCode()).isNull();
    assertThat(row.getErrorDetail()).isNull();
    verify(access).require();
    verify(reports).updateById(row);
  }

  @Test
  void expiredOrNonRetryableFailureCannotBeRetried() {
    MonthlyReportService reports = mock(MonthlyReportService.class);
    MonthlyReportAccess access = mock(MonthlyReportAccess.class);
    MonthlyReportEntity expired = report("task", "FAILED", "GENERATE", NOW.minusSeconds(1));
    when(reports.lock(expired.getId())).thenReturn(expired);
    when(access.require()).thenReturn(actor());
    MonthlyReportTaskLifecycle lifecycle = lifecycle(reports, access);

    assertThatThrownBy(() -> lifecycle.requireRetryAllowed(
        task("task", expired.getId(), MonthlyReportConstants.GENERATE_TASK, true)))
        .isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code())
        .isEqualTo("MONTHLY_REPORT_NOT_RETRYABLE");
    expired.setExpiresAt(NOW.plusSeconds(3600));
    assertThatThrownBy(() -> lifecycle.requireRetryAllowed(
        task("task", expired.getId(), MonthlyReportConstants.GENERATE_TASK, false)))
        .isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code())
        .isEqualTo("MONTHLY_REPORT_NOT_RETRYABLE");
    verify(reports, never()).updateById(expired);
  }

  private static MonthlyReportTaskLifecycle lifecycle(
      MonthlyReportService reports, MonthlyReportAccess access) {
    MonthlyReportTaskLifecycle lifecycle = new MonthlyReportTaskLifecycle();
    ReflectionTestUtils.setField(lifecycle, "reports", reports);
    ReflectionTestUtils.setField(lifecycle, "access", access);
    ReflectionTestUtils.setField(lifecycle, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    return lifecycle;
  }

  private static MonthlyReportEntity report(
      String taskId, String status, String phase, Instant expiresAt) {
    MonthlyReportEntity row = new MonthlyReportEntity();
    row.setId("R".repeat(26));
    row.setTaskId(taskId);
    row.setStatus(status);
    row.setPhase(phase);
    row.setExpiresAt(expiresAt);
    return row;
  }

  private static TaskSnapshot task(String id, String resourceId, String type, boolean retryable) {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn(id);
    when(task.resourceId()).thenReturn(resourceId);
    when(task.taskType()).thenReturn(type);
    when(task.retryable()).thenReturn(retryable);
    return task;
  }

  private static CurrentActorSnapshot actor() {
    return new CurrentActorSnapshot("U".repeat(26), "负责人", "hr@example.test", "D".repeat(26),
        Set.of(), Set.of(), Set.of(), Set.of("HR_DIRECTOR"),
        Set.of(MonthlyReportConstants.PERMISSION), true, false);
  }
}
