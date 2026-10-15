package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.support.IdentityConflictRecorder;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Identity;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.service.CandidateIdentityPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class TalentIntakeAutoIdentityTest {
  @Test
  void matchingCandidateOutsideHandlerScopeNeedsReviewWithoutUpdatingCandidate() {
    TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
    TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
    CandidateIdentityPersistenceService identities = mock(CandidateIdentityPersistenceService.class);
    CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
    IdentityConflictRecorder conflicts = mock(IdentityConflictRecorder.class);
    IdentityConflictPersistenceService conflictPersistence = mock(IdentityConflictPersistenceService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "candidateIdentityPersistenceService", identities);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "identityConflictPersistenceService",
        conflictPersistence);
    ReflectionTestUtils.setField(manager, "conflictRecorder", conflicts);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "transactionTemplate",
        new TransactionTemplate(new AutoConfirmationTransactions()));
    when(intakes.findIntake("intake-1")).thenReturn(intake("READY_FOR_CONFIRMATION", 4));
    when(conflictPersistence.lockIntake("intake-1")).thenReturn(intake("READY_FOR_CONFIRMATION", 4));
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("handler-1");
    when(authorization.currentActor()).thenReturn(actor);
    Identity phone = new Identity();
    phone.setCandidateId("admin-candidate");
    when(identities.findIdentity(eq("PHONE"), any())).thenReturn(phone);
    Candidate candidate = new Candidate();
    candidate.setId("admin-candidate");
    candidate.setFullName("相同姓名");
    candidate.setOwnerUserId("admin-1");
    candidate.setStatus("ACTIVE");
    when(candidates.findCandidateForUpdate("admin-candidate")).thenReturn(candidate);
    doAnswer(invocation -> {
      AuthorizationTarget target = invocation.getArgument(1);
      if ("CANDIDATE".equals(target.getObjectType())) {
        throw PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权修改已有候选人");
      }
      return null;
    }).when(authorization).requireAccess(eq("talent.manage"), any());

    assertThatThrownBy(() -> manager.confirm("intake-1",
        new ConfirmIntakeDTO(null, "相同姓名", null, "13800138000", 4L)))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW"));

    verify(conflicts).record("intake-1", "admin-candidate", null, "CANDIDATE_ACCESS_REQUIRED", 4);
    verify(candidates, never()).insertCandidate(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void lostPreparationClaimReportsAlreadyAdvancedIntake() {
    TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
    TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneOffset.UTC));
    when(intakes.findIntake("intake-1")).thenReturn(intake("PARSING", 3));
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    when(authorization.currentActor()).thenReturn(actor);

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
        manager, "prepareAutoConfirmation", "intake-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("INTAKE_NOT_PARSING"));
  }

  @Test
  void nameWithoutEmailOrPhoneRequiresHumanReviewBeforeCandidateCreation() {
    TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
    ResumeFacts facts = mock(ResumeFacts.class);
    CandidateIdentity identity = mock(CandidateIdentity.class);
    when(facts.candidate()).thenReturn(identity);
    when(identity.fullName()).thenReturn("候三");

    assertThatThrownBy(() -> manager.autoConfirm("intake-1", facts))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code())
                .isEqualTo("IDENTITY_REVIEW_REQUIRED"));
  }

  @Test
  void automaticConflictCommitsParsingTransitionBeforeConflictRecording() {
    TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
    TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
    CandidateIdentityPersistenceService identities = mock(CandidateIdentityPersistenceService.class);
    IdentityConflictPersistenceService conflictPersistence =
        mock(IdentityConflictPersistenceService.class);
    IdentityConflictRecorder conflicts = mock(IdentityConflictRecorder.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    var transactionManager = new AutoConfirmationTransactions();
    var candidates = mock(CandidatePersistenceService.class);
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setTimeout(7);
    transactions.setName("confirmation-template");
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-19T08:00:00Z"), ZoneOffset.UTC);
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "candidateIdentityPersistenceService", identities);
    ReflectionTestUtils.setField(manager, "identityConflictPersistenceService", conflictPersistence);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "conflictRecorder", conflicts);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "transactionTemplate", transactions);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", clock);
    doAnswer(invocation -> {
      assertThat(transactionManager.commits).isOne();
      assertThat(transactionManager.rollbacks).isOne();
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return null;
    }).when(conflicts).record("intake-1", "candidate-1", null, "NAME_MATCH_CONTACT_MISMATCH", 4);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    actor.setSuperAdmin(true);
    actor.setManagedUserIds(Set.of());
    when(authorization.currentActor()).thenReturn(actor);
    ResumeIntake parsing = intake("PARSING", 3);
    ResumeIntake ready = intake("READY_FOR_CONFIRMATION", 4);
    when(conflictPersistence.lockIntake("intake-1")).thenReturn(ready);
    when(intakes.findIntake("intake-1")).thenReturn(parsing, ready, ready);
    when(intakes.advanceIntake("intake-1", "PARSING", "READY_FOR_CONFIRMATION", null,
        "actor-1", Instant.parse("2026-09-19T08:00:00Z"))).thenReturn(1);
    Candidate sameName = new Candidate();
    sameName.setId("candidate-1");
    when(intakes.findCandidatesByExactName(eq("同名候选人"), eq("actor-1"),
        anyBoolean(), anySet(), anySet())).thenReturn(List.of(sameName));
    ResumeFacts facts = mock(ResumeFacts.class);
    CandidateIdentity identity = mock(CandidateIdentity.class);
    when(facts.candidate()).thenReturn(identity);
    when(identity.fullName()).thenReturn("同名候选人");
    when(identity.phone()).thenReturn("13800138000");

    assertThatThrownBy(() -> manager.autoConfirm("intake-1", facts))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code())
                .isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW"));

    assertThat(transactionManager.definitions).hasSize(2);
    assertThat(transactionManager.definitions).extracting(TransactionDefinition::getIsolationLevel)
        .containsExactly(TransactionDefinition.ISOLATION_REPEATABLE_READ, TransactionDefinition.ISOLATION_READ_COMMITTED);
    assertThat(transactionManager.definitions).allSatisfy(definition -> {
      assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      assertThat(definition.getTimeout()).isEqualTo(7);
      assertThat(definition.isReadOnly()).isFalse();
      assertThat(definition.getName()).isEqualTo("confirmation-template");
    });
    assertThat(transactionManager.commits).isOne();
    assertThat(transactionManager.rollbacks).isOne();
    assertThat(transactions.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    var ordered = inOrder(intakes, conflicts);
    ordered.verify(intakes).advanceIntake("intake-1", "PARSING", "READY_FOR_CONFIRMATION",
        null, "actor-1", Instant.parse("2026-09-19T08:00:00Z"));
    ordered.verify(conflicts).record("intake-1", "candidate-1", null,
        "NAME_MATCH_CONTACT_MISMATCH", 4);
    var locks = inOrder(candidates, conflictPersistence);
    locks.verify(candidates).lockCandidate("candidate-1");
    locks.verify(conflictPersistence).lockIntake("intake-1");
  }

  private static ResumeIntake intake(String status, long version) {
    ResumeIntake intake = new ResumeIntake();
    intake.setId("intake-1");
    intake.setStatus(status);
    intake.setVersion(version);
    intake.setOwnerUserId("owner-1");
    intake.setOwnerDepartmentId("department-1");
    return intake;
  }

  @Test
  void publicConfirmationRejectsAnOuterTransactionBeforeAcquiringLocks() throws Exception {
    var manual = TalentIntakeApplicationManager.class.getMethod("confirm", String.class, ConfirmIntakeDTO.class)
        .getAnnotation(Transactional.class);
    var automatic = TalentIntakeApplicationManager.class.getMethod("autoConfirm", String.class, ResumeFacts.class)
        .getAnnotation(Transactional.class);
    assertThat(manual.propagation()).isEqualTo(Propagation.NEVER);
    assertThat(automatic.propagation()).isEqualTo(Propagation.NEVER);
  }

  private static final class AutoConfirmationTransactions extends AbstractPlatformTransactionManager {
    final List<TransactionDefinition> definitions = new ArrayList<>();
    int commits;
    int rollbacks;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
      definitions.add(new DefaultTransactionDefinition(definition));
    }
    @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
    @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
  }
}
