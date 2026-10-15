package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdResumeSourcePersistenceService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaginationCountApplicationManagerTest {
  @Test
  void demandCountsUseTheSameNormalizedFiltersAndActorScopeAsTheirLists() {
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(DemandCatalogPersistenceService.class);
    var manager = new DemandCatalogApplicationManager();
    var actor = actor();
    when(authorization.currentActor()).thenReturn(actor);
    when(persistence.countClients(
        "ACTIVE", actor.getUserId(), true, actor.getOperationalManagedUserIds(),
        actor.visibleDepartmentIds())).thenReturn(8L);
    when(persistence.countJds(
        "运维", "client-1", "ACTIVE", actor.getUserId(), actor.hasCompanyWideScope(),
        actor.getOperationalManagedUserIds(), actor.visibleDepartmentIdsFor("JD"))).thenReturn(41L);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);

    assertThat(manager.countClients(" active ").getCount()).isEqualTo(8);
    assertThat(manager.countJds(" 运维 ", "client-1", " active ").getCount()).isEqualTo(41);

    verify(authorization, times(2)).requirePermission("demand.read");
    verify(persistence).countJds(
        "运维", "client-1", "ACTIVE", actor.getUserId(), actor.hasCompanyWideScope(),
        actor.getOperationalManagedUserIds(), actor.visibleDepartmentIdsFor("JD"));
  }

  @Test
  void resumeSourceCountChecksBothJobAndTalentAccessBeforeReading() {
    var authorization = mock(AuthorizationService.class);
    var queries = mock(DemandQueryService.class);
    var sources = mock(JdResumeSourcePersistenceService.class);
    var manager = new JdResumeSourceApplicationManager();
    var context = new JdVersionSnapshot(
        "jd-version", "jd", "demand", "INTERNAL", "owner", "department", Set.of());
    when(queries.requireJdVersion("jd-version")).thenReturn(context);
    when(sources.countByJdVersion("jd-version")).thenReturn(6L);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "queries", queries);
    ReflectionTestUtils.setField(manager, "sources", sources);

    assertThat(manager.count("jd-version").getCount()).isEqualTo(6);

    verify(authorization).requireAccess(eq("demand.read"), any(AuthorizationTarget.class));
    verify(authorization).requirePermission("talent.read");
    verify(sources).countByJdVersion("jd-version");
  }

  private static CurrentActorSnapshot actor() {
    return new CurrentActorSnapshot(
        "recruiter", "招聘专员", "recruiter@example.com", "department", Set.of("department"),
        Set.of("managed"), Set.of(), Set.of("RECRUITMENT_MANAGER"), Set.of("demand.read"),
        false, false);
  }
}
