package com.ezaigc.hr.recruiting.demand.application.manager;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_FACT_SCHEMA_VERSION;
import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.JD_PARSE_PROMPT_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ModelResult;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ModelUnavailableException;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdAiModelRunEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.JdAiModelRunPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.validator.JdParseContractValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@ResourceLock("SYSTEM_ERR")
class JdParsingApplicationManagerTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-05T07:00:00Z");
  private static final String TASK_ID = "TTTTTTTTTTTTTTTTTTTTTTTTTT";
  private static final String VERSION_ID = "VVVVVVVVVVVVVVVVVVVVVVVVVV";
  private static final String RUN_ID = "RRRRRRRRRRRRRRRRRRRRRRRRRR";
  private static final String SNAPSHOT_ID = "SSSSSSSSSSSSSSSSSSSSSSSSSS";
  private static final String SOURCE_VERSION_ID = "AAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String SOURCE_SNAPSHOT_ID = "BBBBBBBBBBBBBBBBBBBBBBBBBB";

  @Test
  void reusesMatchingSourceSnapshotUnderTheNewJdVersion() {
    SourceCase source = sourceCase("JD text", 1);
    JdFactSnapshotEntity sourceFacts = new JdFactSnapshotEntity();
    sourceFacts.setId(SOURCE_SNAPSHOT_ID);
    sourceFacts.setModelRunId("CCCCCCCCCCCCCCCCCCCCCCCCCC");
    sourceFacts.setSourceType("AI_PARSE");
    sourceFacts.setSchemaVersion(JD_FACT_SCHEMA_VERSION);
    sourceFacts.setFactsJson("{\"professional\":{}}");
    JdAiModelRunEntity sourceRun = new JdAiModelRunEntity();
    sourceRun.setJdVersionId(SOURCE_VERSION_ID);
    sourceRun.setStatus("SUCCEEDED");
    sourceRun.setSchemaVersion(JD_FACT_SCHEMA_VERSION);
    sourceRun.setPromptVersion(JD_PARSE_PROMPT_VERSION);
    sourceRun.setModelProvider("provider");
    sourceRun.setModelVersion("model-v1");
    sourceRun.setRawOutputObjectKey("ai-raw/jd/source/result.json.gz.enc");
    sourceRun.setRawOutputHash("a".repeat(64));
    sourceRun.setRawOutputSize(128L);
    sourceRun.setEncryptionVersion("v1");
    JdFactSnapshotEntity copied = new JdFactSnapshotEntity();
    copied.setId(SNAPSHOT_ID);
    when(source.persistence.findCurrent(SOURCE_VERSION_ID)).thenReturn(sourceFacts);
    when(source.runs.getById(sourceFacts.getModelRunId())).thenReturn(sourceRun);
    when(source.runs.insertModelRun(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    when(source.persistence.completeModelRun(eq(RUN_ID), eq("provider"), eq("model-v1"),
        eq("ai-raw/jd/source/result.json.gz.enc"), eq("a".repeat(64)), eq(128L), eq("v1"),
        eq(0), eq(0), eq(0L), eq(NOW))).thenReturn(1);
    when(source.facts.store(VERSION_ID, RUN_ID, JD_FACT_SCHEMA_VERSION, sourceFacts.getFactsJson(), SOURCE_SNAPSHOT_ID))
        .thenReturn(copied);

    source.manager.process(source.task);

    verify(source.facts).store(VERSION_ID, RUN_ID, JD_FACT_SCHEMA_VERSION, sourceFacts.getFactsJson(), SOURCE_SNAPSHOT_ID);
    verify(source.tasks).succeed(source.task, SNAPSHOT_ID);
    verify(source.model, never()).parse(any());
  }

  @Test
  void waitsForIdenticalSourceFactsBeforeTryingOwnModel() {
    SourceCase source = sourceCase("JD text", 1);
    JdEntity sourceJd = new JdEntity();
    sourceJd.setCurrentVersionNo(1);
    sourceJd.setCurrentParseStatus("RUNNING");
    when(source.catalog.findJd("DDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(sourceJd);

    source.manager.process(source.task);

    verify(source.tasks).failAndRetryAt(eq(source.task), eq("JD_SOURCE_FACTS_PENDING"), anyString(), eq(NOW.plusSeconds(30)));
    verify(source.model, never()).parse(any());
    verify(source.runs, never()).insertModelRun(anyString(), anyString(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void changedBodyUsesOwnModel() {
    SourceCase source = sourceCase("different text", 1);
    when(source.runs.insertModelRun(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    when(source.tasks.fail(eq(source.task), eq("MODEL_RESULT_EMPTY"), anyString(), eq(true))).thenReturn(source.task);
    when(source.task.status()).thenReturn("FAILED");
    when(source.persistence.failModelRun(eq(RUN_ID), anyString(), eq("MODEL_RESULT_EMPTY"), any(), any(), any(),
        any(), any(), any(), anyInt(), anyInt(), anyLong(), eq(NOW))).thenReturn(1);

    source.manager.process(source.task);

    verify(source.model).parse(any());
    verify(source.persistence, never()).findCurrent(SOURCE_VERSION_ID);
  }

  @ParameterizedTest
  @CsvSource({"FAILED,1", "RUNNING,7"})
  void unavailableSourceFactsFallBackToOwnModel(String status, int attempt) {
    SourceCase source = sourceCase("JD text", attempt);
    JdEntity sourceJd = new JdEntity();
    sourceJd.setCurrentVersionNo(1);
    sourceJd.setCurrentParseStatus(status);
    when(source.catalog.findJd("DDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(sourceJd);
    when(source.runs.insertModelRun(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    when(source.tasks.fail(eq(source.task), eq("MODEL_RESULT_EMPTY"), anyString(), eq(true))).thenReturn(source.task);
    when(source.task.status()).thenReturn("FAILED");
    when(source.persistence.failModelRun(eq(RUN_ID), anyString(), eq("MODEL_RESULT_EMPTY"), any(), any(), any(),
        any(), any(), any(), anyInt(), anyInt(), anyLong(), eq(NOW))).thenReturn(1);

    source.manager.process(source.task);

    verify(source.model).parse(any());
    verify(source.tasks, never()).failAndRetryAt(any(), anyString(), anyString(), any());
  }

  private static SourceCase sourceCase(String sourceBody, int attempt) {
    JdParsingApplicationManager manager = new JdParsingApplicationManager();
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);
    JdEntityPersistenceService jds = mock(JdEntityPersistenceService.class);
    DemandCatalogPersistenceService catalog = mock(DemandCatalogPersistenceService.class);
    JdParsingPersistenceService persistence = mock(JdParsingPersistenceService.class);
    JdAiModelRunPersistenceService runs = mock(JdAiModelRunPersistenceService.class);
    JdFactPersistenceManager facts = mock(JdFactPersistenceManager.class);
    JdParseModelPort model = mock(JdParseModelPort.class);
    DemandQueryService queries = mock(DemandQueryService.class);
    JdVersionEntity version = new JdVersionEntity();
    version.setId(VERSION_ID); version.setJdId("JJJJJJJJJJJJJJJJJJJJJJJJJJ");
    version.setTitle("Java 工程师"); version.setRawText("JD text");
    JdVersionEntity sourceVersion = new JdVersionEntity();
    sourceVersion.setId(SOURCE_VERSION_ID); sourceVersion.setJdId("DDDDDDDDDDDDDDDDDDDDDDDDDD");
    sourceVersion.setVersionNo(1); sourceVersion.setTitle("Java 工程师"); sourceVersion.setRawText(sourceBody);
    JdEntity jd = new JdEntity(); jd.setAppliedFromJdVersionId(SOURCE_VERSION_ID);
    when(task.id()).thenReturn(TASK_ID);
    when(task.resourceId()).thenReturn(VERSION_ID);
    when(task.attemptCount()).thenReturn(attempt);
    when(versions.findJdVersion(VERSION_ID)).thenReturn(version);
    when(versions.findJdVersion(SOURCE_VERSION_ID)).thenReturn(sourceVersion);
    when(jds.getById(version.getJdId())).thenReturn(jd);
    when(tasks.renewLease(task)).thenReturn(true);
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> RUN_ID);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "jdEntityPersistenceService", jds);
    ReflectionTestUtils.setField(manager, "catalogPersistenceService", catalog);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "jdAiModelRunPersistenceService", runs);
    ReflectionTestUtils.setField(manager, "factPersistence", facts);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "model", model);
    ReflectionTestUtils.setField(manager, "queries", queries);
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(new RecordingTransactionManager()));
    return new SourceCase(manager, task, tasks, persistence, runs, facts, model, catalog);
  }

  private record SourceCase(JdParsingApplicationManager manager, TaskSnapshot task, AsyncTaskPort tasks,
      JdParsingPersistenceService persistence, JdAiModelRunPersistenceService runs,
      JdFactPersistenceManager facts, JdParseModelPort model, DemandCatalogPersistenceService catalog) {}

  @ParameterizedTest
  @EnumSource(value = Stage.class, names = {"DELETED_BEFORE", "DELETED_DURING"})
  void refusesDeletedJdBeforeModelCallAndBeforePublishingFacts(Stage stage) throws Exception {
    assertProcessing(stage);
  }

  @Test
  void keepsProviderIoOutsideTransactionsAndCommitsPersistenceInShortTransactions()
      throws Exception {
    assertProcessing(Stage.SUCCESS);
  }

  @Test
  void validatesAndArchivesFactsWithoutLegacyEvidenceBeforeSaving() throws Exception {
    assertProcessing(Stage.VALIDATED);
  }

  @ParameterizedTest
  @EnumSource(value = Stage.class, names = {"UNAVAILABLE", "CALL", "ARCHIVE", "PERSISTENCE"})
  void recordsSafeFailuresWithoutChangingRecoveryTransactions(Stage stage) throws Exception {
    assertProcessing(stage);
  }

  @ParameterizedTest
  @EnumSource(value = Stage.class, names = {"UNAVAILABLE", "CALL", "ARCHIVE", "PERSISTENCE"})
  void recordsCancellationWhenItWinsBeforeFailureFinalization(Stage stage) throws Exception {
    assertProcessing(stage, true);
  }

  private void assertProcessing(Stage stage) throws Exception {
    assertProcessing(stage, false);
  }

  private void assertProcessing(Stage stage, boolean cancellationWon) throws Exception {
    Exception failure = switch (stage) {
      case ARCHIVE -> new IOException("audit-person@example.invalid");
      case UNAVAILABLE -> new ModelUnavailableException("audit-person@example.invalid");
      default -> new IllegalStateException("audit-person@example.invalid");
    };
    failure.initCause(new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new RuntimeException("test-secret-marker"));
    JdParsingApplicationManager manager = new JdParsingApplicationManager();
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(tasks.renewLease(task)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);
    DemandQueryService queries = mock(DemandQueryService.class);
    AtomicInteger eligibilityChecks = new AtomicInteger();
    when(queries.requireJdVersionForNewUse(VERSION_ID)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      if (stage == Stage.DELETED_BEFORE || (stage == Stage.DELETED_DURING && eligibilityChecks.incrementAndGet() > 1)) {
        throw new PlatformException(HttpStatus.CONFLICT, "JD_DELETED", "JD 已删除，不能解析", false);
      }
      return new JdVersionSnapshot();
    });
    JdAiModelRunPersistenceService runs = mock(JdAiModelRunPersistenceService.class);
    JdParsingPersistenceService persistence = mock(JdParsingPersistenceService.class);
    JdFactPersistenceManager facts = mock(JdFactPersistenceManager.class);
    JdParseModelPort model = mock(JdParseModelPort.class);
    RawAiArchivePort archive = mock(RawAiArchivePort.class);
    JdParseContractValidator validator = stage == Stage.VALIDATED
        ? new JdParseContractValidator() : mock(JdParseContractValidator.class);
    ObjectMapper json = new ObjectMapper();
    if (stage == Stage.VALIDATED) {
      ReflectionTestUtils.setField(validator, "json", json);
    }
    String rawJson = stage == Stage.VALIDATED ? """
        {"title":null,"summary":null,
         "professional":{"coreSkills":[
           {"id":"skill_java","name":"Java","required":true,"aliases":[]}],
           "roleExperiences":[],"minimumYears":null,"preferredYears":null,
           "responsibilities":[],"industryTags":[],"projectTags":[]},
         "controlledConditions":{"ageMin":null,"ageMax":null,"sex":null,
           "educations":[],"credentials":[],"languages":[]},
         "fulfillmentConditions":{"locations":[],"availability":null,
           "salaryMin":null,"salaryMax":null,"salaryPeriod":null}}
        """ : "{}";
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();

    JdVersionEntity version = new JdVersionEntity();
    version.setId(VERSION_ID);
    version.setTitle("Java 工程师");
    version.setRawText(stage == Stage.VALIDATED ? "Java 工程师" : "JD text");
    JdFactSnapshotEntity snapshot = new JdFactSnapshotEntity();
    snapshot.setId(SNAPSHOT_ID);
    when(task.id()).thenReturn(TASK_ID);
    when(task.resourceId()).thenReturn(VERSION_ID);
    when(task.attemptCount()).thenReturn(stage == Stage.DELETED_BEFORE ? 2 : 1);
    when(versions.findJdVersion(VERSION_ID)).thenReturn(version);
    when(runs.insertModelRun(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(model.parse(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              if (stage == Stage.CALL || stage == Stage.UNAVAILABLE) {
                throw (RuntimeException) failure;
              }
              return new ModelResult("provider", "model-v1", rawJson, 10, 5, 25);
            });
    when(archive.archive(anyString(), eq(rawJson)))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              if (stage == Stage.ARCHIVE) {
                throw failure;
              }
              return new ArchiveResult("ai-raw/jd/result", "a".repeat(64), 128, "v1");
            });
    if (stage != Stage.VALIDATED) {
      when(validator.validateModel("{}", "JD text")).thenReturn(json.readTree("{}"));
    }
    when(persistence.completeModelRun(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyLong(),
            anyString(),
            anyInt(),
            anyInt(),
            anyLong(),
            any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(facts.store(eq(VERSION_ID), eq(RUN_ID), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              if (stage == Stage.PERSISTENCE) {
                throw (RuntimeException) failure;
              }
              return snapshot;
            });
    when(tasks.succeed(task, SNAPSHOT_ID))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return task;
            });

    ReflectionTestUtils.setField(manager, "factPersistence", facts);
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> RUN_ID);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "json", json);
    ReflectionTestUtils.setField(manager, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "queries", queries);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "model", model);
    ReflectionTestUtils.setField(manager, "validator", validator);
    ReflectionTestUtils.setField(manager, "archive", archive);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "jdAiModelRunPersistenceService", runs);
    ReflectionTestUtils.setField(
        manager, "transactionTemplate", new TransactionTemplate(transactionManager));

    String errorCode = switch (stage) {
      case UNAVAILABLE -> "MODEL_UNAVAILABLE";
      case CALL -> "MODEL_CALL_FAILED";
      case ARCHIVE -> "AI_RAW_ARCHIVE_FAILED";
      case PERSISTENCE -> "FACT_PERSISTENCE_FAILED";
      case SUCCESS, VALIDATED -> "";
      case DELETED_BEFORE, DELETED_DURING -> "JD_DELETED";
    };
    boolean deleted = stage == Stage.DELETED_BEFORE || stage == Stage.DELETED_DURING;
    String persistedErrorCode = cancellationWon ? "TASK_CANCELLED" : errorCode;
    when(persistence.failModelRun(eq(RUN_ID), anyString(), eq(persistedErrorCode), any(), any(), any(),
        any(), any(), any(), anyInt(), anyInt(), anyLong(), eq(NOW)))
        .thenAnswer(invocation -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return 1;
        });
    TaskSnapshot outcome = mock(TaskSnapshot.class);
    when(outcome.status()).thenReturn(cancellationWon ? "CANCELLED" : "FAILED");
    when(tasks.fail(eq(task), eq(errorCode), anyString(), eq(!deleted)))
        .thenAnswer(invocation -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return outcome;
        });
    when(tasks.failUnhandled(eq(task), eq(errorCode), anyString(), eq(false)))
        .thenAnswer(invocation -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return outcome;
        });
    PrintStream originalError = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      manager.process(task);
    } finally {
      System.setErr(originalError);
    }
    if (deleted) {
      verify(tasks, never()).succeed(any(), anyString());
      verify(facts, never()).store(anyString(), anyString(), anyString(), anyString());
      if (stage == Stage.DELETED_BEFORE) {
        // 交由共享终结回调清理上一租约尝试遗留的运行记录。
        verify(tasks).failUnhandled(eq(task), eq("JD_DELETED"), anyString(), eq(false));
        verify(model, never()).parse(any());
        verify(runs, never()).insertModelRun(anyString(), anyString(), anyString(), anyString(), anyString(), any());
      } else {
        verify(tasks).fail(eq(task), eq("JD_DELETED"), anyString(), eq(false));
        verify(persistence).failModelRun(eq(RUN_ID), anyString(), eq("JD_DELETED"), any(), any(), any(),
            any(), any(), any(), anyInt(), anyInt(), anyLong(), eq(NOW));
      }
      assertThat(transactionManager.begun()).isEqualTo(stage == Stage.DELETED_BEFORE ? 2 : 3);
      assertThat(transactionManager.committed()).isEqualTo(stage == Stage.DELETED_BEFORE ? 1 : 2);
      assertThat(transactionManager.rolledBack()).isEqualTo(1);
      return;
    }
    if (stage == Stage.VALIDATED) {
      ArgumentCaptor<String> savedJson = ArgumentCaptor.forClass(String.class);
      verify(facts).store(eq(VERSION_ID), eq(RUN_ID), anyString(), savedJson.capture());
      var savedFacts = json.readTree(savedJson.getValue());
      assertThat(savedFacts.at("/professional/coreSkills").size()).isEqualTo(1);
      assertThat(savedFacts.at("/professional/coreSkills/0/name").asString()).isEqualTo("Java");
      assertThat(savedFacts.has("evidence")).isFalse();
      assertThat(savedFacts.at("/professional/minimumYears").isNull()).isTrue();
      assertThat(savedFacts.at("/controlledConditions/educations").size()).isZero();
      verify(archive).archive(anyString(), eq(rawJson));
      verify(tasks).succeed(task, SNAPSHOT_ID);
      verify(tasks, never()).fail(any(), anyString(), anyString(), anyBoolean());
      verify(tasks, never()).failUnhandled(any(), anyString(), anyString(), anyBoolean());
    }
    if (stage != Stage.SUCCESS && stage != Stage.VALIDATED) {
      assertThat(bytes.toString(StandardCharsets.UTF_8))
          .contains(TASK_ID, VERSION_ID, RUN_ID, failure.getClass().getName(),
              "java.lang.IllegalArgumentException", "JdParsingApplicationManagerTransactionTest.java")
          .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
      verify(tasks).fail(eq(task), eq(errorCode), anyString(), eq(true));
      var finalization = inOrder(tasks, persistence);
      finalization.verify(tasks).fail(eq(task), eq(errorCode), anyString(), eq(true));
      finalization.verify(persistence).failModelRun(eq(RUN_ID), anyString(), eq(persistedErrorCode),
          any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyLong(), eq(NOW));
      verify(tasks, never()).succeed(any(), anyString());
      assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");
    }
    assertThat(transactionManager.begun()).isEqualTo(stage == Stage.PERSISTENCE ? 3 : 2);
    assertThat(transactionManager.committed()).isEqualTo(2);
    assertThat(transactionManager.rolledBack()).isEqualTo(stage == Stage.PERSISTENCE ? 1 : 0);
  }

  private enum Stage { SUCCESS, VALIDATED, UNAVAILABLE, CALL, ARCHIVE, PERSISTENCE, DELETED_BEFORE, DELETED_DURING }

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
