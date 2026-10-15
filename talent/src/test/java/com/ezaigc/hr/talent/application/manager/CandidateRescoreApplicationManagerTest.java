package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.api.CandidateScreeningTransitionService;
import com.ezaigc.hr.talent.api.JobContextQuery;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.domain.request.CandidateRescoreItemRequest;
import com.ezaigc.hr.talent.domain.request.CandidateRescoreRequest;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateRescoreApplicationManagerTest {
  @ParameterizedTest
  @CsvSource({"false,false,false", "true,true,false", "true,false,false", "true,true,true"})
  void sharedReclaimJdSelectionCreatesAFreshScoreBindingWithoutChangingOwnership(
      boolean activeScreening, boolean terminatedRecruitment, boolean activeRecruitment) {
    var manager = new CandidateRescoreApplicationManager();
    var authorization = mock(AuthorizationService.class);
    var candidates = mock(CandidatePersistenceService.class);
    var library = mock(CandidateLibraryQueryService.class);
    var recruitment = mock(CandidateRecruitmentStateQuery.class);
    var routing = mock(CandidateResumeVersionService.class);
    var screening = mock(CandidateScreeningStateQuery.class);
    var transitions = mock(CandidateScreeningTransitionService.class);
    var scoring = mock(ResumeIntakeScoringService.class);
    var bindings = mock(ResumeJdScoreBindingPersistenceService.class);
    var idempotencies = mock(TalentIdempotencyPersistenceService.class);
    var resumes = mock(TalentIntakePersistenceService.class);
    var events = mock(TalentProcessEventPersistenceService.class);
    var jobs = mock(JobContextQuery.class);
    var ids = mock(PlatformIdGenerator.class);
    var actor = new CurrentActorSnapshot(
        "owner", "归属人", "owner@example.invalid", "dept", Set.of(), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("talent.manage", "matching.run"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    when(jobs.requireVisibleVersion("jd-version"))
        .thenReturn(new JobContextQuery.Context("jd", "jd-version", "新岗位", "JD-2", 3));
    var candidate = new Candidate();
    candidate.setId("candidate");
    candidate.setOwnerUserId("owner");
    candidate.setStatus("ACTIVE");
    when(candidates.findCandidateForUpdate("candidate")).thenReturn(candidate);
    when(library.eligibleCandidateIds(List.of("candidate"))).thenReturn(Set.of("candidate"));
    var resume = new CandidateResumeRow();
    resume.setCandidateId("candidate");
    resume.setResumeVersionId("resume");
    when(resumes.findCurrentCandidateResumes(List.of("candidate"))).thenReturn(List.of(resume));
    when(screening.activeResumeVersionIds(List.of("candidate")))
        .thenReturn(activeScreening ? Set.of("resume") : Set.of());
    when(recruitment.hasActiveRecruitment("resume")).thenReturn(activeRecruitment);
    when(recruitment.states(List.of("candidate"))).thenReturn(terminatedRecruitment
        ? Map.of("candidate", List.of(new CandidateRecruitmentStateQuery.State(
            "resume", "owner", "TERMINATED", false, false))) : Map.of());
    when(resumes.insertIdempotency(eq("owner"), eq("rescore-key"), eq("SHARED_RECLAIM_SCORE"),
        anyString(), eq("operation"), any())).thenReturn(1);
    when(scoring.enqueueRescore("jd-version", "resume", "owner", "operation:candidate"))
        .thenReturn(new ResumeIntakeScoringService.ScoringTaskRef(
            "task", "fresh-run", "QUEUED", null, false));
    when(bindings.save(any())).thenReturn(true);
    var persisted = new ResumeJdScoreBinding();
    persisted.setId("binding");
    persisted.setCandidateId("candidate");
    persisted.setResumeVersionId("resume");
    persisted.setJdId("jd");
    persisted.setJdVersionId("jd-version");
    persisted.setDefaultMatchRunId("fresh-run");
    when(bindings.findByOperation("operation")).thenReturn(List.of(persisted));
    when(scoring.get("fresh-run", "owner")).thenReturn(
        new ResumeIntakeScoringService.ScoringTaskRef(
            "task", "fresh-run", "QUEUED", null, false));
    when(ids.newId()).thenReturn("operation", "binding", "event");
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "library", library);
    ReflectionTestUtils.setField(manager, "recruitment", recruitment);
    ReflectionTestUtils.setField(manager, "routing", routing);
    ReflectionTestUtils.setField(manager, "screening", screening);
    ReflectionTestUtils.setField(manager, "screeningTransitions", transitions);
    ReflectionTestUtils.setField(manager, "intakeScoring", scoring);
    ReflectionTestUtils.setField(manager, "scoreBindings", bindings);
    ReflectionTestUtils.setField(manager, "idempotencies", idempotencies);
    ReflectionTestUtils.setField(manager, "resumes", resumes);
    ReflectionTestUtils.setField(manager, "events", events);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-22T03:00:00Z"), ZoneOffset.UTC));
    var item = new CandidateRescoreItemRequest();
    item.setCandidateId("candidate");
    item.setResumeVersionId("resume");
    var request = new CandidateRescoreRequest();
    request.setJdVersionId("jd-version");
    request.setItems(List.of(item));

    if (activeRecruitment || (activeScreening && !terminatedRecruitment)) {
      assertThatThrownBy(() -> manager.rescore(request, "rescore-key"))
          .hasMessageContaining(activeRecruitment ? "仍在招聘流程中" : "仍在智能筛选中");
      verifyNoInteractions(scoring, transitions);
      return;
    }
    var result = manager.rescore(request, "rescore-key");

    assertThat(result.getOperationId()).isEqualTo("operation");
    assertThat(result.getRequestedCount()).isEqualTo(1);
    assertThat(result.getItems().getFirst().getDefaultMatchRunId()).isEqualTo("fresh-run");
    assertThat(result.getItems().getFirst().getScoreBindingId()).isEqualTo("binding");
    assertThat(result.getItems().getFirst().getTaskId()).isEqualTo("task");
    var binding = ArgumentCaptor.forClass(ResumeJdScoreBinding.class);
    verify(bindings).save(binding.capture());
    assertThat(binding.getValue().getSelectedByUserId()).isEqualTo("owner");
    assertThat(binding.getValue().getOwnerDepartmentId()).isEqualTo("dept");
    verify(recruitment).hasActiveRecruitment("resume");
    verify(scoring).enqueueRescore("jd-version", "resume", "owner", "operation:candidate");
    if (activeScreening) {
      var order = inOrder(transitions, scoring);
      order.verify(transitions).complete("candidate", "resume", "owner", Instant.parse("2026-09-22T03:00:00Z"));
      order.verify(scoring).enqueueRescore("jd-version", "resume", "owner", "operation:candidate");
    } else {
      verifyNoInteractions(transitions);
    }
  }
}
