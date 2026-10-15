package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.DecisionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OfferStatusRecordPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationDetailsReadTest {
    private final RecruitmentApplicationManager manager = new RecruitmentApplicationManager();
    private final ApplicationPersistenceService applications = mock(ApplicationPersistenceService.class);
    private final ApplicationCollaboratorPersistenceService collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
    private final DecisionPersistenceService decisions = mock(DecisionPersistenceService.class);
    private final OfferStatusRecordPersistenceService offers = mock(OfferStatusRecordPersistenceService.class);
    private final Instant now = Instant.parse("2026-09-12T00:00:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(manager, "persistence", applications);
        ReflectionTestUtils.setField(manager, "applicationCollaboratorPersistenceService", collaborators);
        ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
        ReflectionTestUtils.setField(manager, "recruitmentWorkflowStepPersistenceService", steps);
        ReflectionTestUtils.setField(manager, "decisionPersistenceService", decisions);
        ReflectionTestUtils.setField(manager, "offerStatusRecordPersistenceService", offers);
        ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void readsCollaboratorsOnceForAuthorizationAndResponse() {
        var app = application();
        when(applications.findApplication("app")).thenReturn(app);
        when(collaborators.findCollaborators("app")).thenReturn(List.of("partner-2", "partner-1"));

        var result = manager.get("app");

        assertThat(result.getApplication()).isSameAs(app);
        assertThat(result.getCollaboratorUserIds()).containsExactly("partner-2", "partner-1");
        assertThat(result.getElapsedSeconds()).isEqualTo(120);
        verify(applications).findApplication("app");
        verify(collaborators).findCollaborators("app");
        var target = ArgumentCaptor.forClass(AuthorizationTarget.class);
        verify(authorization).requireAccess(eq("recruitment.manage"), target.capture());
        assertThat(target.getValue().getObjectType()).isEqualTo("APPLICATION");
        assertThat(target.getValue().getObjectId()).isEqualTo("app");
        assertThat(target.getValue().getOwnerUserId()).isEqualTo("owner");
        assertThat(target.getValue().getDepartmentId()).isEqualTo("department");
        assertThat(target.getValue().getCollaboratorUserIds()).containsExactlyInAnyOrder("partner-1", "partner-2");
        var order = inOrder(authorization, steps, decisions, offers);
        order.verify(authorization).requireAccess(eq("recruitment.manage"), any());
        order.verify(steps).findSteps("app");
        order.verify(decisions).findDecisions("app");
        order.verify(offers).findOffers("app");
    }

    @Test
    void deniedAccessStopsBeforeReadingProcessDetails() {
        when(applications.findApplication("app")).thenReturn(application());
        var denied = PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权访问");
        doThrow(denied).when(authorization).requireAccess(any(), any());
        assertThatThrownBy(() -> manager.get("app")).isSameAs(denied);
        verify(collaborators).findCollaborators("app");
        verifyNoInteractions(steps, decisions, offers);
    }

    @Test
    void missingApplicationStopsBeforeReadingCollaboratorsOrAuthorizing() {
        assertThatThrownBy(() -> manager.get("missing"))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("APPLICATION_NOT_FOUND"));
        verifyNoInteractions(collaborators, authorization, steps, decisions, offers);
    }

    @Test
    void elapsedTimeUsesTerminalEventAndNeverBecomesNegative() {
        var app = application();
        when(applications.findApplication("app")).thenReturn(app);
        app.setTerminalAt(now.minusSeconds(60));
        assertThat(manager.get("app").getElapsedSeconds()).isEqualTo(60);
        app.setTerminalAt(app.getCreateTime().minusSeconds(1));
        assertThat(manager.get("app").getElapsedSeconds()).isZero();
    }

    private Application application() {
        var app = new Application();
        app.setId("app");
        app.setOwnerUserId("owner");
        app.setOwnerDepartmentId("department");
        app.setCreateTime(now.minusSeconds(120));
        return app;
    }
}
