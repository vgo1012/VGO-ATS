package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.domain.entity.DownloadAuthorization;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.response.DownloadedResumeResponse;
import com.ezaigc.hr.talent.service.DownloadAuthorizationPersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;

@ResourceLock("SYSTEM_ERR")
class TalentIntakeApplicationManagerDownloadTransactionTest {

  private static final String TOKEN =
      "123e4567-e89b-12d3-a456-426614174000123e4567-e89b-12d3-a456-426614174001";
  private static final String USER_ID = "01JUSER0000000000000000000";
  private static final String GRANT_ID = "01JGRANT000000000000000000";
  private static final String VERSION_ID = "01JVERSION0000000000000000";
  private static final Instant NOW = Instant.parse("2026-09-04T10:00:00Z");

  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
  private final DownloadAuthorizationPersistenceService downloadAuthorizations =
      mock(DownloadAuthorizationPersistenceService.class);
  private final ResumeVersionPersistenceService resumeVersions =
      mock(ResumeVersionPersistenceService.class);
  private final ResumePersistenceService resumes = mock(ResumePersistenceService.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateReadAccess candidateReadAccess = mock(CandidateReadAccess.class);
  private final TalentProcessEventPersistenceService events =
      mock(TalentProcessEventPersistenceService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);

  @BeforeEach
  void setUp() {
    when(authorization.currentActor()).thenReturn(actor());
    when(downloadAuthorizations.findDownloadAuthorization(anyString())).thenReturn(grant());
    when(resumeVersions.findResumeVersion(VERSION_ID)).thenReturn(version());
    when(resumes.findResume("resume-id")).thenReturn(resume());
    when(candidates.findCandidate("candidate-id")).thenReturn(candidate());
    when(ids.newId()).thenReturn("01JEVENT0000000000000000000");
  }

  @Test
  void readableResumeCanBeGrantedWithoutAnIndependentDownloadPermission() {
    doThrow(new PlatformException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无独立下载权限", false))
        .when(authorization).requirePermission("talent.download");

    var response = proxiedManager(new RecordingTransactionManager(false))
        .authorizeDownloadResponse(VERSION_ID);

    assertThat(response.getToken()).hasSize(72);
    assertThat(response.getExpiresAt()).isEqualTo(NOW.plusSeconds(300));
    verify(downloadAuthorizations).insertDownloadAuthorization(
        anyString(), anyString(), eq(VERSION_ID), eq(USER_ID), any(), any());
    verify(candidateReadAccess).requireResume(any(Candidate.class), eq(VERSION_ID));
  }

  @Test
  void rejectedResumeObjectAccessPreventsGrantAndDownloadEvenWithAnExistingToken() throws Exception {
    doThrow(new PlatformException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无查看权限", false))
        .when(candidateReadAccess).requireResume(any(Candidate.class), eq(VERSION_ID));
    var manager = proxiedManager(new RecordingTransactionManager(false));

    assertThatThrownBy(() -> manager.authorizeDownloadResponse(VERSION_ID))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.downloadResponse(TOKEN))
        .isInstanceOf(PlatformException.class);

    verify(downloadAuthorizations, never())
        .insertDownloadAuthorization(anyString(), anyString(), anyString(), anyString(), any(), any());
    verify(storage, never()).open(anyString());
    verify(downloadAuthorizations, never()).consumeDownloadAuthorization(anyString(), any());
  }

  @Test
  void downloadOpensStorageOutsideTransactionAndConsumesGrantAtomically() throws Exception {
    RecordingTransactionManager transactions = new RecordingTransactionManager(false);
    TrackingInputStream content = new TrackingInputStream();
    when(storage.open("resume/object-key"))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return content;
            });
    when(downloadAuthorizations.consumeDownloadAuthorization(GRANT_ID, NOW))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(events.insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });

    DownloadedResumeResponse response = proxiedManager(transactions).downloadResponse(TOKEN);

    assertThat(response.getContent()).isSameAs(content);
    assertThat(content.isClosed()).isFalse();
    assertThat(transactions.begun()).isOne();
    assertThat(transactions.committed()).isOne();
    verify(candidateReadAccess).requireResume(any(Candidate.class), eq(VERSION_ID));
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0})
  void expiredGrantIsRejectedBeforeOpeningStorage(long secondsUntilExpiry) throws Exception {
    var expired = grant();
    expired.setExpiresAt(NOW.plusSeconds(secondsUntilExpiry));
    when(downloadAuthorizations.findDownloadAuthorization(anyString())).thenReturn(expired);
    when(storage.open("resume/object-key")).thenReturn(new TrackingInputStream());
    when(downloadAuthorizations.consumeDownloadAuthorization(GRANT_ID, NOW)).thenReturn(1);

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager(false)).downloadResponse(TOKEN))
        .isInstanceOfSatisfying(PlatformException.class, exception -> {
          assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(exception.code()).isEqualTo("DOWNLOAD_AUTHORIZATION_INVALID");
        });

    verify(storage, never()).open(anyString());
    verify(downloadAuthorizations, never()).consumeDownloadAuthorization(anyString(), any());
    verify(events, never())
        .insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(longs = {299, 300, 301})
  void consumptionUsesCurrentTimeAfterStorageOpens(long elapsedSeconds) throws Exception {
    var time = new AtomicReference<>(NOW);
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenAnswer(ignored -> time.get());
    Instant openedAt = NOW.plusSeconds(elapsedSeconds);
    TrackingInputStream content = new TrackingInputStream();
    when(storage.open("resume/object-key")).thenAnswer(ignored -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      time.set(openedAt);
      return content;
    });
    when(downloadAuthorizations.consumeDownloadAuthorization(eq(GRANT_ID), any()))
        .thenAnswer(ignored -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          Instant consumedAt = ignored.getArgument(1);
          return consumedAt.isBefore(grant().getExpiresAt()) ? 1 : 0;
        });
    var transactions = new RecordingTransactionManager(false);
    var manager = proxiedManager(transactions, clock);

    if (elapsedSeconds < 300) {
      assertThat(manager.downloadResponse(TOKEN).getContent()).isSameAs(content);
      assertThat(content.isClosed()).isFalse();
      assertThat(transactions.committed()).isOne();
      verify(events).insertEvent(anyString(), eq("RESUME_DOWNLOADED"), eq(USER_ID),
          eq("RESUME_VERSION"), eq(VERSION_ID), eq(null), eq(openedAt));
    } else {
      assertThatThrownBy(() -> manager.downloadResponse(TOKEN))
          .isInstanceOfSatisfying(PlatformException.class,
              exception -> assertThat(exception.code()).isEqualTo("DOWNLOAD_AUTHORIZATION_CONSUMED"));
      assertThat(content.isClosed()).isTrue();
      assertThat(transactions.committed()).isZero();
      verify(events, never())
          .insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }
    verify(downloadAuthorizations).consumeDownloadAuthorization(GRANT_ID, openedAt);
  }

  @Test
  void consumptionRechecksTimeAfterTransactionBegins() throws Exception {
    var time = new AtomicReference<>(NOW);
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenAnswer(ignored -> time.get());
    Instant expiresAt = grant().getExpiresAt();
    var transactions = new RecordingTransactionManager(false);
    transactions.onBegin = () -> time.set(expiresAt);
    TrackingInputStream content = new TrackingInputStream();
    when(storage.open("resume/object-key")).thenReturn(content);
    when(downloadAuthorizations.consumeDownloadAuthorization(GRANT_ID, NOW)).thenReturn(1);

    assertThatThrownBy(() -> proxiedManager(transactions, clock).downloadResponse(TOKEN))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("DOWNLOAD_AUTHORIZATION_CONSUMED"));

    verify(downloadAuthorizations).consumeDownloadAuthorization(GRANT_ID, expiresAt);
    assertThat(content.isClosed()).isTrue();
    assertThat(transactions.committed()).isZero();
    verify(events, never())
        .insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void downloadClosesOpenedStreamWhenConsumptionTransactionCannotCommit() throws Exception {
    RecordingTransactionManager transactions = new RecordingTransactionManager(true);
    TrackingInputStream content = new TrackingInputStream();
    when(storage.open("resume/object-key")).thenReturn(content);
    when(downloadAuthorizations.consumeDownloadAuthorization(GRANT_ID, NOW)).thenReturn(1);
    when(events.insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(1);

    assertThatThrownBy(() -> proxiedManager(transactions).downloadResponse(TOKEN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("commit failed");

    assertThat(content.isClosed()).isTrue();
  }

  @Test
  void downloadDoesNotConsumeGrantWhenStorageCannotBeOpened() throws Exception {
    RecordingTransactionManager transactions = new RecordingTransactionManager(false);
    when(storage.open("resume/object-key")).thenThrow(new IOException("storage unavailable"));

    assertThatThrownBy(() -> proxiedManager(transactions).downloadResponse(TOKEN))
        .hasMessageContaining("简历对象读取失败");

    verify(downloadAuthorizations, never()).consumeDownloadAuthorization(anyString(), any());
    verify(events, never())
        .insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void downloadRechecksResumeObjectAccessBeforeOpeningStorageOrConsumingToken() throws Exception {
    doThrow(new PlatformException(HttpStatus.FORBIDDEN, "CANDIDATE_RESUME_ACCESS_FORBIDDEN",
        "无权查看该简历版本", false))
        .when(candidateReadAccess).requireResume(any(Candidate.class), eq(VERSION_ID));

    assertThatThrownBy(() -> proxiedManager(new RecordingTransactionManager(false)).downloadResponse(TOKEN))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("CANDIDATE_RESUME_ACCESS_FORBIDDEN"));

    verify(storage, never()).open(anyString());
    verify(downloadAuthorizations, never()).consumeDownloadAuthorization(anyString(), any());
  }

  @Test
  void closeFailureRemainsSuppressedWithoutLoggingPrivateExceptionPayloads() throws Exception {
    RecordingTransactionManager transactions = new RecordingTransactionManager(true);
    InputStream content = mock(InputStream.class);
    IOException closeFailure = new IOException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    closeFailure.addSuppressed(new RuntimeException("test-secret-marker"));
    doThrow(closeFailure).when(content).close();
    when(storage.open("resume/object-key")).thenReturn(content);
    when(downloadAuthorizations.consumeDownloadAuthorization(GRANT_ID, NOW)).thenReturn(1);
    when(events.insertEvent(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any()))
        .thenReturn(1);
    PrintStream originalError = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      assertThatThrownBy(() -> proxiedManager(transactions).downloadResponse(TOKEN))
          .isInstanceOf(IllegalStateException.class).hasMessageContaining("commit failed")
          .satisfies(error -> assertThat(error.getSuppressed()).contains(closeFailure));
    } finally {
      System.setErr(originalError);
    }
    verify(content).close();
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains(GRANT_ID, VERSION_ID, "java.io.IOException",
            "TalentIntakeApplicationManagerDownloadTransactionTest.java")
        .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    assertThat(closeFailure.getMessage()).isEqualTo("audit-person@example.invalid");
  }

  @Test
  void malformedDownloadTokenIsRejectedBeforeAuthorizationAndPersistence() {
    RecordingTransactionManager transactions = new RecordingTransactionManager(false);

    assertThatThrownBy(() -> proxiedManager(transactions).downloadResponse("short"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("DOWNLOAD_TOKEN_INVALID"));

    verify(authorization, never()).currentActor();
    verify(downloadAuthorizations, never()).findDownloadAuthorization(anyString());
  }

  private TalentIntakeApplicationManager proxiedManager(RecordingTransactionManager transactions) {
    return proxiedManager(transactions, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private TalentIntakeApplicationManager proxiedManager(
      RecordingTransactionManager transactions, Clock clock) {
    TalentIntakeApplicationManager target = new TalentIntakeApplicationManager();
    ReflectionTestUtils.setField(target, "authorization", authorization);
    ReflectionTestUtils.setField(target, "storage", storage);
    ReflectionTestUtils.setField(target, "downloadAuthorizationPersistenceService", downloadAuthorizations);
    ReflectionTestUtils.setField(target, "resumeVersionPersistenceService", resumeVersions);
    ReflectionTestUtils.setField(target, "resumePersistenceService", resumes);
    ReflectionTestUtils.setField(target, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(target, "candidateReadAccess", candidateReadAccess);
    ReflectionTestUtils.setField(target, "talentProcessEventPersistenceService", events);
    ReflectionTestUtils.setField(target, "ids", ids);
    ReflectionTestUtils.setField(target, "clock", clock);
    var transactionTemplateField =
        ReflectionUtils.findField(TalentIntakeApplicationManager.class, "transactionTemplate");
    if (transactionTemplateField != null) {
      ReflectionTestUtils.setField(
          target, "transactionTemplate", new TransactionTemplate(transactions));
    }

    ProxyFactory proxyFactory = new ProxyFactory(target);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    return (TalentIntakeApplicationManager) proxyFactory.getProxy();
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
        Set.of("talent.read"),
        false,
        false);
  }

  private static DownloadAuthorization grant() {
    DownloadAuthorization value = new DownloadAuthorization();
    value.setId(GRANT_ID);
    value.setResumeVersionId(VERSION_ID);
    value.setAuthorizedUserId(USER_ID);
    value.setExpiresAt(NOW.plusSeconds(300));
    return value;
  }

  private static ResumeVersion version() {
    ResumeVersion value = new ResumeVersion();
    value.setId(VERSION_ID);
    value.setResumeId("resume-id");
    value.setOriginalFileName("resume.pdf");
    value.setMediaType("application/pdf");
    value.setSizeBytes(1L);
    value.setObjectKey("resume/object-key");
    return value;
  }

  private static Resume resume() {
    Resume value = new Resume();
    value.setId("resume-id");
    value.setCandidateId("candidate-id");
    return value;
  }

  private static Candidate candidate() {
    Candidate value = new Candidate();
    value.setId("candidate-id");
    value.setStatus("ACTIVE");
    value.setOwnerUserId(USER_ID);
    return value;
  }

  private static final class TrackingInputStream extends ByteArrayInputStream {
    private boolean closed;

    private TrackingInputStream() {
      super(new byte[] {1});
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }

    boolean isClosed() {
      return closed;
    }
  }

  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private final AtomicInteger begun = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();
    private Runnable onBegin = () -> {};
    private final boolean failCommit;

    private RecordingTransactionManager(boolean failCommit) {
      this.failCommit = failCommit;
    }

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun.incrementAndGet();
      onBegin.run();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      if (failCommit) {
        throw new IllegalStateException("commit failed");
      }
      committed.incrementAndGet();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}

    int begun() {
      return begun.get();
    }

    int committed() {
      return committed.get();
    }
  }
}
