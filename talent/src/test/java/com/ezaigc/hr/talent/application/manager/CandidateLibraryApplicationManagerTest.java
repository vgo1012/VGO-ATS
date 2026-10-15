package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateApplicationAccessQuery;
import com.ezaigc.hr.talent.api.CandidateEngagementClaimQuery;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateMatchingStateQuery;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.RiskPersistenceService;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningTransitionService;
import com.ezaigc.hr.talent.api.ScoreRunCompletionQuery;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery.State;
import com.ezaigc.hr.talent.domain.dto.TalentVersionReasonDTO;
import com.ezaigc.hr.talent.domain.response.CandidateJobContextResponse;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.CandidateConflictCountRow;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.domain.request.SharedCandidateReclaimItemRequest;
import com.ezaigc.hr.talent.domain.request.SharedCandidateReclaimRequest;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateLibraryApplicationManagerTest {
  private final CandidateLibraryApplicationManager manager =
      new CandidateLibraryApplicationManager();
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateRecruitmentStateQuery recruitment =
      mock(CandidateRecruitmentStateQuery.class);
  private final CandidateScreeningStateQuery screening = mock(CandidateScreeningStateQuery.class);
  private final CandidateScreeningTransitionService screeningTransitions =
      mock(CandidateScreeningTransitionService.class);
  private final TalentIntakePersistenceService resumes = mock(TalentIntakePersistenceService.class);
  private final TalentProcessEventPersistenceService events =
      mock(TalentProcessEventPersistenceService.class);
  private final IdentityConflictPersistenceService identityConflicts =
      mock(IdentityConflictPersistenceService.class);
  private final Candidate row = new Candidate();
  private final JobContextQuery jobs = mock(JobContextQuery.class);
  private final ResumeLifecyclePersistenceService lifecycle = mock(ResumeLifecyclePersistenceService.class);
  private final RiskPersistenceService risks = mock(RiskPersistenceService.class);
  private final CandidateEngagementClaimQuery engagementClaims =
      mock(CandidateEngagementClaimQuery.class);
  private final ResumeJdScoreBindingPersistenceService scoreBindings =
      mock(ResumeJdScoreBindingPersistenceService.class);
  private final ScoreRunCompletionQuery scoreRuns = mock(ScoreRunCompletionQuery.class);
  private final TalentIdempotencyPersistenceService idempotencies =
      mock(TalentIdempotencyPersistenceService.class);
  private final ResumeLifecycleApplicationManager lifecycleManager =
      mock(ResumeLifecycleApplicationManager.class);
  private final ResumeSearchProjectionRecorder searchProjection =
      mock(ResumeSearchProjectionRecorder.class);
  private final CandidateResumeRow resume = new CandidateResumeRow();
  private final CurrentActorSnapshot actor =
      new CurrentActorSnapshot(
          "owner",
          "归属人",
          "owner@example.invalid",
          "dept",
          Set.of(),
          Set.of(),
          Set.of(),
          Set.of("RECRUITER"),
          Set.of("talent.read", "talent.manage"),
          false,
          false);

  @BeforeEach
  void setup() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "candidate-library"),
        ResumeLifecycle.class);
    var auth = mock(AuthorizationService.class);
    when(auth.currentActor()).thenReturn(actor);
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("audit-event");
    row.setId("candidate");
    row.setFullName("批量候选人");
    row.setOwnerUserId("owner");
    row.setStatus("ACTIVE");
    row.setVersion(4);
    resume.setCandidateId("candidate");
    resume.setResumeVersionId("latest");
    when(candidates.listByIds(any())).thenReturn(List.of(row));
    when(candidates.findCandidateForUpdate("candidate")).thenReturn(row);
    when(resumes.findCurrentCandidateResumes(any())).thenReturn(List.of(resume));
    when(identityConflicts.countOpenConflictsByCandidateIds(any(), anyString(), any(Boolean.class), any(), any()))
        .thenReturn(List.of());
    when(recruitment.states(any())).thenReturn(Map.of());
    when(risks.blacklistTags(any(), any())).thenReturn(List.of());
    ReflectionTestUtils.setField(
        manager, "jobContexts", mock(CandidateJobContextApplicationManager.class));
    ReflectionTestUtils.setField(
        manager, "applicationAccess", mock(CandidateApplicationAccessQuery.class));
    ReflectionTestUtils.setField(manager,"lifecycle",lifecycle);
    ReflectionTestUtils.setField(manager,"lifecycleManager",lifecycleManager);
    ReflectionTestUtils.setField(manager,"routing",mock(CandidateResumeVersionService.class));
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "resumes", resumes);
    ReflectionTestUtils.setField(manager, "identityConflicts", identityConflicts);
    ReflectionTestUtils.setField(manager, "recruitment", recruitment);
    ReflectionTestUtils.setField(manager, "screening", screening);
    ReflectionTestUtils.setField(manager, "screeningTransitions", screeningTransitions);
    ReflectionTestUtils.setField(manager, "risks", risks);
    ReflectionTestUtils.setField(manager, "engagementClaims", engagementClaims);
    ReflectionTestUtils.setField(manager, "scoreBindings", scoreBindings);
    ReflectionTestUtils.setField(manager, "scoreRuns", scoreRuns);
    ReflectionTestUtils.setField(manager, "idempotencies", idempotencies);
    ReflectionTestUtils.setField(manager, "searchProjection", searchProjection);
    ReflectionTestUtils.setField(manager, "events", events);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void jdDeletionGuardChecksOtherOwnersAndBothPersonalLifecyclePhases() {
    row.setOwnerUserId("peer");
    stubDeletionJobContext("jd-version", "latest");
    for (String phase : List.of("PENDING_SCREENING", "PENDING_DISPOSAL")) {
      when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(lifecycle(phase)));
      assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isTrue();
      assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    }
    verify(candidates, never()).lockCandidate(anyString());
    verify(candidates, never()).findCandidateForUpdate(anyString());
  }

  @Test
  void jdDeletionGuardExcludesSharedDeletedBlacklistedAndOtherActivePools() {
    stubDeletionJobContext("jd-version", "latest");
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(lifecycle("SHARED")));
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of());
    row.setStatus("DELETED");
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
    row.setStatus("ACTIVE");
    when(risks.blacklistTags(any(), any())).thenReturn(List.of("candidate"));
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
    when(risks.blacklistTags(any(), any())).thenReturn(List.of());
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of("latest"));
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of());
    when(recruitment.states(any())).thenReturn(Map.of("candidate",
        List.of(new State("latest", "owner", "IN_PROCESS", true, false))));
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
    when(recruitment.states(any())).thenReturn(Map.of("candidate",
        List.of(new State("latest", "owner", "TERMINATED", false, true))));
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
  }

  @Test
  void jdDeletionGuardRequiresCurrentResumeContextRatherThanHistoricalAssociation() {
    stubDeletionJobContext("jd-version", "old-resume");
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
    stubDeletionJobContext("other-jd-version", "latest");
    when(resumes.findCandidateIdsForJdVersion("jd-version", null, 1000)).thenReturn(List.of("candidate"));
    assertThat(manager.hasPersonalPoolResumes(List.of("jd-version"))).isFalse();
    when(resumes.findCurrentCandidateResumes(any())).thenReturn(List.of());
    assertThat(manager.hasPersonalPoolResumes(List.of("other-jd-version"))).isFalse();
  }

  @Test
  void jdDeletionGuardContinuesAfterFullCandidatePageAndAcrossJdVersions() {
    var firstPage = IntStream.range(0, 1000)
        .mapToObj(index -> "candidate-" + index).toList();
    when(resumes.findCandidateIdsForJdVersion(anyString(), eq(null), eq(1000))).thenReturn(firstPage);
    when(resumes.findCandidateIdsForJdVersion(anyString(), eq("candidate-999"), eq(1000)))
        .thenReturn(List.of("candidate"));
    var contexts = mock(CandidateJobContextApplicationManager.class);
    ReflectionTestUtils.setField(manager, "jobContexts", contexts);
    when(contexts.hasCurrentPersonalPoolAssociation(any(), any())).thenReturn(false);
    assertThat(manager.hasPersonalPoolResumes(List.of("version-one", "version-two"))).isFalse();
    verify(resumes).findCandidateIdsForJdVersion("version-one", "candidate-999", 1000);
    verify(resumes).findCandidateIdsForJdVersion("version-two", "candidate-999", 1000);
    assertThat(manager.hasPersonalPoolResumes(List.of())).isFalse();
  }

  @Test
  void jdDeletionGuardNeedsNoJdReadPermissionAndUsesOnlyClaimedBindingAfterReclaim() {
    resume.setJdVersionId("original-version");
    row.setOwnerUserId("peer");
    var claimed = new ResumeJdScoreBinding();
    claimed.setId("claimed"); claimed.setCandidateId("candidate");
    claimed.setResumeVersionId("latest"); claimed.setJdVersionId("claimed-version");
    claimed.setDefaultMatchRunId("claimed-run");
    var old = new ResumeJdScoreBinding();
    old.setId("old-binding"); old.setCandidateId("candidate");
    old.setResumeVersionId("latest"); old.setJdVersionId("original-version");
    old.setDefaultMatchRunId("old-run");
    when(scoreBindings.findForCandidates(any())).thenReturn(List.of(old, claimed));
    when(scoreRuns.successfulRunIds(any())).thenReturn(Set.of("claimed-run", "old-run"));
    when(resumes.findCandidateIdsForJdVersion(anyString(), eq(null), eq(1000)))
        .thenReturn(List.of("candidate"));
    var jobs = mock(JobContextQuery.class);
    when(jobs.visibleVersions(any())).thenReturn(Map.of());
    var matching = mock(CandidateMatchingStateQuery.class);
    var contexts = new CandidateJobContextApplicationManager();
    ReflectionTestUtils.setField(contexts, "candidates", candidates);
    ReflectionTestUtils.setField(contexts, "intakes", resumes);
    ReflectionTestUtils.setField(contexts, "matching", matching);
    ReflectionTestUtils.setField(contexts, "scoreBindings", scoreBindings);
    ReflectionTestUtils.setField(contexts, "scoreRuns", scoreRuns);
    ReflectionTestUtils.setField(contexts, "jobs", jobs);
    ReflectionTestUtils.setField(contexts, "reclaimedScores", new SharedReclaimScoreApplicationManager());
    ReflectionTestUtils.setField(manager, "jobContexts", contexts);
    assertThat(manager.hasPersonalPoolResumes(List.of("original-version"))).isTrue();
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    row.setExt("{\"reclaimedResumeVersionId\":\"latest\",\"reclaimedScoreBindingId\":\"claimed\"}");
    assertThat(manager.hasPersonalPoolResumes(List.of("original-version"))).isFalse();
    assertThat(manager.hasPersonalPoolResumes(List.of("claimed-version"))).isTrue();
    Mockito.verifyNoInteractions(jobs, matching);
  }

  private void stubDeletionJobContext(String jdVersionId, String resumeVersionId) {
    when(resumes.findCandidateIdsForJdVersion(jdVersionId, null, 1000)).thenReturn(List.of("candidate"));
    var contexts = mock(CandidateJobContextApplicationManager.class);
    ReflectionTestUtils.setField(manager, "jobContexts", contexts);
    when(contexts.hasCurrentPersonalPoolAssociation(any(), any())).thenAnswer(invocation -> {
      Collection<String> versions = invocation.getArgument(1);
      return versions.contains(jdVersionId) && "latest".equals(resumeVersionId);
    });
  }

  @Test
  void automaticSharingGrantsEligibilityWithoutInventingManualScreening() {
    var cycle = lifecycle("SHARED");
    cycle.setSharedAt(Instant.parse("2026-09-14T17:00:00Z"));
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(cycle));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    var status = manager.statuses(List.of("candidate")).getFirst();
    assertThat(status.isScreenedOut()).isFalse();
    assertThat(status.isCanScreenOut()).isFalse();
    assertThat(status.getLifecycle().getShareReason()).isEqualTo("AUTO_TIMEOUT");
    assertThat(status.getLifecycle().getSharedAt()).isEqualTo(cycle.getSharedAt());
    assertThat(row.getExt()).isNull();
  }

  @Test
  void automaticSharedEligibilityStillHonorsLiveRecruitmentAndScreeningBlocks() {
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(lifecycle("SHARED")));
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of("latest"));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of());
    when(recruitment.states(any())).thenReturn(Map.of("candidate",
        List.of(new State("old-version", "owner", "IN_PROCESS", true, false))));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    when(recruitment.states(any())).thenReturn(Map.of());
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
  }

  @Test
  void historicalAutomaticSharingDoesNotShareANewerResume() {
    var old = lifecycle("SHARED");
    old.setResumeVersionId("historical-version");
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(old));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_SCREENING"))
        .containsExactly("candidate");
  }

  @Test
  void serverPoolClassificationUsesCurrentLifecycleAndPreservesOwnerScope() {
    var cycle = lifecycle("PENDING_DISPOSAL");
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(cycle));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_DISPOSAL"))
        .containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_SCREENING")).isEmpty();
    // 日历纠正降回待筛选后，旧分类请求不得仍返回该候选人。
    cycle.setPhase("PENDING_SCREENING");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_DISPOSAL")).isEmpty();
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_SCREENING"))
        .containsExactly("candidate");
    row.setOwnerUserId("unmanaged-owner");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_SCREENING")).isEmpty();
  }

  @Test
  void uninitializedCandidatesRemainInPendingScreeningOnly() {
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_SCREENING"))
        .containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, "PENDING_DISPOSAL")).isEmpty();
  }

  @Test
  void unifiedPersonalPoolIncludesLegacyOverdueResumesWithoutExpandingOwnership() {
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(lifecycle("PENDING_DISPOSAL")));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, null))
        .containsExactly("candidate");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    row.setOwnerUserId("unmanaged-owner");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, null)).isEmpty();
  }

  @Test
  void newerManualReclaimOverridesHistoricalAutomaticSharingWithoutRemovingHistory() {
    var cycle = lifecycle("SHARED");
    cycle.setSharedAt(Instant.parse("2026-09-08T01:00:00Z"));
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(cycle));
    row.setExt("{\"reclaimedResumeVersionId\":\"latest\",\"reclaimedAt\":\"2026-09-08T03:00:00Z\"}");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, null))
        .containsExactly("candidate");
    assertThat(cycle.getPhase()).isEqualTo("SHARED");
  }

  @Test
  void historicalReclaimDoesNotHideALaterAutomaticSharingFact() {
    var cycle = lifecycle("SHARED");
    cycle.setSharedAt(Instant.parse("2026-09-08T05:00:00Z"));
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(cycle));
    row.setExt("{\"reclaimedResumeVersionId\":\"latest\",\"reclaimedAt\":\"2026-09-08T03:00:00Z\"}");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), null, null)).isEmpty();
  }

  private static ResumeLifecycle lifecycle(String phase) {
    var value = new ResumeLifecycle();
    value.setId("cycle"); value.setCandidateId("candidate"); value.setResumeVersionId("latest");
    value.setActiveMarker("Y"); value.setPhase(phase); value.setWarningLevel("NORMAL");
    value.setEnteredAt(Instant.parse("2026-09-08T00:00:00Z"));
    return value;
  }

  @Test
  void personalJobFilterUsesVisibleContextsAndPreservesOwnership() {
    var contexts = mock(CandidateJobContextApplicationManager.class);
    ReflectionTestUtils.setField(manager, "jobContexts", contexts);
    when(contexts.contexts(any(), any())).thenReturn(Map.of("candidate", List.of(
        new CandidateJobContextResponse("jd", "old-jd-version", "关联岗位", "JD-000001", 1,
            "latest", "PENDING", null, null, null))));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "jd"))
        .containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "other-jd")).isEmpty();
    resume.setResumeVersionId("newer-resume");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "jd")).isEmpty();
    resume.setResumeVersionId("latest");
    when(contexts.contexts(any(), any())).thenReturn(Map.of());
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "jd")).isEmpty();
    row.setOwnerUserId("other-owner");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "jd")).isEmpty();
  }

  @Test
  void sharedJobFilterIncludesHistoricalVersionsAndRequiresCurrentSharedEligibility() {
    var contexts = mock(CandidateJobContextApplicationManager.class);
    ReflectionTestUtils.setField(manager, "jobContexts", contexts);
    row.setOwnerUserId("other-owner");
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    when(contexts.contexts(any(), any(), any())).thenReturn(Map.of("candidate", List.of(
        new CandidateJobContextResponse("jd", "old-jd-version", "关联岗位", "JD-000001", 1,
            "latest", "FINISHED", null, null, null))));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"), "jd")).containsExactly("candidate");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"), "other-jd")).isEmpty();
    resume.setResumeVersionId("newer-resume");
    row.setExt("{\"screenedOutResumeVersionId\":\"newer-resume\"}");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"), "jd")).containsExactly("candidate");
    resume.setResumeVersionId("latest");
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    when(contexts.contexts(any(), any(), any())).thenReturn(Map.of());
    assertThat(manager.eligibleCandidateIds(List.of("candidate"), "jd")).isEmpty();
    when(contexts.contexts(any(), any(), any())).thenReturn(Map.of("candidate", List.of(
        new CandidateJobContextResponse("jd", "old-jd-version", "关联岗位", "JD-000001", 1,
            "latest", "FINISHED", null, null, null))));
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
        new State("latest", "other-owner", "IN_PROCESS", true, false))));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"), "jd")).isEmpty();
    when(recruitment.states(any())).thenReturn(Map.of());
    row.setExt(null);
    assertThat(manager.eligibleCandidateIds(List.of("candidate"), "jd")).isEmpty();
  }

  @Test
  void companyWideScopeReadsAnotherOwnersPrivateStatusWithoutMakingItShared() {
    actor.setUserId("other");
    actor.setSuperAdmin(true);
    assertThat(manager.statuses(List.of("candidate"))).hasSize(1);
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    assertThat(manager.statuses(List.of("candidate"))).hasSize(1);
  }

  @Test
  void explicitApplicationRelationshipAllowsStatusAccess() {
    actor.setUserId("collaborator");
    var access = mock(CandidateApplicationAccessQuery.class);
    when(access.accessibleResumeVersionIds(any(), eq("collaborator")))
        .thenReturn(Map.of("candidate", Set.of("latest")));
    ReflectionTestUtils.setField(manager, "applicationAccess", access);
    assertThat(manager.statuses(List.of("candidate"))).hasSize(1);
  }

  @Test
  void importingAloneNeverEntersSharedLibrary() {
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    assertThat(manager.statuses(List.of("candidate")).getFirst().isCanScreenOut()).isTrue();
  }

  @Test
  void personalPoolUsesOnlyCurrentOwnerWithoutSharedOrBlockingCandidates() {
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).containsExactly("candidate");

    row.setOwnerUserId("peer");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    actor.setManagedUserIds(Set.of("peer"));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    actor.setManagedUserIds(Set.of());
    row.setOwnerUserId("owner");
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    row.setExt(null);
    when(recruitment.states(any()))
        .thenReturn(Map.of("candidate", List.of(new State("latest", "owner", "IN_PROCESS", true, false))));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
  }

  @Test
  void personalPoolShortCircuitsEmptyInput() {
    assertThat(manager.personalPoolCandidateIds(List.of())).isEmpty();
    Mockito.verifyNoInteractions(candidates);
  }

  @Test
  void admittedCurrentResumeLeavesPersonalPoolAndReturnsOnlyAfterWithdrawal() {
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of("latest"));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of());
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).containsExactly("candidate");
    // 读取待分流事实不依赖用户仍持有 matching.run 权限。
    assertThat(actor.hasPermission("matching.run")).isFalse();
  }

  @Test
  void completedRecruitmentDoesNotReturnToPersonalPoolWithoutFreshReclaim() {
    for (String stage : List.of("TERMINATED", "ONBOARDED")) {
      when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
          new State("latest", "owner", stage, "ONBOARDED".equals(stage), false))));
      assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    }
  }

  @Test
  void batchReclaimMovesASharedResumeToTheCurrentOwnerWithoutStartingRecruitment() {
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    var binding = scoreBinding("score-run");
    when(scoreBindings.listByIds(List.of("score-binding"))).thenReturn(List.of(binding));
    when(scoreRuns.successfulRunIds(List.of("score-run"))).thenReturn(Set.of("score-run"));
    when(engagementClaims.claimableBy("candidate", "owner")).thenReturn(true);
    when(resumes.insertIdempotency(eq("owner"), eq("reclaim-key"),
        eq("SHARED_CANDIDATE_RECLAIM"), anyString(), anyString(), any())).thenReturn(1);
    when(candidates.reclaimFromLibrary(eq("candidate"), eq(4L), eq("owner"), eq("dept"),
        anyString(), eq("owner"), any())).thenReturn(1);
    var item = new SharedCandidateReclaimItemRequest();
    item.setCandidateId("candidate");
    item.setResumeVersionId("latest");
    item.setScoreBindingId("score-binding");
    item.setCandidateVersion(4L);
    var request = new SharedCandidateReclaimRequest();
    request.setItems(List.of(item));

    var result = manager.reclaim(request, "reclaim-key");

    assertThat(result.getReclaimedCount()).isEqualTo(1);
    assertThat(result.getCandidateIds()).containsExactly("candidate");
    verify(jobs).requireVersionForNewUse("jd-version");
    verify(lifecycleManager).reclaimed("candidate", "latest");
    verify(candidates).reclaimFromLibrary(eq("candidate"), eq(4L), eq("owner"), eq("dept"),
        anyString(), eq("owner"), any());
    verify(searchProjection).candidateChanged(eq("candidate"), anyString(), eq("owner"),
        eq("owner"), eq("dept"));
    verify(recruitment, times(1)).states(List.of("candidate"));
  }

  @Test
  void deletedJdRejectsSharedReclaimBeforeCandidateOrLifecycleChanges() {
    String originalExt = "{\"screenedOutResumeVersionId\":\"latest\"}";
    row.setExt(originalExt);
    when(scoreBindings.listByIds(List.of("score-binding"))).thenReturn(List.of(scoreBinding("score-run")));
    when(scoreRuns.successfulRunIds(List.of("score-run"))).thenReturn(Set.of("score-run"));
    when(engagementClaims.claimableBy("candidate", "owner")).thenReturn(true);
    when(resumes.insertIdempotency(eq("owner"), eq("deleted-jd-key"),
        eq("SHARED_CANDIDATE_RECLAIM"), anyString(), anyString(), any())).thenReturn(1);
    Mockito.doThrow(PlatformExceptions.conflict("JD_DELETED", "JD 已删除，不能用于新业务"))
        .when(jobs).requireVersionForNewUse("jd-version");
    var item = new SharedCandidateReclaimItemRequest();
    item.setCandidateId("candidate"); item.setResumeVersionId("latest");
    item.setScoreBindingId("score-binding"); item.setCandidateVersion(4L);
    var request = new SharedCandidateReclaimRequest();
    request.setItems(List.of(item));

    assertThatThrownBy(() -> manager.reclaim(request, "deleted-jd-key"))
        .isInstanceOf(PlatformException.class).hasMessageContaining("JD 已删除");
    assertThat(row.getExt()).isEqualTo(originalExt);
    verify(candidates, never()).reclaimFromLibrary(anyString(), anyLong(), anyString(),
        anyString(), any(), anyString(), any());
    verify(lifecycleManager, never()).reclaimed(anyString(), anyString());
    verify(events, never()).save(any());
    Mockito.verifyNoInteractions(searchProjection);
  }

  @Test
  void failedScoreKeepsTheCandidateInTheSharedLibrary() {
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    when(scoreBindings.listByIds(List.of("score-binding")))
        .thenReturn(List.of(scoreBinding("failed-run")));
    when(scoreRuns.successfulRunIds(List.of("failed-run"))).thenReturn(Set.of());
    when(resumes.insertIdempotency(eq("owner"), eq("failed-score-key"),
        eq("SHARED_CANDIDATE_RECLAIM"), anyString(), anyString(), any())).thenReturn(1);
    var item = new SharedCandidateReclaimItemRequest();
    item.setCandidateId("candidate");
    item.setResumeVersionId("latest");
    item.setScoreBindingId("score-binding");
    item.setCandidateVersion(4L);
    var request = new SharedCandidateReclaimRequest();
    request.setItems(List.of(item));

    assertThatThrownBy(() -> manager.reclaim(request, "failed-score-key"))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("AI 评分尚未成功");

    verify(candidates, never()).reclaimFromLibrary(anyString(), anyLong(), anyString(),
        anyString(), any(), anyString(), any());
  }

  private static ResumeJdScoreBinding scoreBinding(String runId) {
    var binding = new ResumeJdScoreBinding();
    binding.setId("score-binding");
    binding.setCandidateId("candidate");
    binding.setResumeVersionId("latest");
    binding.setJdVersionId("jd-version");
    binding.setDefaultMatchRunId(runId);
    binding.setSelectedByUserId("owner");
    binding.setSelectedAt(Instant.parse("2026-09-08T01:00:00Z"));
    return binding;
  }

  @Test
  void historicalResumeRoutesDoNotConsumeANewCurrentResume() {
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of("old-resume"));
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
        new State("old-resume", "owner", "TERMINATED", false, false))));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).containsExactly("candidate");
  }

  @Test
  void jobFilteredPersonalPoolExcludesScreeningAndUnclaimedFinishedRecruitment() {
    var contexts = mock(CandidateJobContextApplicationManager.class);
    ReflectionTestUtils.setField(manager, "jobContexts", contexts);
    when(contexts.contexts(any(), any())).thenReturn(Map.of("candidate", List.of(
        new CandidateJobContextResponse("jd", "jd-version", "关联岗位", "JD-000001", 1,
            "latest", "PENDING", null, null, null))));
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of("latest"));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "jd")).isEmpty();
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of());
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
        new State("latest", "owner", "TERMINATED", false, false))));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "jd"))
        .isEmpty();
  }

  @Test
  void sameCurrentResumeForSeveralJobsDoesNotDuplicateOrBreakPersonalPool() {
    var otherJob = new CandidateResumeRow();
    otherJob.setCandidateId("candidate");
    otherJob.setResumeVersionId("latest");
    otherJob.setJdVersionId("other-jd-version");
    when(resumes.findCurrentCandidateResumes(any())).thenReturn(List.of(resume, otherJob));
    var contexts = mock(CandidateJobContextApplicationManager.class);
    ReflectionTestUtils.setField(manager, "jobContexts", contexts);
    when(contexts.contexts(any(), any())).thenReturn(Map.of("candidate", List.of(
        new CandidateJobContextResponse("other-jd", "other-jd-version", "另一个岗位", "JD-000001", 1,
            "latest", "PENDING", null, null, null))));

    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"), "other-jd")).containsExactly("candidate");
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of("latest"));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
  }

  @Test
  void routedOrUnclaimedTerminatedCurrentLogicalResumeRemovesTheCandidateRow() {
    var secondResume = new CandidateResumeRow();
    secondResume.setCandidateId("candidate");
    secondResume.setResumeVersionId("second-current");
    when(resumes.findCurrentCandidateResumes(any())).thenReturn(List.of(resume, secondResume));
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of("second-current"));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    when(screening.activeResumeVersionIds(any())).thenReturn(Set.of());
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
        new State("second-current", "owner", "TERMINATED", false, false))));
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    when(recruitment.states(any())).thenReturn(Map.of());
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).containsExactly("candidate");
  }

  @Test
  void statusPageReusesCandidateAndRecruitmentSnapshots() {
    manager.statuses(List.of("candidate"));

    verify(candidates, times(1)).listByIds(List.of("candidate"));
    verify(recruitment, times(1)).states(List.of("candidate"));
  }

  @Test
  void statusPageIncludesCandidateNameFromBatchSnapshot() {
    assertThat(manager.statuses(List.of("candidate")).getFirst().getCandidateName())
        .isEqualTo("批量候选人");
  }

  @Test
  void exposesOpenIdentityConflictCountFromPermissionScopedBatchLookup() {
    var count = new CandidateConflictCountRow();
    count.setCandidateId("candidate");
    count.setOpenIdentityConflictCount(2);
    when(identityConflicts.countOpenConflictsByCandidateIds(any(), eq("owner"), eq(false), any(), any()))
        .thenReturn(List.of(count));
    assertThat(manager.statuses(List.of("candidate")).getFirst().getOpenIdentityConflictCount())
        .isEqualTo(2);
  }

  @Test
  void onlyOwnerCanRecordScreeningWithVersionAndAudit() {
    row.setExt("{\"unrelated\":\"preserved\"}");
    when(candidates.updateLibraryReview(eq("candidate"), eq(4L), eq("owner"), anyString(), any()))
        .thenAnswer(
            call -> {
              row.setExt(call.getArgument(3));
              return 1;
            });
    assertThat(
            manager
                .screenOut("candidate", new TalentVersionReasonDTO(4, "技能与当前需求不符"))
                .isLibraryEligible())
        .isTrue();
    assertThat(row.getExt()).contains("preserved", "latest", "技能与当前需求不符");
    verify(screeningTransitions)
        .complete(
            "candidate", "latest", "owner", Instant.parse("2026-09-08T00:00:00Z"));
    verify(events)
        .insertEvent(
            eq("audit-event"),
            eq("CANDIDATE_SCREENED_OUT"),
            eq("owner"),
            eq("CANDIDATE"),
            eq("candidate"),
            anyString(),
            any());
    actor.setUserId("peer");
    actor.setSuperAdmin(true);
    assertThatThrownBy(
            () -> manager.screenOut("candidate", new TalentVersionReasonDTO(4, "不能代替归属人")))
        .hasMessageContaining("归属人");
  }

  @Test
  void newResumeRequiresReviewAgainAndConflictsDoNotClaimSuccess() {
    row.setExt("{\"screenedOutResumeVersionId\":\"old\"}");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    when(candidates.updateLibraryReview(anyString(), anyLong(), anyString(), anyString(), any()))
        .thenReturn(0);
    assertThatThrownBy(() -> manager.screenOut("candidate", new TalentVersionReasonDTO(4, "筛选原因")))
        .hasMessageContaining("刷新");
  }

  @Test
  void oldScoreCannotShareANewerResume() {
    assertThatThrownBy(
            () -> manager.screenOut("candidate", new TalentVersionReasonDTO(4, "不推进", "old")))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("当前简历已更新");
    Mockito.verifyNoInteractions(events);
    Mockito.verify(candidates, Mockito.never())
        .updateLibraryReview(anyString(), anyLong(), anyString(), anyString(), any());
  }

  @Test
  void matchingCurrentResumeCanBeShared() {
    when(candidates.updateLibraryReview(eq("candidate"), eq(4L), eq("owner"), anyString(), any()))
        .thenAnswer(
            call -> {
              row.setExt(call.getArgument(3));
              return 1;
            });
    assertThat(
            manager
                .screenOut("candidate", new TalentVersionReasonDTO(4, "人工核对完成", "latest"))
                .isLibraryEligible())
        .isTrue();
  }

  @Test
  void activeInterviewOfferAndOnboardedAlwaysBlockLibrary() {
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    for (var stage : List.of("PATH_SELECTION", "IN_PROCESS", "DECISION", "OFFER", "ONBOARDED")) {
      when(recruitment.states(any()))
          .thenReturn(
              Map.of("candidate", List.of(new State("latest", "owner", stage, true, false))));
      assertThat(manager.eligibleCandidateIds(List.of("candidate"))).as(stage).isEmpty();
      assertThat(manager.statuses(List.of("candidate")).getFirst().isCanScreenOut()).isFalse();
    }
  }

  @Test
  void taggedRejectionSharesOnlyTheBoundCurrentResumeAfterEveryActivityEnds() {
    var tagged = new State("latest", "other-owner", "TERMINATED", false, true, "tagged-app", "jd", true);
    var otherActivity = new State("latest", "another-owner", "IN_PROCESS", true, false);
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(tagged, otherActivity)));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(tagged)));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    resume.setResumeVersionId("new-version");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
  }

  @Test
  void activeBlacklistKeepsSharedCandidateVisibleAndOutsidePersonalLibrary() {
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    when(lifecycle.list(any(Wrapper.class))).thenReturn(List.of(lifecycle("SHARED")));
    when(risks.blacklistTags(any(), any())).thenReturn(List.of("candidate"));

    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    var status = manager.statuses(List.of("candidate")).getFirst();
    assertThat(status.isLibraryEligible()).isTrue();
    assertThat(status.isCanScreenOut()).isFalse();
  }

  @Test
  void activeBlacklistSharesCurrentResumeWithoutRequiringAnotherScreeningAction() {
    when(risks.blacklistTags(any(), any())).thenReturn(List.of("candidate"));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    assertThat(manager.statuses(List.of("candidate")).getFirst().isScreenedOut()).isFalse();

    when(recruitment.states(any())).thenReturn(Map.of("candidate",
        List.of(new State("latest", "owner", "IN_PROCESS", true, false))));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    when(recruitment.states(any())).thenReturn(Map.of());
    when(resumes.findCurrentCandidateResumes(any())).thenReturn(List.of());
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
  }

  @Test
  void disabledOrExpiredBlacklistReevaluatesNormalSharingFacts() {
    when(risks.blacklistTags(any(), any())).thenReturn(List.of("candidate"));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    when(risks.blacklistTags(any(), any())).thenReturn(List.of());
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    row.setExt("{\"screenedOutResumeVersionId\":\"latest\"}");
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
  }

  @Test
  void cancelledCurrentRecruitmentMovesToSharedInsteadOfPersonalPool() {
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
        new State("latest", "owner", "TERMINATED", false, false))));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
    assertThat(manager.statuses(List.of("candidate")).getFirst().isScreenedOut()).isFalse();
  }

  @Test
  void sameOwnerReclaimDoesNotShareAgainUntilANewerRecruitmentTerminates() {
    row.setExt("{\"reclaimedResumeVersionId\":\"latest\",\"reclaimedAt\":\"2026-09-08T03:00:00Z\"}");
    for (boolean rejected : List.of(false, true)) {
      when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
          new State("latest", "owner", "TERMINATED", false, rejected, "old-app", "jd", false,
              Instant.parse("2026-09-08T01:00:00Z")))));
      assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
      assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).containsExactly("candidate");
    }
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
        new State("latest", "owner", "TERMINATED", false, false, "new-app", "jd", false,
            Instant.parse("2026-09-08T04:00:00Z")))));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).isEmpty();
  }

  @Test
  void scoreBeforeCancellationCannotReclaimTheNewSharedPeriod() {
    when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(
        new State("latest", "owner", "TERMINATED", false, false, "app", "jd", false,
            Instant.parse("2026-09-08T02:00:00Z")))));
    when(scoreBindings.listByIds(List.of("score-binding"))).thenReturn(List.of(scoreBinding("old-run")));
    when(scoreRuns.successfulRunIds(List.of("old-run"))).thenReturn(Set.of("old-run"));
    when(resumes.insertIdempotency(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);
    var item = new SharedCandidateReclaimItemRequest();
    item.setCandidateId("candidate"); item.setResumeVersionId("latest");
    item.setScoreBindingId("score-binding"); item.setCandidateVersion(4L);
    var request = new SharedCandidateReclaimRequest(); request.setItems(List.of(item));
    assertThatThrownBy(() -> manager.reclaim(request, "stale-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SHARED_RECLAIM_SCORE_STALE"));
    verify(candidates, never()).reclaimFromLibrary(anyString(), anyLong(), anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void personalPoolBuildsOneSharedEligibilitySnapshot() {
    assertThat(manager.personalPoolCandidateIds(List.of("candidate"))).containsExactly("candidate");

    verify(candidates, times(1)).listByIds(List.of("candidate"));
    verify(candidates, times(1)).listByIds(any());
    verify(recruitment, times(1)).states(Set.of("candidate"));
    verify(resumes, times(1)).findCurrentCandidateResumes(List.of("candidate"));
    verify(screening, times(1)).activeResumeVersionIds(Set.of("candidate"));
    verify(risks, times(1)).blacklistTags(List.of("candidate"), Instant.parse("2026-09-08T00:00:00Z"));
  }

  @Test
  void personalPoolReusesOnlyActiveOwnedRowsAndKeepsEveryLiveExclusion() {
    var personal = poolCandidate("personal", "owner", "ACTIVE");
    var shared = poolCandidate("shared", "owner", "ACTIVE");
    shared.setExt("{\"screenedOutResumeVersionId\":\"version-shared\"}");
    var rows = List.of(personal, shared,
        poolCandidate("blocked", "owner", "ACTIVE"),
        poolCandidate("admitted", "owner", "ACTIVE"),
        poolCandidate("blacklisted", "owner", "ACTIVE"),
        poolCandidate("foreign", "peer", "ACTIVE"),
        poolCandidate("inactive", "owner", "DELETED"));
    stubPersonalPoolRows(rows);
    var requested = List.of("personal", "shared", "blocked", "admitted", "blacklisted",
        "foreign", "inactive", "missing");
    Set<String> scoped = Set.of("personal", "shared", "blocked", "admitted", "blacklisted");
    when(recruitment.states(scoped)).thenReturn(Map.of("blocked",
        List.of(new State("version-blocked", "owner", "IN_PROCESS", true, false))));
    when(screening.activeResumeVersionIds(scoped)).thenReturn(Set.of("version-admitted"));
    when(risks.blacklistTags(any(), any())).thenReturn(List.of("blacklisted"));

    assertThat(manager.personalPoolCandidateIds(requested)).containsExactly("personal");

    verify(candidates, times(1)).listByIds(requested);
    verify(candidates, times(1)).listByIds(any());
    verify(recruitment, times(1)).states(scoped);
    verify(resumes, times(1)).findCurrentCandidateResumes(
        argThat(ids -> Set.copyOf(ids).equals(scoped)));
    verify(screening, times(1)).activeResumeVersionIds(scoped);
    verify(risks, times(1)).blacklistTags(
        argThat(ids -> Set.copyOf(ids).equals(scoped)),
        eq(Instant.parse("2026-09-08T00:00:00Z")));
    verify(identityConflicts, times(1)).countOpenConflictsByCandidateIds(
        scoped, "owner", false, Set.of(), Set.of());
    verify(lifecycle, times(1)).list(any(Wrapper.class));
  }

  @Test
  void companyScopeReusesOtherOwnersActiveRowsWithoutAdmittingInactiveCandidates() {
    actor.setCompanyScope(true);
    stubPersonalPoolRows(List.of(
        poolCandidate("owned", "owner", "ACTIVE"),
        poolCandidate("foreign", "peer", "ACTIVE"),
        poolCandidate("inactive", "owner", "DELETED")));
    var requested = List.of("owned", "foreign", "inactive", "missing");
    Set<String> scoped = Set.of("owned", "foreign");

    assertThat(manager.personalPoolCandidateIds(requested))
        .containsExactlyInAnyOrder("owned", "foreign");

    verify(candidates, times(1)).listByIds(requested);
    verify(candidates, times(1)).listByIds(any());
    verify(recruitment, times(1)).states(scoped);
    verify(resumes, times(1)).findCurrentCandidateResumes(
        argThat(ids -> Set.copyOf(ids).equals(scoped)));
    verify(screening, times(1)).activeResumeVersionIds(scoped);
    verify(risks, times(1)).blacklistTags(
        argThat(ids -> Set.copyOf(ids).equals(scoped)),
        eq(Instant.parse("2026-09-08T00:00:00Z")));
    verify(identityConflicts, times(1)).countOpenConflictsByCandidateIds(
        scoped, "owner", true, Set.of(), Set.of());
    verify(lifecycle, times(1)).list(any(Wrapper.class));
  }

  private void stubPersonalPoolRows(List<Candidate> rows) {
    Mockito.doAnswer(invocation -> {
      Collection<?> ids = invocation.getArgument(0);
      return rows.stream().filter(candidate -> ids.contains(candidate.getId())).toList();
    }).when(candidates).listByIds(any());
    Mockito.doAnswer(invocation -> {
      Collection<String> ids = invocation.getArgument(0);
      return ids.stream().map(id -> {
        var current = new CandidateResumeRow();
        current.setCandidateId(id);
        current.setResumeVersionId("version-" + id);
        return current;
      }).toList();
    }).when(resumes).findCurrentCandidateResumes(any());
  }

  private static Candidate poolCandidate(String id, String owner, String status) {
    var candidate = new Candidate();
    candidate.setId(id);
    candidate.setFullName(id);
    candidate.setOwnerUserId(owner);
    candidate.setStatus(status);
    return candidate;
  }

  @Test
  void onlyTerminationOfCurrentResumeByItsOwnerCanEnterLibrary() {
    for (var state :
        List.of(
            new State("old", "owner", "TERMINATED", false, true),
            new State("latest", "peer", "TERMINATED", false, true))) {
      when(recruitment.states(any())).thenReturn(Map.of("candidate", List.of(state)));
      assertThat(manager.eligibleCandidateIds(List.of("candidate"))).isEmpty();
    }
    when(recruitment.states(any()))
        .thenReturn(
            Map.of("candidate", List.of(new State("latest", "owner", "TERMINATED", false, true))));
    assertThat(manager.eligibleCandidateIds(List.of("candidate"))).containsExactly("candidate");
  }
}
