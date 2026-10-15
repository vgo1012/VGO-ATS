package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.MalwareScanPort;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.domain.dto.ResumeUploadDTO;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.enums.IntakeBatchStatus;
import com.ezaigc.hr.talent.enums.MalwareScanVerdict;
import com.ezaigc.hr.talent.enums.SourceChannelStatus;
import com.ezaigc.hr.talent.service.IntakeBatchPersistenceService;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ResourceLock("SYSTEM_ERR")
class TalentIntakeApplicationManagerUploadTransactionTest {

  private static final String USER_ID = "01JUSER0000000000000000000";
  private static final String BATCH_ID = "01JBATCH000000000000000000";
  private static final String CHANNEL_ID = "01JCHANNEL0000000000000000";
  private static final String INTAKE_ID = "01JINTAKE00000000000000000";
  private static final String PROCESSING_ID = "01JPROCESSING0000000000000";
  private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");

  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
  private final MalwareScanPort scanner = mock(MalwareScanPort.class);
  private final IntakeBatchPersistenceService batches = mock(IntakeBatchPersistenceService.class);
  private final SourceChannelPersistenceService channels = mock(SourceChannelPersistenceService.class);
  private final TalentIdempotencyPersistenceService idempotency =
      mock(TalentIdempotencyPersistenceService.class);
  private final TalentIntakePersistenceService intakes =
      mock(TalentIntakePersistenceService.class);
  private final ResumeIntakeProcessingPersistenceService processing =
      mock(ResumeIntakeProcessingPersistenceService.class);
  private final TalentProcessEventPersistenceService events =
      mock(TalentProcessEventPersistenceService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final IntakeBatch storedBatch = batch();
  private final ResumeIntake stored = intake();

  @BeforeEach
  void setUp() throws Exception {
    when(authorization.currentActor())
        .thenAnswer(
            ignored -> {
              assertTransactionActive();
              return actor();
            });
    when(batches.findBatch(BATCH_ID))
        .thenAnswer(
            ignored -> {
              assertTransactionActive();
              return storedBatch;
            });
    when(channels.findChannel(CHANNEL_ID))
        .thenAnswer(
            ignored -> {
              assertTransactionActive();
              return channel();
            });
    when(idempotency.findIdempotency(USER_ID, "upload-key-1")).thenReturn(null);
    when(ids.newId()).thenReturn(INTAKE_ID, "01JEVENT0000000000000000000", PROCESSING_ID,
        "01JPARSEEVENT0000000000000");
    when(scanner.scan(anyString(), any())).thenReturn(MalwareScanVerdict.CLEAN);
    when(storage.exists(anyString())).thenReturn(false);
    when(intakes.findIntake(INTAKE_ID)).thenReturn(stored);
    when(intakes.updateIntakeStatus(INTAKE_ID, "PARSING", null, 0, USER_ID, NOW))
        .thenAnswer(ignored -> {
          assertTransactionActive();
          stored.setStatus("PARSING");
          stored.setVersion(1);
          return 1;
        });
    when(intakes.attachProcessing(INTAKE_ID, PROCESSING_ID, USER_ID, NOW))
        .thenAnswer(ignored -> {
          assertTransactionActive();
          stored.setProcessingId(PROCESSING_ID);
          return 1;
        });
    when(processing.insertProcessing(anyString(), anyString(), anyString(), anyString(), anyString(),
        anyString(), any())).thenAnswer(ignored -> {
          assertTransactionActive();
          return 1;
        });
    var shared = new ResumeIntakeProcessing();
    shared.setId(PROCESSING_ID);
    shared.setStatus("QUEUED");
    when(processing.findByHash(anyString())).thenReturn(shared);
  }

  @Test
  void uploadRunsFileAndStorageIoOutsideThePersistenceTransaction() throws Exception {
    assertUpload(false);
  }

  @Test
  void repeatedUploadKeyReturnsAcceptedIntakeWithoutQueuingAnotherParse() throws Exception {
    assertUpload(false);
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(intakes).insertIdempotency(anyString(), anyString(), anyString(), fingerprint.capture(),
        anyString(), any());
    var previous = new IdempotencyRecord();
    previous.setActionCode("RESUME_INTAKE_UPLOAD");
    previous.setRequestFingerprint(fingerprint.getValue());
    previous.setTargetId(INTAKE_ID);
    when(idempotency.findIdempotency(USER_ID, "upload-key-1")).thenReturn(previous);

    ResumeIntake replay = proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1");

    assertThat(replay).isSameAs(stored);
    assertThat(replay.getStatus()).isEqualTo("PARSING");
    verify(processing, times(1)).insertProcessing(anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), any());
    verify(intakes, times(1)).attachProcessing(INTAKE_ID, PROCESSING_ID, USER_ID, NOW);
  }

  @Test
  void replayOfLegacyCleanPendingUploadQueuesParsingOnce() throws Exception {
    stubAcceptedUpload("upload-key-1");

    ResumeIntake replay = proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1");

    assertThat(replay).isSameAs(stored);
    assertThat(replay.getStatus()).isEqualTo("PARSING");
    assertThat(replay.getProcessingId()).isEqualTo(PROCESSING_ID);
    verify(processing).insertProcessing(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), any());
    verify(intakes, never()).incrementBatch(anyString(), anyInt(), anyInt(), anyString(), any());
    verify(intakes, never()).insertIdempotency(anyString(), anyString(), anyString(), anyString(),
        anyString(), any());
  }

  @Test
  void legacyCleanReplayRecoversWhenRescanIsUnavailable() throws Exception {
    stubAcceptedUpload("upload-key-1");
    when(scanner.scan(anyString(), any())).thenThrow(new IOException("scanner unavailable"));

    ResumeIntake replay = proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1");

    assertThat(replay.getStatus()).isEqualTo("PARSING");
    verify(processing).insertProcessing(anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), any());
  }

  @Test
  void legacyCleanReplayRejectsMaliciousRescanWithoutParsing() throws Exception {
    stubAcceptedUpload("upload-key-1");
    when(scanner.scan(anyString(), any())).thenReturn(MalwareScanVerdict.MALICIOUS);

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MALWARE_OR_SIGNATURE_REJECTED"));

    verifyNoInteractions(processing);
  }

  @Test
  void completedBatchReplayReturnsOriginalIntakeWithoutScanStorageOrWrites() throws Exception {
    storedBatch.setStatus(IntakeBatchStatus.COMPLETED.getCode());
    stored.setStatus("CONFIRMED");
    stubAcceptedUpload("upload-key-1");
    RecordingTransactionManager transactions = new RecordingTransactionManager();

    ResumeIntake replay = proxiedManager(transactions)
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1");

    assertThat(replay).isSameAs(stored);
    assertThat(replay.getStatus()).isEqualTo("CONFIRMED");
    assertThat(transactions.readOnlyBegun()).isOne();
    verifyNoInteractions(channels, scanner, storage, processing, events, ids);
    verify(intakes, never()).insertIdempotency(anyString(), anyString(), anyString(), anyString(),
        anyString(), any());
    verify(intakes, never()).incrementBatch(anyString(), anyInt(), anyInt(), anyString(), any());
  }

  @Test
  void completedBatchRejectsNewFileWithoutScanStorageOrIdempotencyWrite() throws Exception {
    storedBatch.setStatus(IntakeBatchStatus.COMPLETED.getCode());

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "new-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_BATCH_COMPLETED"));

    verifyNoInteractions(scanner, storage, processing, events, ids);
    verify(intakes, never()).insertIdempotency(anyString(), anyString(), anyString(), anyString(),
        anyString(), any());
  }

  @Test
  void completedBatchRejectsSameKeyWithDifferentContent() throws Exception {
    storedBatch.setStatus(IntakeBatchStatus.COMPLETED_WITH_ERRORS.getCode());
    stubAcceptedUpload("upload-key-1");
    byte[] different = "%PDF-different-resume".getBytes(StandardCharsets.UTF_8);
    ResumeUploadDTO upload = new ResumeUploadDTO("candidate.pdf", different.length,
        () -> new ByteArrayInputStream(different));

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, upload, "upload-key-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));

    verifyNoInteractions(scanner, storage, processing, events, ids);
  }

  @Test
  void completedBatchRejectsIdempotencyTargetFromAnotherBatch() throws Exception {
    storedBatch.setStatus(IntakeBatchStatus.COMPLETED.getCode());
    stubAcceptedUpload("upload-key-1");
    stored.setBatchId("another-batch");

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_TARGET_MISMATCH"));

    verifyNoInteractions(scanner, storage, processing, events, ids);
  }

  @Test
  void completedBatchRejectsActorWithoutBatchAccessBeforeReadingFile() throws Exception {
    storedBatch.setStatus(IntakeBatchStatus.COMPLETED.getCode());
    stubAcceptedUpload("upload-key-1");
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权访问收件批次"))
        .when(authorization).requireAccess(anyString(), any());

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));

    verifyNoInteractions(idempotency, scanner, storage, processing, events, ids);
  }

  @Test
  void completedBatchRejectsActorWithoutTargetIntakeAccess() throws Exception {
    storedBatch.setStatus(IntakeBatchStatus.COMPLETED.getCode());
    stubAcceptedUpload("upload-key-1");
    doThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权访问目标收件"))
        .when(authorization).requireAccess(anyString(),
            argThat(target -> "RESUME_INTAKE".equals(target.getObjectType())));

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager())
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));

    verifyNoInteractions(scanner, storage, processing, events, ids);
  }

  @Test
  void uploadReusesSharedProcessingAndPreservesHistoricalDuplicate() throws Exception {
    stored.setDuplicateContent(true);
    doAnswer(ignored -> 0).when(processing).insertProcessing(anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), any());
    var failed = new ResumeIntakeProcessing();
    failed.setId(PROCESSING_ID);
    failed.setStatus("FAILED_FINAL");
    when(processing.findByHash(anyString())).thenReturn(failed);
    when(processing.retry(PROCESSING_ID, USER_ID, NOW)).thenAnswer(ignored -> {
      assertTransactionActive();
      return 1;
    });

    assertUpload(false);

    assertThat(stored.isDuplicateContent()).isTrue();
    verify(processing).findByHash(stored.getContentSha256());
    verify(processing).retry(PROCESSING_ID, USER_ID, NOW);
  }

  @Test
  void unavailableScannerKeepsFileQuarantinedWithoutLoggingPrivateExceptionPayloads() throws Exception {
    assertUpload(true);
  }

  private void assertUpload(boolean scannerFails) throws Exception {
    IOException failure = new IOException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new RuntimeException("test-secret-marker"));
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    ResumeUploadDTO upload = transactionAwareUpload();
    when(scanner.scan(anyString(), any()))
        .thenAnswer(
            ignored -> {
              assertNoTransaction();
              if (scannerFails) {
                throw failure;
              }
              return MalwareScanVerdict.CLEAN;
            });
    when(storage.exists(anyString()))
        .thenAnswer(
            ignored -> {
              assertNoTransaction();
              return false;
            });
    doAnswer(
            ignored -> {
              assertNoTransaction();
              assertThat(ignored.<String>getArgument(0)).startsWith(scannerFails ? "quarantine/" : "resume/");
              return null;
            })
        .when(storage)
        .put(anyString(), any());
    when(intakes.insertIdempotency(
            anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenAnswer(
            ignored -> {
              assertTransactionActive();
              return 1;
            });
    when(intakes.insertIntake(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any()))
        .thenAnswer(
            ignored -> {
              assertTransactionActive();
              assertThat(ignored.<String>getArgument(11)).isEqualTo(scannerFails ? "UNAVAILABLE" : "CLEAN");
              assertThat(ignored.<String>getArgument(12))
                  .isEqualTo(scannerFails ? "FAILED_RETRYABLE" : "PENDING_IMPORT");
              assertThat(ignored.<String>getArgument(13)).isEqualTo(scannerFails ? "MALWARE_SCANNER_UNAVAILABLE" : null);
              stored.setContentSha256(ignored.getArgument(9));
              stored.setObjectKey(ignored.getArgument(10));
              stored.setScanStatus(ignored.getArgument(11));
              stored.setStatus(ignored.getArgument(12));
              return 1;
            });
    when(intakes.incrementBatch(anyString(), anyInt(), anyInt(), anyString(), any()))
        .thenAnswer(
            ignored -> {
              assertTransactionActive();
              return 1;
            });
    when(events.insertEvent(
            anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(
            ignored -> {
              assertTransactionActive();
              return 1;
            });

    PrintStream originalError = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ResumeIntake result;
    try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      result = proxiedManager(transactions).upload(BATCH_ID, upload, "upload-key-1");
    } finally {
      System.setErr(originalError);
    }
    if (scannerFails) {
      assertThat(bytes.toString(StandardCharsets.UTF_8))
          .contains("resume-malware-scan-failed", INTAKE_ID, BATCH_ID, "java.io.IOException",
              "TalentIntakeApplicationManagerUploadTransactionTest.java")
          .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
      assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");
    }

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(scannerFails ? "FAILED_RETRYABLE" : "PARSING");
    if (scannerFails) {
      verifyNoInteractions(processing);
    } else {
      assertThat(result.getProcessingId()).isEqualTo(PROCESSING_ID);
      verify(processing).insertProcessing(PROCESSING_ID, stored.getContentSha256(),
          stored.getOriginalFileName(), stored.getMediaType(), stored.getObjectKey(), USER_ID, NOW);
      verify(intakes).attachProcessing(INTAKE_ID, PROCESSING_ID, USER_ID, NOW);
    }
    assertThat(transactions.begun()).isEqualTo(2);
    assertThat(transactions.committed()).isEqualTo(2);
  }

  @Test
  void uploadDoesNotReserveIdempotencyWhenObjectStorageFails() throws Exception {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    when(storage.exists(anyString())).thenReturn(false);
    doAnswer(ignored -> { throw new IOException("storage unavailable"); })
        .when(storage)
        .put(anyString(), any());

    assertThatThrownBy(
            () ->
                proxiedManager(transactions)
                    .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("OBJECT_STORAGE_WRITE_FAILED"));

    verify(intakes, never())
        .insertIdempotency(
            anyString(), anyString(), anyString(), anyString(), anyString(), any());
    verify(intakes, never())
        .insertIntake(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any());
    assertThat(transactions.begun()).isOne();
    assertThat(transactions.committed()).isOne();
  }

  @Test
  void malformedPlatformFileNameMapsToAStableBusinessError() throws Exception {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    ResumeUploadDTO upload =
        new ResumeUploadDTO("bad\0.pdf", 1, () -> new ByteArrayInputStream(new byte[] {1}));

    assertThatThrownBy(
            () -> proxiedManager(transactions).upload(BATCH_ID, upload, "upload-key-1"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("RESUME_FILE_NAME_INVALID"));

    verify(scanner, never()).scan(anyString(), any());
    assertThat(transactions.begun()).isZero();
  }

  @ParameterizedTest
  @CsvSource({"INTAKE,0", "INTAKE,2", "BATCH,0", "BATCH,2", "EVENT,0", "EVENT,2", "READBACK,0", "PROCESSING_LINK,0"})
  void uploadRollsBackWhenAnyRequiredPersistenceStepDoesNotComplete(FailedStep step, int affected)
      throws Exception {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    when(intakes.insertIdempotency(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenAnswer(ignored -> {
          assertTransactionActive();
          return 1;
        });
    when(intakes.insertIntake(anyString(), anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(),
        anyString(), any(), any()))
        .thenAnswer(ignored -> {
          assertTransactionActive();
          return step == FailedStep.INTAKE ? affected : 1;
        });
    when(intakes.incrementBatch(anyString(), anyInt(), anyInt(), anyString(), any()))
        .thenAnswer(ignored -> {
          assertTransactionActive();
          return step == FailedStep.BATCH ? affected : 1;
        });
    when(events.insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(ignored -> {
          assertTransactionActive();
          return step == FailedStep.EVENT ? affected : 1;
        });
    if (step == FailedStep.READBACK) {
      when(intakes.findIntake(INTAKE_ID)).thenReturn(null);
    }
    if (step == FailedStep.PROCESSING_LINK) {
      doAnswer(ignored -> 0).when(intakes).attachProcessing(INTAKE_ID, PROCESSING_ID, USER_ID, NOW);
    }

    var failure = assertThatThrownBy(() -> proxiedManager(transactions)
        .upload(BATCH_ID, transactionAwareUpload(), "upload-key-1"));

    if (step == FailedStep.BATCH) {
      failure.isInstanceOfSatisfying(PlatformException.class,
          error -> assertThat(error.code()).isEqualTo("INTAKE_BATCH_STATE_CONFLICT"));
    } else {
      failure.isInstanceOf(IllegalStateException.class);
    }
    assertThat(transactions.begun()).isEqualTo(2);
    assertThat(transactions.committed()).isOne();
    assertThat(transactions.rolledBack()).isOne();
    if (step == FailedStep.BATCH) {
      verify(intakes, never())
          .insertIntake(
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyLong(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              any(),
              any());
    }
    if (step == FailedStep.INTAKE || step == FailedStep.BATCH) {
      verify(events, never()).insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }
  }

  private enum FailedStep { INTAKE, BATCH, EVENT, READBACK, PROCESSING_LINK }

  private TalentIntakeApplicationManager proxiedManager(
      RecordingTransactionManager transactions) {
    TalentIntakeApplicationManager target = new TalentIntakeApplicationManager();
    ReflectionTestUtils.setField(target, "authorization", authorization);
    ReflectionTestUtils.setField(target, "storage", storage);
    ReflectionTestUtils.setField(target, "scanner", scanner);
    ReflectionTestUtils.setField(target, "intakeBatchPersistenceService", batches);
    ReflectionTestUtils.setField(target, "sourceChannelPersistenceService", channels);
    ReflectionTestUtils.setField(target, "talentIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(target, "persistence", intakes);
    ReflectionTestUtils.setField(target, "intakeProcessing", processing);
    ReflectionTestUtils.setField(target, "talentProcessEventPersistenceService", events);
    ReflectionTestUtils.setField(target, "ids", ids);
    ReflectionTestUtils.setField(target, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(
        target, "transactionTemplate", new TransactionTemplate(transactions));

    ProxyFactory proxyFactory = new ProxyFactory(target);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    return (TalentIntakeApplicationManager) proxyFactory.getProxy();
  }

  private void stubAcceptedUpload(String key) {
    byte[] content = "%PDF-production-resume".getBytes(StandardCharsets.UTF_8);
    String contentSha = Sha256Utils.hashBytes(content);
    stored.setContentSha256(contentSha);
    var previous = new IdempotencyRecord();
    previous.setActionCode("RESUME_INTAKE_UPLOAD");
    previous.setRequestFingerprint(Sha256Utils.hashUtf8(String.join("\u001f", BATCH_ID,
        "candidate.pdf", Long.toString(content.length), contentSha)));
    previous.setTargetId(INTAKE_ID);
    when(idempotency.findIdempotency(USER_ID, key)).thenReturn(previous);
  }

  private static ResumeUploadDTO transactionAwareUpload() {
    byte[] content = "%PDF-production-resume".getBytes(StandardCharsets.UTF_8);
    return new ResumeUploadDTO(
        "candidate.pdf",
        content.length,
        () -> {
          assertNoTransaction();
          return new ByteArrayInputStream(content);
        });
  }

  private static void assertNoTransaction() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
  }

  private static void assertTransactionActive() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
  }

  private static CurrentActorSnapshot actor() {
    return new CurrentActorSnapshot(
        USER_ID,
        "测试用户",
        "user@example.com",
        "department-1",
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of("RECRUITER"),
        Set.of("talent.manage"),
        false,
        false);
  }

  private static IntakeBatch batch() {
    IntakeBatch value = new IntakeBatch();
    value.setId(BATCH_ID);
    value.setSourceChannelId(CHANNEL_ID);
    value.setSourceRecruiterUserId(USER_ID);
    value.setDefaultOwnerUserId(USER_ID);
    value.setOwnerDepartmentId("department-1");
    value.setStatus(IntakeBatchStatus.OPEN.getCode());
    return value;
  }

  private static SourceChannel channel() {
    SourceChannel value = new SourceChannel();
    value.setId(CHANNEL_ID);
    value.setStatus(SourceChannelStatus.ACTIVE.getCode());
    return value;
  }

  private static ResumeIntake intake() {
    ResumeIntake value = new ResumeIntake();
    value.setId(INTAKE_ID);
    value.setBatchId(BATCH_ID);
    value.setJdVersionId("01JJDVERSION00000000000001");
    value.setOriginalFileName("candidate.pdf");
    value.setMediaType("application/pdf");
    value.setContentSha256("sha");
    value.setObjectKey("resume/key");
    value.setScanStatus("CLEAN");
    value.setStatus("PENDING_IMPORT");
    return value;
  }

  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private final AtomicInteger begun = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();
    private final AtomicInteger rolledBack = new AtomicInteger();
    private final AtomicInteger readOnlyBegun = new AtomicInteger();

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun.incrementAndGet();
      if (definition.isReadOnly()) {
        readOnlyBegun.incrementAndGet();
      }
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

    int readOnlyBegun() {
      return readOnlyBegun.get();
    }
  }
}
