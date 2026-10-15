package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.application.assembler.RiskAssembler;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.service.CompanyBlacklistCurrentPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.RiskPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class RiskProjectionConcurrencyTest {
  private static final String CANDIDATE = "C".repeat(26);
  private static final String PRIOR = "P".repeat(26);
  private static final String WINNER = "W".repeat(26);
  private static final String NEW = "N".repeat(26);
  private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "REACTIVATE", "CORRECT", "DISABLE"})
  void staleProjectionWriteCannotOverwriteAConcurrentActiveFact(String action) {
    verifyProjectionWrite(action, true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "REACTIVATE", "CORRECT", "DISABLE"})
  void currentProjectionWriteAppendsTheCorrectHistoryAndCommits(String action) {
    verifyProjectionWrite(action, false);
  }

  private void verifyProjectionWrite(String action, boolean concurrent) {
    RiskApplicationManager manager = new RiskApplicationManager();
    RiskPersistenceService persistence = mock(RiskPersistenceService.class);
    CompanyBlacklistCurrentPersistenceService current = mock(CompanyBlacklistCurrentPersistenceService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(authorization.currentActor()).thenReturn(actor);
    when(actor.getUserId()).thenReturn("hr");
    boolean activation = action.equals("CREATE") || action.equals("REACTIVATE");
    CompanyBlacklistCurrent original = action.equals("CREATE") ? null : projection(PRIOR, !activation);
    AtomicReference<CompanyBlacklistCurrent> projection = new AtomicReference<>(original);
    when(current.lockCurrent(CANDIDATE)).thenAnswer(call -> projection.get());
    CompanyBlacklistRecord prior = record(PRIOR, activation ? "DISABLED" : "ACTIVE");
    when(persistence.findBlacklist(PRIOR)).thenReturn(prior);
    when(persistence.currentBlacklist(CANDIDATE)).thenReturn(prior);
    when(persistence.findBlacklist(NEW)).thenReturn(record(NEW, action.equals("DISABLE") ? "DISABLED" : "ACTIVE"));
    when(persistence.insertBlacklist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> {
          // 读取旧投影后，另一事务先提交新的生效事实。
          if (concurrent) projection.set(projection(WINNER, true));
          return 1;
        });
    when(current.projectBlacklist(anyString(), any(), anyString(), anyBoolean(), anyString(), any()))
        .thenAnswer(call -> {
          String expected = call.getArgument(1);
          String actual = projection.get() == null ? null : projection.get().getRecordId();
          if (!Objects.equals(actual, expected)) return 0;
          projection.set(projection(call.getArgument(2), call.getArgument(3)));
          return 1;
        });
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidates", mock(CandidateDirectoryService.class));
    CandidatePersistenceService candidatePersistence = mock(CandidatePersistenceService.class);
    when(candidatePersistence.lockCandidate(CANDIDATE)).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(manager, "candidatePersistence", candidatePersistence);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "companyBlacklistCurrentPersistenceService", current);
    ReflectionTestUtils.setField(manager, "assembler", new RiskAssembler());
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> NEW);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    Transactions transactions = new Transactions();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(manager);
    factory.setProxyTargetClass(true);
    factory.addAdvice(interceptor);
    RiskApplicationManager proxy = (RiskApplicationManager) factory.getProxy();

    Runnable write = () -> {
      if (activation) proxy.createBlacklist(CANDIDATE, "QA", "合成依据", "MANUAL", "test", NOW);
      else if (action.equals("CORRECT")) proxy.correctBlacklist(PRIOR, "QA", "合成依据", "MANUAL", "test", NOW);
      else proxy.disableBlacklist(PRIOR, "合成依据");
    };
    if (concurrent) {
      assertThatThrownBy(write::run).isInstanceOfSatisfying(PlatformException.class, error -> {
      assertThat(error.status().value()).isEqualTo(409);
      assertThat(error.code()).isEqualTo(activation ? "COMPANY_BLACKLIST_EXISTS" : "BLACKLIST_RECORD_NOT_CURRENT");
      });
      assertThat(projection.get().getRecordId()).isEqualTo(WINNER);
      assertThat(projection.get().isActive()).isTrue();
      assertThat(transactions.rolledBack).isOne();
      assertThat(transactions.committed).isZero();
      verify(persistence, never()).findBlacklist(NEW);
      verify(persistence, never()).countActiveApplications(CANDIDATE);
    } else {
      write.run();
      assertThat(projection.get().getRecordId()).isEqualTo(NEW);
      assertThat(projection.get().isActive()).isEqualTo(!action.equals("DISABLE"));
      assertThat(transactions.committed).isOne();
      assertThat(transactions.rolledBack).isZero();
      verify(persistence).insertBlacklist(NEW, CANDIDATE,
          activation ? "ACTIVATION" : action.equals("CORRECT") ? "CORRECTION" : "DISABLE",
          activation ? "ACTIVE" : action.equals("CORRECT") ? "CORRECTED" : "DISABLED",
          action.equals("DISABLE") ? "DISABLED" : "QA", "合成依据", "MANUAL", "test",
          activation ? null : PRIOR, "hr", NOW, NOW);
    }
    verify(current).projectBlacklist(CANDIDATE, action.equals("CREATE") ? null : PRIOR,
        NEW, !action.equals("DISABLE"), "hr", NOW);
    var lockOrder = inOrder(candidatePersistence, current);
    lockOrder.verify(candidatePersistence).lockCandidate(CANDIDATE);
    lockOrder.verify(current).lockCurrent(CANDIDATE);
    verify(current, never()).getById(CANDIDATE);
    verify(persistence, never()).currentBlacklist(CANDIDATE);
  }

  private static CompanyBlacklistCurrent projection(String recordId, boolean active) {
    CompanyBlacklistCurrent value = new CompanyBlacklistCurrent();
    value.setCandidateId(CANDIDATE);
    value.setRecordId(recordId);
    value.setActive(active);
    return value;
  }

  private static CompanyBlacklistRecord record(String id, String status) {
    CompanyBlacklistRecord value = new CompanyBlacklistRecord();
    value.setId(id);
    value.setCandidateId(CANDIDATE);
    value.setStatus(status);
    value.setEvidenceReference("test");
    return value;
  }

  private static final class Transactions extends AbstractPlatformTransactionManager {
    int committed;
    int rolledBack;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
    @Override protected void doCommit(DefaultTransactionStatus status) { committed++; }
    @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack++; }
  }
}
