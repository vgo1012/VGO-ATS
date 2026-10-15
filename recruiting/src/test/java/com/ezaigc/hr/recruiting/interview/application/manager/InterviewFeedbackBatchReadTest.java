package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.service.FeedbackVersionPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewFeedbackBatchReadTest {
  private static final String ACTOR = "A".repeat(26);
  private final InterviewApplicationManager manager = new InterviewApplicationManager();
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final InterviewFeedbackPersistenceService feedback = mock(InterviewFeedbackPersistenceService.class);
  private final FeedbackVersionPersistenceService versions = mock(FeedbackVersionPersistenceService.class);
  private final ParticipantPersistenceService participants = mock(ParticipantPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final List<String> ids = IntStream.range(0, 20)
      .mapToObj(index -> String.format("%026d", index))
      .toList();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(manager, "persistence", interviews);
    ReflectionTestUtils.setField(manager, "interviewFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(manager, "feedbackVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(
        manager, "cursorCodec", new SignedCursorCodec("feedback-batch-test-signing-key-32-bytes"));
    CurrentActorSnapshot actor = new CurrentActorSnapshot(
        ACTOR, "面试官", null, "D".repeat(26), Set.of(), Set.of(), Set.of(), Set.of(),
        Set.of("interview.feedback.submit-own"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    when(interviews.listByIds(any())).thenReturn(ids.stream().map(InterviewFeedbackBatchReadTest::interview).toList());
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(
        ids.stream().map(id -> new Participant(id, ACTOR, "INTERVIEWER", "ACCEPTED")).toList());
  }

  @Test
  void readsTwentyRoundsInOneBoundedPageAndKeepsEmptyRoundsVisible() {
    Feedback first = feedback("F".repeat(26), ids.getFirst(), Instant.parse("2026-01-02T00:00:00Z"));
    Feedback second = feedback("E".repeat(26), ids.get(1), Instant.parse("2026-01-01T00:00:00Z"));
    when(feedback.findVisibleFeedbackByInterviewIds(
        any(), anyString(), nullable(Instant.class), nullable(String.class), anyInt()))
        .thenReturn(List.of(first, second));
    Map<String, FeedbackVersion> current = new LinkedHashMap<>();
    current.put(first.getId(), version(first));
    when(versions.findFeedbackVersions(any())).thenReturn(current);

    var response = manager.feedbackBatchResponses(
        List.of(ids.get(3), ids.getFirst(), ids.get(3), ids.get(1), ids.get(2),
            ids.get(4), ids.get(5), ids.get(6), ids.get(7), ids.get(8), ids.get(9),
            ids.get(10), ids.get(11), ids.get(12), ids.get(13), ids.get(14), ids.get(15),
            ids.get(16), ids.get(17), ids.get(18), ids.get(19)), null, 1);

    assertThat(response.getVisibleInterviewIds()).containsExactlyElementsOf(ids);
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.isHasMore()).isTrue();
    assertThat(response.getNextCursor()).isNotBlank();
    verify(interviews).listByIds(ids);
    verify(feedback).findVisibleFeedbackByInterviewIds(Set.copyOf(ids), ACTOR, null, null, 2);
    verify(versions).findFeedbackVersions(Map.of(first.getId(), 1));
  }

  @Test
  void sharedBatchIncludesOtherAuthorsDraftsAndDoesNotRevealPrivateInterviews() {
    var library = mock(CandidateLibraryQueryService.class);
    ReflectionTestUtils.setField(manager, "candidateLibrary", library);
    when(library.eligibleCandidateIds(any())).thenReturn(Set.of("shared-candidate"));
    var shared = interview(ids.getFirst()); shared.setCandidateId("shared-candidate");
    var hidden = interview(ids.get(1)); hidden.setCandidateId("private-candidate");
    when(interviews.listByIds(any())).thenReturn(List.of(shared, hidden));
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(List.of());
    Feedback draft = feedback("F".repeat(26), shared.getId(), Instant.parse("2026-01-02T00:00:00Z"));
    draft.setStatus("DRAFT"); draft.setAuthorUserId("another-author");
    when(feedback.findVisibleFeedbackByInterviewIds(Set.of(shared.getId()), ACTOR,
        Set.of(shared.getId()), null, null, 21)).thenReturn(List.of(draft));
    when(versions.findFeedbackVersions(any())).thenReturn(Map.of(draft.getId(), version(draft)));
    var response = manager.feedbackBatchResponses(List.of(shared.getId(), hidden.getId()), null, 20);
    assertThat(response.getVisibleInterviewIds()).containsExactly(shared.getId());
    assertThat(response.getItems()).singleElement()
        .satisfies(item -> assertThat(item.getFeedback().getStatus()).isEqualTo("DRAFT"));
    when(library.eligibleCandidateIds(any())).thenReturn(Set.of());
    assertThat(manager.feedbackBatchResponses(List.of(shared.getId(), hidden.getId()), null, 20).getItems()).isEmpty();
  }

  @Test
  void authorizationChangeInvalidatesCursorBeforeReadingFeedbackAgain() {
    Feedback first = feedback("F".repeat(26), ids.getFirst(), Instant.parse("2026-01-02T00:00:00Z"));
    Feedback second = feedback("E".repeat(26), ids.get(1), Instant.parse("2026-01-01T00:00:00Z"));
    when(feedback.findVisibleFeedbackByInterviewIds(
        any(), anyString(), nullable(Instant.class), nullable(String.class), anyInt()))
        .thenReturn(List.of(first, second));
    when(versions.findFeedbackVersions(any())).thenReturn(Map.of(first.getId(), version(first)));
    String cursor = manager.feedbackBatchResponses(ids, null, 1).getNextCursor();
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(List.of());

    assertThatThrownBy(() -> manager.feedbackBatchResponses(ids, cursor, 1))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
    verify(feedback, times(1))
        .findVisibleFeedbackByInterviewIds(Set.copyOf(ids), ACTOR, null, null, 2);
  }

  @Test
  void cursorRejectsChangedRequestActorAndPermissionScope() {
    String cursor = cursorFor(ids);

    assertCursorInvalid(List.of(ids.getFirst(), ids.get(1)), cursor);

    CurrentActorSnapshot otherActor = actor("B".repeat(26), Set.of("interview.feedback.submit-own"),
        "D".repeat(26), Set.of(), false);
    when(authorization.currentActor()).thenReturn(otherActor);
    assertCursorInvalid(ids, cursor);

    CurrentActorSnapshot changedScope = actor(
        ACTOR, Set.of("interview.feedback.submit-own", "interview.schedule"),
        "E".repeat(26), Set.of("M".repeat(26)), true);
    when(authorization.currentActor()).thenReturn(changedScope);
    when(authorization.accessibleObjectIds(any(), anyString(), any())).thenReturn(Set.copyOf(ids));
    assertCursorInvalid(ids, cursor);

    verify(feedback, times(1))
        .findVisibleFeedbackByInterviewIds(Set.copyOf(ids), ACTOR, null, null, 2);
  }

  @Test
  void noVisibleInterviewsReturnsEmptyResponseWithoutFeedbackOrVersionQueries() {
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(List.of());

    var response = manager.feedbackBatchResponses(ids, null, 50);

    assertThat(response.getVisibleInterviewIds()).isEmpty();
    assertThat(response.getItems()).isEmpty();
    assertThat(response.getNextCursor()).isNull();
    assertThat(response.isHasMore()).isFalse();
    verifyNoInteractions(feedback, versions);
  }

  @Test
  void invitationOnlyActorSeesOnlyParticipantSubsetAndObjectScopeCannotExpandIt() {
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(
        List.of(new Participant(ids.get(2), ACTOR, "INTERVIEWER", "ACCEPTED")));
    when(authorization.accessibleObjectIds(any(), anyString(), any())).thenReturn(Set.copyOf(ids));

    var response = manager.feedbackBatchResponses(ids, null, 50);

    assertThat(response.getVisibleInterviewIds()).containsExactly(ids.get(2));
    verify(authorization, never()).accessibleObjectIds(any(), anyString(), any());
    verify(feedback).findVisibleFeedbackByInterviewIds(Set.of(ids.get(2)), ACTOR, null, null, 51);
  }

  @Test
  void recruitingActorSeesUnionOfParticipationAndObjectAuthorization() {
    CurrentActorSnapshot recruiter = actor(
        ACTOR, Set.of("interview.feedback.read-authorized", "interview.schedule"),
        "D".repeat(26), Set.of(), false);
    when(authorization.currentActor()).thenReturn(recruiter);
    when(participants.findParticipantsByInterviewIds(any())).thenReturn(
        List.of(new Participant(ids.getFirst(), ACTOR, "INTERVIEWER", "ACCEPTED")));
    when(authorization.accessibleObjectIds(any(), anyString(), any())).thenReturn(Set.of(ids.get(1)));

    var response = manager.feedbackBatchResponses(ids, null, 50);

    assertThat(response.getVisibleInterviewIds()).containsExactly(ids.getFirst(), ids.get(1));
    verify(feedback).findVisibleFeedbackByInterviewIds(
        Set.of(ids.getFirst(), ids.get(1)), ACTOR, null, null, 51);
  }

  @Test
  void rejectsInvalidIdsBatchSizeAndPageLimitBeforeFeedbackQuery() {
    List<String> tooMany = IntStream.range(0, 101)
        .mapToObj(index -> String.format("%026d", index))
        .toList();

    assertThatThrownBy(() -> manager.feedbackBatchResponses(List.of(), null, 50))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_IDS_INVALID"));
    assertThatThrownBy(() -> manager.feedbackBatchResponses(tooMany, null, 50))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_IDS_INVALID"));
    assertThatThrownBy(() -> manager.feedbackBatchResponses(List.of("short"), null, 50))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDENTIFIER_INVALID"));
    assertThatThrownBy(() -> manager.feedbackBatchResponses(ids, null, 101))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("PAGE_LIMIT_INVALID"));
    verifyNoInteractions(feedback, versions);
  }

  private String cursorFor(List<String> requestedIds) {
    Feedback first = feedback("F".repeat(26), ids.getFirst(), Instant.parse("2026-01-02T00:00:00Z"));
    Feedback second = feedback("E".repeat(26), ids.get(1), Instant.parse("2026-01-01T00:00:00Z"));
    when(feedback.findVisibleFeedbackByInterviewIds(
        any(), anyString(), nullable(Instant.class), nullable(String.class), anyInt()))
        .thenReturn(List.of(first, second));
    when(versions.findFeedbackVersions(any())).thenReturn(Map.of(first.getId(), version(first)));
    return manager.feedbackBatchResponses(requestedIds, null, 1).getNextCursor();
  }

  private void assertCursorInvalid(List<String> requestedIds, String cursor) {
    assertThatThrownBy(() -> manager.feedbackBatchResponses(requestedIds, cursor, 1))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
  }

  private static CurrentActorSnapshot actor(
      String userId,
      Set<String> permissions,
      String departmentId,
      Set<String> managedUserIds,
      boolean companyScope) {
    return new CurrentActorSnapshot(
        userId, "操作者", null, departmentId, Set.of(), managedUserIds, Set.of(), Set.of(),
        permissions, companyScope, false);
  }

  private static Interview interview(String id) {
    Interview value = new Interview();
    value.setId(id);
    return value;
  }

  private static Feedback feedback(String id, String interviewId, Instant createTime) {
    Feedback value = new Feedback(id, interviewId, ACTOR, "INTERNAL", "SUBMITTED", 1);
    value.setCreateTime(createTime);
    return value;
  }

  private static FeedbackVersion version(Feedback feedback) {
    return new FeedbackVersion(
        "V".repeat(26), feedback.getId(), 1, "PASS", "通过", null,
        feedback.getCreateTime(), "提交");
  }
}
