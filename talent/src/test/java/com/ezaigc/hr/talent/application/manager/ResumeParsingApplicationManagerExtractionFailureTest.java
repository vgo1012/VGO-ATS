package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedDocument;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedPage;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelResult;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeExtractionPagePersistenceService;
import com.ezaigc.hr.talent.service.ResumeModelRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class ResumeParsingApplicationManagerExtractionFailureTest {

  private static final String TASK_ID = "T".repeat(26);
  private static final String VERSION_ID = "V".repeat(26);
  private static final String EXTRACTION_ID = "E".repeat(26);
  private static final String PAGE_ID = "P".repeat(26);
  private static final String MODEL_RUN_ID = "M".repeat(26);
  private static final String OBJECT_KEY = "resumes/version.pdf";
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void documentExtractionLimitsArePersistedAsNonRetryableStableFailures() throws Exception {
    assertNonRetryableLimit("DOCUMENT_PAGE_LIMIT", "DOCUMENT_PAGE_LIMIT");
    assertNonRetryableLimit("DOCUMENT_TEXT_LIMIT:4", "DOCUMENT_TEXT_LIMIT");
    assertNonRetryableLimit("DOCUMENT_CONTAINER_LIMIT", "DOCUMENT_CONTAINER_LIMIT");
    assertNonRetryableLimit("DOCUMENT_CONTAINER_INVALID", "DOCUMENT_CONTAINER_INVALID");
    assertNonRetryableLimit("DOCUMENT_PAGE_RENDER_LIMIT:7", "DOCUMENT_PAGE_RENDER_LIMIT");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @ResourceLock("SYSTEM_ERR")
  void extractionFailureLogsHideSensitiveMessagesWithoutChangingFailureClassification(boolean runtimeFailure) throws Exception {
    Exception failure = runtimeFailure
        ? new IllegalStateException("audit-person@example.invalid", new IllegalArgumentException("15500000000"))
        : new IOException("DOCUMENT_PAGE_LIMIT:audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new IOException("test-secret-marker"));
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream original = System.err;
    try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setErr(output);
      assertFailure(failure, runtimeFailure ? "DOCUMENT_EXTRACTION_FAILED" : "DOCUMENT_PAGE_LIMIT", runtimeFailure);
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("resume-extraction-failed", TASK_ID, VERSION_ID,
        "java.lang.IllegalArgumentException", "ResumeParsingApplicationManagerExtractionFailureTest")
        .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void modelIoAndSchemaFailuresScheduleAnotherAttempt(boolean schemaInvalid) throws Exception {
    var manager = new ResumeParsingApplicationManager();
    var tasks = mock(AsyncTaskPort.class);
    var task = mock(TaskSnapshot.class);
    var persistence = mock(ResumeParsingPersistenceService.class);
    var extractionRuns = mock(ExtractionRunPersistenceService.class);
    var extractionPages = mock(ResumeExtractionPagePersistenceService.class);
    var modelRuns = mock(ResumeModelRunPersistenceService.class);
    var storage = mock(ObjectStoragePort.class);
    var extraction = mock(ResumeDocumentExtractionPort.class);
    var model = mock(ResumeParseModelPort.class);
    var archive = mock(RawAiArchivePort.class);
    var validator = new ResumeParseContractValidator();
    var ids = mock(PlatformIdGenerator.class);
    var clock = Clock.fixed(NOW, ZoneOffset.UTC);
    String errorCode = schemaInvalid ? "MODEL_SCHEMA_INVALID" : "MODEL_CALL_FAILED";
    String detail = schemaInvalid ? "模型输出不符合简历事实契约" : "简历解析模型调用失败，请稍后重试";

    when(task.id()).thenReturn(TASK_ID);
    when(task.resourceId()).thenReturn(VERSION_ID);
    when(task.attemptCount()).thenReturn(1);
    when(tasks.renewLease(task)).thenReturn(true);
    when(persistence.findResumeResource(VERSION_ID))
        .thenReturn(new ResumeResourceEntity(VERSION_ID, "resume.pdf", "application/pdf", "hash",
            OBJECT_KEY, "C".repeat(26), "O".repeat(26), "D".repeat(26)));
    when(ids.newId()).thenReturn(EXTRACTION_ID, PAGE_ID, MODEL_RUN_ID);
    when(extractionRuns.insertExtractionRun(EXTRACTION_ID, VERSION_ID, TASK_ID, NOW)).thenReturn(1);
    when(storage.open(OBJECT_KEY)).thenReturn(new ByteArrayInputStream(new byte[] {1}));
    when(extraction.extract(eq("resume.pdf"), any())).thenReturn(new ExtractedDocument(
        "extractor-v1", "NATIVE_TEXT", 1, 1, 0, 1.0, 1.0,
        List.of(new ExtractedPage(1, "NATIVE_TEXT", "resume text", 11, 1.0, 1.0, "page-hash"))));
    when(extractionPages.insertExtractionPages(any())).thenReturn(true);
    when(extractionRuns.completeExtraction(eq(EXTRACTION_ID), any(), any(), eq(1), eq(1), eq(0),
        any(), any(), eq(NOW))).thenReturn(1);
    when(modelRuns.insertModelRun(eq(MODEL_RUN_ID), eq(VERSION_ID), eq(EXTRACTION_ID),
        any(), any(), eq(NOW))).thenReturn(1);
    when(modelRuns.failModelRun(eq(MODEL_RUN_ID), any(), eq(errorCode), any(), any(), any(),
        any(), any(), any(), anyInt(), anyInt(), anyLong(), eq(NOW)))
        .thenReturn(1);
    if (schemaInvalid) {
      when(model.parse(any())).thenReturn(new ModelResult("provider", "model", "{}", 1, 1, 1));
      when(archive.archive(any(), eq("{}"))).thenReturn(new ArchiveResult("raw", "hash", 2, "v1"));
    } else {
      when(model.parse(any())).thenThrow(new IllegalStateException("synthetic network I/O"));
    }
    ReflectionTestUtils.setField(validator, "objectMapper", new ObjectMapper());
    ReflectionTestUtils.setField(validator, "clock", clock);
    ReflectionTestUtils.setField(manager, "clock", clock);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "extractionRunPersistenceService", extractionRuns);
    ReflectionTestUtils.setField(manager, "resumeExtractionPagePersistenceService", extractionPages);
    ReflectionTestUtils.setField(manager, "resumeModelRunPersistenceService", modelRuns);
    ReflectionTestUtils.setField(manager, "storage", storage);
    ReflectionTestUtils.setField(manager, "extraction", extraction);
    ReflectionTestUtils.setField(manager, "model", model);
    ReflectionTestUtils.setField(manager, "archive", archive);
    ReflectionTestUtils.setField(manager, "validator", validator);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(new TestTransactionManager()));

    manager.process(task);

    var retryAt = ArgumentCaptor.forClass(Instant.class);
    verify(tasks).failAndRetryAt(eq(task), eq(errorCode), eq(detail), retryAt.capture());
    assertThat(retryAt.getValue()).isBetween(NOW.plusMillis(2_500), NOW.plusSeconds(5));
  }

  private static void assertNonRetryableLimit(String extractionMessage, String expectedCode)
      throws Exception {
    assertFailure(new IOException(extractionMessage), expectedCode, false);
  }

  private static void assertFailure(Exception failure, String expectedCode, boolean retryable) throws Exception {
    ResumeParsingApplicationManager manager = new ResumeParsingApplicationManager();
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(tasks.renewLease(task)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    ResumeParsingPersistenceService persistence = mock(ResumeParsingPersistenceService.class);
    ExtractionRunPersistenceService extractionRuns = mock(ExtractionRunPersistenceService.class);
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    ResumeDocumentExtractionPort extraction = mock(ResumeDocumentExtractionPort.class);
    when(task.id()).thenReturn(TASK_ID);
    when(task.resourceId()).thenReturn(VERSION_ID);
    when(task.attemptCount()).thenReturn(1);
    when(persistence.findResumeResource(VERSION_ID))
        .thenReturn(
            new ResumeResourceEntity(
                VERSION_ID,
                "resume.pdf",
                "application/pdf",
                "sha256",
                OBJECT_KEY,
                "C".repeat(26),
                "O".repeat(26),
                "D".repeat(26)));
    when(ids.newId()).thenReturn(EXTRACTION_ID);
    when(extractionRuns.insertExtractionRun(EXTRACTION_ID, VERSION_ID, TASK_ID, NOW))
        .thenReturn(1);
    when(extractionRuns.failExtraction(EXTRACTION_ID, expectedCode, NOW)).thenReturn(1);
    when(storage.open(OBJECT_KEY)).thenReturn(new ByteArrayInputStream(new byte[] {1}));
    when(extraction.extract(eq("resume.pdf"), any()))
        .thenThrow(failure);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "extractionRunPersistenceService", extractionRuns);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "storage", storage);
    ReflectionTestUtils.setField(manager, "extraction", extraction);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(new TestTransactionManager()));

    manager.process(task);

    verify(extractionRuns).failExtraction(EXTRACTION_ID, expectedCode, NOW);
    String detail = "简历正文提取失败，请检查文件质量或稍后重试";
    if (retryable) {
      var retryAt = ArgumentCaptor.forClass(Instant.class);
      verify(tasks).failAndRetryAt(eq(task), eq(expectedCode), eq(detail), retryAt.capture());
      assertThat(retryAt.getValue()).isBetween(NOW.plusMillis(2_500), NOW.plusSeconds(5));
      verify(tasks, never()).fail(eq(task), eq(expectedCode), eq(detail), eq(true));
    } else {
      var actionableDetail = ArgumentCaptor.forClass(String.class);
      verify(tasks).fail(eq(task), eq(expectedCode), actionableDetail.capture(), eq(false));
      assertThat(actionableDetail.getValue()).contains("重新导出").doesNotContain("稍后重试");
      verify(tasks, never()).failAndRetryAt(eq(task), eq(expectedCode), any(), any());
    }
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
