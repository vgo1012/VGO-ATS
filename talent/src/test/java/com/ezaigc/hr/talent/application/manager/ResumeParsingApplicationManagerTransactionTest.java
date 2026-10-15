package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_PARSE_PROMPT_VERSION;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_SCHEMA_VERSION;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedDocument;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedPage;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelResult;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ParseRequest;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeExtractionPagePersistenceService;
import com.ezaigc.hr.talent.service.ResumeModelRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParseSnapshotPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class ResumeParsingApplicationManagerTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-05T08:00:00Z");
  private static final String TASK_ID = "T".repeat(26);
  private static final String VERSION_ID = "V".repeat(26);
  private static final String EXTRACTION_ID = "E".repeat(26);
  private static final String PAGE_ID = "P".repeat(26);
  private static final String MODEL_RUN_ID = "M".repeat(26);
  private static final String SNAPSHOT_ID = "S".repeat(26);

  @Test
  void preservesControlledFactConflictInRetryableTaskFailure() {
    var manager = new ResumeParsingApplicationManager();
    var tasks = mock(AsyncTaskPort.class);
    var task = mock(TaskSnapshot.class);
    var modelRuns = mock(ResumeModelRunPersistenceService.class);
    var transactionManager = new RecordingTransactionManager();
    when(task.id()).thenReturn(TASK_ID);
    when(modelRuns.failModelRun(eq(MODEL_RUN_ID), any(), eq("HUMAN_CORRECTION_CURRENT"),
        nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class),
        nullable(Long.class), nullable(String.class), anyInt(), anyInt(), anyLong(), eq(NOW)))
        .thenReturn(1);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "resumeModelRunPersistenceService", modelRuns);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(transactionManager));
    var resource = new ResumeResourceEntity(
        VERSION_ID, "resume.pdf", "application/pdf", "hash", "object", "candidate", "owner", "department");
    var failure = new PlatformException(
        HttpStatus.CONFLICT, "HUMAN_CORRECTION_CURRENT", "当前事实已人工更正，请继续人工更正，或上传新简历版本", true);

    ReflectionTestUtils.invokeMethod(
        manager, "handleFactPersistenceFailure", task, resource, MODEL_RUN_ID, null, null, failure);

    verify(tasks).fail(task, "HUMAN_CORRECTION_CURRENT", "当前事实已人工更正，请继续人工更正，或上传新简历版本", false);
  }

  @Test
  void reusesOnlyTheCurrentAiSnapshotFromTheSameResumeVersion() {
    var manager = new ResumeParsingApplicationManager();
    var tasks = mock(AsyncTaskPort.class);
    var task = mock(TaskSnapshot.class);
    var persistence = mock(ResumeParsingPersistenceService.class);
    var parseSnapshots = mock(ResumeParseSnapshotPersistenceService.class);
    var resumeVersions = mock(ResumeVersionPersistenceService.class);
    var transactionManager = new RecordingTransactionManager();
    var resource = new ResumeResourceEntity(
        VERSION_ID, "resume.pdf", "application/pdf", "hash", "object", "candidate", "owner", "department");
    var cached = new FactSnapshot();
    cached.setId(SNAPSHOT_ID);
    cached.setResumeVersionId(VERSION_ID);
    cached.setSourceType("AI_PARSE");

    when(parseSnapshots.findParseSnapshotByCache("cache-key")).thenReturn("parse-id");
    when(resumeVersions.lockForRouting(VERSION_ID)).thenReturn(VERSION_ID);
    when(tasks.renewLease(task)).thenReturn(true);
    when(persistence.findCurrentFactSnapshotForUpdate(VERSION_ID)).thenReturn(cached);
    when(persistence.findFactSnapshotByParse("parse-id")).thenReturn(cached);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "resumeParseSnapshotPersistenceService", parseSnapshots);
    ReflectionTestUtils.setField(manager, "resumeVersions", resumeVersions);
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(transactionManager));

    Boolean completed = ReflectionTestUtils.invokeMethod(
        manager, "completeFromCurrentCache", task, resource, "cache-key");

    assertThat(completed).isTrue();
    verify(tasks).succeed(task, SNAPSHOT_ID);
    assertThat(transactionManager.committed()).isEqualTo(1);
  }

  @Test
  void rejectsCrossVersionCacheSnapshotWithoutChangingTaskState() {
    var manager = new ResumeParsingApplicationManager();
    var tasks = mock(AsyncTaskPort.class);
    var task = mock(TaskSnapshot.class);
    var persistence = mock(ResumeParsingPersistenceService.class);
    var parseSnapshots = mock(ResumeParseSnapshotPersistenceService.class);
    var resumeVersions = mock(ResumeVersionPersistenceService.class);
    var transactionManager = new RecordingTransactionManager();
    var resource = new ResumeResourceEntity(
        VERSION_ID, "resume.pdf", "application/pdf", "hash", "object", "candidate", "owner", "department");
    var current = new FactSnapshot();
    current.setId(SNAPSHOT_ID);
    current.setResumeVersionId(VERSION_ID);
    current.setSourceType("AI_PARSE");
    var foreign = new FactSnapshot();
    foreign.setId("F".repeat(26));
    foreign.setResumeVersionId("X".repeat(26));
    foreign.setSourceType("AI_PARSE");

    when(parseSnapshots.findParseSnapshotByCache("cache-key")).thenReturn("parse-id");
    when(resumeVersions.lockForRouting(VERSION_ID)).thenReturn(VERSION_ID);
    when(tasks.renewLease(task)).thenReturn(true);
    when(persistence.findCurrentFactSnapshotForUpdate(VERSION_ID)).thenReturn(current);
    when(persistence.findFactSnapshotByParse("parse-id")).thenReturn(foreign);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "resumeParseSnapshotPersistenceService", parseSnapshots);
    ReflectionTestUtils.setField(manager, "resumeVersions", resumeVersions);
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(transactionManager));

    Boolean completed = ReflectionTestUtils.invokeMethod(
        manager, "completeFromCurrentCache", task, resource, "cache-key");

    assertThat(completed).isFalse();
    verify(tasks, never()).succeed(any(), anyString());
    verify(tasks, never()).fail(any(), anyString(), anyString(), eq(false));
  }

  @Test
  void stopsBeforeModelReuseWhenHumanCorrectionBecameCurrent() {
    var manager = new ResumeParsingApplicationManager();
    var tasks = mock(AsyncTaskPort.class);
    var task = mock(TaskSnapshot.class);
    var persistence = mock(ResumeParsingPersistenceService.class);
    var parseSnapshots = mock(ResumeParseSnapshotPersistenceService.class);
    var resumeVersions = mock(ResumeVersionPersistenceService.class);
    var transactionManager = new RecordingTransactionManager();
    var resource = new ResumeResourceEntity(
        VERSION_ID, "resume.pdf", "application/pdf", "hash", "object", "candidate", "owner", "department");
    var correction = new FactSnapshot();
    correction.setId(SNAPSHOT_ID);
    correction.setResumeVersionId(VERSION_ID);
    correction.setSourceType("HUMAN_CORRECTION");

    when(parseSnapshots.findParseSnapshotByCache("cache-key")).thenReturn("parse-id");
    when(resumeVersions.lockForRouting(VERSION_ID)).thenReturn(VERSION_ID);
    when(tasks.renewLease(task)).thenReturn(true);
    when(persistence.findCurrentFactSnapshotForUpdate(VERSION_ID)).thenReturn(correction);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "resumeParseSnapshotPersistenceService", parseSnapshots);
    ReflectionTestUtils.setField(manager, "resumeVersions", resumeVersions);
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(transactionManager));

    Boolean completed = ReflectionTestUtils.invokeMethod(
        manager, "completeFromCurrentCache", task, resource, "cache-key");

    assertThat(completed).isTrue();
    verify(tasks).fail(
        task,
        "HUMAN_CORRECTION_CURRENT",
        "当前事实已人工更正，请继续人工更正，或上传新简历版本",
        false);
    verify(tasks, never()).succeed(any(), anyString());
  }

  @Test
  void cacheKeysAreStableWithinAndIsolatedAcrossResumeVersions() {
    var first = new ResumeResourceEntity(
        VERSION_ID, "resume.pdf", "application/pdf", "hash", "object", "candidate", "owner", "department");
    var second = new ResumeResourceEntity(
        "X".repeat(26), "resume.pdf", "application/pdf", "hash", "object", "candidate", "owner", "department");
    var document = new ExtractedDocument(
        "extractor-v1", "NATIVE_TEXT", 1, 1, 0, 1.0, 1.0,
        List.of(new ExtractedPage(1, "NATIVE_TEXT", "resume", 6, 1.0, 1.0, "page-hash")));

    String firstKey = ReflectionTestUtils.invokeMethod(
        ResumeParsingApplicationManager.class, "parseCacheKey", first, document, "provider", "model");
    String repeatedKey = ReflectionTestUtils.invokeMethod(
        ResumeParsingApplicationManager.class, "parseCacheKey", first, document, "provider", "model");
    String secondKey = ReflectionTestUtils.invokeMethod(
        ResumeParsingApplicationManager.class, "parseCacheKey", second, document, "provider", "model");

    assertThat(firstKey).hasSize(64).isEqualTo(repeatedKey).isNotEqualTo(secondKey);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NATIVE_TEXT", "OCR_TEXT", "MULTIMODAL"})
  void keepsProviderIoOutsideTransactionsAndCommitsLifecycleGroupsAtomically(String route)
      throws Exception {
    ResumeParsingApplicationManager manager = new ResumeParsingApplicationManager();
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(tasks.renewLease(task)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    ResumeParsingPersistenceService persistence = mock(ResumeParsingPersistenceService.class);
    ExtractionRunPersistenceService extractionRuns = mock(ExtractionRunPersistenceService.class);
    ResumeExtractionPagePersistenceService extractionPages =
        mock(ResumeExtractionPagePersistenceService.class);
    ResumeModelRunPersistenceService modelRuns = mock(ResumeModelRunPersistenceService.class);
    ObjectStoragePort storage = mock(ObjectStoragePort.class);
    ResumeDocumentExtractionPort extraction = mock(ResumeDocumentExtractionPort.class);
    ResumeParseModelPort model = mock(ResumeParseModelPort.class);
    RawAiArchivePort archive = mock(RawAiArchivePort.class);
    ResumeParseContractValidator validator = mock(ResumeParseContractValidator.class);
    ResumeFactPersistenceManager facts = mock(ResumeFactPersistenceManager.class);
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();

    ResumeResourceEntity resource =
        new ResumeResourceEntity(
            VERSION_ID,
            "resume.pdf",
            "application/pdf",
            "content-sha256",
            "resumes/version.pdf",
            "C".repeat(26),
            "O".repeat(26),
            "D".repeat(26));
    int fallbackPages = "NATIVE_TEXT".equals(route) ? 0 : 1;
    ExtractedPage page = new ExtractedPage(1, route, "resume text", 11, 1.0, 1.0, "page-hash");
    ExtractedDocument document =
        new ExtractedDocument("extractor-v1", route, 1, 1, fallbackPages, 1.0, 1.0, List.of(page));
    ModelResult result = new ModelResult("provider", "model-v1", "{}", 10, 5, 25);
    ResumeFacts validatedFacts = mock(ResumeFacts.class);
    FactSnapshot snapshot = new FactSnapshot();
    snapshot.setId(SNAPSHOT_ID);

    when(task.id()).thenReturn(TASK_ID);
    when(task.resourceId()).thenReturn(VERSION_ID);
    when(persistence.findResumeResource(VERSION_ID)).thenReturn(resource);
    when(ids.newId()).thenReturn(EXTRACTION_ID, PAGE_ID, MODEL_RUN_ID);
    when(extractionRuns.insertExtractionRun(EXTRACTION_ID, VERSION_ID, TASK_ID, NOW))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(storage.open("resumes/version.pdf"))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new ByteArrayInputStream(new byte[] {1});
            });
    when(extraction.extract(eq("resume.pdf"), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return document;
            });
    when(extractionPages.insertExtractionPages(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              assertThat(invocation.<List<?>>getArgument(0)).singleElement();
              return true;
            });
    when(extractionRuns.completeExtraction(
            eq(EXTRACTION_ID),
            eq("extractor-v1"),
            eq(route),
            eq(1),
            eq(1),
            eq(fallbackPages),
            any(),
            any(),
            eq(NOW)))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(modelRuns.insertModelRun(
            MODEL_RUN_ID, VERSION_ID, EXTRACTION_ID, RESUME_FACT_SCHEMA_VERSION, RESUME_PARSE_PROMPT_VERSION, NOW))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              // 模型运行和事实快照的提示词版本列均为VARCHAR(40) ASCII。
              assertThat(invocation.<String>getArgument(4)).matches("[\\x21-\\x7E]{1,40}");
              return 1;
            });
    when(model.parse(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(invocation.<ParseRequest>getArgument(0).fileName()).isEqualTo("resume.pdf");
              return result;
            });
    when(archive.archive(anyString(), eq("{}")))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new ArchiveResult("ai-raw/resume/result", "a".repeat(64), 128, "v1");
            });
    when(validator.validateModel(eq("{}"), any())).thenReturn(validatedFacts);
    when(modelRuns.completeModelRun(
            eq(MODEL_RUN_ID),
            eq("provider"),
            eq("model-v1"),
            eq("ai-raw/resume/result"),
            eq("a".repeat(64)),
            eq(128L),
            eq("v1"),
            eq(10),
            eq(5),
            eq(25L),
            eq(NOW)))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(facts.storeAiFacts(
            eq(VERSION_ID),
            eq(EXTRACTION_ID),
            eq(MODEL_RUN_ID),
            eq(RESUME_FACT_SCHEMA_VERSION),
            eq(RESUME_PARSE_PROMPT_VERSION),
            anyString(),
            eq(validatedFacts)))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return snapshot;
            });
    when(tasks.succeed(task, SNAPSHOT_ID))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return task;
            });

    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "extractionRunPersistenceService", extractionRuns);
    ReflectionTestUtils.setField(
        manager, "resumeExtractionPagePersistenceService", extractionPages);
    ReflectionTestUtils.setField(manager, "resumeModelRunPersistenceService", modelRuns);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "storage", storage);
    ReflectionTestUtils.setField(manager, "extraction", extraction);
    ReflectionTestUtils.setField(manager, "model", model);
    ReflectionTestUtils.setField(manager, "archive", archive);
    ReflectionTestUtils.setField(manager, "validator", validator);
    ReflectionTestUtils.setField(manager, "factPersistence", facts);
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(transactionManager));

    manager.process(task);

    verify(tasks).succeed(task, SNAPSHOT_ID);
    assertThat(transactionManager.begun()).isEqualTo(4);
    assertThat(transactionManager.committed()).isEqualTo(4);
    assertThat(transactionManager.rolledBack()).isZero();
  }

  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private final AtomicInteger begun = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();
    private final AtomicInteger rolledBack = new AtomicInteger();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun.incrementAndGet();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      committed.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rolledBack.incrementAndGet();
    }

    int begun() {
      return begun.get();
    }

    int committed() {
      return committed.get();
    }

    int rolledBack() {
      return rolledBack.get();
    }
  }
}
