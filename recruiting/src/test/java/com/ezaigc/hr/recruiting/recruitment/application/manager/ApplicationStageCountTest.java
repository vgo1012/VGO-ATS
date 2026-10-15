package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountQuery;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationStageCountResponse;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class ApplicationStageCountTest {
    private final ApplicationPersistenceService persistence = mock(ApplicationPersistenceService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ApplicationCollaboratorPersistenceService collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    private final RecruitmentApplicationManager manager = new RecruitmentApplicationManager();
    private final Instant from = Instant.parse("2026-09-06T16:00:00Z");
    private final Instant to = Instant.parse("2026-09-13T16:00:00Z");

    ApplicationStageCountTest() {
        ReflectionTestUtils.setField(manager, "persistence", persistence);
        ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
        ReflectionTestUtils.setField(manager, "applicationCollaboratorPersistenceService", collaborators);
    }

    @Test
    void aggregatesIntrinsicScopeAndBoundedGrantPagesWithoutLoadingApplications() {
        when(authorization.currentActor()).thenReturn(actor(false, false));
        var firstGrants = IntStream.range(0, 200).mapToObj(i -> String.format("grant-%03d", i)).toList();
        when(authorization.grantedObjectIds("recruitment.manage", "APPLICATION", null)).thenReturn(firstGrants);
        when(authorization.grantedObjectIds("recruitment.manage", "APPLICATION", "grant-199"))
                .thenReturn(List.of("grant-200"));
        when(persistence.countStages(any())).thenAnswer(invocation -> {
            ApplicationStageCountQuery query = invocation.getArgument(0);
            assertThat(query.getFrom()).isEqualTo(from);
            assertThat(query.getTo()).isEqualTo(to);
            assertThat(query.getJdId()).isEqualTo("jd");
            assertThat(query.getJdVersionId()).isNull();
            assertThat(query.isAllScope()).isFalse();
            assertThat(query.getActorId()).isEqualTo("actor");
            assertThat(query.getManagedUserIds()).containsExactly("managed");
            assertThat(query.getDepartmentIds()).isEmpty();
            if (query.getGrantedApplicationIds() == null) return List.of(new ApplicationStageCountDTO("IN_PROCESS", 300, "b"));
            if (query.getGrantedApplicationIds().size() == 200) return List.of(new ApplicationStageCountDTO("IN_PROCESS", 199, "a"));
            assertThat(query.getGrantedApplicationIds()).containsExactly("grant-200");
            return List.of(new ApplicationStageCountDTO("ONBOARDED", 1, "c"));
        });
        assertThat(manager.stageCounts(from, to, " jd ", "  ")).containsExactly(
                new ApplicationStageCountResponse("IN_PROCESS", 499), new ApplicationStageCountResponse("ONBOARDED", 1));
        verify(persistence, times(3)).countStages(any());
        verifyNoMoreInteractions(persistence);
        verifyNoInteractions(collaborators);
    }

    @Test
    void companyScopeAndSuperAdminNeedOnlyOneAggregate() {
        for (var actor : List.of(actor(true, false), actor(false, true))) {
            when(authorization.currentActor()).thenReturn(actor);
            when(persistence.countStages(any())).thenReturn(List.of(new ApplicationStageCountDTO("ONBOARDED", 501, "a")));
            assertThat(manager.stageCounts(from, to, null, null)).containsExactly(new ApplicationStageCountResponse("ONBOARDED", 501));
            verify(persistence).countStages(argThat(ApplicationStageCountQuery::isAllScope));
            verify(authorization, never()).grantedObjectIds(any(), any(), any());
            verifyNoMoreInteractions(persistence);
            clearInvocations(persistence);
        }
    }

    @Test
    void rejectsInvalidRangeOrMissingPermissionBeforeQuerying() {
        assertThatThrownBy(() -> manager.stageCounts(to, from, null, null)).hasMessageContaining("时间");
        assertThatThrownBy(() -> manager.stageCounts(from, from.plusSeconds(367L * 86400), null, null)).hasMessageContaining("时间");
        doThrow(new IllegalStateException("denied")).when(authorization).requirePermission("recruitment.manage");
        assertThatThrownBy(() -> manager.stageCounts(from, to, null, null)).hasMessage("denied");
        verifyNoInteractions(persistence);
    }

    private static CurrentActorSnapshot actor(boolean companyScope, boolean superAdmin) {
        return new CurrentActorSnapshot("actor", "Actor", null, "home", Set.of("support"), Set.of("managed"),
                Set.of("granted-department"), Set.of("RECRUITER"), Set.of("recruitment.manage"), companyScope, superAdmin);
    }

    @Test
    void stageCountResponsePreservesTheJsonContract() {
        var mapper = JsonMapper.builder().build();
        var response = new ApplicationStageCountResponse("IN_PROCESS", 7);
        assertThat(mapper.readTree(mapper.writeValueAsString(response)))
            .isEqualTo(mapper.readTree("{\"stage\":\"IN_PROCESS\",\"count\":7}"));
    }
}
