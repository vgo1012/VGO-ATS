package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class IntakeFileDownloadTest {
  private final TalentIntakePersistenceService persistence = mock(TalentIntakePersistenceService.class);
  private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final TalentProcessEventPersistenceService events = mock(TalentProcessEventPersistenceService.class);
  private final RecordingTransactions transactions = new RecordingTransactions();
  private final TrackingStream stream = new TrackingStream();
  private final ResumeIntake intake = new ResumeIntake();
  private TalentIntakeApplicationManager manager;

  @BeforeEach
  void setup() throws IOException {
    intake.setId("intake");
    intake.setOwnerUserId("owner");
    intake.setOwnerDepartmentId("department");
    intake.setStatus("FAILED_RETRYABLE");
    intake.setScanStatus("CLEAN");
    intake.setObjectKey("uploaded/original");
    intake.setOriginalFileName("原始简历.pdf");
    intake.setMediaType("application/pdf");
    intake.setSizeBytes(3);
    when(persistence.findIntake("intake")).thenReturn(intake);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("reader");
    when(authorization.currentActor()).thenReturn(actor);
    when(storage.open("uploaded/original")).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return stream;
    });
    when(events.insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return 1;
        });
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("event");
    var target = new TalentIntakeApplicationManager();
    ReflectionTestUtils.setField(target, "persistence", persistence);
    ReflectionTestUtils.setField(target, "storage", storage);
    ReflectionTestUtils.setField(target, "authorization", authorization);
    ReflectionTestUtils.setField(target, "talentProcessEventPersistenceService", events);
    ReflectionTestUtils.setField(target, "ids", ids);
    ReflectionTestUtils.setField(target, "clock", Clock.systemUTC());
    ReflectionTestUtils.setField(target, "transactionTemplate", new TransactionTemplate(transactions));
    var interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    var proxy = new ProxyFactory(target);
    proxy.addAdvice(interceptor);
    manager = (TalentIntakeApplicationManager) proxy.getProxy();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING_IMPORT", "PARSING", "READY_FOR_CONFIRMATION",
      "IDENTITY_REVIEW_REQUIRED", "FAILED_RETRYABLE", "CONFIRMED", "COMPLETED", "IGNORED"})
  void cleanOriginalIsReadableBeforeAndAfterImportWithAudit(String status) throws Exception {
    intake.setStatus(status);
    if (Set.of("CONFIRMED", "COMPLETED").contains(status)) {
      intake.setCandidateId("candidate");
      intake.setResumeVersionId("version");
    }
    var file = manager.downloadIntakeFile("intake");
    assertThat(file.getFileName()).isEqualTo("原始简历.pdf");
    assertThat(file.getMediaType()).isEqualTo("application/pdf");
    assertThat(file.getSizeBytes()).isEqualTo(3);
    assertThat(file.getContent().readAllBytes()).containsExactly(1, 2, 3);
    verify(authorization).requireAccess("talent.read", target());
    verify(events).insertEvent(eq("event"), eq("INTAKE_FILE_DOWNLOADED"), eq("reader"),
        eq("RESUME_INTAKE"), eq("intake"), eq(null), any());
    assertThat(transactions.committed).isEqualTo(1);
    assertThat(stream.closed).isFalse();
  }

  @Test
  void missingIntakeIs404AndNeverReadsStorage() {
    when(persistence.findIntake("intake")).thenReturn(null);
    assertFailure(404, "RESUME_INTAKE_NOT_FOUND");
    verifyNoInteractions(authorization, storage, events);
  }

  @Test
  void deniedDataScopeNeverOpensTheFile() {
    doThrow(PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权查看"))
        .when(authorization).requireAccess("talent.read", target());
    assertFailure(403, "DATA_SCOPE_FORBIDDEN");
    verifyNoInteractions(storage, events);
  }

  @Test
  void permissionIsRecheckedOnEveryReadAndRevocationBlocksBytes() throws Exception {
    manager.downloadIntakeFile("intake").getContent().close();
    doThrow(PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "授权已撤销"))
        .when(authorization).requireAccess("talent.read", target());
    assertFailure(403, "DATA_SCOPE_FORBIDDEN");
    verify(authorization, times(2)).requireAccess("talent.read", target());
    verify(storage, times(1)).open("uploaded/original");
    assertThat(transactions.committed).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource(value = {"MALICIOUS,FAILED_RETRYABLE", "UNAVAILABLE,FAILED_RETRYABLE",
      "NULL,FAILED_RETRYABLE", "CLEAN,QUARANTINED", "CLEAN,REJECTED"}, nullValues = "NULL")
  void unsafeOrRejectedIntakesNeverReadStorage(String scanStatus, String status) {
    intake.setScanStatus(scanStatus);
    intake.setStatus(status);
    assertFailure(409, "INTAKE_FILE_UNAVAILABLE");
    verifyNoInteractions(storage, events);
  }

  @Test
  void storageFailurePreservesCauseAndDoesNotAuditSuccess() throws Exception {
    var failure = new IOException("storage unavailable");
    when(storage.open("uploaded/original")).thenThrow(failure);
    assertThatThrownBy(() -> manager.downloadIntakeFile("intake"))
        .isInstanceOfSatisfying(PlatformException.class, ex -> {
          assertThat(ex.status().value()).isEqualTo(503);
          assertThat(ex.code()).isEqualTo("OBJECT_STORAGE_READ_FAILED");
        }).hasCause(failure);
    verifyNoInteractions(events);
    assertThat(transactions.committed).isZero();
  }

  @Test
  void auditFailureRollsBackAndClosesTheOpenedStream() {
    var failure = new IllegalStateException("audit failed");
    doThrow(failure).when(events).insertEvent(anyString(), anyString(), anyString(),
        anyString(), anyString(), any(), any());
    assertThatThrownBy(() -> manager.downloadIntakeFile("intake")).isSameAs(failure);
    assertThat(stream.closed).isTrue();
    assertThat(transactions.committed).isZero();
    assertThat(transactions.rolledBack).isEqualTo(1);
  }

  @Test
  void auditCommitFailureClosesStreamAndRetainsCloseFailureAsSuppressed() {
    transactions.commitFailure = new IllegalStateException("commit failed");
    stream.closeFailure = new IOException("close failed");
    assertThatThrownBy(() -> manager.downloadIntakeFile("intake"))
        .isSameAs(transactions.commitFailure).hasSuppressedException(stream.closeFailure);
    assertThat(stream.closed).isTrue();
  }

  private AuthorizationTarget target() {
    return new AuthorizationTarget("RESUME_INTAKE", "intake", "owner", "department", Set.of());
  }

  private void assertFailure(int status, String code) {
    assertThatThrownBy(() -> manager.downloadIntakeFile("intake"))
        .isInstanceOfSatisfying(PlatformException.class, ex -> {
          assertThat(ex.status().value()).isEqualTo(status);
          assertThat(ex.code()).isEqualTo(code);
        });
  }

  private static final class TrackingStream extends ByteArrayInputStream {
    private boolean closed;
    private IOException closeFailure;

    private TrackingStream() { super(new byte[] {1, 2, 3}); }

    @Override
    public void close() throws IOException {
      closed = true;
      if (closeFailure != null) { throw closeFailure; }
      super.close();
    }
  }

  private static final class RecordingTransactions extends AbstractPlatformTransactionManager {
    private int committed;
    private int rolledBack;
    private RuntimeException commitFailure;

    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) {
      if (commitFailure != null) { throw commitFailure; }
      committed++;
    }
    @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack++; }
  }
}
