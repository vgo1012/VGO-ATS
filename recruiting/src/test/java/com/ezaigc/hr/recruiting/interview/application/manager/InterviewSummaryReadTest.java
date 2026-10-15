package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.service.FeedbackVersionPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ScheduleHistoryPersistenceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewSummaryReadTest {
  @Test
  void summaryDoesNotLoadAnyHistoryOrFeedbackContent() {
    InterviewApplicationManager manager = new InterviewApplicationManager();
    InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
    ParticipantPersistenceService participants = mock(ParticipantPersistenceService.class);
    InterviewFeedbackPersistenceService feedback = mock(InterviewFeedbackPersistenceService.class);
    FeedbackVersionPersistenceService versions = mock(FeedbackVersionPersistenceService.class);
    ScheduleHistoryPersistenceService history = mock(ScheduleHistoryPersistenceService.class);
    NotificationIntentPersistenceService notifications = mock(NotificationIntentPersistenceService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("A".repeat(26));
    when(authorization.currentActor()).thenReturn(actor);
    when(authorization.canAccess(any(), anyString(), any())).thenReturn(true);
    Interview interview = new Interview();
    interview.setId("I".repeat(26));
    Participant participant = new Participant();
    participant.setUserId("B".repeat(26));
    when(interviews.findInterview(interview.getId())).thenReturn(interview);
    when(participants.findParticipants(interview.getId())).thenReturn(List.of(participant));
    ReflectionTestUtils.setField(manager, "persistence", interviews);
    ReflectionTestUtils.setField(manager, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(manager, "interviewFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(manager, "feedbackVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "scheduleHistoryPersistenceService", history);
    ReflectionTestUtils.setField(manager, "notificationIntentPersistenceService", notifications);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));

    var summary = manager.get(interview.getId());

    assertThat(summary.getInterview()).isSameAs(interview);
    assertThat(summary.getParticipants()).containsExactly(participant);
    verifyNoInteractions(feedback, versions, history, notifications);
  }
}
