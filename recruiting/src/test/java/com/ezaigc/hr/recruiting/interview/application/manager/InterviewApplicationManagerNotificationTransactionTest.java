package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort.DeliveryResult;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewNotificationResponse;
import com.ezaigc.hr.recruiting.interview.service.InterviewIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationAttemptPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;

@ResourceLock("SYSTEM_ERR")
class InterviewApplicationManagerNotificationTransactionTest {

  private static final String ACTOR_ID = "A".repeat(26);
  private static final String DEPARTMENT_ID = "D".repeat(26);
  private static final String INTERVIEW_ID = "I".repeat(26);
  private static final String NOTIFICATION_ID = "N".repeat(26);
  private static final String ATTEMPT_ID = "T".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T03:00:00Z");

  @Test
  void retryCommitsClaimBeforeCallingProviderAndFinalizesInAnotherShortTransaction() {
    assertRetry(false, false);
  }

  @Test
  void providerExceptionProducesSafeFailureAuditAndLogsWithoutPrivatePayloads() {
    assertRetry(true, false);
  }

  @Test
  void providerRejectionCodeDoesNotExposePrivatePayloadsInLogs() {
    assertRetry(false, true);
  }

  private void assertRetry(boolean providerFails, boolean providerRejects) {
    IllegalStateException failure = new IllegalStateException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new RuntimeException("test-secret-marker"));
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    InterviewPersistenceService persistence = mock(InterviewPersistenceService.class);
    NotificationIntentPersistenceService notifications =
        mock(NotificationIntentPersistenceService.class);
    NotificationAttemptPersistenceService attempts =
        mock(NotificationAttemptPersistenceService.class);
    InterviewIdempotencyPersistenceService idempotency =
        mock(InterviewIdempotencyPersistenceService.class);
    ParticipantPersistenceService participants = mock(ParticipantPersistenceService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    NotificationDeliveryPort delivery = mock(NotificationDeliveryPort.class);
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    InterviewAssembler assembler = mock(InterviewAssembler.class);
    NotificationIntent pending = notification("PENDING_MANUAL", 0L, null);
    NotificationIntent sending = notification("SENDING_EXTERNAL", 1L, null);
    String rawExternalMessageId = "id\r\n" + "x".repeat(250);
    boolean failed = providerFails || providerRejects;
    String storedExternalMessageId = failed ? null : "id " + "x".repeat(197);
    String finalStatus = failed ? "FAILED_EXTERNAL" : "SENT_EXTERNAL";
    String errorCode = providerFails ? "DELIVERY_EXCEPTION" : providerRejects ? "15500000000" : null;
    String errorDetail = providerFails ? "外部通知渠道调用异常"
        : providerRejects ? "audit-person@example.invalid test-secret-marker" : null;
    NotificationIntent sent = notification(finalStatus, 2L, storedExternalMessageId);
    Interview interview = interview();
    InterviewNotificationResponse response = mock(InterviewNotificationResponse.class);

    when(authorization.currentActor()).thenReturn(actor());
    when(authorization.canAccess(
            any(CurrentActorSnapshot.class),
            eq("interview.feedback.read-authorized"),
            any(AuthorizationTarget.class)))
        .thenReturn(true);
    when(participants.findParticipants(INTERVIEW_ID)).thenReturn(List.of());
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(persistence.lockInterview(INTERVIEW_ID)).thenReturn(interview);
    when(persistence.lockNotification(NOTIFICATION_ID)).thenReturn(pending, sending);
    when(persistence.insertIdempotency(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    when(ids.newId()).thenReturn(ATTEMPT_ID);
    when(notifications.updateNotification(
            eq(NOTIFICATION_ID),
            eq("EMAIL"),
            eq("SENDING_EXTERNAL"),
            isNull(),
            isNull(),
            isNull(),
            eq(1),
            eq(0L),
            eq(ACTOR_ID),
            eq(NOW)))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(delivery.deliver(any()))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(transactions.committed()).isOne();
              if (providerFails) {
                throw failure;
              }
              if (providerRejects) {
                return new DeliveryResult(false, null, errorCode, errorDetail);
              }
              return new DeliveryResult(true, rawExternalMessageId, "IGNORED", "ignored detail");
            });
    when(notifications.updateNotification(
            eq(NOTIFICATION_ID),
            eq("EMAIL"),
            eq(finalStatus),
            eq(storedExternalMessageId),
            eq(errorCode),
            eq(errorDetail),
            eq(0),
            eq(1L),
            eq(ACTOR_ID),
            eq(NOW)))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(attempts.insertNotificationAttempt(
            eq(ATTEMPT_ID),
            eq(NOTIFICATION_ID),
            eq("EXTERNAL_RETRY"),
            eq("EMAIL"),
            eq(failed ? "FAILED" : "SENT"),
            eq(storedExternalMessageId),
            eq(errorCode),
            eq(errorDetail),
            eq(ACTOR_ID),
            eq(NOW)))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              return 1;
            });
    when(notifications.findNotification(NOTIFICATION_ID)).thenReturn(sent);
    when(assembler.toResponse(sent)).thenReturn(response);

    InterviewApplicationManager manager = proxiedManager(
                transactions,
                persistence,
                notifications,
                attempts,
                idempotency,
                participants,
                authorization,
                delivery,
                ids,
                assembler);
    InterviewNotificationResponse actual;
    PrintStream originalError = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      actual = manager.retryNotificationResponse(NOTIFICATION_ID, "retry-transaction-1");
    } finally {
      System.setErr(originalError);
    }
    if (providerFails) {
      assertThat(bytes.toString(StandardCharsets.UTF_8))
          .contains("external-notification-delivery-exception", NOTIFICATION_ID, "EMAIL",
              "java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
              "InterviewApplicationManagerNotificationTransactionTest.java")
          .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
      assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");
    }
    if (providerRejects) {
      assertThat(bytes.toString(StandardCharsets.UTF_8))
          .contains("external-notification-delivery-failed", NOTIFICATION_ID, "EMAIL")
          .doesNotContain("15500000000", "audit-person@example.invalid", "test-secret-marker");
    }

    assertThat(actual).isSameAs(response);
    assertThat(transactions.begun()).isEqualTo(2);
    assertThat(transactions.committed()).isEqualTo(2);
  }

  private static InterviewApplicationManager proxiedManager(
      RecordingTransactionManager transactions,
      InterviewPersistenceService persistence,
      NotificationIntentPersistenceService notifications,
      NotificationAttemptPersistenceService attempts,
      InterviewIdempotencyPersistenceService idempotency,
      ParticipantPersistenceService participants,
      AuthorizationService authorization,
      NotificationDeliveryPort delivery,
      PlatformIdGenerator ids,
      InterviewAssembler assembler) {
    InterviewApplicationManager target = new InterviewApplicationManager();
    ReflectionTestUtils.setField(target, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(persistence.findCandidateId(any())).thenReturn("C".repeat(26));
    when(persistence.findNotificationCandidateId(any())).thenReturn("C".repeat(26));
    ReflectionTestUtils.setField(target, "persistence", persistence);
    ReflectionTestUtils.setField(target, "notificationIntentPersistenceService", notifications);
    ReflectionTestUtils.setField(target, "notificationAttemptPersistenceService", attempts);
    ReflectionTestUtils.setField(target, "interviewIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(target, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(target, "authorization", authorization);
    ReflectionTestUtils.setField(target, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(target, "delivery", delivery);
    ReflectionTestUtils.setField(target, "ids", ids);
    ReflectionTestUtils.setField(target, "assembler", assembler);
    ReflectionTestUtils.setField(target, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    var transactionTemplateField =
        ReflectionUtils.findField(InterviewApplicationManager.class, "transactionTemplate");
    if (transactionTemplateField != null) {
      ReflectionTestUtils.setField(
          target, "transactionTemplate", new TransactionTemplate(transactions));
    }

    ProxyFactory proxyFactory = new ProxyFactory(target);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    return (InterviewApplicationManager) proxyFactory.getProxy();
  }

  private static CurrentActorSnapshot actor() {
    return new CurrentActorSnapshot(
        ACTOR_ID,
        "测试招聘用户",
        "recruiter@example.com",
        DEPARTMENT_ID,
        Set.of(),
        Set.of(),
        Set.of(),
        Set.of("RECRUITER"),
        Set.of("interview.feedback.read-authorized", "interview.schedule"),
        false,
        false);
  }

  private static NotificationIntent notification(String status, long version, String externalId) {
    NotificationIntent value =
        new NotificationIntent(
            NOTIFICATION_ID,
            INTERVIEW_ID,
            "INTERVIEW_INVITATION",
            "CANDIDATE",
            "C".repeat(26),
            "EMAIL",
            "M".repeat(26),
            "面试邀请",
            "请参加面试",
            status,
            externalId,
            null,
            null,
            status.equals("PENDING_MANUAL") ? 0 : 1,
            version);
    value.setUpdateTime(NOW);
    return value;
  }

  private static Interview interview() {
    Interview value = new Interview();
    value.setId(INTERVIEW_ID);
    value.setOwnerUserId(ACTOR_ID);
    value.setOwnerDepartmentId(DEPARTMENT_ID);
    return value;
  }

  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private final AtomicInteger begun = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();

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
    protected void doRollback(DefaultTransactionStatus status) {}

    int begun() {
      return begun.get();
    }

    int committed() {
      return committed.get();
    }
  }
}
