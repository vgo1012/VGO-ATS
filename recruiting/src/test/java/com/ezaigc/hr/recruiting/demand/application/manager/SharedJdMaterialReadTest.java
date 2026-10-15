package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.SharedCandidateJdQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SharedJdMaterialReadTest {
  @Test void historicalApplicationsGrantOnlyTheirBoundVersionWhileCandidateIsShared() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "shared-jd-read"), Application.class);
    var manager = new SharedJdReadApplicationManager();
    var applications = mock(ApplicationPersistenceService.class);
    var library = mock(CandidateLibraryQueryService.class);
    ReflectionTestUtils.setField(manager, "applications", applications);
    ReflectionTestUtils.setField(manager, "library", library);
    ReflectionTestUtils.setField(manager, "materials", mock(SharedCandidateJdQuery.class));
    var application = new Application(); application.setId("application"); application.setCandidateId("candidate");
    when(applications.page(any(), any())).thenReturn(new Page<Application>().setRecords(List.of(application)));
    when(library.eligibleCandidateIds(List.of("candidate"))).thenReturn(Set.of("candidate"));
    assertThat(manager.isShared("historical-version")).isTrue();
    when(library.eligibleCandidateIds(List.of("candidate"))).thenReturn(Set.of());
    assertThat(manager.isShared("historical-version")).isFalse();
  }

  @Test void sharedJdFactsUseTheSameMaterialReadBoundary() {
    var manager = new JdParsingApplicationManager();
    var auth = mock(AuthorizationService.class);
    var shared = mock(SharedJdReadApplicationManager.class);
    var queries = mock(DemandQueryService.class);
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "sharedJdRead", shared);
    ReflectionTestUtils.setField(manager, "queries", queries);
    ReflectionTestUtils.setField(manager, "persistence", mock(JdParsingPersistenceService.class));
    when(queries.requireJdVersion("version")).thenReturn(mock(JdVersionSnapshot.class));
    var denied = PlatformExceptions.forbidden("DENIED", "无岗位权限");
    doThrow(denied).when(auth).requireAccess(eq("demand.read"), any());
    when(shared.isShared("version")).thenReturn(true);
    assertThatThrownBy(() -> manager.facts("version")).hasMessage("JD 事实尚未生成");
    when(shared.isShared("version")).thenReturn(false);
    assertThatThrownBy(() -> manager.facts("version")).isSameAs(denied);
  }
  @Test void readsOnlySharedBoundFrozenVersionAndRevokesAfterLibraryExit() {
    var manager = new DemandCatalogApplicationManager();
    var auth = mock(AuthorizationService.class);
    var shared = mock(SharedJdReadApplicationManager.class);
    var versions = mock(JdVersionPersistenceService.class);
    var jds = mock(DemandCatalogPersistenceService.class);
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "sharedJdRead", shared);
    ReflectionTestUtils.setField(manager, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "persistence", jds);
    var version = new JdVersionEntity(); version.setId("version"); version.setJdId("jd");
    var jd = new JdEntity(); jd.setId("jd"); jd.setOwnerUserId("other"); jd.setCode("JD-TEST");
    jd.setContentShared(true);
    when(versions.findJdVersion("version")).thenReturn(version);
    when(jds.findJd("jd")).thenReturn(jd);
    var denied = PlatformExceptions.forbidden("DENIED", "无岗位权限");
    doThrow(denied).when(auth).requireAccess(eq("demand.read"), any());
    when(shared.isShared("version")).thenReturn(true);
    assertThat(manager.getJdVersion("version").getJdCode()).isEqualTo("JD-TEST");
    when(shared.isShared("version")).thenReturn(false);
    assertThatThrownBy(() -> manager.getJdVersion("version")).isSameAs(denied);
    assertThatThrownBy(() -> manager.listJdSources("version", null, 20)).isSameAs(denied);
  }
}
