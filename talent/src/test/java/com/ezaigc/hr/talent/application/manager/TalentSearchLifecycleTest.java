package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.application.support.IdentityConflictRecorder;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.ResolveIdentityConflictDTO;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.dto.UpdateCandidateDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Identity;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.service.CandidateIdentityPersistenceService;
import com.ezaigc.hr.talent.service.CandidateMergeHistoryPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class TalentSearchLifecycleTest {
  private AutoCloseable mocks;
  @Mock private AuthorizationService authorization;
  @Mock private CandidatePersistenceService candidates;
  @Mock private CandidateIdentityPersistenceService identities;
  @Mock private CandidateMergeHistoryPersistenceService merges;
  @Mock private IdentityConflictPersistenceService conflicts;
  @Mock private IdentityConflictRecorder conflictRecorder;
  @Mock private TalentIntakePersistenceService intakes;
  @Mock private ResumePersistenceService resumes;
  @Mock private ResumeVersionPersistenceService versions;
  @Mock private TalentProcessEventPersistenceService events;
  @Mock private ResumeSearchProjectionRecorder projection;
  @Mock private TalentIntakeAssembler assembler;
  @Mock private CandidateReadAccess candidateReadAccess;
  @Mock private CandidateRecruitmentStateQuery recruitment;
  @Mock private CandidateScreeningStateQuery screening;
  @Mock private PlatformIdGenerator ids;
  @Mock private Clock clock;
  @Mock private PlatformTransactionManager transactions;
  @InjectMocks private TalentIntakeApplicationManager manager;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    when(conflicts.lockIntake(anyString())).thenAnswer(invocation -> intakes.findIntake(invocation.getArgument(0)));
    var actor = new CurrentActorSnapshot();
    actor.setUserId("actor"); actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    when(authorization.canAccess(eq("talent.manage"), any())).thenReturn(true);
    when(candidateReadAccess.evaluate(any())).thenReturn(
        new CandidateReadDecision(Set.of("candidate", "merged"), Set.of(), Map.of()));
    when(assembler.toResponse(any(IdentityConflict.class))).thenAnswer(invocation ->
        new TalentIntakeAssembler().toResponse((IdentityConflict) invocation.getArgument(0)));
    when(clock.instant()).thenReturn(Instant.EPOCH);
    when(ids.newId()).thenReturn("new-version", "event");
    when(candidates.findCandidate("candidate")).thenReturn(candidate("candidate", "owner"));
    when(candidates.findCandidateForUpdate(anyString()))
        .thenAnswer(invocation -> candidates.findCandidate(invocation.getArgument(0)));
  }

  @AfterEach
  void closeMocks() throws Exception {
    mocks.close();
  }

  @Test
  void deletionRecordsATombstoneIntentForTheOriginalOwner() {
    when(candidates.deleteCandidate("candidate", 7, "actor", Instant.EPOCH)).thenReturn(1);
    manager.deleteCandidate("candidate", new TalentVersionReasonDTO(7, "Synthetic deletion"));
    verify(projection).candidateChanged("candidate", "deleted:7", "actor", "owner", "department");
  }

  @Test
  void candidateContactUpdateRecordsCurrentCandidateForProjection() {
    var updated = candidate("candidate", "new-owner");
    updated.setVersion(8);
    when(candidates.updateCandidate(eq("candidate"), anyString(), any(), any(), eq("new-owner"),
        eq("department"), eq(7L), eq("actor"), eq(Instant.EPOCH))).thenReturn(1);
    when(candidates.findCandidate("candidate")).thenReturn(candidate("candidate", "owner"), updated);

    manager.updateCandidate("candidate",
        new UpdateCandidateDTO("姓名", "mail@example.com", "13800138000", "new-owner", "department", 7));

    verify(projection).candidateChanged("candidate", "updated:8", "actor", "new-owner", "department");
  }

  @Test
  void changingOnlyCandidateOwnershipPreservesPreviouslyBoundContactAliases() {
    var previous = candidate("candidate", "owner");
    previous.setPrimaryEmail("primary@example.invalid");
    previous.setPrimaryPhone("13800138000");
    var updated = candidate("candidate", "new-owner");
    updated.setVersion(8);
    when(candidates.findCandidate("candidate")).thenReturn(previous, updated);
    when(candidates.updateCandidate(eq("candidate"), anyString(), any(), any(), eq("new-owner"),
        eq("department"), eq(7L), eq("actor"), eq(Instant.EPOCH))).thenReturn(1);
    var ownedIdentity = new Identity();
    ownedIdentity.setCandidateId("candidate");
    when(identities.findIdentity(eq("EMAIL"), anyString())).thenReturn(ownedIdentity);
    when(identities.findIdentity(eq("PHONE"), anyString())).thenReturn(ownedIdentity);

    manager.updateCandidate("candidate", new UpdateCandidateDTO("Synthetic candidate",
        "primary@example.invalid", "13800138000", "new-owner", "department", 7));

    verify(identities, never()).deleteIdentities("candidate");
    verify(identities, never()).insertIdentity(anyString(), eq("candidate"),
        anyString(), anyString(), anyString(), eq(Instant.EPOCH));
  }

  @Test
  void changingThePrimaryPhoneRemovesOnlyItsOldIdentityAndKeepsAliases() {
    var previous = candidate("candidate", "owner");
    previous.setPrimaryPhone("13800138000");
    var updated = candidate("candidate", "owner");
    updated.setVersion(8);
    when(candidates.findCandidate("candidate")).thenReturn(previous, updated);
    when(candidates.updateCandidate(eq("candidate"), anyString(), any(), eq("13900139000"),
        eq("owner"), eq("department"), eq(7L), eq("actor"), eq(Instant.EPOCH)))
        .thenReturn(1);

    manager.updateCandidate("candidate", new UpdateCandidateDTO("Synthetic candidate",
        null, "13900139000", "owner", "department", 7));

    verify(identities).deleteIdentity("candidate", "PHONE", Sha256Utils.hashUtf8("13800138000"));
    verify(identities).insertIdentity(anyString(), eq("candidate"), eq("PHONE"),
        eq(Sha256Utils.hashUtf8("13900139000")), eq("9000"), eq(Instant.EPOCH));
    verify(identities, never()).deleteIdentities("candidate");
  }

  @Test
  void promotingAnExistingPhoneAliasToPrimaryDoesNotInsertItTwice() {
    var previous = candidate("candidate", "owner");
    previous.setPrimaryPhone("13800138000");
    var updated = candidate("candidate", "owner");
    updated.setVersion(8);
    when(candidates.findCandidate("candidate")).thenReturn(previous, updated);
    when(candidates.updateCandidate(eq("candidate"), anyString(), any(), eq("13900139000"),
        eq("owner"), eq("department"), eq(7L), eq("actor"), eq(Instant.EPOCH)))
        .thenReturn(1);
    var alias = new Identity(); alias.setCandidateId("candidate");
    when(identities.findIdentity("PHONE", Sha256Utils.hashUtf8("13900139000")))
        .thenReturn(alias);

    manager.updateCandidate("candidate", new UpdateCandidateDTO("Synthetic candidate",
        null, "13900139000", "owner", "department", 7));

    verify(identities).deleteIdentity("candidate", "PHONE", Sha256Utils.hashUtf8("13800138000"));
    verify(identities, never()).insertIdentity(anyString(), eq("candidate"), eq("PHONE"),
        anyString(), anyString(), eq(Instant.EPOCH));
    verify(identities, never()).deleteIdentities("candidate");
  }

  @Test
  void anotherCandidatesPhoneCannotBecomeTheNewPrimaryPhone() {
    var previous = candidate("candidate", "owner");
    previous.setPrimaryPhone("13800138000");
    when(candidates.findCandidate("candidate")).thenReturn(previous);
    var foreign = new Identity(); foreign.setCandidateId("other-candidate");
    when(identities.findIdentity("PHONE", Sha256Utils.hashUtf8("13900139000")))
        .thenReturn(foreign);

    assertThatThrownBy(() -> manager.updateCandidate("candidate",
        new UpdateCandidateDTO("Synthetic candidate", null, "13900139000",
            "owner", "department", 7)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CANDIDATE_IDENTITY_DUPLICATE"));
    verify(candidates, never()).updateCandidate(anyString(), anyString(), any(), any(),
        anyString(), anyString(), anyLong(), anyString(), any());
    verify(identities, never()).deleteIdentity(anyString(), anyString(), anyString());
  }

  @Test
  void clearingThePrimaryPhoneLeavesOtherContactAliasesUntouched() {
    var previous = candidate("candidate", "owner");
    previous.setPrimaryPhone("13800138000");
    var updated = candidate("candidate", "owner");
    updated.setVersion(8);
    when(candidates.findCandidate("candidate")).thenReturn(previous, updated);
    when(candidates.updateCandidate(eq("candidate"), anyString(), any(), eq(null),
        eq("owner"), eq("department"), eq(7L), eq("actor"), eq(Instant.EPOCH)))
        .thenReturn(1);

    manager.updateCandidate("candidate", new UpdateCandidateDTO("Synthetic candidate",
        null, null, "owner", "department", 7));

    verify(identities).deleteIdentity("candidate", "PHONE", Sha256Utils.hashUtf8("13800138000"));
    verify(identities, never()).deleteIdentities("candidate");
    verify(identities, never()).insertIdentity(anyString(), eq("candidate"), eq("PHONE"),
        anyString(), anyString(), eq(Instant.EPOCH));
  }

  @Test
  void confirmingANewAvailableVersionRecordsTheExistingCandidatesOwner() {
    var intake = intake();
    when(intakes.findIntake("intake")).thenReturn(intake);
    var resume = new Resume(); resume.setId("resume");
    when(resumes.findResumeForCandidate("candidate")).thenReturn(resume);
    when(intakes.findMaxResumeVersion("resume")).thenReturn(2);
    when(intakes.confirmIntake("intake", "candidate", "new-version", 0, "actor", Instant.EPOCH)).thenReturn(1);
    manager.confirm("intake", new ConfirmIntakeDTO("candidate", "Synthetic candidate", null, null, 0));
    verify(projection).candidateChanged("candidate", "available:new-version", "actor", "owner", "department");
  }

  @Test
  void mergingRecordsBothTheSurvivorAndTheDeletedCandidate() {
    var mergedCandidate = candidate("merged", "second-owner");
    mergedCandidate.setExt("{\"displayHint\":\"duplicate\"}");
    when(candidates.findCandidate("merged")).thenReturn(mergedCandidate);
    var conflict = new IdentityConflict();
    conflict.setId("conflict"); conflict.setIntakeId("intake");
    conflict.setCandidateIdA("candidate"); conflict.setCandidateIdB("merged");
    when(conflicts.findConflict("conflict")).thenReturn(conflict);
    when(intakes.findIntake("intake")).thenReturn(intake());
    when(candidates.markCandidateMerged("merged", 7, "actor", Instant.EPOCH)).thenReturn(1);
    when(conflicts.resolveConflict(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    manager.resolveConflict("conflict", new ResolveIdentityConflictDTO("MERGE", "candidate", "Synthetic merge"));
    var order = inOrder(candidates, recruitment, screening, identities);
    order.verify(candidates).lockCandidate("candidate");
    order.verify(candidates).lockCandidate("merged");
    order.verify(candidates).findCandidateForUpdate("candidate");
    order.verify(candidates).findCandidateForUpdate("merged");
    order.verify(candidates).hasMergeProtectedHistory("candidate");
    order.verify(recruitment).hasMergeProtectedHistory("candidate");
    order.verify(screening).hasMergeProtectedHistory("candidate");
    order.verify(candidates).hasMergeProtectedHistory("merged");
    order.verify(recruitment).hasMergeProtectedHistory("merged");
    order.verify(screening).hasMergeProtectedHistory("merged");
    order.verify(identities).moveIdentities("candidate", "merged", "actor", Instant.EPOCH);
    verify(projection).candidateChanged("candidate", "merge:conflict", "actor", "owner", "department");
    verify(projection).candidateChanged("merged", "merge:conflict", "actor", "second-owner", "department");
  }

  @ParameterizedTest
  @CsvSource({"candidate,TALENT", "merged,TALENT", "candidate,RECRUITMENT", "merged,RECRUITMENT",
      "candidate,SCREENING", "merged,SCREENING"})
  void mergeRejectsBusinessHistoryOnEitherCandidateBeforeMovingResumes(String protectedId, String module) {
    when(candidates.findCandidate("merged")).thenReturn(candidate("merged", "second-owner"));
    var conflict = new IdentityConflict();
    conflict.setId("conflict"); conflict.setIntakeId("intake");
    conflict.setCandidateIdA("candidate"); conflict.setCandidateIdB("merged");
    when(conflicts.findConflict("conflict")).thenReturn(conflict);
    when(intakes.findIntake("intake")).thenReturn(intake());
    when(candidates.markCandidateMerged("merged", 7, "actor", Instant.EPOCH)).thenReturn(1);
    when(conflicts.resolveConflict(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    var survivorResume = new Resume(); survivorResume.setId("survivor-resume");
    var mergedResume = new Resume(); mergedResume.setId("merged-resume");
    when(resumes.findResumeForCandidate("candidate")).thenReturn(survivorResume);
    when(resumes.findResumeForCandidate("merged")).thenReturn(mergedResume);
    switch (module) {
      case "TALENT" -> when(candidates.hasMergeProtectedHistory(protectedId)).thenReturn(true);
      case "RECRUITMENT" -> when(recruitment.hasMergeProtectedHistory(protectedId)).thenReturn(true);
      case "SCREENING" -> when(screening.hasMergeProtectedHistory(protectedId)).thenReturn(true);
      default -> throw new AssertionError(module);
    }

    assertThatThrownBy(() -> manager.resolveConflict("conflict",
        new ResolveIdentityConflictDTO("MERGE", "candidate", "人工确认重复档案")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CANDIDATE_MERGE_HAS_BUSINESS_HISTORY"));
    verify(identities, never()).moveIdentities(any(), any(), any(), any());
    verify(intakes, never()).moveResumeVersions(any(), any(), anyInt());
    verify(intakes, never()).redirectCandidateIntakes(any(), any(), any(), any());
    verify(candidates, never()).markCandidateMerged(any(), anyLong(), any(), any());
    verify(merges, never()).insertMergeHistory(any(), any(), any(), any(), anyLong(), anyLong(), any(), any(), any());
    verify(projection, never()).candidateChanged(any(), any(), any(), any(), any());
  }

  @Test
  void mergeRechecksCurrentOwnerPermissionAfterBothLocks() {
    var stale = candidate("merged", "second-owner");
    when(candidates.findCandidate("merged")).thenReturn(stale);
    var current = candidate("merged", "new-owner");
    // doReturn 不调用全局 thenAnswer，避免其嵌套 findCandidate 改写锁前快照 stub。
    doReturn(current).when(candidates).findCandidateForUpdate("merged");
    var conflict = new IdentityConflict(); conflict.setId("conflict"); conflict.setIntakeId("intake");
    conflict.setCandidateIdA("candidate"); conflict.setCandidateIdB("merged");
    when(conflicts.findConflict("conflict")).thenReturn(conflict);
    when(intakes.findIntake("intake")).thenReturn(intake());
    doThrow(PlatformExceptions.forbidden("PERMISSION_DENIED", "归属已变化"))
        .when(authorization).requireAccess("talent.manage",
            new AuthorizationTarget("CANDIDATE", "merged", "new-owner", "department", Set.of()));

    assertThatThrownBy(() -> manager.resolveConflict("conflict",
        new ResolveIdentityConflictDTO("MERGE", "candidate", "人工核验")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("PERMISSION_DENIED"));
    var order = inOrder(candidates, authorization);
    order.verify(candidates).lockCandidate("candidate");
    order.verify(candidates).lockCandidate("merged");
    order.verify(candidates).findCandidateForUpdate("candidate");
    order.verify(candidates).findCandidateForUpdate("merged");
    order.verify(authorization).requireAccess("talent.manage",
        new AuthorizationTarget("CANDIDATE", "merged", "new-owner", "department", Set.of()));
    verify(identities, never()).moveIdentities(any(), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"candidate,screenedOutResumeVersionId", "merged,screenedOutResumeVersionId",
      "candidate,screenedOutAt", "merged,screenedOutAt", "candidate,reclaimedResumeVersionId",
      "merged,reclaimedResumeVersionId", "candidate,reclaimedScoreBindingId", "merged,reclaimedScoreBindingId",
      "candidate,reclaimedAt", "merged,reclaimedAt", "candidate,onboardingReturnApplicationResumeVersionId",
      "merged,onboardingReturnApplicationResumeVersionId"})
  void mergeRejectsExplicitLibraryMetadataEvenWhenLifecycleIsDisabled(String protectedId, String field) {
    when(candidates.findCandidate("merged")).thenReturn(candidate("merged", "second-owner"));
    Candidate current = candidate(protectedId, protectedId.equals("candidate") ? "owner" : "second-owner");
    current.setExt("{\"" + field + "\":\"business-reference\"}");
    when(candidates.findCandidateForUpdate(protectedId)).thenReturn(current);
    var conflict = new IdentityConflict(); conflict.setId("conflict"); conflict.setIntakeId("intake");
    conflict.setCandidateIdA("candidate"); conflict.setCandidateIdB("merged");
    when(conflicts.findConflict("conflict")).thenReturn(conflict);
    when(intakes.findIntake("intake")).thenReturn(intake());

    assertThatThrownBy(() -> manager.resolveConflict("conflict",
        new ResolveIdentityConflictDTO("MERGE", "candidate", "人工确认重复档案")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CANDIDATE_MERGE_HAS_BUSINESS_HISTORY"));
    verify(identities, never()).moveIdentities(any(), any(), any(), any());
    verify(candidates, never()).markCandidateMerged(any(), anyLong(), any(), any());
  }

  @Test
  void resolvingTheOnlyOpenConflictReleasesItsIntakeForConfirmation() {
    var conflict = conflict("USE_EXISTING", "candidate");
    conflict.setStatus("OPEN");
    when(conflicts.findConflict("conflict")).thenReturn(conflict);
    var intake = intake(); intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW"); intake.setVersion(4);
    when(intakes.findIntake("intake")).thenReturn(intake);
    when(conflicts.resolveConflict("conflict", "USE_EXISTING", "candidate", "人工核实", "actor", Instant.EPOCH))
        .thenReturn(1);
    when(intakes.updateIntakeStatus("intake", "READY_FOR_CONFIRMATION", null, 4, "actor", Instant.EPOCH))
        .thenReturn(1);

    manager.resolveConflict("conflict", new ResolveIdentityConflictDTO("USE_EXISTING", "candidate", "人工核实"));

    verify(intakes).updateIntakeStatus("intake", "READY_FOR_CONFIRMATION", null, 4, "actor", Instant.EPOCH);
  }

  @Test
  void anotherOpenConflictKeepsTheIntakeInReview() {
    var conflict = conflict("USE_EXISTING", "candidate");
    when(conflicts.findConflict("conflict")).thenReturn(conflict);
    var intake = intake(); intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW");
    when(intakes.findIntake("intake")).thenReturn(intake);
    when(conflicts.resolveConflict(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);
    var remaining = new IdentityConflict(); remaining.setId("other-open");
    when(conflicts.findOpenConflictForIntake("intake")).thenReturn(remaining);

    manager.resolveConflict("conflict", new ResolveIdentityConflictDTO("USE_EXISTING", "candidate", "人工核实"));

    verify(intakes, never()).updateIntakeStatus(anyString(), anyString(), any(),
        anyLong(), anyString(), any());
  }

  @Test
  void resolvingAConflictDoesNotClearAnUnrelatedReviewFailure() {
    when(conflicts.findConflict("conflict")).thenReturn(conflict("USE_EXISTING", "candidate"));
    var intake = intake();
    intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("PARSE_CONTENT_INVALID");
    when(intakes.findIntake("intake")).thenReturn(intake);
    when(conflicts.resolveConflict(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);

    manager.resolveConflict("conflict", new ResolveIdentityConflictDTO("USE_EXISTING", "candidate", "人工核实"));

    verify(intakes, never()).updateIntakeStatus(anyString(), anyString(), any(),
        anyLong(), anyString(), any());
  }

  @Test
  void failedIntakeVersionCheckRejectsConflictResolution() {
    when(conflicts.findConflict("conflict")).thenReturn(conflict("USE_EXISTING", "candidate"));
    var intake = intake();
    intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW");
    intake.setVersion(4);
    when(intakes.findIntake("intake")).thenReturn(intake);
    when(conflicts.resolveConflict(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(1);

    assertThatThrownBy(() -> manager.resolveConflict("conflict",
        new ResolveIdentityConflictDTO("USE_EXISTING", "candidate", "人工核实")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("VERSION_CONFLICT"));
  }

  @Test
  void anOpenConflictCannotBeBypassedByDirectConfirmation() {
    when(intakes.findIntake("intake")).thenReturn(intake());
    when(conflicts.findOpenConflictForIntake("intake")).thenReturn(new IdentityConflict());

    assertThatThrownBy(() -> manager.confirm("intake",
        new ConfirmIntakeDTO("candidate", "Synthetic candidate", null, null, 0)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code())
                .isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW"));
    verify(intakes, never()).confirmIntake(anyString(), anyString(), anyString(),
        eq(0L), anyString(), any());
  }

  @Test
  void aHistoricalResolvedConflictAllowsConfirmationWhenRereadAsANewEntity() {
    var intake = intake();
    intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW");
    when(intakes.findIntake("intake")).thenReturn(intake);
    when(conflicts.findResolvedConflictForIntake("intake")).thenAnswer(ignored -> {
      var decision = conflict("USE_EXISTING", "candidate");
      decision.setStatus("RESOLVED");
      return decision;
    });
    var resume = new Resume(); resume.setId("resume");
    when(resumes.findResumeForCandidate("candidate")).thenReturn(resume);
    when(intakes.confirmIntake(eq("intake"), eq("candidate"), anyString(), eq(0L), eq("actor"), eq(Instant.EPOCH)))
        .thenReturn(1);

    manager.confirm("intake", new ConfirmIntakeDTO(null, "Synthetic candidate", null, null, 0));

    verify(intakes).confirmIntake(eq("intake"), eq("candidate"), anyString(), eq(0L),
        eq("actor"), eq(Instant.EPOCH));
  }

  @Test
  void aHistoricalIdentityReviewCannotConfirmWithoutAResolvedDecision() {
    var intake = intake();
    intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW");
    when(intakes.findIntake("intake")).thenReturn(intake);

    assertThatThrownBy(() -> manager.confirm("intake",
        new ConfirmIntakeDTO("candidate", "Synthetic candidate", null, null, 0)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW"));
    verify(intakes, never()).confirmIntake(anyString(), anyString(), anyString(),
        eq(0L), anyString(), any());
  }

  @Test
  void aHistoricalIdentityReviewRejectsUnsupportedOrIncompleteResolutions() {
    var intake = intake();
    intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW");
    when(intakes.findIntake("intake")).thenReturn(intake);
    var unsupported = conflict("UNKNOWN", "candidate");
    unsupported.setStatus("RESOLVED");
    var unchosen = conflict("USE_EXISTING", null);
    unchosen.setStatus("RESOLVED");
    var unchosenMerge = conflict("MERGE", null);
    unchosenMerge.setStatus("RESOLVED");
    unchosenMerge.setCandidateIdB("merged");
    var incompleteMerge = conflict("MERGE", "candidate");
    incompleteMerge.setStatus("RESOLVED");
    var contradictory = conflict("CREATE_SEPARATE", "candidate");
    contradictory.setStatus("RESOLVED");
    var stillOpen = conflict("USE_EXISTING", "candidate");
    stillOpen.setStatus("OPEN");
    var foreignChoice = conflict("USE_EXISTING", "other-candidate");
    foreignChoice.setStatus("RESOLVED");
    when(conflicts.findResolvedConflictForIntake("intake"))
        .thenReturn(unsupported, unchosen, unchosenMerge, incompleteMerge,
            contradictory, stillOpen, foreignChoice);

    for (int attempt = 0; attempt < 7; attempt++) {
      assertThatThrownBy(() -> manager.confirm("intake",
          new ConfirmIntakeDTO(null, "Synthetic candidate", null, null, 0)))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW"));
    }
    verify(intakes, never()).confirmIntake(anyString(), anyString(), anyString(),
        eq(0L), anyString(), any());
  }

  @Test
  void aHistoricalSeparateDecisionCanCreateItsOwnCandidate() {
    var intake = intake();
    intake.setStatus("IDENTITY_REVIEW_REQUIRED");
    intake.setFailureCode("IDENTITY_CONFLICT_REQUIRES_REVIEW");
    when(intakes.findIntake("intake")).thenReturn(intake);
    var decision = conflict("CREATE_SEPARATE", null);
    decision.setStatus("RESOLVED");
    when(conflicts.findResolvedConflictForIntake("intake")).thenReturn(decision);
    when(ids.newId()).thenReturn("new-candidate", "new-resume", "new-version", "event");
    when(candidates.findCandidate("new-candidate"))
        .thenReturn(candidate("new-candidate", "intake-owner"));
    when(intakes.confirmIntake(eq("intake"), eq("new-candidate"), eq("new-version"),
        eq(0L), eq("actor"), eq(Instant.EPOCH))).thenReturn(1);

    manager.confirm("intake", new ConfirmIntakeDTO(null, "Synthetic candidate", null, null, 0));

    verify(candidates).insertCandidate(eq("new-candidate"), eq("Synthetic candidate"),
        eq(null), eq(null), eq("intake-owner"), eq("department"), eq("actor"), eq(Instant.EPOCH));
    verify(intakes).confirmIntake("intake", "new-candidate", "new-version", 0,
        "actor", Instant.EPOCH);
  }

  @Test
  void resolvedChoiceReusesCandidateAndBindsAFreePhoneIdentity() {
    when(intakes.findIntake("intake")).thenReturn(intake());
    when(conflicts.findResolvedConflictForIntake("intake"))
        .thenReturn(conflict("USE_EXISTING", "candidate"));
    var resume = new Resume(); resume.setId("resume");
    when(resumes.findResumeForCandidate("candidate")).thenReturn(resume);
    when(intakes.confirmIntake(eq("intake"), eq("candidate"), anyString(), eq(0L), eq("actor"), eq(Instant.EPOCH)))
        .thenReturn(1);

    manager.confirm("intake", new ConfirmIntakeDTO(null, "Synthetic candidate", null, "13800138000", 0));

    verify(identities).insertIdentity(anyString(), eq("candidate"), eq("PHONE"), anyString(),
        eq("8000"), eq(Instant.EPOCH));
    verify(intakes).confirmIntake(eq("intake"), eq("candidate"), anyString(), eq(0L),
        eq("actor"), eq(Instant.EPOCH));
    verify(candidates, never()).insertCandidate(anyString(), anyString(), any(), any(),
        anyString(), anyString(), anyString(), any());
  }

  @Test
  void resolvedChoiceCannotClaimPhoneOwnedByAnotherCandidate() {
    when(intakes.findIntake("intake")).thenReturn(intake());
    when(conflicts.findResolvedConflictForIntake("intake"))
        .thenReturn(conflict("USE_EXISTING", "candidate"));
    var phoneIdentity = new Identity(); phoneIdentity.setCandidateId("merged");
    when(identities.findIdentity(eq("PHONE"), anyString())).thenReturn(phoneIdentity);

    assertThatThrownBy(() -> manager.confirm("intake",
        new ConfirmIntakeDTO(null, "Synthetic candidate", null, "13800138000", 0)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code())
                .isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW"));
    verify(intakes, never()).confirmIntake(anyString(), anyString(), anyString(),
        eq(0L), anyString(), any());
  }

  @Test
  void separateCandidateDecisionCannotReuseAnExistingContactIdentity() {
    when(intakes.findIntake("intake")).thenReturn(intake());
    when(conflicts.findResolvedConflictForIntake("intake"))
        .thenReturn(conflict("CREATE_SEPARATE", null));
    var phoneIdentity = new Identity(); phoneIdentity.setCandidateId("candidate");
    when(identities.findIdentity(eq("PHONE"), anyString())).thenReturn(phoneIdentity);

    assertThatThrownBy(() -> manager.confirm("intake",
        new ConfirmIntakeDTO(null, "Synthetic candidate", null, "13800138000", 0)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code())
                .isEqualTo("IDENTITY_CONFLICT_REQUIRES_REVIEW"));
    verify(intakes, never()).confirmIntake(anyString(), anyString(), anyString(),
        eq(0L), anyString(), any());
  }

  private static IdentityConflict conflict(String resolution, String chosen) {
    var conflict = new IdentityConflict();
    conflict.setId("conflict"); conflict.setIntakeId("intake");
    conflict.setCandidateIdA("candidate"); conflict.setResolution(resolution);
    conflict.setChosenCandidateId(chosen);
    return conflict;
  }

  private static Candidate candidate(String id, String owner) {
    var candidate = new Candidate();
    candidate.setId(id); candidate.setStatus("ACTIVE"); candidate.setVersion(7);
    candidate.setFullName("Synthetic candidate"); candidate.setOwnerUserId(owner);
    candidate.setOwnerDepartmentId("department");
    return candidate;
  }

  private static ResumeIntake intake() {
    var intake = new ResumeIntake();
    intake.setId("intake"); intake.setStatus("READY_FOR_CONFIRMATION");
    intake.setOwnerUserId("intake-owner"); intake.setOwnerDepartmentId("department");
    intake.setSourceRecruiterUserId("source-recruiter");
    return intake;
  }
}
