package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportProcessingManager;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportTransactionManager;
import com.ezaigc.hr.reporting.monthlyreport.application.provider.MonthlyReportFileProvider;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportFileService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MonthlyReportCleanupTest {
  @Test
  void failedFirstBatchDoesNotStarveLaterFilesAndWrapsForRetry() throws IOException {
    Instant now = Instant.parse("2026-09-14T00:00:00Z");
    MonthlyReportProcessingManager manager = new MonthlyReportProcessingManager();
    MonthlyReportFileService files = mock(MonthlyReportFileService.class);
    MonthlyReportFileProvider provider = mock(MonthlyReportFileProvider.class);
    ReflectionTestUtils.setField(manager, "fileProvider", provider);
    ReflectionTestUtils.setField(manager, "files", files);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    List<MonthlyReportFileEntity> blocked = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      MonthlyReportFileEntity file = new MonthlyReportFileEntity();
      file.setId(String.format("%02d", i));
      file.setExpiresAt(now.minusSeconds(1));
      file.setObjectKey("synthetic/" + file.getId());
      blocked.add(file);
      doThrow(new IOException("temporary outage")).when(provider).delete(file.getObjectKey());
    }
    MonthlyReportFileEntity later = new MonthlyReportFileEntity();
    later.setId("20"); later.setExpiresAt(now); later.setObjectKey("synthetic/later");
    when(files.expiredAfter(now, null, null, 20)).thenReturn(blocked);
    when(files.expiredAfter(now, now.minusSeconds(1), "19", 20)).thenReturn(List.of(later));
    when(files.expiredAfter(now, now, "20", 20)).thenReturn(List.of());
    when(files.updateById(later)).thenReturn(true);

    manager.cleanup();
    manager.cleanup();
    assertThat(later.isDeleted()).isTrue();
    verify(provider).delete(later.getObjectKey());
    manager.cleanup();
    verify(provider, times(2)).delete(blocked.getFirst().getObjectKey());
  }
  @Test
  void failedObjectDeletionRemainsEligibleAndIsRetriedOnTheNextCleanup() throws IOException {
    Instant now = Instant.parse("2026-09-14T00:00:00Z");
    MonthlyReportProcessingManager manager = new MonthlyReportProcessingManager();
    MonthlyReportFileService files = mock(MonthlyReportFileService.class);
    MonthlyReportFileProvider provider = mock(MonthlyReportFileProvider.class);
    MonthlyReportFileEntity file = new MonthlyReportFileEntity();
    file.setId("file");
    file.setObjectKey("synthetic/report.docx");
    file.setExpiresAt(now);
    ReflectionTestUtils.setField(manager, "transactions", mock(MonthlyReportTransactionManager.class));
    ReflectionTestUtils.setField(manager, "fileProvider", provider);
    ReflectionTestUtils.setField(manager, "files", files);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    when(files.expiredAfter(now, null, null, 20)).thenReturn(List.of(file));
    when(files.expiredAfter(now, now, "file", 20)).thenReturn(List.of());
    when(files.updateById(file)).thenReturn(true);
    doThrow(new IOException("temporary storage outage")).doNothing().when(provider).delete(file.getObjectKey());

    manager.cleanup();
    assertThat(file.isDeleted()).isFalse();
    verify(files, never()).updateById(file);
    manager.cleanup();
    assertThat(file.isDeleted()).isTrue();
    verify(files).updateById(file);
    verify(provider, times(2)).delete(file.getObjectKey());
  }
}
