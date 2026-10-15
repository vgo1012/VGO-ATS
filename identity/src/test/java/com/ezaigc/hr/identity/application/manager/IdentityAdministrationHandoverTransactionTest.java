package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.identity.api.HandoverResource;
import com.ezaigc.hr.identity.api.HandoverResourceContributor;
import com.ezaigc.hr.identity.application.assembler.IdentityAdministrationAssembler;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.domain.response.HandoverBatchDetailsResponse;
import com.ezaigc.hr.identity.service.HandoverBatchPersistenceService;
import com.ezaigc.hr.identity.service.HandoverItemPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
class IdentityAdministrationHandoverTransactionTest {

  private static final String ACTOR_ID = "01JACTOR00000000000000000";
  private static final String BATCH_ID = "01JBATCH00000000000000000";
  private static final String RESOURCE_ID = "01JRESOURCE000000000000000";
  private static final Instant NOW = Instant.parse("2026-09-05T04:00:00Z");

  @Test
  void isolatesEachTransferFailureAndPersistsItsDiagnosticResult() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    IdentityAdministrationPersistenceService persistence =
        mock(IdentityAdministrationPersistenceService.class);
    IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
    HandoverBatchPersistenceService batches = mock(HandoverBatchPersistenceService.class);
    HandoverItemPersistenceService items = mock(HandoverItemPersistenceService.class);
    IdentityAuditEventPersistenceService auditEvents =
        mock(IdentityAuditEventPersistenceService.class);
    IdentityAdministrationAssembler assembler = mock(IdentityAdministrationAssembler.class);
    RuntimeException transferFailure = new IllegalStateException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    transferFailure.addSuppressed(new RuntimeException("test-secret-marker"));
    HandoverResourceContributor contributor = mock(HandoverResourceContributor.class);

    when(accounts.findById("source")).thenReturn(activeUser("source"));
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(contributor.resourceType()).thenReturn("APPLICATION");
    when(contributor.preview("source"))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return List.of(
                  new HandoverResource(
                      "APPLICATION", RESOURCE_ID, "候选人申请", true, true, null));
            });
    when(contributor.transfer(RESOURCE_ID, "source", "target", ACTOR_ID, "离职交接"))
        .thenAnswer(
            ignored -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              throw transferFailure;
            });
    when(batches.findHandoverById(BATCH_ID)).thenReturn(failedBatch());
    when(items.findHandoverItems(BATCH_ID)).thenReturn(List.of());
    when(persistence.completeHandoverBatch(BATCH_ID, "FAILED", 0, 1, 0, NOW))
        .thenReturn(1);
    when(assembler.toDetails(any(HandoverBatchRecord.class), anyList()))
        .thenReturn(mock(HandoverBatchDetailsResponse.class));

    IdentityAdministrationApplicationManager service =
        proxiedService(
            transactions, persistence, accounts, batches, items, auditEvents, assembler, contributor);

    PrintStream originalError = System.err;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
      System.setErr(capture);
      assertThatCode(() -> service.executeHandover(
          "source", "target", "ALL", Set.of(), "离职交接", "handover-key"))
          .doesNotThrowAnyException();
    } finally {
      System.setErr(originalError);
    }
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains("handover-item-transfer-failed", BATCH_ID, RESOURCE_ID,
            "java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
            "IdentityAdministrationHandoverTransactionTest.java")
        .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
    assertThat(transferFailure.getMessage()).isEqualTo("audit-person@example.invalid");

    assertThat(transactions.begun()).isEqualTo(4);
    assertThat(transactions.committed()).isEqualTo(3);
    assertThat(transactions.rolledBack()).isOne();
    verify(items)
        .insertHandoverItem(
            anyString(),
            eq(BATCH_ID),
            eq("APPLICATION"),
            eq(RESOURCE_ID),
            eq("FAILED"),
            eq(true),
            eq("HANDOVER_ITEM_FAILED"),
            eq("交接事项处理失败"),
            eq("source"),
            eq("target"),
            isNull(),
            eq(NOW));
    verify(persistence).completeHandoverBatch(BATCH_ID, "FAILED", 0, 1, 0, NOW);
  }

  private static IdentityAdministrationApplicationManager proxiedService(
      RecordingTransactionManager transactions,
      IdentityAdministrationPersistenceService persistence,
      IdentityAccountPersistenceService accounts,
      HandoverBatchPersistenceService batches,
      HandoverItemPersistenceService items,
      IdentityAuditEventPersistenceService auditEvents,
      IdentityAdministrationAssembler assembler,
      HandoverResourceContributor contributor) {
    IdentityAdministrationApplicationManager target =
        new IdentityAdministrationApplicationManager();
    CurrentActorProvider actorProvider =
        () -> Optional.of(new CurrentActor(ACTOR_ID, Set.of(), true));
    PlatformIdGenerator ids = () -> BATCH_ID;
    ReflectionTestUtils.setField(target, "persistence", persistence);
    ReflectionTestUtils.setField(target, "accounts", accounts);
    ReflectionTestUtils.setField(target, "handoverBatchPersistenceService", batches);
    ReflectionTestUtils.setField(target, "handoverItemPersistenceService", items);
    ReflectionTestUtils.setField(target, "identityAuditEventPersistenceService", auditEvents);
    ReflectionTestUtils.setField(target, "actorProvider", actorProvider);
    ReflectionTestUtils.setField(target, "ids", ids);
    ReflectionTestUtils.setField(target, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(target, "handoverContributors", List.of(contributor));
    ReflectionTestUtils.setField(target, "assembler", assembler);
    var transactionTemplateField =
        ReflectionUtils.findField(IdentityAdministrationApplicationManager.class, "transactionTemplate");
    if (transactionTemplateField != null) {
      ReflectionTestUtils.setField(
          target, "transactionTemplate", new TransactionTemplate(transactions));
    }

    ProxyFactory proxyFactory = new ProxyFactory(target);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    return (IdentityAdministrationApplicationManager) proxyFactory.getProxy();
  }

  private static UserAccountRecord activeUser(String id) {
    return new UserAccountRecord(
        id,
        "账号",
        "user@example.com",
        "user@example.com",
        "{noop}secret",
        "department",
        "ACTIVE",
        false,
        0);
  }

  private static HandoverBatchRecord failedBatch() {
    return new HandoverBatchRecord(
        BATCH_ID,
        "source",
        "target",
        "ALL",
        "FAILED",
        1,
        0,
        1,
        0,
        "离职交接",
        ACTOR_ID,
        NOW,
        NOW,
        1,
        "handover-key");
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
