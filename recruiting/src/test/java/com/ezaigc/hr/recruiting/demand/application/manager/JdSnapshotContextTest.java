package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.recruiting.demand.application.adapter.DemandQueryAdapter;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class JdSnapshotContextTest {
  @ParameterizedTest
  @CsvSource({"INTERNAL,,false", "EXTERNAL,original-client,false", "EXTERNAL,original-client,true"})
  void exactVersionRetainsClientAndRemainsAvailableForExistingProcesses(
      String recruitmentType, String clientId, boolean deleted) {
    var catalog = mock(DemandCatalogPersistenceService.class);
    var versions = mock(JdVersionPersistenceService.class);
    var demands = mock(DemandEntityPersistenceService.class);
    var adapter = new DemandQueryAdapter();
    ReflectionTestUtils.setField(adapter, "persistence", catalog);
    ReflectionTestUtils.setField(adapter, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(adapter, "demandEntityPersistenceService", demands);
    var jd = new JdEntity(); jd.setId("jd"); jd.setClientId("new-client");
    jd.setRecruitmentType("EXTERNAL"); jd.setOwnerUserId("owner"); jd.setOwnerDepartmentId("department");
    jd.setPublicVisible(true); jd.setDeleted(deleted);
    var version = new JdVersionEntity(); version.setId("old-version"); version.setJdId("jd");
    version.setRecruitmentType(recruitmentType); version.setClientId(clientId);
    when(catalog.findJd("jd")).thenReturn(jd);
    when(versions.findJdVersion("old-version")).thenReturn(version);

    var snapshot = adapter.requireJdVersion("old-version");
    var submission = adapter.requireSubmissionContextForJdVersion("old-version");

    assertThat(snapshot.getClientId()).isEqualTo(clientId);
    assertThat(snapshot.getRecruitmentType()).isEqualTo(recruitmentType);
    assertThat(snapshot.isDeleted()).isEqualTo(deleted);
    assertThat(snapshot.isPublicVisible()).isFalse();
    assertThat(snapshot.getCollaboratorUserIds()).isEmpty();
    assertThat(submission.clientId()).isEqualTo(clientId);
    assertThat(submission.demandId()).isNull();
    assertThat(submission.positionId()).isNull();
    verifyNoInteractions(demands);
  }
}
