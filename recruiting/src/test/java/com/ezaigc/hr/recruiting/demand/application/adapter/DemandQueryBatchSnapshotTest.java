package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandCollaboratorEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DemandQueryBatchSnapshotTest {

  private final JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);
  private final JdEntityPersistenceService jds = mock(JdEntityPersistenceService.class);
  private final DemandEntityPersistenceService demands = mock(DemandEntityPersistenceService.class);
  private final DemandCollaboratorPersistenceService collaborators =
      mock(DemandCollaboratorPersistenceService.class);
  private final DemandCatalogPersistenceService clients =
      mock(DemandCatalogPersistenceService.class);
  private final DemandQueryAdapter adapter = new DemandQueryAdapter();

  @BeforeAll
  static void mappings() {
    var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "jd-snapshot-batch");
    TableInfoHelper.initTableInfo(assistant, JdVersionEntity.class);
    TableInfoHelper.initTableInfo(assistant, JdEntity.class);
    TableInfoHelper.initTableInfo(assistant, DemandEntity.class);
    TableInfoHelper.initTableInfo(assistant, ClientEntity.class);
  }

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adapter, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(adapter, "jdEntityPersistenceService", jds);
    ReflectionTestUtils.setField(adapter, "demandEntityPersistenceService", demands);
    ReflectionTestUtils.setField(adapter, "demandCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(adapter, "persistence", clients);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void batchesEveryRelationOnceAndPreservesHistoricalSnapshotSemantics() {
    when(versions.list(any(Wrapper.class)))
        .thenReturn(
            List.of(
                version("v-external", "jd-shared", "EXTERNAL", "historical-client"),
                version("v-internal", "jd-shared", "INTERNAL", null),
                version("v-legacy", "jd-legacy", null, null),
                version("v-no-demand", "jd-no-demand", null, null),
                version("v-missing-demand", "jd-missing-demand", "EXTERNAL", "missing-client")));
    when(jds.list(any(Wrapper.class)))
        .thenReturn(
            List.of(
                jd("jd-shared", "d-shared", "EXTERNAL", "current-client", true, false),
                jd("jd-legacy", "d-shared", null, "current-client", false, true),
                jd("jd-no-demand", null, null, null, false, false),
                jd("jd-missing-demand", "d-missing", null, "missing-client", true, false)));
    var sharedDemand = demand("d-shared", "EXTERNAL");
    sharedDemand.setClientId("current-client");
    when(demands.list(any(Wrapper.class))).thenReturn(List.of(sharedDemand));
    when(collaborators.findForDemands(List.of("d-shared")))
        .thenReturn(
            List.of(
                collaborator("d-shared", "user-2"),
                collaborator("d-shared", "user-1"),
                collaborator("d-shared", "user-1")));
    when(clients.list(any(Wrapper.class)))
        .thenReturn(
            List.of(
                client("historical-client", "历史客户"),
                client("current-client", "当前客户")));

    Map<String, JdVersionSnapshot> snapshots =
        adapter.requireJdVersions(
            List.of(
                "v-external",
                "v-internal",
                "v-legacy",
                "v-no-demand",
                "v-missing-demand",
                "v-external"));

    assertThat(snapshots.keySet())
        .containsExactly(
            "v-external", "v-internal", "v-legacy", "v-no-demand", "v-missing-demand");
    assertThat(snapshots.get("v-external"))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getJdId()).isEqualTo("jd-shared");
              assertThat(snapshot.getDemandId()).isEqualTo("d-shared");
              assertThat(snapshot.getRecruitmentType()).isEqualTo("EXTERNAL");
              assertThat(snapshot.getClientId()).isEqualTo("historical-client");
              assertThat(snapshot.getClientName()).isEqualTo("历史客户");
              assertThat(snapshot.getOwnerUserId()).isEqualTo("owner-jd-shared");
              assertThat(snapshot.getOwnerDepartmentId()).isEqualTo("department-jd-shared");
              assertThat(snapshot.getCollaboratorUserIds()).containsExactlyInAnyOrder("user-1", "user-2");
              assertThatThrownBy(() -> snapshot.getCollaboratorUserIds().add("unrelated-user"))
                  .isInstanceOf(UnsupportedOperationException.class);
            assertThat(snapshot.isPublicVisible()).isFalse();
              assertThat(snapshot.isDeleted()).isFalse();
            });
    assertThat(snapshots.get("v-internal"))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getRecruitmentType()).isEqualTo("INTERNAL");
              assertThat(snapshot.getClientId()).isNull();
              assertThat(snapshot.getClientName()).isNull();
              assertThat(snapshot.getCollaboratorUserIds()).containsExactlyInAnyOrder("user-1", "user-2");
            });
    assertThat(snapshots.get("v-legacy"))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getRecruitmentType()).isEqualTo("EXTERNAL");
              assertThat(snapshot.getClientId()).isEqualTo("current-client");
              assertThat(snapshot.getClientName()).isEqualTo("当前客户");
              assertThat(snapshot.isPublicVisible()).isFalse();
              assertThat(snapshot.isDeleted()).isTrue();
            });
    assertThat(snapshots.get("v-no-demand"))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getRecruitmentType()).isEqualTo("INTERNAL");
              assertThat(snapshot.getDemandId()).isNull();
              assertThat(snapshot.getCollaboratorUserIds()).isEmpty();
              assertThat(snapshot.getClientId()).isNull();
            });
    assertThat(snapshots.get("v-missing-demand"))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getRecruitmentType()).isEqualTo("EXTERNAL");
              assertThat(snapshot.getDemandId()).isEqualTo("d-missing");
              assertThat(snapshot.getCollaboratorUserIds()).isEmpty();
              assertThat(snapshot.getClientId()).isEqualTo("missing-client");
              assertThat(snapshot.getClientName()).isNull();
            });
    assertThatThrownBy(() -> snapshots.put("another", new JdVersionSnapshot()))
        .isInstanceOf(UnsupportedOperationException.class);

    ArgumentCaptor<LambdaQueryWrapper<JdVersionEntity>> versionQuery = queryCaptor();
    ArgumentCaptor<LambdaQueryWrapper<JdEntity>> jdQuery = queryCaptor();
    ArgumentCaptor<LambdaQueryWrapper<DemandEntity>> demandQuery = queryCaptor();
    ArgumentCaptor<LambdaQueryWrapper<ClientEntity>> clientQuery = queryCaptor();
    verify(versions, times(1)).list(versionQuery.capture());
    verify(jds, times(1)).list(jdQuery.capture());
    verify(demands, times(1)).list(demandQuery.capture());
    verify(collaborators, times(1)).findForDemands(List.of("d-shared"));
    verify(clients, times(1)).list(clientQuery.capture());
    assertThat(versionQuery.getValue().getSqlSelect())
        .isEqualTo("id,jd_id,recruitment_type,client_id");
    assertThat(jdQuery.getValue().getSqlSelect())
        .isEqualTo(
            "id,demand_id,recruitment_type,client_id,owner_user_id,owner_department_id,create_id,source_owner_department_id,public_visible,deleted");
    assertThat(demandQuery.getValue().getSqlSelect()).isEqualTo("id,recruitment_type,client_id,owner_user_id,owner_department_id");
    assertThat(clientQuery.getValue().getSqlSelect()).isEqualTo("id,name");
    assertThat(versionQuery.getValue().getSqlSegment()).contains("id IN").doesNotContain("FOR UPDATE");
    assertThat(jdQuery.getValue().getSqlSegment()).contains("id IN").doesNotContain("FOR UPDATE");
    assertThat(demandQuery.getValue().getSqlSegment()).contains("id IN").doesNotContain("FOR UPDATE");
    assertThat(clientQuery.getValue().getSqlSegment()).contains("id IN").doesNotContain("FOR UPDATE");
    assertThat(versionQuery.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(
            "v-external", "v-internal", "v-legacy", "v-no-demand", "v-missing-demand");
    assertThat(jdQuery.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(
            "jd-shared", "jd-legacy", "jd-no-demand", "jd-missing-demand");
    assertThat(demandQuery.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder("d-shared", "d-missing");
    assertThat(clientQuery.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder("historical-client", "current-client", "missing-client");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void preservesDemandAndSourceFallbacksUsedByTheSingleJdProjection() {
    var demand = demand("demand", "EXTERNAL");
    demand.setOwnerUserId("demand-owner");
    demand.setOwnerDepartmentId("demand-department");
    demand.setClientId("demand-client");
    var inherited = jd("inherited", "demand", null, "ignored-raw-client", false, false);
    inherited.setOwnerUserId(null);
    inherited.setOwnerDepartmentId(null);
    inherited.setCreateId("creator");
    inherited.setSourceOwnerDepartmentId("source-department");
    var orphan = jd("orphan", "missing-demand", null, "ignored-orphan-client", false, false);
    orphan.setOwnerUserId(null);
    orphan.setOwnerDepartmentId(null);
    orphan.setCreateId("orphan-creator");
    orphan.setSourceOwnerDepartmentId("orphan-source");
    var explicit = jd("explicit", "demand", "INTERNAL", null, false, false);
    when(versions.list(any(Wrapper.class))).thenReturn(List.of(
        version("legacy", "inherited", null, null), version("frozen", "inherited", "INTERNAL", null),
        version("orphan-version", "orphan", null, null), version("explicit-version", "explicit", null, null)));
    when(jds.list(any(Wrapper.class))).thenReturn(List.of(inherited, orphan, explicit));
    when(demands.list(any(Wrapper.class))).thenReturn(List.of(demand));
    when(collaborators.findForDemands(List.of("demand"))).thenReturn(List.of(collaborator("demand", "collaborator")));
    when(clients.list(any(Wrapper.class))).thenReturn(List.of(client("demand-client", "需求客户")));

    var values = adapter.requireJdVersions(List.of("legacy", "frozen", "orphan-version", "explicit-version"));

    assertThat(values.get("legacy").getOwnerUserId()).isEqualTo("demand-owner");
    assertThat(values.get("legacy").getOwnerDepartmentId()).isEqualTo("demand-department");
    assertThat(values.get("legacy").getClientId()).isEqualTo("demand-client");
    assertThat(values.get("legacy").getClientName()).isEqualTo("需求客户");
    assertThat(values.get("legacy").getCollaboratorUserIds()).containsExactly("collaborator");
    assertThat(values.get("frozen").getClientId()).isNull();
    assertThat(values.get("frozen").getRecruitmentType()).isEqualTo("INTERNAL");
    assertThat(values.get("orphan-version").getOwnerUserId()).isEqualTo("orphan-creator");
    assertThat(values.get("orphan-version").getOwnerDepartmentId()).isEqualTo("orphan-source");
    assertThat(values.get("orphan-version").getClientId()).isNull();
    assertThat(values.get("orphan-version").getCollaboratorUserIds()).isEmpty();
    assertThat(values.get("explicit-version").getOwnerUserId()).isEqualTo("owner-explicit");
    assertThat(values.get("explicit-version").getOwnerDepartmentId()).isEqualTo("department-explicit");
    assertThat(values.get("explicit-version").getClientId()).isNull();
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void skipsDemandCollaboratorAndClientReadsWhenNoRelatedIdsExist() {
    when(versions.list(any(Wrapper.class)))
        .thenReturn(List.of(version("version", "jd", "INTERNAL", null)));
    when(jds.list(any(Wrapper.class)))
        .thenReturn(List.of(jd("jd", null, "EXTERNAL", "current-client", false, false)));

    JdVersionSnapshot snapshot = adapter.requireJdVersions(List.of("version")).get("version");

    assertThat(snapshot.getRecruitmentType()).isEqualTo("INTERNAL");
    assertThat(snapshot.getClientId()).isNull();
    assertThat(snapshot.getCollaboratorUserIds()).isEmpty();
    verify(versions).list(any(Wrapper.class));
    verify(jds).list(any(Wrapper.class));
    verifyNoInteractions(demands, collaborators, clients);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void rejectsMissingVersionBeforeReadingOtherRelations() {
    when(versions.list(any(Wrapper.class)))
        .thenReturn(List.of(version("present", "jd", "INTERNAL", null)));

    assertThatThrownBy(() -> adapter.requireJdVersions(List.of("present", "missing")))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("JD_VERSION_NOT_FOUND"));

    verify(versions).list(any(Wrapper.class));
    verifyNoInteractions(jds, demands, collaborators, clients);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void rejectsMissingLogicalJdBeforeReadingOptionalRelations() {
    when(versions.list(any(Wrapper.class)))
        .thenReturn(List.of(version("version", "missing-jd", "INTERNAL", null)));
    when(jds.list(any(Wrapper.class))).thenReturn(List.of());

    assertThatThrownBy(() -> adapter.requireJdVersions(List.of("version")))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("JD_NOT_FOUND"));

    verify(versions).list(any(Wrapper.class));
    verify(jds).list(any(Wrapper.class));
    verifyNoInteractions(demands, collaborators, clients);
  }

  @Test
  void emptyAndInvalidInputNeverReadsDatabase() {
    assertThat(adapter.requireJdVersions(List.of())).isEmpty();
    assertThatThrownBy(() -> adapter.requireJdVersions(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.requireJdVersions(Arrays.asList("valid", null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.requireJdVersions(List.of("valid", " ")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> adapter.requireJdVersions(Collections.nCopies(201, "same-version")))
        .isInstanceOf(IllegalArgumentException.class);

    verify(versions, never()).list(any(LambdaQueryWrapper.class));
    verifyNoInteractions(jds, demands, collaborators, clients);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T> ArgumentCaptor<LambdaQueryWrapper<T>> queryCaptor() {
    return ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
  }

  private static JdVersionEntity version(
      String id, String jdId, String recruitmentType, String clientId) {
    var version = new JdVersionEntity();
    version.setId(id);
    version.setJdId(jdId);
    version.setRecruitmentType(recruitmentType);
    version.setClientId(clientId);
    return version;
  }

  private static JdEntity jd(
      String id,
      String demandId,
      String recruitmentType,
      String clientId,
      boolean publicVisible,
      boolean deleted) {
    var jd = new JdEntity();
    jd.setId(id);
    jd.setDemandId(demandId);
    jd.setRecruitmentType(recruitmentType);
    jd.setClientId(clientId);
    jd.setOwnerUserId("owner-" + id);
    jd.setOwnerDepartmentId("department-" + id);
    jd.setPublicVisible(publicVisible);
    jd.setDeleted(deleted);
    return jd;
  }

  private static DemandEntity demand(String id, String recruitmentType) {
    var demand = new DemandEntity();
    demand.setId(id);
    demand.setRecruitmentType(recruitmentType);
    return demand;
  }

  private static DemandCollaboratorEntity collaborator(String demandId, String userId) {
    var collaborator = new DemandCollaboratorEntity();
    collaborator.setDemandId(demandId);
    collaborator.setUserId(userId);
    return collaborator;
  }

  private static ClientEntity client(String id, String name) {
    var client = new ClientEntity();
    client.setId(id);
    client.setName(name);
    return client;
  }
}
