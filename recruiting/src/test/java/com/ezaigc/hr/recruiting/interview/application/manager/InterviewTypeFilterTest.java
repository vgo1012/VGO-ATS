package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewTypeFilterTest {

  private static final Instant START = Instant.parse("2026-09-09T08:00:00Z");
  private final InterviewApplicationManager manager = new InterviewApplicationManager();
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final ParticipantPersistenceService participants = mock(ParticipantPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final DemandQueryService demands = mock(DemandQueryService.class);

  @BeforeEach
  void setUp() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot(
        "actor", "招聘专员", "hr@example.invalid", "department", Set.of("department"), Set.of(),
        Set.of(), Set.of("RECRUITER"), Set.of("interview.schedule", "interview.feedback.read-authorized"),
        false, false);
    when(authorization.currentActor()).thenReturn(actor);
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(), anyString(), any())).thenAnswer(call ->
        call.<List<AuthorizationTarget>>getArgument(2).stream()
            .map(AuthorizationTarget::getObjectId)
            .collect(Collectors.toSet()));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "demands", demands);
    ReflectionTestUtils.setField(manager, "persistence", interviews);
    ReflectionTestUtils.setField(manager, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(manager, "cursorCodec",
        new SignedCursorCodec("interview-type-filter-test-key-32-bytes"));
  }

  @Test
  void typeAndClientFiltersReachDatabaseWithoutLoadingClientMaps() {
    Interview external = interview("external", "EXTERNAL", 0);
    Interview hidden = interview("hidden", "EXTERNAL_CLIENT", 1);
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(external, hidden));
    doReturn(Set.of("external")).when(authorization).accessibleObjectIds(any(), anyString(), any());

    assertThat(manager.list(null, null, null, null, null, null, null, null, null,
        null, 10, "EXTERNAL", "client-1").getItems()).containsExactly(external);

    verify(interviews).findInterviews(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
        isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("EXTERNAL"), eq("client-1"), eq(200));
    verifyNoInteractions(demands);
  }

  @Test
  void permissionFilteringContinuesAcrossHiddenBatchesAndPreservesCursor() {
    List<Interview> hidden = IntStream.range(0, 200)
        .mapToObj(index -> interview(String.format("I%025d", index), "EXTERNAL", index)).toList();
    Interview first = interview("X".repeat(26), "EXTERNAL_CLIENT", 200);
    Interview second = interview("Y".repeat(26), "EXTERNAL_CLIENT_AI", 201);
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenAnswer(call -> {
          String after = call.getArgument(11);
          if (after == null) return hidden;
          if (after.equals(hidden.getLast().getId())) return List.of(first, second);
          if (after.equals(first.getId())) return List.of(second);
          return List.of();
        });
    doReturn(Set.of(first.getId(), second.getId())).when(authorization).accessibleObjectIds(any(), anyString(), any());
    var page = manager.list(null, null, null, null, null, null, null, null, null,
        null, 1, "EXTERNAL", "client-1");
    assertThat(page.getItems()).containsExactly(first);
    assertThat(page.isHasMore()).isTrue();
    var next = manager.list(null, null, null, null, null, null, null, null, null,
        page.getNextCursor(), 1, "EXTERNAL", "client-1");
    assertThat(next.getItems()).containsExactly(second);
    assertThat(next.isHasMore()).isFalse();
    verifyNoInteractions(demands);
  }

  @Test
  void typeAndClientFiltersRemainBoundToTheCursorScope() {
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(interview("X".repeat(26), "EXTERNAL_CLIENT", 0),
            interview("Y".repeat(26), "EXTERNAL_CLIENT_AI", 1)));
    var page = manager.list(null, null, null, null, null, null, null, null, null,
        null, 1, "EXTERNAL", "client-1");
    assertThatThrownBy(() -> manager.list(null, null, null, null, null, null, null, null, null,
        page.getNextCursor(), 1, "EXTERNAL_CLIENT", "client-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
    assertThatThrownBy(() -> manager.list(null, null, null, null, null, null, null, null, null,
        page.getNextCursor(), 1, "EXTERNAL", "client-2"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
  }

  @Test
  void applicationFilterIsAppliedBeforeVisibilityFiltering() {
    String applicationId = "A".repeat(26);
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of());

    assertThat(manager.list(null, null, null, null, null, null, null, null, null,
        null, 10, null, null, applicationId).getItems()).isEmpty();

    verify(interviews).findInterviews(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
        isNull(), eq(applicationId), isNull(), isNull(), isNull(), isNull(), isNull(), any(), any(), eq(200));
  }

  @Test
  void workflowStepFilterIsAppliedBeforePaginationAndBindsCursorScope() {
    String applicationId = "A".repeat(26);
    String stepId = "S".repeat(26);
    when(interviews.findInterviews(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(List.of(interview("I".repeat(26), "INTERNAL", 0),
            interview("J".repeat(26), "INTERNAL", 1)));

    var page = manager.list(null, null, null, null, null, null, null, null, null, null,
        1, null, null, applicationId, " " + stepId + " ");

    verify(interviews).findInterviews(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
        isNull(), eq(applicationId), isNull(), isNull(), isNull(), isNull(), eq(stepId), any(), any(), eq(200));
    assertThat(page.getItems()).hasSize(1);
    assertThatThrownBy(() -> manager.list(null, null, null, null, null, null, null, null, null,
        page.getNextCursor(), 1, null, null, applicationId, "T".repeat(26)))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
  }

  private static Interview interview(String id, String type, int order) {
    Interview interview = new Interview();
    interview.setId(id);
    interview.setInterviewType(type);
    interview.setScheduledStartAt(START.plusSeconds(order));
    interview.setApplicationId("A".repeat(26));
    interview.setCandidateId("C".repeat(26));
    interview.setJdId("J".repeat(26));
    interview.setJdVersionId("V".repeat(26));
    interview.setOwnerUserId("actor");
    interview.setOwnerDepartmentId("department");
    return interview;
  }
}
