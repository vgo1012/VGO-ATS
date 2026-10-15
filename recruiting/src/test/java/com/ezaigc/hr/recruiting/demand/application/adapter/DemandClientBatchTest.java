package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DemandClientBatchTest {
  private final JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);
  private final JdEntityPersistenceService jds = mock(JdEntityPersistenceService.class);
  private final DemandQueryAdapter adapter = new DemandQueryAdapter();
  private final DemandCatalogPersistenceService clients = mock(DemandCatalogPersistenceService.class);

  DemandClientBatchTest() {
    ReflectionTestUtils.setField(adapter, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(adapter, "jdEntityPersistenceService", jds);
    ReflectionTestUtils.setField(adapter, "persistence", clients);
  }

  @BeforeAll static void mappings() {
    var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "client-batch");
    TableInfoHelper.initTableInfo(assistant, JdVersionEntity.class);
    TableInfoHelper.initTableInfo(assistant, JdEntity.class);
    TableInfoHelper.initTableInfo(assistant, ClientEntity.class);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void summariesBatchOnlyDistinctClientIdsAndPreserveHistoricalInternalAndMissingValues() {
    var jd = new JdEntity(); jd.setId("jd"); jd.setClientId("current-client"); jd.setCode("JD-001");
    when(versions.list(any(Wrapper.class))).thenReturn(List.of(
        version("legacy", null, null), version("external", "EXTERNAL", "historical-client"),
        version("duplicate-client", "EXTERNAL", "historical-client"),
        version("internal", "INTERNAL", null), version("missing-client", "EXTERNAL", "missing")));
    when(jds.list(any(Wrapper.class))).thenReturn(List.of(jd));
    var current = new ClientEntity(); current.setId("current-client"); current.setName("当前客户");
    var historical = new ClientEntity(); historical.setId("historical-client"); historical.setName("历史客户");
    when(clients.list(any(Wrapper.class))).thenReturn(List.of(current,historical));

    var summaries = adapter.clientSummariesForVersions(
        List.of("legacy","external","duplicate-client","internal","missing-client","external"));

    assertThat(summaries).hasSize(5);
    assertThat(summaries.get("external").getJdTitle()).isEqualTo("历史岗位");
    assertThat(summaries.get("external").getJdCode()).isEqualTo("JD-001");
    assertThat(summaries.get("internal").getJdTitle()).isEqualTo("历史岗位");
    assertThat(summaries.get("internal").getJdCode()).isEqualTo("JD-001");
    ArgumentCaptor<LambdaQueryWrapper<JdVersionEntity>> versionQuery = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    ArgumentCaptor<LambdaQueryWrapper<JdEntity>> jdQuery = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(versions).list(versionQuery.capture());
    verify(jds).list(jdQuery.capture());
    assertThat(versionQuery.getValue().getSqlSelect()).isEqualTo("id,jd_id,recruitment_type,client_id,title");
    assertThat(jdQuery.getValue().getSqlSelect()).isEqualTo("id,client_id,code");
    verifyNoMoreInteractions(versions, jds);
    assertThat(summaries.get("legacy").getClientId()).isEqualTo("current-client");
    assertThat(summaries.get("legacy").getClientName()).isEqualTo("当前客户");
    assertThat(summaries.get("external").getClientId()).isEqualTo("historical-client");
    assertThat(summaries.get("external").getClientName()).isEqualTo("历史客户");
    assertThat(summaries.get("duplicate-client")).isEqualTo(summaries.get("external"));
    assertThat(summaries.get("internal").getClientId()).isNull();
    assertThat(summaries.get("internal").getClientName()).isNull();
    assertThat(summaries.get("missing-client").getClientId()).isEqualTo("missing");
    assertThat(summaries.get("missing-client").getClientName()).isNull();
    ArgumentCaptor<LambdaQueryWrapper<ClientEntity>> query = ArgumentCaptor.forClass((Class)LambdaQueryWrapper.class);
    verify(clients).list(query.capture());
    assertThat(query.getValue().getSqlSelect()).isEqualTo("id,name");
    assertThat(query.getValue().getSqlSegment()).contains("id IN");
    assertThat(query.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder("current-client","historical-client","missing");
    verifyNoMoreInteractions(clients);
  }

  @Test void internalOnlySummariesDoNotLoadClients() {
    var jd = new JdEntity(); jd.setId("jd"); jd.setClientId("current-client");
    when(versions.list(any(Wrapper.class))).thenReturn(List.of(version("internal","INTERNAL",null)));
    when(jds.list(any(Wrapper.class))).thenReturn(List.of(jd));
    var summaries = adapter.clientSummariesForVersions(List.of("internal"));
    assertThat(summaries.get("internal").getClientId()).isNull();
    assertThat(summaries.get("internal").getClientName()).isNull();
    verifyNoInteractions(clients);
  }

  @Test void summaryInputBoundsAndEmptyRequestsDoNotQuery() {
    assertThat(adapter.clientSummariesForVersions(List.of())).isEmpty();
    assertThatThrownBy(() -> adapter.clientSummariesForVersions(Collections.nCopies(201,"version")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.clientSummariesForVersions(null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(versions,jds,clients);
  }

  @Test void batchesHistoricalClientSelectionAndDoesNotReplaceAnInternalVersionsNullClient() {
    var legacy = version("legacy", null, null);
    var external = version("external", "EXTERNAL", "historical-client");
    var internal = version("internal", "INTERNAL", null);
    var jd = new JdEntity(); jd.setId("jd"); jd.setClientId("current-client");
    when(versions.list(any(Wrapper.class))).thenReturn(List.of(legacy, external, internal));
    when(jds.list(any(Wrapper.class))).thenReturn(List.of(jd));

    var clients = adapter.clientIdsForVersions(List.of("legacy", "external", "internal", "legacy"));

    assertThat(clients).hasSize(3).containsEntry("legacy", "current-client")
        .containsEntry("external", "historical-client").containsEntry("internal", null);
    verify(versions).list(any(Wrapper.class)); verify(jds).list(any(Wrapper.class));
    verifyNoMoreInteractions(versions, jds);
  }

  @Test void keepsMissingVersionAndMissingJdFailures() {
    when(versions.list(any(Wrapper.class))).thenReturn(List.of());
    assertThatThrownBy(() -> adapter.clientIdsForVersions(List.of("missing")))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("JD_VERSION_NOT_FOUND"));
    when(versions.list(any(Wrapper.class))).thenReturn(List.of(version("version", "EXTERNAL", "client")));
    when(jds.list(any(Wrapper.class))).thenReturn(List.of());
    assertThatThrownBy(() -> adapter.clientIdsForVersions(List.of("version")))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("JD_NOT_FOUND"));
  }

  @Test void emptyAndOversizedRequestsDoNotQuery() {
    assertThat(adapter.clientIdsForVersions(List.of())).isEmpty();
    assertThatThrownBy(() -> adapter.clientIdsForVersions(Collections.nCopies(201, "version")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoMoreInteractions(versions, jds);
  }

  private static JdVersionEntity version(String id, String type, String client) {
    var version = new JdVersionEntity(); version.setId(id); version.setJdId("jd");
    version.setTitle("历史岗位");
    version.setRecruitmentType(type); version.setClientId(client); return version;
  }
}
