package com.ezaigc.hr.recruiting.interview.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.WecomCollaborationDirectoryService;
import com.ezaigc.hr.recruiting.interview.api.InterviewExternalCalendarPort;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewExternalCalendarDTO;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarAttemptPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewExternalCalendarProjectionPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class InterviewExternalCalendarApplicationManagerTest {
    private static final Instant NOW = Instant.parse("2026-09-17T06:00:00Z");
    private static final String INTERVIEW_ID = "I".repeat(26);
    private static final String ACTOR_ID = "A".repeat(26);
    private static final String INTERVIEWER_ID = "U".repeat(26);

    private final InterviewExternalCalendarProjectionPersistenceService projections =
            mock(InterviewExternalCalendarProjectionPersistenceService.class);
    private final InterviewExternalCalendarAttemptPersistenceService attempts =
            mock(InterviewExternalCalendarAttemptPersistenceService.class);
    private final WecomCollaborationDirectoryService directory =
            mock(WecomCollaborationDirectoryService.class);
    private final InterviewExternalCalendarPort port = mock(InterviewExternalCalendarPort.class);
    private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    private InterviewExternalCalendarApplicationManager manager;

    @BeforeEach
    void setUp() {
        manager = new InterviewExternalCalendarApplicationManager();
        ReflectionTestUtils.setField(manager, "projections", projections);
        ReflectionTestUtils.setField(manager, "attempts", attempts);
        ReflectionTestUtils.setField(manager, "directory", directory);
        ReflectionTestUtils.setField(manager, "port", port);
        ReflectionTestUtils.setField(manager, "ids", ids);
        ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
        when(ids.newId()).thenReturn("P".repeat(26));
        when(projections.save(any())).thenReturn(true);
    }

    @Test
    void createsDurableMeetingProjectionFromLinkedInterviewersAndExtraMembers() {
        when(directory.requireLinkedUserIds(Set.of(INTERVIEWER_ID)))
                .thenReturn(Map.of(INTERVIEWER_ID, "interviewer-wecom"));
        when(directory.requireMembers(Set.of("extra-wecom"))).thenReturn(Map.of(
                "extra-wecom", new WecomCollaborationDirectoryService.Member(
                        "extra-wecom", "额外面试官", "专家", null)));
        when(directory.requireMembers(Set.of("admin-wecom"))).thenReturn(Map.of(
                "admin-wecom", new WecomCollaborationDirectoryService.Member(
                        "admin-wecom", "发起人", "HR", ACTOR_ID)));

        manager.configure(interview(), List.of(INTERVIEWER_ID),
                new InterviewExternalCalendarDTO("WECOM_MEETING", "招聘面试", "技术面试",
                        "admin-wecom", List.of("extra-wecom"), 5, true, false, true), ACTOR_ID);

        ArgumentCaptor<InterviewExternalCalendarProjection> saved =
                ArgumentCaptor.forClass(InterviewExternalCalendarProjection.class);
        verify(projections).save(saved.capture());
        assertThat(saved.getValue()).satisfies(value -> {
            assertThat(value.getInterviewId()).isEqualTo(INTERVIEW_ID);
            assertThat(value.getDesiredType()).isEqualTo("WECOM_MEETING");
            assertThat(value.getStatus()).isEqualTo("PENDING");
            assertThat(value.getInviteeUserIdsJson())
                    .contains("interviewer-wecom", "extra-wecom", "admin-wecom");
            assertThat(value.getReminderSeconds()).isEqualTo(300);
            assertThat(value.isWaitingRoomEnabled()).isTrue();
            assertThat(value.isAllowEnterBeforeHost()).isFalse();
        });
        verifyNoInteractions(port, attempts);
    }

    @Test
    void localOnlyProjectionDoesNotRequireEnterpriseIdentityMapping() {
        manager.configure(interview(), List.of(INTERVIEWER_ID),
                new InterviewExternalCalendarDTO("LOCAL_ONLY", null, null, null,
                        List.of(), null, null, null, null), ACTOR_ID);
        ArgumentCaptor<InterviewExternalCalendarProjection> saved =
                ArgumentCaptor.forClass(InterviewExternalCalendarProjection.class);
        verify(projections).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("CANCELLED");
        verifyNoInteractions(directory, port, attempts);
    }

    @Test
    void cancellationSupersedesInFlightCreateWithoutReleasingItsLease() {
        InterviewExternalCalendarProjection existing = new InterviewExternalCalendarProjection();
        existing.setId("P".repeat(26));
        existing.setInterviewId(INTERVIEW_ID);
        existing.setDesiredType("WECOM_MEETING");
        existing.setActiveType("WECOM_MEETING");
        existing.setStatus("PROCESSING");
        existing.setOperationRevision(4);
        existing.setAttemptCount(7);
        existing.setLeaseToken("L".repeat(26));
        existing.setLeaseUntil(NOW.plusSeconds(120));
        existing.setVersion(2);
        when(projections.findByInterviewIdForUpdate(INTERVIEW_ID)).thenReturn(existing);
        when(projections.updateById(any())).thenReturn(true);

        manager.cancel(INTERVIEW_ID, ACTOR_ID);

        ArgumentCaptor<InterviewExternalCalendarProjection> saved =
                ArgumentCaptor.forClass(InterviewExternalCalendarProjection.class);
        verify(projections).updateById(saved.capture());
        assertThat(saved.getValue()).satisfies(value -> {
            assertThat(value.getDesiredType()).isEqualTo("LOCAL_ONLY");
            assertThat(value.getStatus()).isEqualTo("PENDING");
            assertThat(value.getOperationRevision()).isEqualTo(5);
            assertThat(value.getAttemptCount()).isZero();
            assertThat(value.getLeaseToken()).isEqualTo("L".repeat(26));
            assertThat(value.getLeaseUntil()).isEqualTo(NOW.plusSeconds(120));
        });
    }

    private Interview interview() {
        Interview value = new Interview();
        value.setId(INTERVIEW_ID);
        value.setScheduledStartAt(NOW.plusSeconds(3600));
        value.setScheduledEndAt(NOW.plusSeconds(5400));
        value.setTimezoneId("Asia/Shanghai");
        value.setLocationText("线上");
        return value;
    }
}
