package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationCollaborator;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FollowUpApplicationBatchAccessTest {
    private final ApplicationPersistenceService persistence = mock(ApplicationPersistenceService.class);
    private final ApplicationCollaboratorPersistenceService collaborators =
            mock(ApplicationCollaboratorPersistenceService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final RecruitmentApplicationManager manager = new RecruitmentApplicationManager();
    private final CurrentActorSnapshot actor = new CurrentActorSnapshot(
            "actor", "Actor", null, "department", Set.of(), Set.of(), Set.of(),
            Set.of("RECRUITER"), Set.of("recruitment.manage"), false, false);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(manager, "persistence", persistence);
        ReflectionTestUtils.setField(manager, "applicationCollaboratorPersistenceService", collaborators);
        ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
        when(authorization.currentActor()).thenReturn(actor);
    }

    @Test
    void loadsAndAuthorizesOneHundredApplicationsInSingleBatches() {
        List<Application> applications = IntStream.range(0, 100)
                .mapToObj(index -> application(id(index)))
                .toList();
        List<String> ids = applications.stream().map(Application::getId).toList();
        when(persistence.listByIds(any())).thenReturn(applications);
        var collaborator = new ApplicationCollaborator();
        collaborator.setApplicationId(ids.getFirst());
        collaborator.setUserId("partner");
        when(collaborators.findCollaboratorsByApplicationIds(any())).thenReturn(List.of(collaborator));
        when(authorization.accessibleObjectIds(eq(actor), eq("recruitment.manage"), any()))
                .thenAnswer(invocation -> {
                    Collection<AuthorizationTarget> targets = invocation.getArgument(2);
                    assertThat(targets).hasSize(100).allSatisfy(target -> {
                        assertThat(target.getObjectType()).isEqualTo("APPLICATION");
                        assertThat(target.getOwnerUserId()).isEqualTo("owner");
                        assertThat(target.getDepartmentId()).isEqualTo("department");
                        assertThat(target.getCollaboratorUserIds()).isEqualTo(
                                target.getObjectId().equals(ids.getFirst()) ? Set.of("partner") : Set.of());
                    });
                    return Set.copyOf(ids);
                });

        assertThat(manager.requireFollowUpApplications(ids).keySet()).containsExactlyInAnyOrderElementsOf(ids);

        verify(persistence).listByIds(any());
        verify(collaborators).findCollaboratorsByApplicationIds(Set.copyOf(ids));
        verify(authorization, never()).requirePermission("recruitment.manage");
        verify(authorization).accessibleObjectIds(eq(actor), eq("recruitment.manage"), any());
        verify(authorization, never()).requireAccess(any(), any());
    }

    @Test
    void missingPermissionStillRejectsNonSharedApplications() {
        when(persistence.listByIds(any())).thenReturn(List.of(application(id(0))));
        when(authorization.accessibleObjectIds(any(), any(), any())).thenReturn(Set.of());
        assertThatThrownBy(() -> manager.requireFollowUpApplications(List.of(id(0))))
                .isInstanceOfSatisfying(PlatformException.class,
                        error -> assertThat(error.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
    }

    @Test
    void rejectsMissingApplicationBeforeAuthorizationBatch() {
        Application found = application(id(1));
        when(persistence.listByIds(any())).thenReturn(List.of(found));

        assertThatThrownBy(() -> manager.requireFollowUpApplications(List.of(id(1), id(2))))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.code()).isEqualTo("APPLICATION_NOT_FOUND"));

        verifyNoInteractions(collaborators);
        verify(authorization, never()).accessibleObjectIds(any(), any(), any());
    }

    @Test
    void rejectsWhenAnyApplicationIsNoLongerAccessible() {
        Application visible = application(id(1));
        Application hidden = application(id(2));
        when(persistence.listByIds(any())).thenReturn(List.of(visible, hidden));
        when(collaborators.findCollaboratorsByApplicationIds(any())).thenReturn(List.of());
        when(authorization.accessibleObjectIds(eq(actor), eq("recruitment.manage"), any()))
                .thenReturn(Set.of(id(1)));

        assertThatThrownBy(() -> manager.requireFollowUpApplications(List.of(id(1), id(2))))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
    }

    @Test
    void deduplicatesIdsBeforeLoadingAndAuthorization() {
        Application application = application(id(0));
        when(persistence.listByIds(any())).thenReturn(List.of(application));
        when(collaborators.findCollaboratorsByApplicationIds(any())).thenReturn(List.of());
        when(authorization.accessibleObjectIds(eq(actor), eq("recruitment.manage"), any()))
                .thenReturn(Set.of(application.getId()));

        assertThat(manager.requireFollowUpApplications(List.of(id(0), id(0))))
                .containsOnlyKeys(id(0));

        verify(persistence).listByIds(Set.of(id(0)));
        verify(collaborators).findCollaboratorsByApplicationIds(Set.of(id(0)));
    }

    @Test
    void emptyInputReturnsWithoutDatabaseOrAuthorizationCalls() {
        assertThat(manager.requireFollowUpApplications(List.of())).isEmpty();

        verifyNoInteractions(persistence, collaborators, authorization);
    }

    @Test
    void rejectsMoreThanOneHundredDistinctIdsWithoutDatabaseCalls() {
        List<String> ids = IntStream.rangeClosed(0, 100).mapToObj(index -> id(index)).toList();

        assertThatThrownBy(() -> manager.requireFollowUpApplications(ids))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("FOLLOW_UP_APPLICATION_IDS_LIMIT_EXCEEDED"));

        verifyNoInteractions(persistence, collaborators, authorization);
    }

    @Test
    void countsOnlyDistinctIdsTowardTheBound() {
        List<String> ids = new ArrayList<>(IntStream.range(0, 100)
                .mapToObj(index -> id(index))
                .toList());
        ids.add(id(0));
        List<Application> applications = ids.stream().distinct().map(this::application).toList();
        when(persistence.listByIds(any())).thenReturn(applications);
        when(collaborators.findCollaboratorsByApplicationIds(any())).thenReturn(List.of());
        when(authorization.accessibleObjectIds(eq(actor), eq("recruitment.manage"), any()))
                .thenReturn(applications.stream().map(Application::getId).collect(Collectors.toSet()));

        assertThat(manager.requireFollowUpApplications(ids)).hasSize(100);
        verify(persistence, times(1)).listByIds(any());
    }

    private static String id(int index) {
        return "A" + String.format("%025d", index);
    }

    private Application application(String id) {
        return new Application(
                id, "candidate", "demand", "jd", "jd-version", "resume-version", "workflow-version",
                null, "INTERNAL", "IN_PROCESS", null, "owner", "department", 1L, null, 0L);
    }
}
