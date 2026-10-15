package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.application.adapter.CandidateDirectoryAdapter;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.dto.ResolveIdentityConflictDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.service.CandidateIdentityPersistenceService;
import com.ezaigc.hr.talent.service.CandidateMergeHistoryPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实应用调用与 Spring 事务；行锁替身用于可重复调度，不替代 MySQL 方言验证。 */
class CandidateMergeConcurrencyTest {
  @Test
  void committedNewBusinessFactBlocksMergeWaitingForCandidateLock() throws Exception {
    var fixture = new Fixture();
    var writerHasLock = new CountDownLatch(1);
    var finishWriter = new CountDownLatch(1);
    var mergerReachedLock = new CountDownLatch(1);
    fixture.beforeLock = id -> {
      if ("candidate".equals(id)) mergerReachedLock.countDown();
    };
    try (var workers = Executors.newFixedThreadPool(2)) {
      var writer = workers.submit(() -> fixture.transactions.executeWithoutResult(status -> {
        fixture.writer.lockActive("merged");
        writerHasLock.countDown();
        await(finishWriter);
        fixture.businessHistory.set(true);
      }));
      assertThat(writerHasLock.await(5, TimeUnit.SECONDS)).isTrue();
      var merge = workers.submit(() -> rejectedCode(() -> fixture.merge.resolveConflict("conflict",
          new ResolveIdentityConflictDTO("MERGE", "candidate", "人工核验"))));
      assertThat(mergerReachedLock.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(merge.isDone()).isFalse();
      finishWriter.countDown();
      writer.get(5, TimeUnit.SECONDS);
      assertThat(merge.get(5, TimeUnit.SECONDS)).isEqualTo("CANDIDATE_MERGE_HAS_BUSINESS_HISTORY");
    } finally {
      finishWriter.countDown();
    }
    verify(fixture.candidates, never()).markCandidateMerged(any(), anyLong(), any(), any());
    verify(fixture.identities, never()).moveIdentities(any(), any(), any(), any());
  }

  @Test
  void completedMergeRejectsLateNewFactOnOldCandidateDespiteEarlierActiveSnapshot() throws Exception {
    var fixture = new Fixture();
    var mergerHasBothLocks = new CountDownLatch(1);
    var finishMerge = new CountDownLatch(1);
    var writerReachedLock = new CountDownLatch(1);
    fixture.beforeHistory = () -> {
      assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
          .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
      mergerHasBothLocks.countDown();
      await(finishMerge);
    };
    try (var workers = Executors.newFixedThreadPool(2)) {
      var merge = workers.submit(() -> fixture.merge.resolveConflict("conflict",
          new ResolveIdentityConflictDTO("MERGE", "candidate", "人工核验")));
      assertThat(mergerHasBothLocks.await(5, TimeUnit.SECONDS)).isTrue();
      fixture.beforeLock = id -> {
        if ("merged".equals(id)) writerReachedLock.countDown();
      };
      var writer = workers.submit(() -> rejectedCode(() -> fixture.transactions.executeWithoutResult(status -> {
        assertThat(fixture.candidates.findCandidate("merged").getStatus()).isEqualTo("ACTIVE");
        fixture.writer.lockActive("merged");
        fixture.businessHistory.set(true);
      })));
      assertThat(writerReachedLock.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(writer.isDone()).isFalse();
      finishMerge.countDown();
      merge.get(5, TimeUnit.SECONDS);
      assertThat(writer.get(5, TimeUnit.SECONDS)).isEqualTo("CANDIDATE_NOT_FOUND");
    } finally {
      finishMerge.countDown();
    }
    assertThat(fixture.businessHistory).isFalse();
    assertThat(fixture.rows.get("merged").getStatus()).isEqualTo("DELETED");
  }

  @Test
  void oppositeMergesUseSameLockOrderAndOnlyOneCanCommit() throws Exception {
    var fixture = new Fixture();
    var begin = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> { await(begin); return mergeResult(fixture, "candidate"); });
      var second = workers.submit(() -> { await(begin); return mergeResult(fixture, "merged"); });
      begin.countDown();
      assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("COMMITTED", "CANDIDATE_NOT_FOUND");
    }
    assertThat(fixture.mergedCount).hasValue(1);
  }

  private static String mergeResult(Fixture fixture, String chosen) {
    try {
      fixture.merge.resolveConflict("conflict", new ResolveIdentityConflictDTO("MERGE", chosen, "人工核验"));
      return "COMMITTED";
    } catch (PlatformException exception) {
      return exception.code();
    }
  }

  private static String rejectedCode(Runnable operation) {
    try {
      operation.run();
      throw new AssertionError("必须拒绝不安全命令");
    } catch (PlatformException exception) {
      return exception.code();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("并发调度超时");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private static final class Fixture {
    final Map<String, Candidate> rows = new ConcurrentHashMap<>();
    final Map<String, ReentrantLock> locks = Map.of("candidate", new ReentrantLock(), "merged", new ReentrantLock());
    final AtomicBoolean businessHistory = new AtomicBoolean();
    final AtomicInteger mergedCount = new AtomicInteger();
    final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
    final CandidateIdentityPersistenceService identities = mock(CandidateIdentityPersistenceService.class);
    final CandidateRecruitmentStateQuery recruitment = mock(CandidateRecruitmentStateQuery.class);
    final CandidateScreeningStateQuery screening = mock(CandidateScreeningStateQuery.class);
    final LocalTransactions transactionManager = new LocalTransactions();
    final TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    final TalentIntakeApplicationManager merge;
    final CandidateDirectoryAdapter writer;
    volatile Consumer<String> beforeLock = ignored -> {};
    volatile Runnable beforeHistory = () -> {};

    Fixture() {
      transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
      rows.put("candidate", candidate("candidate"));
      rows.put("merged", candidate("merged"));
      when(candidates.findCandidate(anyString())).thenAnswer(call -> snapshot(call.getArgument(0)));
      when(candidates.lockCandidate(anyString())).thenAnswer(call -> {
        String id = call.getArgument(0); acquire(id); return id;
      });
      when(candidates.findCandidateForUpdate(anyString())).thenAnswer(call -> {
        String id = call.getArgument(0); acquire(id); return snapshot(id);
      });
      when(candidates.hasMergeProtectedHistory(anyString())).thenAnswer(call -> {
        beforeHistory.run();
        return "merged".equals(call.getArgument(0)) && businessHistory.get();
      });
      when(candidates.markCandidateMerged(any(), anyLong(), any(), any())).thenAnswer(call -> {
        rows.get(call.getArgument(0)).setStatus("DELETED"); mergedCount.incrementAndGet(); return 1;
      });
      when(candidates.listByIds(any())).thenAnswer(call ->
          ((Collection<String>) call.getArgument(0)).stream().map(this::snapshot).toList());
      var target = new TalentIntakeApplicationManager();
      var auth = mock(AuthorizationService.class);
      var actor = new CurrentActorSnapshot(); actor.setUserId("actor"); actor.setSuperAdmin(true);
      when(auth.currentActor()).thenReturn(actor);
      var conflicts = mock(IdentityConflictPersistenceService.class);
      var conflict = new IdentityConflict(); conflict.setId("conflict"); conflict.setIntakeId("intake");
      conflict.setCandidateIdA("candidate"); conflict.setCandidateIdB("merged");
      when(conflicts.findConflict("conflict")).thenReturn(conflict);
      when(conflicts.resolveConflict(any(), any(), any(), any(), any(), any())).thenReturn(1);
      var intakes = mock(TalentIntakePersistenceService.class);
      var intake = new ResumeIntake(); intake.setId("intake"); intake.setStatus("READY_FOR_CONFIRMATION");
      intake.setOwnerUserId("actor"); intake.setOwnerDepartmentId("department");
      when(intakes.findIntake("intake")).thenReturn(intake);
      var access = mock(CandidateReadAccess.class);
      when(access.evaluate(any())).thenReturn(new CandidateReadDecision(Set.of("candidate", "merged"), Set.of(), Map.of()));
      var ids = mock(PlatformIdGenerator.class); when(ids.newId()).thenReturn("new-id");
      ReflectionTestUtils.setField(target, "authorization", auth);
      ReflectionTestUtils.setField(target, "candidatePersistenceService", candidates);
      ReflectionTestUtils.setField(target, "candidateIdentityPersistenceService", identities);
      ReflectionTestUtils.setField(target, "candidateMergeHistoryPersistenceService", mock(CandidateMergeHistoryPersistenceService.class));
      ReflectionTestUtils.setField(target, "identityConflictPersistenceService", conflicts);
      ReflectionTestUtils.setField(target, "persistence", intakes);
      ReflectionTestUtils.setField(target, "resumePersistenceService", mock(ResumePersistenceService.class));
      ReflectionTestUtils.setField(target, "recruitment", recruitment);
      ReflectionTestUtils.setField(target, "screening", screening);
      ReflectionTestUtils.setField(target, "candidateReadAccess", access);
      ReflectionTestUtils.setField(target, "assembler", new TalentIntakeAssembler());
      ReflectionTestUtils.setField(target, "searchProjection", mock(ResumeSearchProjectionRecorder.class));
      ReflectionTestUtils.setField(target, "talentProcessEventPersistenceService", mock(TalentProcessEventPersistenceService.class));
      ReflectionTestUtils.setField(target, "ids", ids);
      ReflectionTestUtils.setField(target, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
      merge = proxy(target, TalentIntakeApplicationManager.class);
      var directory = new CandidateDirectoryAdapter();
      ReflectionTestUtils.setField(directory, "candidatePersistenceService", candidates);
      writer = proxy(directory, CandidateDirectoryAdapter.class);
    }

    private void acquire(String id) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      beforeLock.accept(id);
      ReentrantLock lock = locks.get(id);
      if (lock.isHeldByCurrentThread()) return;
      lock.lock();
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) { lock.unlock(); }
      });
    }

    private Candidate snapshot(String id) {
      var row = rows.get(id);
      if (row == null) return null;
      var copy = candidate(id); copy.setStatus(row.getStatus()); return copy;
    }

    private <T> T proxy(T target, Class<T> type) {
      var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
      factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
      return type.cast(factory.getProxy());
    }
  }

  private static Candidate candidate(String id) {
    var row = new Candidate(); row.setId(id); row.setStatus("ACTIVE"); row.setVersion(7);
    row.setFullName("合成候选人"); row.setOwnerUserId("actor"); row.setOwnerDepartmentId("department");
    return row;
  }

  private static final class LocalTransactions extends AbstractPlatformTransactionManager {
    private final ThreadLocal<LocalTransaction> current = new ThreadLocal<>();
    @Override protected Object doGetTransaction() {
      return current.get() == null ? new LocalTransaction() : current.get();
    }
    @Override protected boolean isExistingTransaction(Object transaction) {
      return current.get() == transaction;
    }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
      current.set((LocalTransaction) transaction);
    }
    @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) {
      ((LocalTransaction) status.getTransaction()).rollbackOnly = true;
    }
    @Override protected void doCommit(DefaultTransactionStatus status) {}
    @Override protected void doRollback(DefaultTransactionStatus status) {}
    @Override protected void doCleanupAfterCompletion(Object transaction) { current.remove(); }

    private static final class LocalTransaction implements SmartTransactionObject {
      private boolean rollbackOnly;
      @Override public boolean isRollbackOnly() { return rollbackOnly; }
      @Override public void flush() {}
    }
  }
}
