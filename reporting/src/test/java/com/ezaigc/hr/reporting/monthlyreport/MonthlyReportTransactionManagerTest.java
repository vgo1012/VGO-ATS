package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportTransactionManager;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportAccess;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.DemandSummaryDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportDiagnosticDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.OfferSummaryDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportDiagnosticSeverity;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportFileService;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.reporting.service.AuditService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class MonthlyReportTransactionManagerTest {
  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

  @Test
  void deleteClearsSnapshotExpiresFilesAndAuditsOnceWithTaskFirstLocking() {
    Fixture fixture = fixture();
    MonthlyReportEntity row = report("task", "SUCCEEDED", "GENERATE", 7);
    row.setSnapshotJson("sensitive snapshot");
    row.setManagementRemarks("management note");
    MonthlyReportFileEntity input = file(row.getId(), "OFFER");
    MonthlyReportFileEntity output = file(row.getId(), "REPORT");
    MonthlyReportFileEntity removed = file(row.getId(), "DEMAND");
    removed.setDeleted(true);
    when(fixture.reports.getById(row.getId())).thenReturn(row);
    when(fixture.reports.lock(row.getId())).thenReturn(row);
    when(fixture.files.forReport(row.getId())).thenReturn(List.of(input, output, removed));
    when(fixture.files.updateById(any(MonthlyReportFileEntity.class))).thenReturn(true);
    when(fixture.reports.updateById(row)).thenReturn(true);
    when(fixture.audits.save(any())).thenReturn(true);

    fixture.manager.delete(row.getId(), 7, "重复导入");
    fixture.manager.delete(row.getId(), 7, "重复请求");

    assertThat(row.getStatus()).isEqualTo("DELETED");
    assertThat(row.getSnapshotJson()).isNull();
    assertThat(row.getManagementRemarks()).isNull();
    assertThat(row.getExpiresAt()).isEqualTo(NOW);
    assertThat(input.getExpiresAt()).isEqualTo(NOW);
    assertThat(output.getExpiresAt()).isEqualTo(NOW);
    assertThat(input.isDeleted()).isFalse();
    verify(fixture.files, never()).updateById(removed);
    verify(fixture.reports).updateById(row);
    verify(fixture.audits).save(argThat(event ->
        "MONTHLY_REPORT_DELETE".equals(event.getEventType()) && "重复导入".equals(event.getReason())));
    InOrder ordered = inOrder(fixture.taskService, fixture.reports, fixture.files);
    ordered.verify(fixture.reports).getById(row.getId());
    ordered.verify(fixture.taskService).lockTask("task");
    ordered.verify(fixture.reports).lock(row.getId());
    ordered.verify(fixture.files).forReport(row.getId());
  }

  @Test
  void deleteRejectsActiveAndStaleVersionsBeforeChangingAnything() {
    for (String state : List.of("UPLOADING", "VALIDATING", "QUEUED", "RUNNING", "CANCELLING")) {
      Fixture fixture = fixture();
      MonthlyReportEntity row = report("task", state, "GENERATE", 7);
      when(fixture.reports.getById(row.getId())).thenReturn(row);
      when(fixture.reports.lock(row.getId())).thenReturn(row);
      assertThatThrownBy(() -> fixture.manager.delete(row.getId(), 7, "清理"))
          .isInstanceOf(PlatformException.class)
          .extracting(error -> ((PlatformException) error).code()).isEqualTo("MONTHLY_REPORT_PROCESSING");
      verifyNoInteractions(fixture.files, fixture.audits);
      verify(fixture.reports, never()).updateById(row);
    }
    Fixture fixture = fixture();
    MonthlyReportEntity row = report(null, "READY", "VALIDATE", 7);
    when(fixture.reports.getById(row.getId())).thenReturn(row);
    when(fixture.reports.lock(row.getId())).thenReturn(row);
    assertThatThrownBy(() -> fixture.manager.delete(row.getId(), 6, "清理"))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(fixture.files, fixture.audits);
    verify(fixture.reports, never()).updateById(row);
  }

  @Test
  void deletedReportsAreUnavailableToReadersAndCommands() {
    Fixture fixture = fixture();
    MonthlyReportEntity row = report("task", "DELETED", "GENERATE", 8);
    when(fixture.reports.getById(row.getId())).thenReturn(row);
    when(fixture.reports.lock(row.getId())).thenReturn(row);
    List<Runnable> actions = List.of(() -> fixture.manager.require(row.getId()),
        () -> fixture.manager.downloadAudit(row.getId()),
        () -> fixture.manager.generate(row.getId(), 8, "key", "hash", "", true),
        () -> fixture.manager.retry(row.getId(), 8, "reason", "key"),
        () -> fixture.manager.cancel(row.getId(), 8, "reason", "key"));
    for (Runnable action : actions) assertThatThrownBy(action::run).isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code()).isEqualTo("MONTHLY_REPORT_NOT_FOUND");
    verifyNoInteractions(fixture.files, fixture.tasks, fixture.audits);
  }

  @Test
  void deleteChecksAuthorizationBeforeLookingUpEvenAnAlreadyDeletedReport() {
    Fixture fixture = fixture();
    when(fixture.access.require()).thenThrow(new IllegalStateException("denied"));
    assertThatThrownBy(() -> fixture.manager.delete("report", 7, "清理")).hasMessage("denied");
    verifyNoInteractions(fixture.reports, fixture.files, fixture.taskService, fixture.audits);
  }

  @Test
  void validatedAndPublishDoNotWriteAnythingAfterLeaseLoss() {
    Fixture fixture = fixture();
    TaskSnapshot validationTask = task("validate-task", "report", MonthlyReportConstants.VALIDATE_TASK);
    TaskSnapshot generationTask = task("generate-task", "report", MonthlyReportConstants.GENERATE_TASK);
    when(fixture.tasks.renewLease(validationTask)).thenReturn(false);
    when(fixture.tasks.renewLease(generationTask)).thenReturn(false);

    assertThat(fixture.manager.validated(validationTask, snapshot(false))).isFalse();
    assertThat(fixture.manager.publish(generationTask, "file")).isFalse();

    verify(fixture.tasks, never()).succeed(any(), any());
    verifyNoInteractions(fixture.reports, fixture.files, fixture.audits);
  }

  @Test
  void publishUpdatesOutputCompletesTaskReportAndAuditInOneOrderedTransaction() {
    Fixture fixture = fixture();
    TaskSnapshot task = task("task", "report", MonthlyReportConstants.GENERATE_TASK);
    MonthlyReportEntity report = report("task", "QUEUED", "GENERATE", 7);
    MonthlyReportFileEntity output = file(report.getId(), "REPORT_TEMP");
    when(fixture.tasks.renewLease(task)).thenReturn(true);
    when(fixture.reports.lock(report.getId())).thenReturn(report);
    when(fixture.files.getById("file")).thenReturn(output);
    when(fixture.files.updateById(output)).thenReturn(true);
    when(fixture.reports.updateById(report)).thenReturn(true);
    when(fixture.audits.save(any())).thenReturn(true);

    assertThat(fixture.manager.publish(task, "file")).isTrue();
    assertThat(output.getFileKind()).isEqualTo("REPORT");
    assertThat(output.getExpiresAt()).isEqualTo(report.getExpiresAt());
    assertThat(report.getStatus()).isEqualTo("SUCCEEDED");
    InOrder ordered = inOrder(fixture.files, fixture.tasks, fixture.reports, fixture.audits);
    ordered.verify(fixture.files).updateById(output);
    ordered.verify(fixture.tasks).succeed(task, report.getId());
    ordered.verify(fixture.reports).updateById(report);
    ordered.verify(fixture.audits).save(any());
  }

  @Test
  void generateRejectsUnacknowledgedWarningsBeforeEnqueueing() throws Exception {
    Fixture fixture = fixture();
    MonthlyReportEntity report = report(null, "READY", "VALIDATE", 4);
    report.setSnapshotJson(fixture.json.writeValueAsString(snapshot(true)));
    when(fixture.reports.lock(report.getId())).thenReturn(report);

    assertThatThrownBy(() -> fixture.manager.generate(report.getId(), 4, "generation", "fingerprint", "", false))
        .isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code())
        .isEqualTo("MONTHLY_REPORT_WARNINGS_REQUIRED");
    verify(fixture.tasks, never()).enqueue(any());
    verify(fixture.reports, never()).updateById(report);
  }

  @Test
  void generateRejectsLegacyReadySnapshotWithNoOffersBeforeEnqueueing() {
    Fixture fixture = fixture();
    MonthlyReportEntity report = report(null, "READY", "VALIDATE", 4);
    report.setSnapshotJson(fixture.json.writeValueAsString(snapshot(false, 0)));
    when(fixture.reports.lock(report.getId())).thenReturn(report);

    assertThatThrownBy(() -> fixture.manager.generate(report.getId(), 4, "generation", "fingerprint", "", true))
        .isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code())
        .isEqualTo("MONTHLY_REPORT_NO_OFFERS");
    verifyNoInteractions(fixture.tasks, fixture.audits);
    verify(fixture.reports, never()).updateById(report);
  }

  @Test
  void generateWithTheSameGenerationKeyAndFingerprintIsIdempotent() {
    Fixture fixture = fixture();
    MonthlyReportEntity report = report(null, "READY", "VALIDATE", 4);
    report.setGenerationKey("generation");
    report.setGenerationFingerprint("fingerprint");
    when(fixture.reports.lock(report.getId())).thenReturn(report);

    fixture.manager.generate(report.getId(), 999, "generation", "fingerprint", "changed", true);

    verifyNoInteractions(fixture.tasks, fixture.audits);
    verify(fixture.reports, never()).updateById(report);
  }

  @Test
  void cancelRejectsAStaleVersionBeforeCancellingTheTask() {
    Fixture fixture = fixture();
    MonthlyReportEntity initial = report("task", "QUEUED", "GENERATE", 5);
    MonthlyReportEntity current = report("task", "QUEUED", "GENERATE", 6);
    when(fixture.reports.getById(initial.getId())).thenReturn(initial);
    when(fixture.reports.lock(initial.getId())).thenReturn(current);

    assertThatThrownBy(() -> fixture.manager.cancel(initial.getId(), 5, "用户取消", "cancel-key"))
        .isInstanceOf(PlatformException.class)
        .extracting(error -> ((PlatformException) error).code())
        .isEqualTo("VERSION_CONFLICT");
    verify(fixture.taskService).lockTask("task");
    verify(fixture.tasks, never()).cancel(any(), any());
    verifyNoInteractions(fixture.audits);
  }

  @Test
  void validationMustNotAcceptAGenerationTask() {
    Fixture fixture = fixture();
    TaskSnapshot generationTask = task("task", "report", MonthlyReportConstants.GENERATE_TASK);
    MonthlyReportEntity report = report("task", "QUEUED", "GENERATE", 4);
    when(fixture.tasks.renewLease(generationTask)).thenReturn(true);
    when(fixture.reports.lock(report.getId())).thenReturn(report);

    assertThat(fixture.manager.validated(generationTask, snapshot(false))).isFalse();
    verify(fixture.tasks, never()).succeed(any(), any());
  }

  @Test
  void publishingMustNotAcceptAValidationTask() {
    Fixture fixture = fixture();
    TaskSnapshot validationTask = task("task", "report", MonthlyReportConstants.VALIDATE_TASK);
    MonthlyReportEntity report = report("task", "VALIDATING", "VALIDATE", 4);
    MonthlyReportFileEntity output = file(report.getId(), "REPORT_TEMP");
    when(fixture.tasks.renewLease(validationTask)).thenReturn(true);
    when(fixture.reports.lock(report.getId())).thenReturn(report);
    when(fixture.files.getById(output.getId())).thenReturn(output);

    assertThat(fixture.manager.publish(validationTask, output.getId())).isFalse();
    verify(fixture.files, never()).updateById(any());
    verify(fixture.tasks, never()).succeed(any(), any());
  }

  private static Fixture fixture() {
    Fixture fixture = new Fixture();
    when(fixture.access.require()).thenReturn(actor());
    ReflectionTestUtils.setField(fixture.manager, "reports", fixture.reports);
    ReflectionTestUtils.setField(fixture.manager, "files", fixture.files);
    ReflectionTestUtils.setField(fixture.manager, "tasks", fixture.tasks);
    ReflectionTestUtils.setField(fixture.manager, "taskService", fixture.taskService);
    ReflectionTestUtils.setField(fixture.manager, "audits", fixture.audits);
    ReflectionTestUtils.setField(fixture.manager, "access", fixture.access);
    ReflectionTestUtils.setField(fixture.manager, "properties", fixture.properties);
    ReflectionTestUtils.setField(fixture.manager, "ids", (PlatformIdGenerator) () -> "A".repeat(26));
    ReflectionTestUtils.setField(fixture.manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(fixture.manager, "json", fixture.json);
    return fixture;
  }

  private static MonthlyReportEntity report(String taskId, String status, String phase, int version) {
    MonthlyReportEntity report = new MonthlyReportEntity();
    report.setId("report");
    report.setTaskId(taskId);
    report.setStatus(status);
    report.setPhase(phase);
    report.setVersion(version);
    report.setOwnerUserId("owner");
    report.setOwnerDepartmentId("department");
    report.setExpiresAt(NOW.plusSeconds(3600));
    return report;
  }

  private static MonthlyReportFileEntity file(String reportId, String kind) {
    MonthlyReportFileEntity file = new MonthlyReportFileEntity();
    file.setId("file");
    file.setReportId(reportId);
    file.setFileKind(kind);
    return file;
  }

  private static TaskSnapshot task(String id, String resourceId, String type) {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn(id);
    when(task.resourceId()).thenReturn(resourceId);
    when(task.taskType()).thenReturn(type);
    when(task.resourceType()).thenReturn(MonthlyReportConstants.RESOURCE);
    return task;
  }

  private static MonthlyReportSnapshotDTO snapshot(boolean warning) {
    return snapshot(warning, 2);
  }

  private static MonthlyReportSnapshotDTO snapshot(boolean warning, int offerCount) {
    return new MonthlyReportSnapshotDTO("rules", "parser", "template", "2026-09", "2026-09-14", "COMPANY",
        true, new OfferSummaryDTO(offerCount, offerCount, new BigDecimal("122.75"), Map.of(), BigDecimal.ZERO, BigDecimal.ONE,
        BigDecimal.ZERO, BigDecimal.ZERO, "2026-09-01", "2026-09-14", Map.of()),
        new DemandSummaryDTO(0, 0, 0, 0, 0, 0), List.of(), List.of(),
        warning ? List.of(new MonthlyReportDiagnosticDTO(MonthlyReportDiagnosticSeverity.WARNING, "W", "OFFER",
            "sheet", 1, "A", "确认", 1)) : List.of(), List.of());
  }

  private static CurrentActorSnapshot actor() {
    return new CurrentActorSnapshot("owner", "负责人", "owner@example.test", "department", Set.of(), Set.of(),
        Set.of(), Set.of("HR_DIRECTOR"), Set.of(MonthlyReportConstants.PERMISSION), true, false);
  }

  private static final class Fixture {
    private final MonthlyReportTransactionManager manager = new MonthlyReportTransactionManager();
    private final MonthlyReportService reports = mock(MonthlyReportService.class);
    private final MonthlyReportFileService files = mock(MonthlyReportFileService.class);
    private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    private final AsyncTaskService taskService = mock(AsyncTaskService.class);
    private final AuditService audits = mock(AuditService.class);
    private final MonthlyReportAccess access = mock(MonthlyReportAccess.class);
    private final MonthlyReportProperties properties = new MonthlyReportProperties();
    private final ObjectMapper json = new ObjectMapper();
  }
}
