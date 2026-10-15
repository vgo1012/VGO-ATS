package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JdNewUseConcurrencyTest {
  private static final String JD_ID = "jd";
  private static final String VERSION_ID = "jd-version";

  private final DemandCatalogPersistenceService catalog = mock(DemandCatalogPersistenceService.class);
  private final JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);
  private final DemandQueryAdapter adapter = new DemandQueryAdapter();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adapter, "persistence", catalog);
    ReflectionTestUtils.setField(adapter, "jdVersionPersistenceService", versions);
    var version = new JdVersionEntity();
    version.setId(VERSION_ID);
    version.setJdId(JD_ID);
    version.setClientId("client-at-version-creation");
    version.setRecruitmentType("EXTERNAL");
    when(versions.findJdVersion(VERSION_ID)).thenReturn(version);
  }

  @Test
  void deletionCommittedAfterEarlierReadPreventsNewUse() {
    when(catalog.findJd(JD_ID)).thenReturn(jd(false, true, "old-owner"));
    when(catalog.findJdForUpdate(JD_ID)).thenReturn(jd(true, false, "new-owner"));

    assertThat(adapter.requireJdVersion(VERSION_ID).isDeleted()).isFalse();
    assertThatThrownBy(() -> adapter.requireJdVersionForNewUse(VERSION_ID))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("JD_DELETED"));
    verify(catalog).findJdForUpdate(JD_ID);
    // 历史只读语义保持独立；业务拒绝由当前锁定结果决定。
    assertThat(adapter.requireJdVersion(VERSION_ID).isPublicVisible()).isFalse();
  }

  @Test
  void newUseTakesOwnershipFromCurrentLockedRowAndIgnoresLegacyPublicFlag() {
    when(catalog.findJd(JD_ID)).thenReturn(jd(false, true, "old-owner"));
    var current = jd(false, false, "new-owner");
    current.setClientId("client-changed-later");
    when(catalog.findJdForUpdate(JD_ID)).thenReturn(current);

    var result = adapter.requireJdVersionForNewUse(VERSION_ID);

    assertThat(result.isPublicVisible()).isFalse();
    assertThat(result.getOwnerUserId()).isEqualTo("new-owner");
    assertThat(result.getOwnerDepartmentId()).isEqualTo("new-owner-department");
    assertThat(result.getClientId()).isEqualTo("client-at-version-creation");
    assertThat(result.getRecruitmentType()).isEqualTo("EXTERNAL");
    verify(catalog, never()).findJd(JD_ID);
  }

  @Test
  void historicalReadRemainsAvailableAfterDeletionWithoutLockingForNewUse() {
    when(catalog.findJd(JD_ID)).thenReturn(jd(true, false, "owner"));

    var result = adapter.requireJdVersion(VERSION_ID);

    assertThat(result.isDeleted()).isTrue();
    assertThat(result.getClientId()).isEqualTo("client-at-version-creation");
    verify(catalog, never()).findJdForUpdate(JD_ID);
  }

  private JdEntity jd(boolean deleted, boolean publicVisible, String owner) {
    var jd = new JdEntity();
    jd.setId(JD_ID);
    jd.setDeleted(deleted);
    jd.setPublicVisible(publicVisible);
    jd.setOwnerUserId(owner);
    jd.setOwnerDepartmentId(owner + "-department");
    jd.setRecruitmentType("INTERNAL");
    return jd;
  }
}
