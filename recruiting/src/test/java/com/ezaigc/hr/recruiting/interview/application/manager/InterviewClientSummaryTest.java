package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewResponse;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewClientSummaryTest {
  private final InterviewApplicationManager manager = new InterviewApplicationManager();
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final CandidateDirectoryService candidates = mock(CandidateDirectoryService.class);
  private final DemandQueryService demands = mock(DemandQueryService.class);

  @BeforeEach
  void setUp() {
    var authorization = mock(AuthorizationService.class);
    var participants = mock(ParticipantPersistenceService.class);
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot(
        "actor", "招聘专员", "hr@example.invalid", "department", Set.of("department"), Set.of(),
        Set.of(), Set.of("RECRUITER"), Set.of("interview.schedule", "interview.feedback.read-authorized"), false, false));
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(), anyString(), any())).thenAnswer(call ->
        call.<List<AuthorizationTarget>>getArgument(2).stream().map(AuthorizationTarget::getObjectId)
            .filter(id -> !id.equals("hidden")).collect(Collectors.toSet()));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(manager, "persistence", interviews);
    ReflectionTestUtils.setField(manager, "demands", demands);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("interview-summary-test-key-32-bytes"));
  }

  @Test
  void batchesOnlyTheVisibleReturnedPageInsteadOfLoadingOneHundredFullJds() {
    List<Interview> rows = new ArrayList<>();
    rows.add(interview("hidden", "hidden-version", 0));
    rows.addAll(IntStream.range(0, 101).mapToObj(index -> interview(String.format("I%025d", index), "version-" + index, index + 1)).toList());
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(rows);
    Map<String, JdClientSummary> summaries = IntStream.range(0, 100).boxed()
        .collect(Collectors.toMap(index -> "version-" + index, index -> new JdClientSummary("client-" + index, "客户" + index, "岗位" + index, "JD-" + index)));
    when(demands.clientSummariesForVersions(any())).thenReturn(summaries);
    Map<String, String> names = IntStream.range(0, 100).boxed()
        .collect(Collectors.toMap(index -> String.format("I%025d", index), index -> "候选人" + index));
    when(candidates.namesForAuthorizedCandidates(any())).thenReturn(names);
    when(demands.requireJdVersion(any())).thenReturn(new JdVersionSnapshot());

    var page = manager.listResponses(null, null, null, null, null, null, null, null, null, null, 100, null, null);

    assertThat(page.getItems()).hasSize(100);
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isNotBlank();
    verify(demands).clientSummariesForVersions(summaries.keySet());
    verify(candidates).namesForAuthorizedCandidates(names.keySet());
    assertThat(page.getItems()).extracting(InterviewResponse::getCandidateName)
        .containsExactlyElementsOf(IntStream.range(0, 100).mapToObj(index -> "候选人" + index).toList());
    assertThat(page.getItems().getFirst().getJdTitle()).isEqualTo("岗位0");
    assertThat(page.getItems().getFirst().getJdCode()).isEqualTo("JD-0");
    verify(demands, never()).requireJdVersion(any());
    assertThat(page.getItems()).extracting(InterviewResponse::getClientName)
        .containsExactlyElementsOf(IntStream.range(0, 100).mapToObj(index -> "客户" + index).toList());
  }

  @Test
  void keepsOrderAndCursorWithRepeatedVersionsAndInternalNullClients() {
    var first = interview("F".repeat(26), "external", 0);
    var second = interview("S".repeat(26), "external", 1);
    var internal = interview("I".repeat(26), "internal-version", 2);
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenAnswer(call -> call.getArgument(11) == null ? List.of(first, second, internal) : List.of(internal));
    when(demands.clientSummariesForVersions(Set.of("external"))).thenReturn(Map.of("external", new JdClientSummary("historical", "历史客户")));
    when(demands.clientSummariesForVersions(Set.of("internal-version"))).thenReturn(Map.of("internal-version", new JdClientSummary(null, null)));
    when(demands.requireJdVersion(any())).thenReturn(new JdVersionSnapshot());

    var page = manager.listResponses(null, null, null, null, null, null, null, null, null, null, 2, null, null);
    assertThat(page.getItems()).extracting(InterviewResponse::getId).containsExactly(first.getId(), second.getId());
    assertThat(page.getItems()).extracting(InterviewResponse::getClientId).containsExactly("historical", "historical");
    var next = manager.listResponses(null, null, null, null, null, null, null, null, null, page.getNextCursor(), 2, null, null);
    assertThat(next.getItems()).extracting(InterviewResponse::getId).containsExactly(internal.getId());
    assertThat(next.getItems().getFirst().getClientId()).isNull();
    assertThat(next.getItems().getFirst().getClientName()).isNull();
    assertThat(next.isHasMore()).isFalse();
    verify(demands).clientSummariesForVersions(Set.of("external"));
    verify(demands).clientSummariesForVersions(Set.of("internal-version"));
  }

  @Test
  void emptyPageDoesNotLoadClientSummaries() {
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
    assertThat(manager.listResponses(null, null, null, null, null, null, null, null, null, null, 10, null, null).getItems()).isEmpty();
    verifyNoInteractions(demands, candidates);
  }

  private static Interview interview(String id, String version, int order) {
    var value = new Interview();
    value.setId(id); value.setCandidateId(id); value.setJdVersionId(version); value.setInterviewType("EXTERNAL_CLIENT");
    value.setScheduledStartAt(Instant.parse("2026-09-12T01:00:00Z").minusSeconds(order));
    value.setOwnerUserId("actor"); value.setOwnerDepartmentId("department");
    return value;
  }
}
