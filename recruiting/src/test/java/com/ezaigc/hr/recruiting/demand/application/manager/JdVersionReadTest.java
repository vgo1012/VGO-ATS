package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JdVersionReadTest {
  private final DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final DemandCatalogPersistenceService catalog = mock(DemandCatalogPersistenceService.class);
  private final DemandCollaboratorPersistenceService collaborators =
      mock(DemandCollaboratorPersistenceService.class);
  private final JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "sharedJdRead", mock(SharedJdReadApplicationManager.class));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", catalog);
    ReflectionTestUtils.setField(manager, "demandCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(manager, "jdVersionPersistenceService", versions);
  }

  @Test
  void returnsAnExactVisibleHistoricalVersion() {
    JdVersionEntity version = version();
    JdEntity jd = jd();
    when(versions.findJdVersion(version.getId())).thenReturn(version);
    when(catalog.findJd(jd.getId())).thenReturn(jd);
    when(collaborators.findCollaboratorIds(jd.getDemandId())).thenReturn(List.of("collaborator"));

    assertThat(manager.getJdVersion(version.getId())).isSameAs(version);

    verify(authorization)
        .requireAccess(
            "demand.read",
            new AuthorizationTarget(
                "JD", "jd-1", "current-owner", "current-department", Set.of("collaborator")));
    verify(versions, never()).findJdVersions(any(), any(), any(), anyInt());
  }

  @Test
  void rejectsAMissingVersionBeforeLookingUpItsParent() {
    assertThatThrownBy(() -> manager.getJdVersion("missing-version"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("JD_VERSION_NOT_FOUND"));
    verify(catalog, never()).findJd(anyString());
  }

  @Test
  void rejectsAHistoricalVersionWhoseParentJdIsMissing() {
    when(versions.findJdVersion("version-2")).thenReturn(version());

    assertThatThrownBy(() -> manager.getJdVersion("version-2"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("JD_NOT_FOUND"));
    verify(authorization, never()).requireAccess(anyString(), any());
  }

  @Test
  void propagatesDemandReadAuthorizationRejection() {
    JdVersionEntity version = version();
    JdEntity jd = jd();
    when(versions.findJdVersion(version.getId())).thenReturn(version);
    when(catalog.findJd(jd.getId())).thenReturn(jd);
    when(collaborators.findCollaboratorIds(jd.getDemandId())).thenReturn(List.of("collaborator"));
    AuthorizationTarget target =
        new AuthorizationTarget(
            "JD", "jd-1", "current-owner", "current-department", Set.of("collaborator"));
    doThrow(PlatformExceptions.forbidden("PERMISSION_REQUIRED", "缺少操作权限：demand.read"))
        .when(authorization)
        .requireAccess("demand.read", target);

    assertThatThrownBy(() -> manager.getJdVersion(version.getId()))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("PERMISSION_REQUIRED"));
  }

  private static JdVersionEntity version() {
    JdVersionEntity version = new JdVersionEntity();
    version.setId("version-2");
    version.setJdId("jd-1");
    version.setVersionNo(2);
    version.setTitle("历史标题");
    version.setRecruitmentType("EXTERNAL");
    return version;
  }

  private static JdEntity jd() {
    JdEntity jd = new JdEntity();
    jd.setId("jd-1");
    jd.setOwnerUserId("current-owner");
    jd.setOwnerDepartmentId("current-department");
    jd.setDemandId("demand-current");
    jd.setCurrentVersionNo(3);
    return jd;
  }
}
