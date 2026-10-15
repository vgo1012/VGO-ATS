package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.domain.dto.PublicJdSourceDTO;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DemandCatalogOperationalScopeTest {

  @Test
  void personalAndPublicListsAndCountsUseTheSameHistoricalCitySearchAlias() {
    var manager = new DemandCatalogApplicationManager();
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(DemandCatalogPersistenceService.class);
    var actor = new CurrentActorSnapshot("actor", "HR", "hr@example.invalid", "home",
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
    when(persistence.findJds("北京", null, null, "actor", false, Set.of(), Set.of(), null, null, 21)).thenReturn(List.of());
    when(persistence.findPublicJdSources("北京", "ACTIVE", "PUBLISHED", null, null, 21)).thenReturn(List.of());
    manager.listJds("北京市", null, null, null, 20); manager.countJds("北京市", null, null);
    manager.listPublicJdSources("北京市", null, 20); manager.countPublicJdSources("北京市");
    verify(persistence).findJds("北京", null, null, "actor", false, Set.of(), Set.of(), null, null, 21);
    verify(persistence).countJds("北京", null, null, "actor", false, Set.of(), Set.of());
    verify(persistence).findPublicJdSources("北京", "ACTIVE", "PUBLISHED", null, null, 21);
    verify(persistence).countPublicJdSources("北京", "ACTIVE", "PUBLISHED");
  }

  @Test
  void publicSourceOwnersAreBatchEnrichedAfterRemovingPaginationLookahead() {
    var manager = new DemandCatalogApplicationManager();
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(DemandCatalogPersistenceService.class);
    var directory = mock(IdentityDirectoryService.class);
    var actor = new CurrentActorSnapshot("actor", "HR", "hr@example.invalid", "home",
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "identityDirectory", directory);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
    var sources = IntStream.range(0, 101).mapToObj(i -> {
      var row = new PublicJdSourceDTO(); row.setId("jd-" + i); row.setCreateTime(Instant.EPOCH);
      row.setOwnerUserId("user-" + i); row.setOwnerDepartmentId("dept-" + i); row.setWorkRegionName("北京");
      return row;
    }).toList();
    Set<String> users = sources.subList(0, 100).stream().map(PublicJdSourceDTO::getOwnerUserId).collect(Collectors.toSet());
    Set<String> departments = sources.subList(0, 100).stream().map(PublicJdSourceDTO::getOwnerDepartmentId).collect(Collectors.toSet());
    when(persistence.findPublicJdSources(null, "ACTIVE", "PUBLISHED", null, null, 101)).thenReturn(sources);
    when(directory.userNames(users)).thenReturn(Map.of("user-0", "当前所属人"));
    when(directory.departmentNames(departments)).thenReturn(Map.of("dept-0", "当前部门"));
    var page = manager.listPublicJdSources(null, null, 100);
    assertThat(page.getItems()).hasSize(100);
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getItems().getFirst().getOwnerUserName()).isEqualTo("当前所属人");
    assertThat(page.getItems().getFirst().getOwnerDepartmentName()).isEqualTo("当前部门");
    assertThat(page.getItems().getFirst().getWorkRegionName()).isEqualTo("北京市");
    assertThat(page.getItems().get(1).getOwnerUserName()).isNull();
    verify(directory).userNames(users); verify(directory).departmentNames(departments);
    var unassigned = new PublicJdSourceDTO(); unassigned.setId("unassigned");
    when(persistence.findPublicJdSource("unassigned", "ACTIVE", "PUBLISHED")).thenReturn(unassigned);
    assertThat(manager.getPublicJdSource("unassigned").getOwnerUserName()).isNull();
  }

  @Test
  void publicSourceEndpointsRejectMissingReadPermissionBeforePersistence() {
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(DemandCatalogPersistenceService.class);
    var denied = new SecurityException("缺少 JD 读取权限");
    doThrow(denied).when(authorization).requirePermission("demand.read");
    var manager = new DemandCatalogApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);

    assertThatThrownBy(() -> manager.listPublicJdSources(null, null, 20)).isSameAs(denied);
    assertThatThrownBy(() -> manager.countPublicJdSources(null)).isSameAs(denied);
    assertThatThrownBy(() -> manager.getPublicJdSource("private")).isSameAs(denied);
    verifyNoInteractions(persistence);
  }

  @Test
  void publicSourceEndpointsReadSharedContentAcrossDepartmentsAndHidePrivateDetails() {
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(DemandCatalogPersistenceService.class);
    var actor = new CurrentActorSnapshot("manager", "招聘负责人", "manager@example.invalid", "D".repeat(26),
        Set.of("support"), Set.of(), Set.of(), Set.of("RECRUITER"),
        Set.of("demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    when(persistence.findPublicJdSources("岗位", "ACTIVE", "PUBLISHED",
        null, null, 21)).thenReturn(List.of());
    when(persistence.countPublicJdSources("岗位", "ACTIVE", "PUBLISHED"))
        .thenReturn(1L);
    var source = new PublicJdSourceDTO(); source.setId("source");
    when(persistence.findPublicJdSource("source", "ACTIVE", "PUBLISHED"))
        .thenReturn(source);
    var manager = new DemandCatalogApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));

    assertThat(manager.listPublicJdSources("岗位", null, 20).getItems()).isEmpty();
    assertThat(manager.countPublicJdSources("岗位").getCount()).isEqualTo(1);
    assertThat(manager.getPublicJdSource("source")).isSameAs(source);
    assertThatThrownBy(() -> manager.getPublicJdSource("private"))
        .isInstanceOf(PlatformException.class);
    verify(persistence).findPublicJdSource("private", "ACTIVE", "PUBLISHED");
  }

  @Test
  void recruitmentManagerJdListDoesNotIncludeManagementChain() {
    var authorization = mock(AuthorizationService.class);
    var persistence = mock(DemandCatalogPersistenceService.class);
    var actor = new CurrentActorSnapshot(
        "manager", "招聘负责人", "manager@example.invalid", "department",
        Set.of(), Set.of("member"), Set.of(), Set.of("RECRUITMENT_MANAGER"),
        Set.of("demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    when(persistence.findJds(null, null, null, "manager", false, Set.of(), Set.of(),
        null, null, 21)).thenReturn(List.of());
    var manager = new DemandCatalogApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));

    manager.listJds(null, null, null, null, 20);

    verify(persistence).findJds(null, null, null, "manager", false, Set.of(), Set.of(),
        null, null, 21);
  }
}
