package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.reporting.monthlyreport.application.provider.MonthlyReportFileProvider;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.infrastructure.MonthlyReportFileAdapter;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

class MonthlyReportFileAdapterTest {
    @TempDir Path temporaryDirectory;
    private final MonthlyReportFileAdapter adapter = new MonthlyReportFileAdapter();
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);

    MonthlyReportFileAdapterTest() {
        var properties = new MonthlyReportProperties();
        properties.setMaxFileBytes(16);
        ReflectionTestUtils.setField(adapter, "properties", properties);
        ReflectionTestUtils.setField(adapter, "storage", storage);
    }

    @Test
    void hashingKeepsKnownDigestsAndRejectsNull() {
        assertThat(MonthlyReportFileProvider.hash(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(MonthlyReportFileProvider.hash("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(MonthlyReportFileProvider.hash("招聘".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("e671efe43010439acc6abde702263df9aff001643e7a103c1fcc821f3a12ab1c");
        assertThat(MonthlyReportFileProvider.hash(new byte[] {1, 2, 3}))
                .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThatThrownBy(() -> MonthlyReportFileProvider.hash(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void stagingHashesInputAndDeletesLocalCopyOnClose() throws IOException {
        byte[] content = {1, 2, 3};
        Path path;
        try (var staged = adapter.stage(new MockMultipartFile("offerFile", "offer.xlsx", "application/octet-stream", content))) {
            path = staged.path();
            assertThat(Files.readAllBytes(path)).isEqualTo(content);
            assertThat(staged.hash()).isEqualTo(MonthlyReportFileProvider.hash(content));
        }
        assertThat(path).doesNotExist();
    }

    @Test
    void streamLimitAppliesEvenIfMultipartSizeIsFalse() throws IOException {
        MultipartFile forged = mock(MultipartFile.class);
        when(forged.getOriginalFilename()).thenReturn("offer.xlsx");
        when(forged.getSize()).thenReturn(1L);
        when(forged.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[17]));
        assertThatThrownBy(() -> adapter.stage(forged)).isInstanceOf(IOException.class);
    }

    @Test
    void changedStoredInputFailsIntegrityCheck() throws IOException {
        when(storage.open("private-key")).thenReturn(new ByteArrayInputStream(new byte[] {1, 2}));
        assertThatThrownBy(() -> adapter.fetch("private-key", MonthlyReportFileProvider.hash(new byte[] {1, 3}), 2))
                .isInstanceOf(IOException.class).hasMessageContaining("完整性");
    }

    @Test
    void fetchKeepsReadFailureWhenTemporaryCleanupAlsoFails() throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "input-", ".xlsx");
        IOException original = new IOException("original storage failure");
        IOException cleanup = new IOException("cleanup failure");
        when(storage.open("private-key")).thenThrow(original);
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.createTempFile("hr-monthly-input-", ".xlsx")).thenReturn(path);
            files.when(() -> Files.deleteIfExists(path)).thenThrow(cleanup);
            Throwable failure = catchThrowable(() -> adapter.fetch("private-key", "unused", 1));
            assertThat(failure).isSameAs(original);
            assertThat(failure.getSuppressed()).containsExactly(cleanup);
        }
    }

    @Test
    void stageKeepsRuntimeFailureWhenTemporaryCleanupAlsoFails() throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "upload-", ".xlsx");
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("offer.xlsx");
        when(file.getSize()).thenReturn(1L);
        IllegalStateException original = new IllegalStateException("original multipart failure");
        IOException cleanup = new IOException("cleanup failure");
        when(file.getInputStream()).thenThrow(original);
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.createTempFile("hr-monthly-upload-", ".xlsx")).thenReturn(path);
            files.when(() -> Files.deleteIfExists(path)).thenThrow(cleanup);
            Throwable failure = catchThrowable(() -> adapter.stage(file));
            assertThat(failure).isSameAs(original);
            assertThat(failure.getSuppressed()).containsExactly(cleanup);
        }
    }
}
