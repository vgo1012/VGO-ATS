package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportApplicationManager;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportTransactionManager;
import com.ezaigc.hr.reporting.monthlyreport.application.provider.MonthlyReportFileProvider;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportAccess;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportFileService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class MonthlyReportDownloadTest {
  @ParameterizedTest
  @ValueSource(strings = {"MONTHLY_REPORT_FORBIDDEN", "MONTHLY_REPORT_NOT_FOUND"})
  void closeIoFailureDoesNotReplaceDownloadAuditRejection(String code) throws IOException {
    Fixture fixture = new Fixture();
    PlatformException primary = "MONTHLY_REPORT_FORBIDDEN".equals(code)
        ? PlatformExceptions.forbidden(code, "权限已撤销")
        : PlatformExceptions.notFound(code, "报告已删除");
    IOException cleanup = new IOException("关闭对象流失败");
    doThrow(primary).when(fixture.transactions).downloadAudit(fixture.id);
    doThrow(cleanup).when(fixture.stream).close();

    assertThatThrownBy(() -> fixture.manager.download(fixture.id))
        .isSameAs(primary)
        .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(cleanup));
    verify(fixture.stream).close();
  }

  @Test
  void closeRuntimeFailureDoesNotReplaceDownloadStateRejection() throws IOException {
    Fixture fixture = new Fixture();
    PlatformException primary = PlatformExceptions.conflict(
        "MONTHLY_REPORT_NOT_COMPLETE", "报告状态已变化");
    IllegalStateException cleanup = new IllegalStateException("关闭对象流失败");
    doThrow(primary).when(fixture.transactions).downloadAudit(fixture.id);
    doThrow(cleanup).when(fixture.stream).close();

    assertThatThrownBy(() -> fixture.manager.download(fixture.id))
        .isSameAs(primary)
        .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(cleanup));
    verify(fixture.stream).close();
  }

  @Test
  void successfulCleanupPreservesAuditFailure() throws IOException {
    Fixture fixture = new Fixture();
    PlatformException primary = PlatformExceptions.forbidden(
        "MONTHLY_REPORT_FORBIDDEN", "权限已撤销");
    doThrow(primary).when(fixture.transactions).downloadAudit(fixture.id);

    assertThatThrownBy(() -> fixture.manager.download(fixture.id))
        .isSameAs(primary)
        .satisfies(error -> assertThat(error.getSuppressed()).isEmpty());
    verify(fixture.stream).close();
  }

  @Test
  void sameAuditAndCleanupExceptionDoesNotCauseSelfSuppression() throws IOException {
    Fixture fixture = new Fixture();
    PlatformException primary = PlatformExceptions.forbidden(
        "MONTHLY_REPORT_FORBIDDEN", "权限已撤销");
    doThrow(primary).when(fixture.transactions).downloadAudit(fixture.id);
    doThrow(primary).when(fixture.stream).close();

    assertThatThrownBy(() -> fixture.manager.download(fixture.id))
        .isSameAs(primary)
        .satisfies(error -> assertThat(error.getSuppressed()).isEmpty());
    verify(fixture.stream).close();
  }

  @Test
  void successfulDownloadTransfersOpenStreamToCallerAfterAudit() throws IOException {
    Fixture fixture = new Fixture();

    var response = fixture.manager.download(fixture.id);

    assertThat(response.stream()).isSameAs(fixture.stream);
    assertThat(response.fileName()).isEqualTo("report.docx");
    assertThat(response.sizeBytes()).isEqualTo(5);
    verify(fixture.transactions).downloadAudit(fixture.id);
    verify(fixture.stream, never()).close();
  }

  @Test
  void objectOpenFailureRetainsStorageUnavailableResponseWithoutAudit() throws IOException {
    Fixture fixture = new Fixture();
    when(fixture.provider.open("private/report")).thenThrow(new IOException("打开对象失败"));

    assertThatThrownBy(() -> fixture.manager.download(fixture.id))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MONTHLY_REPORT_STORAGE_UNAVAILABLE"));
    verify(fixture.transactions, never()).downloadAudit(fixture.id);
    verifyNoInteractions(fixture.stream);
  }

  @Test
  void initialAuthorizationFailureDoesNotReadReportOrOpenStorage() throws IOException {
    Fixture fixture = new Fixture();
    PlatformException primary = PlatformExceptions.forbidden(
        "MONTHLY_REPORT_FORBIDDEN", "无权下载");
    when(fixture.access.require()).thenThrow(primary);

    assertThatThrownBy(() -> fixture.manager.download(fixture.id)).isSameAs(primary);
    verifyNoInteractions(fixture.transactions, fixture.provider, fixture.files, fixture.stream);
  }

  private static final class Fixture {
    private final String id = "R".repeat(26);
    private final MonthlyReportApplicationManager manager = new MonthlyReportApplicationManager();
    private final MonthlyReportAccess access = mock(MonthlyReportAccess.class);
    private final MonthlyReportTransactionManager transactions = mock(MonthlyReportTransactionManager.class);
    private final MonthlyReportFileProvider provider = mock(MonthlyReportFileProvider.class);
    private final MonthlyReportFileService files = mock(MonthlyReportFileService.class);
    private final InputStream stream = mock(InputStream.class);

    private Fixture() throws IOException {
      Instant now = Instant.parse("2026-09-14T00:00:00Z");
      ReflectionTestUtils.setField(manager, "access", access);
      ReflectionTestUtils.setField(manager, "transactions", transactions);
      ReflectionTestUtils.setField(manager, "fileProvider", provider);
      ReflectionTestUtils.setField(manager, "files", files);
      ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
      MonthlyReportEntity row = new MonthlyReportEntity();
      row.setId(id);
      row.setStatus("SUCCEEDED");
      row.setExpiresAt(now.plusSeconds(3600));
      when(transactions.require(id)).thenReturn(row);
      MonthlyReportFileEntity file = new MonthlyReportFileEntity();
      file.setId("F".repeat(26));
      file.setReportId(id);
      file.setFileKind("REPORT");
      file.setFileName("report.docx");
      file.setObjectKey("private/report");
      file.setSizeBytes(5);
      when(files.forReport(id)).thenReturn(List.of(file));
      when(provider.open("private/report")).thenReturn(stream);
    }
  }
}
