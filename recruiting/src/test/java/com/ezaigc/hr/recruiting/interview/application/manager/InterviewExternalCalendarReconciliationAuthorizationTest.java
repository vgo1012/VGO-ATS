package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarReconciliationDTO;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewDetails;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewDetailsResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewResponse;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewExternalCalendarReconciliationAuthorizationTest {
    private static final String INTERVIEW_ID = "I".repeat(26);
    private static final String CANDIDATE_ID = "C".repeat(26);
    private final InterviewApplicationManager manager = new InterviewApplicationManager();
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final InterviewPersistenceService persistence = mock(InterviewPersistenceService.class);
    private final CompanyBlacklistQueryService blacklist = mock(CompanyBlacklistQueryService.class);
    private final InterviewExternalCalendarApplicationManager calendars = mock(InterviewExternalCalendarApplicationManager.class);
    private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
    private final Interview interview = new Interview();
    private final InterviewExternalCalendarReconciliationDTO command = new InterviewExternalCalendarReconciliationDTO(
            3, "L".repeat(26), "ADOPT_EXISTING", "verified-meeting", "管理员已核对原意图", false);
    private final InterviewDetailsResponse expected = new InterviewDetailsResponse(mock(InterviewResponse.class), List.of());

    @BeforeEach
    void setUp() {
        actor.setUserId("A".repeat(26));
        when(authorization.currentActor()).thenReturn(actor);
        interview.setId(INTERVIEW_ID);
        interview.setCandidateId(CANDIDATE_ID);
        interview.setOwnerUserId(actor.getUserId());
        interview.setOwnerDepartmentId("D".repeat(26));
        interview.setStatus("SCHEDULED");
        when(persistence.findCandidateId(INTERVIEW_ID)).thenReturn(CANDIDATE_ID);
        when(persistence.lockInterview(INTERVIEW_ID)).thenReturn(interview);
        ReflectionTestUtils.setField(manager, "authorization", authorization);
        ReflectionTestUtils.setField(manager, "persistence", persistence);
        ReflectionTestUtils.setField(manager, "blacklist", blacklist);
        ReflectionTestUtils.setField(manager, "externalCalendars", calendars);
        ParticipantPersistenceService participants = mock(ParticipantPersistenceService.class);
        when(participants.findParticipants(INTERVIEW_ID)).thenReturn(List.of());
        ReflectionTestUtils.setField(manager, "participantPersistenceService", participants);
        InterviewAssembler assembler = mock(InterviewAssembler.class);
        when(assembler.toResponse(any(InterviewDetails.class))).thenReturn(expected);
        ReflectionTestUtils.setField(manager, "assembler", assembler);
        ReflectionTestUtils.setField(manager, "demands", mock(DemandQueryService.class));
    }

    @Test
    void ordinaryRecruiterCannotAdoptAnArbitraryRemoteId() {
        assertThatThrownBy(() -> manager.reconcileExternalCalendar(INTERVIEW_ID, command))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("超级管理员");
        verifyNoInteractions(persistence, blacklist, calendars);
    }

    @Test
    void administratorStillNeedsInterviewObjectAuthorization() {
        actor.setSuperAdmin(true);
        doThrow(PlatformExceptions.forbidden("INTERVIEW_ACCESS_FORBIDDEN", "无权操作该面试"))
                .when(authorization).requireAccess(eq("interview.schedule"), any());
        assertThatThrownBy(() -> manager.reconcileExternalCalendar(INTERVIEW_ID, command))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(calendars);
    }

    @Test
    void authorizedAdministratorLocksCandidateThenInterviewAndRecordsTheActualActor() {
        actor.setSuperAdmin(true);
        assertThat(manager.reconcileExternalCalendar(INTERVIEW_ID, command)).isSameAs(expected);
        var ordered = inOrder(blacklist, persistence, authorization, calendars);
        ordered.verify(blacklist).lockCandidateForCommand(CANDIDATE_ID);
        ordered.verify(persistence).lockInterview(INTERVIEW_ID);
        ordered.verify(authorization).requireAccess(eq("interview.schedule"), argThat(target ->
                INTERVIEW_ID.equals(target.getObjectId()) && actor.getUserId().equals(target.getOwnerUserId())));
        ordered.verify(blacklist).requireRecruitmentAllowed(CANDIDATE_ID);
        ordered.verify(calendars).reconcile(INTERVIEW_ID, command, actor.getUserId());
    }

    @Test
    void cancelledInterviewCanReconcileForRemoteCleanupWithoutNewRecruitment() {
        actor.setSuperAdmin(true);
        interview.setStatus("CANCELLED");
        assertThat(manager.reconcileExternalCalendar(INTERVIEW_ID, command)).isSameAs(expected);
        verify(blacklist, never()).requireRecruitmentAllowed(CANDIDATE_ID);
        verify(calendars).reconcile(INTERVIEW_ID, command, actor.getUserId());
    }
}
