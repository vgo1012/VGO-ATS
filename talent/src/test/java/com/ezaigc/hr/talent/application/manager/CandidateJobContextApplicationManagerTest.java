package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.talent.api.*;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.domain.response.CandidateJobContextResponse;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateJobContextApplicationManagerTest {
  @Test
  void deletionAssociationUsesReadyCurrentIntakeAndSuccessfulCurrentBindingsWithoutVisibility() {
    var jobs = mock(JobContextQuery.class);
    var scores = mock(ScoreRunCompletionQuery.class);
    when(scores.successfulRunIds(any())).thenReturn(Set.of("successful", "historical-run"));
    var manager = historyManager(List.of("person"), List.of(resume("person", "current", "intake-job")),
        List.of(binding("person", "current", "successful-job", "successful"),
            binding("person", "current", "failed-job", "failed"),
            binding("person", "old", "historical-job", "historical-run")), jobs, scores);

    assertThat(manager.hasCurrentPersonalPoolAssociation(List.of("person"), List.of("intake-job"))).isTrue();
    assertThat(manager.hasCurrentPersonalPoolAssociation(List.of("person"), List.of("successful-job"))).isTrue();
    assertThat(manager.hasCurrentPersonalPoolAssociation(List.of("person"), List.of("failed-job"))).isFalse();
    assertThat(manager.hasCurrentPersonalPoolAssociation(List.of("person"), List.of("historical-job"))).isFalse();
    verifyNoInteractions(jobs);
  }

  @Test
  void freshClaimUsesOnlyItsNewScoreAndOldSameJobCancellationCannotOverrideIt() {
    var manager = new CandidateJobContextApplicationManager(); allowAllBoundScores(manager);
    var candidates = mock(CandidatePersistenceService.class);
    var candidate = new Candidate(); candidate.setId("person");
    candidate.setExt("{\"reclaimedResumeVersionId\":\"resume\",\"reclaimedScoreBindingId\":\"claimed\",\"reclaimedAt\":\"2026-09-08T03:00:00Z\"}");
    when(candidates.listByIds(any())).thenReturn(List.of(candidate));
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    var jobs = mock(JobContextQuery.class); var intakes = mock(TalentIntakePersistenceService.class);
    var matching = mock(CandidateMatchingStateQuery.class); var bindings = mock(ResumeJdScoreBindingPersistenceService.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs); ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "matching", matching); ReflectionTestUtils.setField(manager, "scoreBindings", bindings);
    when(intakes.findCurrentCandidateResumes(any())).thenReturn(List.of(resume("person", "resume", "old-job")));
    var claimed = binding("person", "resume", "new-job", "new-run"); claimed.setId("claimed");
    var old = binding("person", "resume", "old-job", "old-run"); old.setId("old");
    when(bindings.findForCandidates(any())).thenReturn(List.of(claimed, old));
    when(jobs.visibleVersions(any())).thenReturn(Map.of(
        "old-job", new JobContextQuery.Context("old", "old-job", "旧岗位", "JD-OLD", 1),
        "new-job", new JobContextQuery.Context("new", "new-job", "本次岗位", "JD-NEW", 1)));
    var history = Map.of("person", List.of(new CandidateRecruitmentStateQuery.State(
        "resume", "owner", "TERMINATED", false, false, "old-app", "new-job", false,
        Instant.parse("2026-09-08T01:00:00Z"))));
    var contexts = manager.contexts(List.of("person"), history).get("person");
    assertThat(contexts).singleElement().satisfies(context -> {
      assertThat(context.getJdVersionId()).isEqualTo("new-job");
      assertThat(context.getMatchRunId()).isEqualTo("new-run");
      assertThat(context.getStatus()).isEqualTo("PENDING");
    });
  }

  @Test
  void sharedCandidateWithoutCurrentSuccessfulIntakeKeepsHistoricalScreeningAndRecruitment() {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var jobs = mock(JobContextQuery.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "scoreBindings", mock(ResumeJdScoreBindingPersistenceService.class));
    when(intakes.findCandidateResumeHistory(List.of("shared"))).thenReturn(List.of(
        resume("shared", "intake-only-resume", "imported")));
    when(matching.completedStates(List.of("shared", "private"))).thenReturn(List.of(
        new CandidateMatchingStateQuery.State("shared", "historical", "screened", "old-run")));
    when(jobs.visibleVersions(any(), any())).thenReturn(Map.of(
        "imported", new JobContextQuery.Context("i", "imported", "历史收件", "JD-I", 1),
        "screened", new JobContextQuery.Context("s", "screened", "历史筛选", "JD-S", 1),
        "recruited", new JobContextQuery.Context("r", "recruited", "历史招聘", "JD-R", 1)));
    var applications = Map.of("shared", List.of(new CandidateRecruitmentStateQuery.State(
        "old-resume", "owner", "TERMINATED", false, true, "old-application", "recruited")));

    var result = manager.contexts(List.of("shared", "private"), applications, Set.of("shared"));

    assertThat(result.get("shared")).extracting(CandidateJobContextResponse::getJdVersionId)
        .containsExactly("imported", "screened", "recruited");
    assertThat(result.get("shared")).extracting(CandidateJobContextResponse::getResumeVersionId)
        .containsExactly("intake-only-resume", "historical", "old-resume");
    assertThat(result.get("private")).isEmpty();
    verify(matching).completedStates(List.of("shared", "private"));
    verify(intakes).findCandidateResumeHistory(List.of("shared"));
  }
  @Test
  void sharedHistoricalJobsDoNotExpandPrivateCandidatesAndMultipleJobsArePreserved() {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var jobs = mock(JobContextQuery.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "scoreBindings", mock(ResumeJdScoreBindingPersistenceService.class));
    when(intakes.findCurrentCandidateResumes(any())).thenReturn(List.of(
        resume("shared", "current", "jd-a"), resume("shared", "current", "jd-b"),
        resume("private", "private-resume", "jd-a")));
    when(matching.completedStates(any())).thenReturn(List.of(
        new CandidateMatchingStateQuery.State("shared", "historical", "jd-old", "old-run")));
    when(jobs.visibleVersions(any())).thenReturn(Map.of());
    when(jobs.visibleVersions(any(), any())).thenReturn(Map.of(
        "jd-a", new JobContextQuery.Context("a", "jd-a", "共享岗位甲", "JD-A", 1),
        "jd-b", new JobContextQuery.Context("b", "jd-b", "共享岗位乙", "JD-B", 1),
        "jd-old", new JobContextQuery.Context("old", "jd-old", "历史岗位", "JD-OLD", 1)));
    var result = manager.contexts(List.of("shared", "private"), Map.of(), Set.of("shared"));
    assertThat(result.get("shared")).extracting(CandidateJobContextResponse::getJdVersionId)
        .containsExactlyInAnyOrder("jd-a", "jd-b", "jd-old");
    assertThat(result.get("private")).isEmpty();
    assertThat(result.get("shared")).filteredOn(row -> "historical".equals(row.getResumeVersionId()))
        .singleElement().satisfies(row -> assertThat(row.getMatchRunId()).isEqualTo("old-run"));
  }
  @Test
  void jobStatesAreVersionSpecificAndDoNotTurnAiScreeningIntoRecruitment() {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var jobs = mock(JobContextQuery.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "matching", matching);
    when(jobs.visibleVersions(any()))
        .thenReturn(
            Map.of(
                "job-a",
                new JobContextQuery.Context("a", "job-a", "岗位甲", "JD-000001", 1),
                "job-b",
                new JobContextQuery.Context("b", "job-b", "岗位乙", "JD-000001", 2)));
    var resume = new CandidateResumeRow();
    resume.setCandidateId("person");
    resume.setResumeVersionId("current");
    resume.setJdVersionId("job-a");
    resume.setDefaultMatchRunId("default-run");
    var intakes = mock(TalentIntakePersistenceService.class);
    when(intakes.findCurrentCandidateResumes(any())).thenReturn(List.of(resume));
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "scoreBindings", mock(ResumeJdScoreBindingPersistenceService.class));
    when(matching.completedStates(any()))
        .thenReturn(
            List.of(new CandidateMatchingStateQuery.State("person", "old", "job-a", "old-run")));
    assertThat(manager.contexts(List.of("person"), Map.of()).get("person"))
        .singleElement()
        .satisfies(row -> {
          assertThat(row.getJdCode()).isEqualTo("JD-000001");
          assertThat(row.getStatus()).isEqualTo("PENDING");
          assertThat(row.getMatchRunId()).isEqualTo("default-run");
        });
    when(matching.completedStates(any()))
        .thenReturn(
            List.of(new CandidateMatchingStateQuery.State("person", "current", "job-a", "run")));
    var states =
        manager
            .contexts(
                List.of("person"),
                Map.of(
                    "person",
                    List.of(
                        new CandidateRecruitmentStateQuery.State(
                            "current", "hr", "IN_PROCESS", true, false, "application", "job-b"))))
            .get("person");
    assertThat(states)
        .extracting(row -> row.getStatus())
        .containsExactly("AI_SCREENED", "ADVANCED");
    assertThat(states.getFirst().getApplicationId()).isNull();
    assertThat(states.get(1).getApplicationId()).isEqualTo("application");
    when(jobs.visibleVersions(any())).thenReturn(Map.of());
    assertThat(manager.contexts(List.of("person"), Map.of()).get("person")).isEmpty();
  }

  @Test
  void indexesThousandCompletedStatesOnceAndPreservesCandidateAndJobOrder() {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var jobs = mock(JobContextQuery.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "scoreBindings", mock(ResumeJdScoreBindingPersistenceService.class));
    var resumes = new ArrayList<CandidateResumeRow>();
    var screened = new ArrayList<CandidateMatchingStateQuery.State>();
    var candidateIds = new ArrayList<String>();
    var candidateReads = new AtomicInteger();
    for (int index = 0; index < 1000; index++) {
      String candidateId = "candidate-" + index;
      candidateIds.add(candidateId);
      resumes.add(resume(candidateId, "resume-" + index, "job-base"));
      int reverse = 999 - index;
      var state = mock(CandidateMatchingStateQuery.State.class, withSettings()
          .spiedInstance(new CandidateMatchingStateQuery.State(
              "candidate-" + reverse, "resume-" + reverse, "job-screened", "run-" + reverse))
          .defaultAnswer(CALLS_REAL_METHODS).stubOnly());
      doAnswer(invocation -> {
        candidateReads.incrementAndGet();
        return invocation.callRealMethod();
      }).when(state).getCandidateId();
      screened.add(state);
    }
    when(intakes.findCurrentCandidateResumes(candidateIds)).thenReturn(resumes);
    when(matching.completedStates(candidateIds)).thenReturn(screened);
    when(jobs.visibleVersions(any())).thenReturn(Map.of(
        "job-base", new JobContextQuery.Context("base", "job-base", "待筛选岗位", "JD-000001", 1),
        "job-screened", new JobContextQuery.Context("screened", "job-screened", "已筛选岗位", "JD-000001", 2)));

    var result = manager.contexts(candidateIds, Map.of());

    assertThat(result.keySet()).containsExactlyElementsOf(candidateIds);
    for (int index = 0; index < 1000; index++) {
      var contexts = result.get("candidate-" + index);
      assertThat(contexts).extracting(row -> row.getJdVersionId())
          .containsExactly("job-base", "job-screened");
      assertThat(contexts).extracting(row -> row.getStatus())
          .containsExactly("PENDING", "AI_SCREENED");
      assertThat(contexts).extracting(row -> row.getResumeVersionId())
          .containsExactly("resume-" + index, "resume-" + index);
      assertThat(contexts.get(1).getMatchRunId()).isEqualTo("run-" + index);
    }
    assertThat(candidateReads.get()).isLessThanOrEqualTo(1000);
    verify(intakes).findCurrentCandidateResumes(candidateIds);
    verify(intakes, never()).findCandidateResumeHistory(any());
    verify(matching).completedStates(candidateIds);
    verify(jobs).visibleVersions(List.of("job-base", "job-screened"));
  }

  @Test
  void keepsMatchingFactsBoundToBothCandidateAndCurrentResume() {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var jobs = mock(JobContextQuery.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "scoreBindings", mock(ResumeJdScoreBindingPersistenceService.class));
    when(intakes.findCurrentCandidateResumes(any())).thenReturn(List.of(
        resume("first", "shared-resume", "base"),
        resume("second", "shared-resume", "base")));
    when(matching.completedStates(any())).thenReturn(List.of(
        new CandidateMatchingStateQuery.State("first", "old-resume", "old-job", "old-run"),
        new CandidateMatchingStateQuery.State("first", "shared-resume", "first-job", "first-run"),
        new CandidateMatchingStateQuery.State("second", "shared-resume", "second-job", "second-run"),
        new CandidateMatchingStateQuery.State("other", "shared-resume", "other-job", "other-run")));
    when(jobs.visibleVersions(any())).thenReturn(Map.of(
        "base", new JobContextQuery.Context("base", "base", "基础岗位", "JD-000001", 1),
        "old-job", new JobContextQuery.Context("old", "old-job", "历史岗位", "JD-000001", 1),
        "first-job", new JobContextQuery.Context("first", "first-job", "第一岗位", "JD-000001", 1),
        "second-job", new JobContextQuery.Context("second", "second-job", "第二岗位", "JD-000001", 1),
        "other-job", new JobContextQuery.Context("other", "other-job", "其他岗位", "JD-000001", 1)));

    var result = manager.contexts(List.of("first", "second"), Map.of());

    assertThat(result.get("first")).extracting(row -> row.getJdVersionId())
        .containsExactly("base", "first-job");
    assertThat(result.get("first").get(1).getMatchRunId()).isEqualTo("first-run");
    assertThat(result.get("second")).extracting(row -> row.getJdVersionId())
        .containsExactly("base", "second-job");
    assertThat(result.get("second").get(1).getMatchRunId()).isEqualTo("second-run");
  }

  @Test
  void preservesLatestMatchingOrderRecruitmentPrecedenceAndVisibleJobs() {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var jobs = mock(JobContextQuery.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "scoreBindings", mock(ResumeJdScoreBindingPersistenceService.class));
    when(intakes.findCurrentCandidateResumes(any()))
        .thenReturn(List.of(resume("person", "current", "base")));
    when(matching.completedStates(any())).thenReturn(List.of(
        new CandidateMatchingStateQuery.State("person", "current", "second", "second-old"),
        new CandidateMatchingStateQuery.State("person", "current", "base", "base-run"),
        new CandidateMatchingStateQuery.State("person", "current", "hidden", "hidden-run"),
        new CandidateMatchingStateQuery.State("person", "current", "third", "third-run"),
        new CandidateMatchingStateQuery.State("person", "current", "second", "second-latest")));
    when(jobs.visibleVersions(any())).thenReturn(Map.of(
        "base", new JobContextQuery.Context("base", "base", "基础岗位", "JD-000001", 1),
        "second", new JobContextQuery.Context("second", "second", "第二岗位", "JD-000001", 2),
        "third", new JobContextQuery.Context("third", "third", "第三岗位", "JD-000001", 3)));
    var applications = Map.of("person", List.of(
        new CandidateRecruitmentStateQuery.State(
            "current", "hr", "IN_PROCESS", true, false, "active", "third"),
        new CandidateRecruitmentStateQuery.State(
            "current", "hr", "REJECTED", false, true, "finished", "third"),
        new CandidateRecruitmentStateQuery.State(
            "current", "hr", "IN_PROCESS", true, false, "hidden-application", "hidden")));

    var contexts = manager.contexts(List.of("person"), applications).get("person");

    assertThat(contexts).extracting(row -> row.getJdVersionId())
        .containsExactly("base", "second", "third");
    assertThat(contexts).extracting(row -> row.getStatus())
        .containsExactly("AI_SCREENED", "AI_SCREENED", "ADVANCED");
    assertThat(contexts).extracting(row -> row.getMatchRunId())
        .containsExactly("base-run", "second-latest", null);
    assertThat(contexts.get(2).getApplicationId()).isEqualTo("active");
    assertThat(contexts.get(2).getStage()).isEqualTo("IN_PROCESS");
  }

  @Test
  void latestExplicitRescoreReplacesTheOlderRunForTheSameResumeAndJob() {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var jobs = mock(JobContextQuery.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    var scoreBindings = mock(ResumeJdScoreBindingPersistenceService.class);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "scoreBindings", scoreBindings);
    when(intakes.findCurrentCandidateResumes(any()))
        .thenReturn(List.of(resume("person", "resume", "original-job")));
    when(scoreBindings.findForCandidates(any())).thenReturn(List.of(
        binding("person", "resume", "selected-job", "latest-run"),
        binding("person", "resume", "selected-job", "older-run")));
    when(jobs.visibleVersions(any())).thenReturn(Map.of(
        "original-job", new JobContextQuery.Context("original", "original-job", "原岗位", "JD-1", 1),
        "selected-job", new JobContextQuery.Context("selected", "selected-job", "重评岗位", "JD-2", 1)));

    var contexts = manager.contexts(List.of("person"), Map.of()).get("person");

    assertThat(contexts).extracting(row -> row.getJdVersionId())
        .containsExactly("original-job", "selected-job");
    assertThat(contexts.get(1).getMatchRunId()).isEqualTo("latest-run");
  }

  @ParameterizedTest(name = "{0} historical runs, latest run also succeeded: {1}")
  @CsvSource({"0,false", "1,false", "1000,false", "1001,false", "2001,false",
      "1001,true", "2001,true"})
  void readsCompleteSharedRescoreHistoryWithinTheExistingRunQueryLimit(
      int size, boolean latestAlsoSucceeded) {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var candidates = mock(CandidatePersistenceService.class);
    var candidate = new Candidate();
    candidate.setId("person");
    candidate.setOwnerUserId("owner");
    when(candidates.listByIds(List.of("person"))).thenReturn(List.of(candidate));
    var jobs = mock(JobContextQuery.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    var matching = mock(CandidateMatchingStateQuery.class);
    var bindings = mock(ResumeJdScoreBindingPersistenceService.class);
    var scoreRuns = mock(ScoreRunCompletionQuery.class);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "scoreBindings", bindings);
    ReflectionTestUtils.setField(manager, "scoreRuns", scoreRuns);
    when(intakes.findCurrentCandidateResumes(List.of("person")))
        .thenReturn(List.of(resume("person", "resume", "selected-job")));
    when(intakes.findCandidateResumeHistory(List.of("person"))).thenReturn(List.of());
    List<String> runIds = IntStream.range(0, size)
        .mapToObj(index -> String.format(Locale.ROOT, "%026d", index)).toList();
    when(bindings.findForCandidates(List.of("person"))).thenReturn(runIds.stream()
        .map(id -> binding("person", "resume", "selected-job", id)).toList());
    var visibleJobs = Map.of("selected-job",
        new JobContextQuery.Context("job", "selected-job", "当前岗位", "JD-1", 1));
    when(jobs.visibleVersions(any())).thenReturn(visibleJobs);
    when(jobs.visibleVersions(any(), any())).thenReturn(visibleJobs);
    Map<String, String> statuses = new LinkedHashMap<>();
    for (int index = 0; index < size; index++) {
      statuses.put(runIds.get(index), index == size - 1 || latestAlsoSucceeded && index == 0 ? "SUCCEEDED"
          : index % 2 == 0 ? "FAILED" : "PARTIAL");
    }
    List<List<String>> queriedBatches = new ArrayList<>();
    when(scoreRuns.successfulRunIds(any())).thenAnswer(invocation -> {
      Collection<String> requested = invocation.getArgument(0);
      if (requested == null || requested.isEmpty()) return Set.of();
      // 独立复现现有提供方的 1000 项契约，红测不依赖尚未新增的 API 常量。
      if (requested.size() > 1000) throw new IllegalArgumentException("评分运行最多1000项");
      queriedBatches.add(List.copyOf(requested));
      return Set.copyOf(requested.stream()
          .filter(id -> "SUCCEEDED".equals(statuses.get(id))).toList());
    });

    var result = assertDoesNotThrow(() -> manager.contexts(List.of("person"), Map.of(), Set.of("person")));

    assertThat(result.get("person")).singleElement().satisfies(context -> {
      assertThat(context.getJdVersionId()).isEqualTo("selected-job");
      assertThat(context.getResumeVersionId()).isEqualTo("resume");
      assertThat(context.getStatus()).isEqualTo("PENDING");
      assertThat(context.getMatchRunId()).isEqualTo(size == 0 ? "default-run"
          : latestAlsoSucceeded ? runIds.getFirst() : runIds.getLast());
    });
    assertThat(queriedBatches).hasSize((size + 999) / 1000);
    for (var batch : queriedBatches) assertThat(batch.size()).isBetween(1, 1000);
    assertThat(queriedBatches.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(runIds);
    verify(jobs).visibleVersions(List.of("selected-job"));
    verify(jobs).visibleVersions(Set.of("selected-job"), Set.of("selected-job"));
    verify(intakes).findCandidateResumeHistory(List.of("person"));
  }

  @ParameterizedTest(name = "{0} distinct historical JD versions, shared: {1}")
  @CsvSource({"0,false", "1,false", "1000,false", "1001,false", "2001,false",
      "0,true", "1,true", "1000,true", "1001,true", "2001,true"})
  void readsCompleteVisibleJobVersionHistoryWithinTheExistingLimit(int size, boolean shared) {
    List<String> versionIds = IntStream.range(0, size)
        .mapToObj(index -> String.format(Locale.ROOT, "%026d", index)).toList();
    Map<String, JobContextQuery.Context> allJobs = new LinkedHashMap<>();
    Map<String, JobContextQuery.Context> privateJobs = new LinkedHashMap<>();
    for (int index = 0; index < size; index++) {
      String id = versionIds.get(index);
      var context = new JobContextQuery.Context("logical-job", id, "历史岗位", "JD-1", size - index);
      allJobs.put(id, context);
      if (index % 3 != 0) privateJobs.put(id, context);
    }
    var jobs = mock(JobContextQuery.class);
    List<List<String>> privateBatches = new ArrayList<>();
    List<List<String>> sharedBatches = new ArrayList<>();
    when(jobs.visibleVersions(any())).thenAnswer(invocation ->
        boundedJobContexts(invocation.getArgument(0), privateJobs, privateBatches));
    when(jobs.visibleVersions(any(), any())).thenAnswer(invocation -> {
      Collection<String> requested = invocation.getArgument(0);
      Collection<String> trusted = invocation.getArgument(1);
      assertThat(trusted).containsExactlyInAnyOrderElementsOf(requested);
      return boundedJobContexts(requested, allJobs, sharedBatches);
    });
    List<List<String>> scoreBatches = new ArrayList<>();
    var manager = historyManager(List.of("person"), size == 0 ? List.of()
        : List.of(resume("person", "resume", versionIds.getFirst())), versionIds.stream()
        .map(id -> binding("person", "resume", id, "run-" + id)).toList(), jobs,
        boundedSuccessfulScores(scoreBatches));

    var result = assertDoesNotThrow(() -> manager.contexts(List.of("person"), Map.of(),
        shared ? Set.of("person") : Set.of()));

    var expected = shared ? allJobs : privateJobs;
    assertThat(result.get("person")).extracting(CandidateJobContextResponse::getJdVersionId)
        .containsExactlyElementsOf(expected.keySet());
    assertThat(result.get("person")).allSatisfy(context -> {
      assertThat(context.getJdId()).isEqualTo("logical-job");
      assertThat(context.getResumeVersionId()).isEqualTo("resume");
      assertThat(context.getMatchRunId()).isEqualTo("run-" + context.getJdVersionId());
      assertThat(context.getStatus()).isEqualTo("PENDING");
    });
    assertThat(privateBatches).hasSize((size + 999) / 1000);
    assertThat(privateBatches.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(versionIds);
    assertThat(sharedBatches).hasSize(shared ? (size + 999) / 1000 : 0);
    if (shared) assertThat(sharedBatches.stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(versionIds);
    assertThat(scoreBatches.stream().flatMap(List::stream).toList())
        .containsExactlyElementsOf(versionIds.stream().map(id -> "run-" + id).toList());
  }

  @Test
  void sharedBatchesDoNotExposeHiddenPrivateJobsEvenWhenTheSameVersionIsShared() {
    List<String> sharedVersions = IntStream.range(0, 1001)
        .mapToObj(index -> String.format(Locale.ROOT, "%026d", index)).toList();
    Map<String, JobContextQuery.Context> sharedJobs = new LinkedHashMap<>();
    sharedVersions.forEach(id -> sharedJobs.put(id,
        new JobContextQuery.Context("logical-job", id, "共享历史岗位", "JD-1", 1)));
    var jobs = mock(JobContextQuery.class);
    List<List<String>> privateBatches = new ArrayList<>();
    List<List<String>> sharedBatches = new ArrayList<>();
    when(jobs.visibleVersions(any())).thenAnswer(invocation ->
        boundedJobContexts(invocation.getArgument(0), Map.of(), privateBatches));
    when(jobs.visibleVersions(any(), any())).thenAnswer(invocation -> {
      Collection<String> requested = invocation.getArgument(0);
      Collection<String> trusted = invocation.getArgument(1);
      assertThat(trusted).containsExactlyInAnyOrderElementsOf(requested);
      assertThat(trusted).doesNotContain("private-only-hidden");
      assertThat(sharedVersions).containsAll(trusted);
      return boundedJobContexts(requested, sharedJobs, sharedBatches);
    });
    var bindings = new ArrayList<>(sharedVersions.stream()
        .map(id -> binding("shared", "shared-resume", id, "shared-run-" + id)).toList());
    bindings.add(binding("private", "private-resume", sharedVersions.getFirst(), "private-same-version"));
    bindings.add(binding("private", "private-resume", "private-only-hidden", "private-only-run"));
    List<List<String>> scoreBatches = new ArrayList<>();
    var manager = historyManager(List.of("private", "shared"), List.of(
        resume("private", "private-resume", sharedVersions.getFirst()),
        resume("shared", "shared-resume", sharedVersions.getFirst())), bindings, jobs,
        boundedSuccessfulScores(scoreBatches));

    var result = assertDoesNotThrow(() -> manager.contexts(List.of("private", "shared"),
        Map.of(), Set.of("shared")));

    assertThat(result.keySet()).containsExactly("private", "shared");
    assertThat(result.get("private")).isEmpty();
    assertThat(result.get("shared")).extracting(CandidateJobContextResponse::getJdVersionId)
        .containsExactlyElementsOf(sharedVersions);
    assertThat(result.get("shared")).allSatisfy(context -> {
      assertThat(context.getResumeVersionId()).isEqualTo("shared-resume");
      assertThat(context.getMatchRunId()).isEqualTo("shared-run-" + context.getJdVersionId());
      assertThat(context.getStatus()).isEqualTo("PENDING");
    });
    assertThat(privateBatches).hasSize(2);
    var privateVersions = new ArrayList<>(sharedVersions);
    privateVersions.add("private-only-hidden");
    assertThat(privateBatches.stream().flatMap(List::stream).toList())
        .containsExactlyElementsOf(privateVersions);
    assertThat(sharedBatches).hasSize(2);
    assertThat(sharedBatches.stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(sharedVersions);
    assertThat(scoreBatches.stream().flatMap(List::stream).toList())
        .containsExactlyElementsOf(bindings.stream().map(ResumeJdScoreBinding::getDefaultMatchRunId).toList());
  }

  @Test
  void indexesThousandReboundBindingsOnceAndPreservesCandidateAndJobOrder() {
    var candidateIds = IntStream.range(0, 1000).mapToObj(index -> "candidate-" + index).toList();
    var candidateReads = new AtomicInteger();
    var bindings = new ArrayList<ResumeJdScoreBinding>();
    var runIds = new ArrayList<String>();
    for (int index = 999; index >= 0; index--) {
      var row = new ResumeJdScoreBinding() {
        @Override
        public String getCandidateId() {
          candidateReads.incrementAndGet();
          return super.getCandidateId();
        }
      };
      row.setCandidateId("candidate-" + index);
      row.setResumeVersionId("same-resume");
      row.setJdVersionId("rebound-job");
      row.setDefaultMatchRunId("run-" + index);
      bindings.add(row);
      runIds.add(row.getDefaultMatchRunId());
    }
    var jobs = mock(JobContextQuery.class);
    when(jobs.visibleVersions(any())).thenReturn(Map.of(
        "base-job", new JobContextQuery.Context("base", "base-job", "原岗位", "JD-BASE", 1),
        "rebound-job", new JobContextQuery.Context("rebound", "rebound-job", "重评岗位", "JD-REBOUND", 1)));
    var scores = mock(ScoreRunCompletionQuery.class);
    when(scores.successfulRunIds(any())).thenAnswer(invocation -> Set.copyOf(invocation.getArgument(0)));
    var manager = historyManager(candidateIds, candidateIds.stream()
        .map(id -> resume(id, "same-resume", "base-job")).toList(), bindings, jobs, scores);

    var result = manager.contexts(candidateIds, Map.of());

    assertThat(result.keySet()).containsExactlyElementsOf(candidateIds);
    for (int index = 0; index < 1000; index++) {
      var contexts = result.get("candidate-" + index);
      assertThat(contexts).extracting(CandidateJobContextResponse::getJdVersionId)
          .containsExactly("base-job", "rebound-job");
      assertThat(contexts).extracting(CandidateJobContextResponse::getResumeVersionId)
          .containsExactly("same-resume", "same-resume");
      assertThat(contexts).extracting(CandidateJobContextResponse::getStatus)
          .containsExactly("PENDING", "PENDING");
      assertThat(contexts).extracting(CandidateJobContextResponse::getMatchRunId)
          .containsExactly("default-run", "run-" + index);
    }
    assertThat(candidateReads.get()).isLessThanOrEqualTo(1000);
    verify(scores).successfulRunIds(runIds);
    verify(jobs).visibleVersions(List.of("base-job", "rebound-job"));
    verifyNoMoreInteractions(scores, jobs);
  }

  @Test
  void keepsReboundCandidatesLatestSuccessfulOrderAndSharedVisibilitySeparate() {
    var candidateIds = List.of("first", "second");
    var bindings = List.of(
        binding("second", "same-resume", "selected-job", "failed-latest"),
        binding("first", "same-resume", "selected-job", "first-latest"),
        binding("second", "same-resume", "selected-job", "second-latest"),
        binding("first", "same-resume", "selected-job", "first-old"),
        binding("second", "same-resume", "selected-job", "second-old"),
        binding("first", "old-resume", "stale-job", "private-stale"),
        binding("first", "same-resume", "hidden-job", "private-hidden"),
        binding("second", "same-resume", "hidden-job", "shared-hidden"),
        binding("second", "old-resume", "historical-job", "shared-history"));
    var jobs = mock(JobContextQuery.class);
    var visible = Map.of(
        "base-job", new JobContextQuery.Context("base", "base-job", "原岗位", "JD-BASE", 1),
        "selected-job", new JobContextQuery.Context("selected", "selected-job", "重评岗位", "JD-SELECTED", 1),
        "stale-job", new JobContextQuery.Context("stale", "stale-job", "旧简历岗位", "JD-STALE", 1),
        "historical-job", new JobContextQuery.Context("history", "historical-job", "历史岗位", "JD-HISTORY", 1));
    var sharedVisible = new LinkedHashMap<>(visible);
    sharedVisible.put("hidden-job", new JobContextQuery.Context("hidden", "hidden-job", "共享岗位", "JD-HIDDEN", 1));
    when(jobs.visibleVersions(any())).thenReturn(visible);
    when(jobs.visibleVersions(any(), any())).thenReturn(sharedVisible);
    var scores = mock(ScoreRunCompletionQuery.class);
    when(scores.successfulRunIds(any())).thenAnswer(invocation -> {
      Collection<String> requested = invocation.getArgument(0);
      return Set.copyOf(requested.stream().filter(id -> !"failed-latest".equals(id)).toList());
    });
    var manager = historyManager(candidateIds, List.of(
        resume("first", "same-resume", "base-job"),
        resume("second", "same-resume", "base-job")), bindings, jobs, scores);

    var result = manager.contexts(candidateIds, Map.of(), Set.of("second"));

    assertThat(result.keySet()).containsExactly("first", "second");
    assertThat(result.get("first")).extracting(CandidateJobContextResponse::getJdVersionId)
        .containsExactly("base-job", "selected-job");
    assertThat(result.get("first")).extracting(CandidateJobContextResponse::getMatchRunId)
        .containsExactly("default-run", "first-latest");
    assertThat(result.get("first")).extracting(CandidateJobContextResponse::getResumeVersionId)
        .containsExactly("same-resume", "same-resume");
    assertThat(result.get("second")).extracting(CandidateJobContextResponse::getJdVersionId)
        .containsExactly("base-job", "selected-job", "hidden-job", "historical-job");
    assertThat(result.get("second")).extracting(CandidateJobContextResponse::getMatchRunId)
        .containsExactly("default-run", "second-latest", "shared-hidden", "shared-history");
    assertThat(result.get("second")).extracting(CandidateJobContextResponse::getResumeVersionId)
        .containsExactly("same-resume", "same-resume", "same-resume", "old-resume");
    assertThat(result.values().stream().flatMap(List::stream)).allSatisfy(context ->
        assertThat(context.getStatus()).isEqualTo("PENDING"));
    verify(scores).successfulRunIds(bindings.stream().map(ResumeJdScoreBinding::getDefaultMatchRunId).toList());
    verify(jobs).visibleVersions(List.of("base-job", "selected-job", "stale-job", "hidden-job", "historical-job"));
    var trustedShared = Set.of("base-job", "selected-job", "hidden-job", "historical-job");
    verify(jobs).visibleVersions(trustedShared, trustedShared);
    verifyNoMoreInteractions(scores, jobs);
  }

  private static Map<String, JobContextQuery.Context> boundedJobContexts(Collection<String> requested,
      Map<String, JobContextQuery.Context> visible, List<List<String>> batches) {
    // 独立复现原提供方上限，红测不引用尚未新增的 API 常量。
    if (requested.size() > 1000) throw PlatformExceptions.unprocessable("TOO_MANY_JDS", "岗位上下文最多1000项");
    if (requested.isEmpty()) return Map.of();
    batches.add(List.copyOf(requested));
    Map<String, JobContextQuery.Context> result = new LinkedHashMap<>();
    requested.forEach(id -> { if (visible.containsKey(id)) result.put(id, visible.get(id)); });
    return result;
  }

  private static ScoreRunCompletionQuery boundedSuccessfulScores(List<List<String>> batches) {
    var scores = mock(ScoreRunCompletionQuery.class);
    when(scores.successfulRunIds(any())).thenAnswer(invocation -> {
      Collection<String> requested = invocation.getArgument(0);
      if (requested.size() > 1000) throw new IllegalArgumentException("评分运行最多1000项");
      if (!requested.isEmpty()) batches.add(List.copyOf(requested));
      return Set.copyOf(requested);
    });
    return scores;
  }

  private static CandidateJobContextApplicationManager historyManager(List<String> candidateIds,
      List<CandidateResumeRow> resumes, List<ResumeJdScoreBinding> bindings, JobContextQuery jobs,
      ScoreRunCompletionQuery scores) {
    var manager = new CandidateJobContextApplicationManager();
    allowAllBoundScores(manager);
    var candidates = mock(CandidatePersistenceService.class);
    when(candidates.listByIds(candidateIds)).thenReturn(candidateIds.stream().map(id -> {
      var candidate = new Candidate(); candidate.setId(id); candidate.setOwnerUserId("owner-" + id);
      return candidate;
    }).toList());
    var intakes = mock(TalentIntakePersistenceService.class);
    when(intakes.findCurrentCandidateResumes(candidateIds)).thenReturn(resumes);
    when(intakes.findCandidateResumeHistory(any())).thenReturn(List.of());
    var matching = mock(CandidateMatchingStateQuery.class);
    when(matching.completedStates(candidateIds)).thenReturn(List.of());
    var scoresByCandidate = mock(ResumeJdScoreBindingPersistenceService.class);
    when(scoresByCandidate.findForCandidates(candidateIds)).thenReturn(bindings);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "jobs", jobs);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "matching", matching);
    ReflectionTestUtils.setField(manager, "scoreBindings", scoresByCandidate);
    ReflectionTestUtils.setField(manager, "scoreRuns", scores);
    return manager;
  }

  private static void allowAllBoundScores(CandidateJobContextApplicationManager manager) {
    ReflectionTestUtils.setField(manager, "candidates", mock(CandidatePersistenceService.class));
    ReflectionTestUtils.setField(manager, "reclaimedScores", new SharedReclaimScoreApplicationManager());
    var scoreRuns = mock(ScoreRunCompletionQuery.class);
    when(scoreRuns.successfulRunIds(any())).thenAnswer(invocation ->
        Set.copyOf(invocation.getArgument(0)));
    ReflectionTestUtils.setField(manager, "scoreRuns", scoreRuns);
  }

  private static CandidateResumeRow resume(String candidateId, String resumeVersionId, String jdVersionId) {
    var row = new CandidateResumeRow();
    row.setCandidateId(candidateId);
    row.setResumeVersionId(resumeVersionId);
    row.setJdVersionId(jdVersionId);
    row.setDefaultMatchRunId("default-run");
    return row;
  }

  private static ResumeJdScoreBinding binding(
      String candidateId, String resumeVersionId, String jdVersionId, String runId) {
    var row = new ResumeJdScoreBinding();
    row.setCandidateId(candidateId);
    row.setResumeVersionId(resumeVersionId);
    row.setJdVersionId(jdVersionId);
    row.setDefaultMatchRunId(runId);
    return row;
  }
}
