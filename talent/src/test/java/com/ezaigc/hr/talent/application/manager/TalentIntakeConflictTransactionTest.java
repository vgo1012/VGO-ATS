package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.application.support.IdentityConflictRecorder;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Identity;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.service.CandidateIdentityPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class TalentIntakeConflictTransactionTest {
  @ParameterizedTest
  @CsvSource({
      "false, CONTACT_NAME_MISMATCH, false",
      "true, CONTACT_NAME_MISMATCH, false",
      "false, CANDIDATE_ACCESS_REQUIRED, false",
      "true, CANDIDATE_ACCESS_REQUIRED, false",
      "false, CONTACT_NAME_MISMATCH, true",
      "true, CONTACT_NAME_MISMATCH, true"
  })
  void recordsConflictOnlyAfterConfirmationRollsBackAndReleasesCandidateLock(
      boolean automatic, String conflictType, boolean recordingFails) {
    var manager = new TalentIntakeApplicationManager();
    var intakes = mock(TalentIntakePersistenceService.class);
    var identities = mock(CandidateIdentityPersistenceService.class);
    var candidates = mock(CandidatePersistenceService.class);
    var recorder = mock(IdentityConflictRecorder.class);
    var conflictPersistence = mock(IdentityConflictPersistenceService.class);
    var authorization = mock(AuthorizationService.class);
    var transactions = new ConfirmationTransactions();
    var proxy = transactionProxy(manager, transactions);
    var template = new TransactionTemplate(transactions);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setTimeout(7);
    template.setName("confirmation-template");
    Instant now = Instant.parse("2026-10-09T08:00:00Z");
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "candidateIdentityPersistenceService", identities);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "identityConflictPersistenceService",
        conflictPersistence);
    ReflectionTestUtils.setField(manager, "conflictRecorder", recorder);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "transactionTemplate", template);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    var actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    when(authorization.currentActor()).thenReturn(actor);
    when(authorization.canAccess(eq("talent.manage"), any()))
        .thenReturn(!"CANDIDATE_ACCESS_REQUIRED".equals(conflictType));
    ResumeIntake ready = intake("READY_FOR_CONFIRMATION", 4);
    when(conflictPersistence.lockIntake("intake-1")).thenReturn(ready);
    if (automatic) {
      when(intakes.findIntake("intake-1")).thenReturn(intake("PARSING", 3), ready, ready);
      when(intakes.advanceIntake("intake-1", "PARSING", "READY_FOR_CONFIRMATION",
          null, "actor-1", now)).thenReturn(1);
    } else {
      when(intakes.findIntake("intake-1")).thenReturn(ready);
    }
    when(identities.findIdentity(eq("PHONE"), any())).thenAnswer(invocation -> {
      var contact = new Identity();
      contact.setId("phone-identity-1");
      contact.setCandidateId("candidate-1");
      contact.setIdentityType("PHONE");
      contact.setNormalizedValueHash(invocation.getArgument(1));
      return contact;
    });
    var candidate = new Candidate();
    candidate.setId("candidate-1");
    candidate.setFullName("原姓名");
    candidate.setStatus("ACTIVE");
    when(candidates.lockCandidate("candidate-1")).thenAnswer(ignored -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
          .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
      transactions.candidateLockHeld = true;
      return "candidate-1";
    });
    when(candidates.findCandidateForUpdate("candidate-1")).thenReturn(candidate);
    var persistenceFailure = new IllegalStateException("synthetic conflict persistence failure");
    doAnswer(ignored -> {
      assertThat(transactions.rollbacks).as("先回滚确认事务，再写冲突审计").isOne();
      assertThat(transactions.candidateLockHeld).as("外层候选人锁已释放").isFalse();
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      if (recordingFails) throw persistenceFailure;
      return "conflict-1";
    }).when(recorder).record("intake-1", "candidate-1", null, conflictType, 4);
    String name = "CONTACT_NAME_MISMATCH".equals(conflictType) ? "不同姓名" : "原姓名";
    ResumeFacts facts = mock(ResumeFacts.class);
    CandidateIdentity identity = mock(CandidateIdentity.class);
    when(facts.candidate()).thenReturn(identity);
    when(identity.fullName()).thenReturn(name);
    when(identity.phone()).thenReturn("13800138000");

    var failure = assertThatThrownBy(() -> {
      if (automatic) proxy.autoConfirm("intake-1", facts);
      else proxy.confirm("intake-1", new ConfirmIntakeDTO(null, name, null, "13800138000", 4L));
    });
    if (recordingFails) failure.isSameAs(persistenceFailure);
    else failure.isInstanceOfSatisfying(PlatformException.class, platformFailure -> {
      assertThat(platformFailure.code()).isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW");
      assertThat(platformFailure.status().value()).isEqualTo(409);
      assertThat(platformFailure.recoverable()).isTrue();
    });

    verify(recorder).record("intake-1", "candidate-1", null, conflictType, 4);
    var locks = inOrder(candidates, conflictPersistence);
    locks.verify(candidates).lockCandidate("candidate-1");
    locks.verify(conflictPersistence).lockIntake("intake-1");
    verify(candidates, never()).insertCandidate(any(), any(), any(), any(), any(), any(), any(), any());
    assertThat(transactions.commits).isEqualTo(automatic ? 1 : 0);
    assertThat(transactions.rollbacks).isOne();
    assertThat(transactions.definitions).hasSize(automatic ? 2 : 1);
    var confirmation = transactions.definitions.getLast();
    assertThat(confirmation.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
    assertThat(confirmation.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThat(confirmation.getTimeout()).isEqualTo(7);
    assertThat(confirmation.getName()).isEqualTo("confirmation-template");
    assertThat(confirmation.isReadOnly()).isFalse();
    assertThat(template.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsOuterTransactionsBeforeReadingOrRecording(boolean automatic) {
    var target = new TalentIntakeApplicationManager();
    var recorder = mock(IdentityConflictRecorder.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    var transactions = new ConfirmationTransactions();
    ReflectionTestUtils.setField(target, "conflictRecorder", recorder);
    ReflectionTestUtils.setField(target, "persistence", intakes);
    var proxy = transactionProxy(target, transactions);
    new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
      assertThatThrownBy(() -> {
        if (automatic) proxy.autoConfirm("intake-1", null);
        else proxy.confirm("intake-1", null);
      }).isInstanceOf(IllegalTransactionStateException.class);
      verifyNoInteractions(intakes, recorder);
      assertThat(transactions.definitions).hasSize(1);
    });
  }

  private static TalentIntakeApplicationManager transactionProxy(
      TalentIntakeApplicationManager target, ConfirmationTransactions transactions) {
    var interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    var factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(interceptor);
    return (TalentIntakeApplicationManager) factory.getProxy();
  }

  @Test
  void rereadsAndReauthorizesTheIntakeAfterLockingBeforeAcceptingTheCommandVersion() {
    var manager = new TalentIntakeApplicationManager();
    var persistence = mock(TalentIntakePersistenceService.class);
    var conflicts = mock(IdentityConflictPersistenceService.class);
    var authorization = mock(AuthorizationService.class);
    var recorder = mock(IdentityConflictRecorder.class);
    var transactions = new ConfirmationTransactions();
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "identityConflictPersistenceService", conflicts);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "conflictRecorder", recorder);
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", mock(CandidatePersistenceService.class));
    ReflectionTestUtils.setField(manager, "candidateIdentityPersistenceService", mock(CandidateIdentityPersistenceService.class));
    var actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    when(authorization.currentActor()).thenReturn(actor);
    when(persistence.findIntake("intake-1"))
        .thenReturn(intake("READY_FOR_CONFIRMATION", 4), intake("READY_FOR_CONFIRMATION", 5));
    when(conflicts.lockIntake("intake-1")).thenReturn(intake("READY_FOR_CONFIRMATION", 5));

    assertThatThrownBy(() -> transactionProxy(manager, transactions).confirm("intake-1",
        new ConfirmIntakeDTO(null, "原姓名", null, "13800138000", 4L)))
        .isInstanceOfSatisfying(PlatformException.class,
            failure -> assertThat(failure.code()).isEqualTo("VERSION_CONFLICT"));

    var order = inOrder(persistence, conflicts, authorization);
    order.verify(persistence).findIntake("intake-1");
    order.verify(authorization).requireAccess(eq("talent.manage"), any(AuthorizationTarget.class));
    order.verify(conflicts).lockIntake("intake-1");
    order.verify(persistence).findIntake("intake-1");
    order.verify(authorization).requireAccess(eq("talent.manage"), any(AuthorizationTarget.class));
    verify(persistence, times(2)).findIntake("intake-1");
    verifyNoInteractions(recorder);
    assertThat(transactions.rollbacks).isOne();
  }

  private static ResumeIntake intake(String status, long version) {
    var intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setStatus(status);
    intake.setVersion(version);
    intake.setOwnerUserId("owner-1");
    intake.setOwnerDepartmentId("department-1");
    return intake;
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "status", "resolution", "chosenCandidateId", "candidateIdA", "candidateIdB", "version"})
  void rejectsChangedDecisionSourceWithoutLockingNewCandidatesAfterTheIntake(String field) {
    var manager = new TalentIntakeApplicationManager();
    var intakes = mock(TalentIntakePersistenceService.class);
    var conflicts = mock(IdentityConflictPersistenceService.class);
    var candidates = mock(CandidatePersistenceService.class);
    var recorder = mock(IdentityConflictRecorder.class);
    var authorization = mock(AuthorizationService.class);
    var transactions = new ConfirmationTransactions();
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "identityConflictPersistenceService", conflicts);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "candidateIdentityPersistenceService", mock(CandidateIdentityPersistenceService.class));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "conflictRecorder", recorder);
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot());
    when(intakes.findIntake("intake-1")).thenReturn(intake("READY_FOR_CONFIRMATION", 4));
    when(conflicts.lockIntake("intake-1")).thenReturn(intake("READY_FOR_CONFIRMATION", 4));
    var original = new IdentityConflict();
    original.setResolution("USE_EXISTING");
    original.setChosenCandidateId("original-candidate");
    var changed = new IdentityConflict();
    changed.setResolution("USE_EXISTING");
    changed.setChosenCandidateId("original-candidate");
    switch (field) {
      case "id" -> changed.setId("new-decision");
      case "status" -> changed.setStatus("RESOLVED");
      case "resolution" -> changed.setResolution("MERGE");
      case "chosenCandidateId" -> changed.setChosenCandidateId("new-candidate");
      case "candidateIdA" -> changed.setCandidateIdA("new-candidate");
      case "candidateIdB" -> changed.setCandidateIdB("new-candidate");
      case "version" -> changed.setVersion(1);
      default -> throw new IllegalArgumentException(field);
    }
    when(conflicts.findResolvedConflictForIntake("intake-1")).thenReturn(original, changed);

    assertThatThrownBy(() -> transactionProxy(manager, transactions).confirm("intake-1",
        new ConfirmIntakeDTO(null, "姓名", null, null, 4L)))
        .isInstanceOfSatisfying(PlatformException.class,
            failure -> assertThat(failure.code()).isEqualTo("VERSION_CONFLICT"));

    var order = inOrder(candidates, conflicts);
    order.verify(candidates).lockCandidate("original-candidate");
    order.verify(conflicts).lockIntake("intake-1");
    verify(candidates, never()).lockCandidate("new-candidate");
    verifyNoInteractions(recorder);
    assertThat(transactions.rollbacks).isOne();
  }

  private static final class ConfirmationTransactions extends AbstractPlatformTransactionManager {
    final List<TransactionDefinition> definitions = new ArrayList<>();
    int commits;
    int rollbacks;
    boolean candidateLockHeld;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected boolean isExistingTransaction(Object transaction) {
      return TransactionSynchronizationManager.isActualTransactionActive();
    }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
      definitions.add(new DefaultTransactionDefinition(definition));
    }
    @Override protected void doCommit(DefaultTransactionStatus status) {
      commits++;
      candidateLockHeld = false;
    }
    @Override protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
      candidateLockHeld = false;
    }
  }
}
