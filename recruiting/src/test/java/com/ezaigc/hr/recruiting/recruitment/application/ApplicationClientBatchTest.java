package com.ezaigc.hr.recruiting.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.ApplicationAssembler;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingProcessPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationClientBatchTest {
  private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
  private final RecruitmentApplicationManager manager = new RecruitmentApplicationManager();
  private final ApplicationPersistenceService persistence = mock(ApplicationPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final DemandQueryService demands = mock(DemandQueryService.class);
  private final RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
  private final OnboardingProcessPersistenceService onboardingProcesses =
      mock(OnboardingProcessPersistenceService.class);

  @BeforeEach
  void setUp() {
    var actor = new CurrentActorSnapshot(id("U", 1), "HR", null, id("D", 1),
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("recruitment.manage"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "demands", demands);
    ReflectionTestUtils.setField(manager, "interviewPersistenceService", interviews);
    ReflectionTestUtils.setField(manager, "recruitmentWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(manager, "onboardingProcessPersistenceService", onboardingProcesses);
    ReflectionTestUtils.setField(manager, "applicationCollaboratorPersistenceService", mock(ApplicationCollaboratorPersistenceService.class));
    ReflectionTestUtils.setField(manager, "assembler", new ApplicationAssembler());
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("application-client-batch-key-32bytes"));
    when(demands.requireJdVersion(any())).thenReturn(new JdVersionSnapshot());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void hundredVisibleVersionsUseOneBatchWithoutHiddenOrLookaheadRows() {
    var returned = IntStream.range(0, 100).mapToObj(index -> application(index, index)).toList();
    var hidden = application(100, 100);
    hidden.setUpdateTime(NOW.plusSeconds(1));
    returned.get(1).setRecruitmentType("INTERNAL");
    var ahead = application(101, 101);
    var scanned = new ArrayList<Application>();
    scanned.add(hidden);
    scanned.addAll(returned);
    scanned.add(ahead);
    Set<String> visible = returned.stream().map(Application::getId).collect(Collectors.toSet());
    visible.add(ahead.getId());
    rows(scanned, visible);
    Map<String, JdClientSummary> summaries = new LinkedHashMap<>();
    returned.forEach(app -> summaries.put(app.getJdVersionId(), new JdClientSummary(id("C", 1), "历史客户")));
    summaries.put(id("V", 1), new JdClientSummary(null, null));
    summaries.put(id("V", 2), new JdClientSummary(id("C", 2), null));
    when(demands.clientSummariesForVersions(any())).thenReturn(summaries);
    var step = new WorkflowStep();
    step.setId(id("S", 1));
    step.setApplicationId(returned.getFirst().getId());
    step.setStepCode("INTERVIEW_ROUND_2");
    step.setRoundNo(2);
    step.setStatus("IN_PROGRESS");
    var secondStep = new WorkflowStep();
    secondStep.setId(id("S", 2)); secondStep.setApplicationId(returned.get(1).getId());
    secondStep.setStepCode("INTERVIEW_ROUND_1"); secondStep.setRoundNo(2); secondStep.setStatus("IN_PROGRESS");
    when(steps.findActiveSteps(any())).thenReturn(List.of(step, secondStep));
    when(interviews.latestTypesByStepIds(Set.of(step.getId(), secondStep.getId())))
        .thenReturn(Map.of(step.getId(), "INTERNAL", secondStep.getId(), "EXTERNAL"));
    when(interviews.activeRoundNosByApplicationIds(any()))
        .thenReturn(Map.of(returned.getFirst().getId(), List.of(3, 5)));
    when(interviews.latestCompletedRoundNosByApplicationIds(any()))
        .thenReturn(Map.of(returned.getFirst().getId(), 2));
    var sourcePage = manager.list(null, null, null, null, null, null, 100);

    var page = manager.applicationResponses(null, null, null, null, null, null, 100);

    ArgumentCaptor<Collection<String>> batch = ArgumentCaptor.forClass((Class) Collection.class);
    verify(demands).clientSummariesForVersions(batch.capture());
    assertThat(batch.getValue()).containsExactlyInAnyOrderElementsOf(returned.stream().map(Application::getJdVersionId).toList());
    verify(demands, never()).requireJdVersion(any());
    assertThat(page.getItems()).extracting(value -> value.getId())
        .containsExactlyElementsOf(returned.stream().map(Application::getId).toList());
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isNotBlank().isEqualTo(sourcePage.getNextCursor());
    assertThat(page.getItems().getFirst().getCurrentStepCode()).isEqualTo("INTERVIEW_ROUND_2");
    assertThat(page.getItems().getFirst().getCurrentStepRoundNo()).isEqualTo(2);
    assertThat(page.getItems().getFirst().getCurrentStepStatus()).isEqualTo("IN_PROGRESS");
    assertThat(page.getItems().getFirst().getCurrentInterviewType()).isEqualTo("INTERNAL");
    assertThat(page.getItems().get(1).getCurrentInterviewType()).isEqualTo("EXTERNAL");
    verify(interviews).latestTypesByStepIds(Set.of(step.getId(), secondStep.getId()));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<String>> activeApplications = ArgumentCaptor.forClass(Set.class);
    verify(interviews).activeRoundNosByApplicationIds(activeApplications.capture());
    assertThat(activeApplications.getValue())
        .containsExactlyInAnyOrderElementsOf(returned.stream().map(Application::getId).toList())
        .doesNotContain(hidden.getId(), ahead.getId());
    verify(interviews).latestCompletedRoundNosByApplicationIds(activeApplications.capture());
    assertThat(activeApplications.getValue())
        .containsExactlyInAnyOrderElementsOf(returned.stream().map(Application::getId).toList())
        .doesNotContain(hidden.getId(), ahead.getId());
    assertThat(page.getItems().getFirst().getActiveInterviewRoundNos()).containsExactly(3, 5);
    assertThat(page.getItems().getFirst().getLatestCompletedInterviewRoundNo()).isEqualTo(2);
    assertThat(page.getItems().get(1).getActiveInterviewRoundNos()).isEmpty();
    assertThat(page.getItems().get(1).getLatestCompletedInterviewRoundNo()).isNull();
    assertThat(page.getItems().getFirst().getClientId()).isEqualTo(id("C", 1));
    assertThat(page.getItems().getFirst().getClientName()).isEqualTo("历史客户");
    assertThat(page.getItems().get(1).getClientId()).isNull();
    assertThat(page.getItems().get(1).getClientName()).isNull();
    assertThat(page.getItems().get(2).getClientId()).isEqualTo(id("C", 2));
    assertThat(page.getItems().get(2).getClientName()).isNull();
    verify(steps).findActiveSteps(returned.stream().map(Application::getId).toList());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void repeatedVersionIsLoadedOnceAndBothRowsKeepItsCustomer() {
    var rows = List.of(application(1, 0), application(2, 0));
    rows(rows, rows.stream().map(Application::getId).collect(Collectors.toSet()));
    when(demands.clientSummariesForVersions(any())).thenReturn(Map.of(id("V", 0), new JdClientSummary(id("C", 1), "客户")));
    var page = manager.applicationResponses(null, null, null, null, null, null, 100);
    ArgumentCaptor<Collection<String>> batch = ArgumentCaptor.forClass((Class) Collection.class);
    verify(demands).clientSummariesForVersions(batch.capture());
    assertThat(batch.getValue()).containsExactly(id("V", 0));
    assertThat(page.getItems()).extracting(value -> value.getClientName()).containsExactly("客户", "客户");
    verify(demands, never()).requireJdVersion(any());
  }

  @Test
  void emptyPageDoesNotQueryCustomersOrWorkflowSteps() {
    rows(List.of(), Set.of());
    var page = manager.applicationResponses(null, null, null, null, null, null, 100);
    assertThat(page.getItems()).isEmpty();
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
    verifyNoInteractions(demands, steps, interviews);
  }

  private void rows(List<Application> values, Set<String> visible) {
    when(persistence.findApplications(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(values);
    when(authorization.accessibleObjectIds(any(), any(), any())).thenReturn(visible);
  }

  private static Application application(int index, int version) {
    var app = new Application();
    app.setId(id("A", index));
    app.setJdVersionId(id("V", version));
    app.setOwnerUserId(id("U", 1));
    app.setOwnerDepartmentId(id("D", 1));
    app.setRecruitmentType("EXTERNAL");
    app.setCurrentStage("IN_PROCESS");
    app.setUpdateTime(NOW.minusSeconds(index));
    return app;
  }

  private static String id(String prefix, int number) {
    return prefix + String.format("%025d", number);
  }
}
