package com.ezaigc.hr.recruiting.recruitment.application.manager;

import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.service.CollaborationRequestPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.aop.framework.ProxyFactory;
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
class EngagementLockApplicationManagerExpiryTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");

  @Test
  void isolatesEachExpiryAndContinuesAfterOneItemRollsBack() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    EngagementLockPersistenceService persistence = mock(EngagementLockPersistenceService.class);
    CollaborationRequestPersistenceService collaborations =
        mock(CollaborationRequestPersistenceService.class);
    LockHistoryPersistenceService histories = mock(LockHistoryPersistenceService.class);
    AtomicBoolean dueQueryOutsideTransaction = new AtomicBoolean();
    AtomicBoolean itemWritesInsideTransaction = new AtomicBoolean(true);
    EngagementLock first = lock("lock-1", "candidate-1", 3);
    EngagementLock second = lock("lock-2", "candidate-2", 7);
    IllegalStateException failure = new IllegalStateException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new RuntimeException("test-secret-marker"));

    when(persistence.due(NOW, 2))
        .thenAnswer(
            invocation -> {
              dueQueryOutsideTransaction.set(
                  !TransactionSynchronizationManager.isActualTransactionActive());
              return List.of(first, second);
            });
    when(persistence.expire(anyString(), anyLong(), eq(NOW)))
        .thenAnswer(
            invocation -> {
              itemWritesInsideTransaction.set(
                  itemWritesInsideTransaction.get()
                      && TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });
    when(collaborations.cancelCollaborations(anyString(), anyString(), anyString(), eq(NOW)))
        .thenAnswer(
            invocation -> {
              itemWritesInsideTransaction.set(
                  itemWritesInsideTransaction.get()
                      && TransactionSynchronizationManager.isActualTransactionActive());
              if ("lock-1".equals(invocation.getArgument(0))) {
                throw failure;
              }
              return 1;
            });
    when(histories.insertHistory(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            isNull(),
            isNull(),
            anyString(),
            anyString(),
            eq(NOW)))
        .thenAnswer(
            invocation -> {
              itemWritesInsideTransaction.set(
                  itemWritesInsideTransaction.get()
                      && TransactionSynchronizationManager.isActualTransactionActive());
              return 1;
            });

    EngagementLockApplicationManager target = new EngagementLockApplicationManager();
    ReflectionTestUtils.setField(target, "candidateCommands", mock(CompanyBlacklistQueryService.class));
    ReflectionTestUtils.setField(target, "persistence", persistence);
    ReflectionTestUtils.setField(target, "collaborationRequestPersistenceService", collaborations);
    ReflectionTestUtils.setField(target, "lockHistoryPersistenceService", histories);
    ReflectionTestUtils.setField(target, "ids", (PlatformIdGenerator) () -> "history-id");
    ReflectionTestUtils.setField(target, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    if (ReflectionUtils.findField(EngagementLockApplicationManager.class, "transactionTemplate")
        != null) {
      ReflectionTestUtils.setField(
          target, "transactionTemplate", new TransactionTemplate(transactions));
    }
    EngagementLockApplicationManager manager = proxy(target, transactions);

    PrintStream originalError = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      assertThat(manager.expireDue(2)).isEqualTo(1);
    } finally {
      System.setErr(originalError);
    }
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains("engagement-lock-expiry-failed", "lock-1", "expectedVersion=3",
            "java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
            "EngagementLockApplicationManagerExpiryTransactionTest.java")
        .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");

    assertThat(dueQueryOutsideTransaction).isTrue();
    assertThat(itemWritesInsideTransaction).isTrue();
    assertThat(transactions.begun()).isEqualTo(2);
    assertThat(transactions.committed()).isEqualTo(1);
    assertThat(transactions.rolledBack()).isEqualTo(1);
    verify(persistence).expire("lock-2", 7, NOW);
    verify(histories, never())
        .insertHistory(
            anyString(),
            eq("lock-1"),
            anyString(),
            anyString(),
            any(),
            any(),
            any(),
            anyString(),
            anyString(),
            eq(NOW));
  }

  private static EngagementLockApplicationManager proxy(
      EngagementLockApplicationManager target, RecordingTransactionManager transactions) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    return (EngagementLockApplicationManager) proxyFactory.getProxy();
  }

  private static EngagementLock lock(String id, String candidateId, int version) {
    EngagementLock result = new EngagementLock();
    result.setId(id);
    result.setCandidateId(candidateId);
    result.setOwnerUserId("owner-1");
    result.setVersion(version);
    return result;
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
