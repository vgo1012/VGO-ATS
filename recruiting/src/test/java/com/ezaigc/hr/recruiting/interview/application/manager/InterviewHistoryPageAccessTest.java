package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.ScheduleHistory;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ScheduleHistoryPersistenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewHistoryPageAccessTest {
  private static final String ID = "I".repeat(26);
  private static final String OTHER = "O".repeat(26);
  private final InterviewApplicationManager manager = new InterviewApplicationManager();
  private final InterviewFeedbackPersistenceService feedback = mock(InterviewFeedbackPersistenceService.class);
  private final NotificationIntentPersistenceService notifications = mock(NotificationIntentPersistenceService.class);
  private final ScheduleHistoryPersistenceService history = mock(ScheduleHistoryPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);

  @BeforeEach
  void setUp() {
    InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
    for (String id : List.of(ID, OTHER)) {
      Interview interview = new Interview();
      interview.setId(id);
      when(interviews.findInterview(id)).thenReturn(interview);
    }
    when(authorization.currentActor()).thenReturn(actor);
    when(actor.getUserId()).thenReturn("A".repeat(26));
    when(authorization.canAccess(any(), anyString(), any())).thenReturn(true);
    ReflectionTestUtils.setField(manager, "persistence", interviews);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("interview-history-access-test-key-32-bytes"));
    ReflectionTestUtils.setField(manager, "participantPersistenceService", mock(ParticipantPersistenceService.class));
    ReflectionTestUtils.setField(manager, "interviewFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(manager, "notificationIntentPersistenceService", notifications);
    ReflectionTestUtils.setField(manager, "scheduleHistoryPersistenceService", history);
  }

  @ParameterizedTest
  @ValueSource(strings = {"feedback", "notifications", "schedule-history"})
  void everyPageRechecksInterviewAccessAndValidatesLimits(String kind) {
    for (int limit : List.of(0, 101)) {
      assertCode(() -> read(kind, ID, null, limit), "PAGE_LIMIT_INVALID");
    }
    when(authorization.canAccess(any(), anyString(), any())).thenReturn(false);
    assertCode(() -> read(kind, ID, null, 2), "INTERVIEW_ACCESS_FORBIDDEN");
    verifyNoInteractions(feedback, history, notifications);
  }

  @ParameterizedTest
  @ValueSource(strings = {"notifications", "schedule-history"})
  void cursorContinuesTiesAndRejectsReuseForAnotherResourceOrActor(String kind) {
    List<String> ids = List.of("3".repeat(26), "2".repeat(26), "1".repeat(26));
    when(history.findHistory(anyString(), nullable(Instant.class), nullable(String.class), anyInt())).thenAnswer(call ->
        ids.stream().filter(id -> call.getArgument(2) == null || id.compareTo(call.getArgument(2)) < 0).map(id -> {
          ScheduleHistory row = new ScheduleHistory(); row.setId(id); row.setOccurredAt(Instant.EPOCH); return row;
        }).toList());
    when(notifications.findNotifications(anyString(), nullable(Instant.class), nullable(String.class), anyInt())).thenAnswer(call ->
        ids.stream().filter(id -> call.getArgument(2) == null || id.compareTo(call.getArgument(2)) < 0).map(id -> {
          NotificationIntent row = new NotificationIntent(); row.setId(id); row.setCreateTime(Instant.EPOCH); return row;
        }).toList());

    CursorPage<?> first = read(kind, ID, null, 2);
    assertThat(first.getItems()).hasSize(2);
    assertThat(first.isHasMore()).isTrue();
    CursorPage<?> last = read(kind, ID, first.getNextCursor(), 2);
    assertThat(last.getItems()).hasSize(1);
    assertThat(last.isHasMore()).isFalse();
    assertThat(last.getNextCursor()).isNull();
    clearInvocations(history, notifications, feedback);
    assertCode(() -> read(kind, OTHER, first.getNextCursor(), 2), "CURSOR_INVALID");
    assertCode(() -> read("feedback", ID, first.getNextCursor(), 2), "CURSOR_INVALID");
    assertCode(() -> read(kind, ID, first.getNextCursor() + "X", 2), "CURSOR_INVALID");
    when(actor.getUserId()).thenReturn("B".repeat(26));
    assertCode(() -> read(kind, ID, first.getNextCursor(), 2), "CURSOR_INVALID");
    when(authorization.canAccess(any(), anyString(), any())).thenReturn(false);
    assertCode(() -> read(kind, ID, first.getNextCursor(), 2), "INTERVIEW_ACCESS_FORBIDDEN");
    verifyNoInteractions(feedback, history, notifications);
  }

  @Test
  void emptyHistoriesHaveNoContinuation() {
    for (String kind : List.of("feedback", "notifications", "schedule-history")) {
      CursorPage<?> page = read(kind, ID, null, null);
      assertThat(page.getItems()).isEmpty();
      assertThat(page.isHasMore()).isFalse();
      assertThat(page.getNextCursor()).isNull();
    }
  }

  private CursorPage<?> read(String kind, String id, String cursor, Integer limit) {
    return switch (kind) {
      case "feedback" -> manager.feedback(id, cursor, limit);
      case "notifications" -> manager.notificationResponses(id, cursor, limit);
      default -> manager.historyResponses(id, cursor, limit);
    };
  }

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo(code));
  }
}
