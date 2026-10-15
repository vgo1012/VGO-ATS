package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationClientBatchFilterTest {
  @Test void batchesOnlyAuthorizedMatchingTypesAndSkipsDemandReadsWithoutClientFilter() {
    var manager = new RecruitmentApplicationManager();
    var persistence = mock(ApplicationPersistenceService.class);
    var authorization = mock(AuthorizationService.class);
    var demands = mock(DemandQueryService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("owner");
    when(actor.getRoles()).thenReturn(Set.of());
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "demands", demands);
    ReflectionTestUtils.setField(manager, "applicationCollaboratorPersistenceService", mock(ApplicationCollaboratorPersistenceService.class));
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("client-filter-signing-key-32-bytes"));
    var first = application("first", "shared", "EXTERNAL");
    var second = application("second", "shared", "EXTERNAL");
    var other = application("other", "other-version", "EXTERNAL");
    var internal = application("internal", "internal-version", "INTERNAL");
    var hidden = application("hidden", "private-version", "EXTERNAL");
    when(persistence.findApplications("jd", null, "owner", "ONBOARDED", null, "ALL", null, null, null, null, null, 200))
        .thenReturn(List.of(first, second, other, internal, hidden));
    when(authorization.accessibleObjectIds(eq(actor), eq("recruitment.manage"), anyList()))
        .thenReturn(Set.of("first", "second", "other", "internal"));
    when(demands.clientIdsForVersions(List.of("shared", "other-version")))
        .thenReturn(Map.of("shared", "selected-client", "other-version", "other-client"));

    var page = manager.list("jd", null, "ONBOARDED", null, null, null, 20, "EXTERNAL", "selected-client");
    assertThat(page.getItems()).containsExactly(first, second);
    assertThat(page.isHasMore()).isFalse();
    verify(demands).clientIdsForVersions(List.of("shared", "other-version"));
    assertThat(manager.list("jd", null, "ONBOARDED", null, null, null, 20, "EXTERNAL", null).getItems())
        .containsExactly(first, second, other);
    verifyNoMoreInteractions(demands);
  }

  private static Application application(String id, String version, String type) {
    var app = new Application(); app.setId(id); app.setJdVersionId(version); app.setRecruitmentType(type);
    app.setUpdateTime(Instant.parse("2026-09-11T00:00:00Z")); return app;
  }
}
