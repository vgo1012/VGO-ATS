package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class JdFactBatchReadAccessTest {
  private final JdFactQueryAdapter adapter = new JdFactQueryAdapter();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final DemandQueryService demands = mock(DemandQueryService.class);
  private final JdParsingPersistenceService persistence = mock(JdParsingPersistenceService.class);
  private final ObjectMapper json = mock(ObjectMapper.class);
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), JdFactSnapshotEntity.class);
    actor.setUserId("reader");
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(adapter, "authorization", authorization);
    ReflectionTestUtils.setField(adapter, "demandQuery", demands);
    ReflectionTestUtils.setField(adapter, "persistence", persistence);
    ReflectionTestUtils.setField(adapter, "json", json);
  }

  @Test
  void readsTwoHundredFactsAndAuthorizesTargetsInOneBatchWithoutJsonOrLocks() {
    var ids = IntStream.range(0, 200).mapToObj(index -> "version-" + index).toList();
    when(persistence.list(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
      var query = invocation.getArgument(0, LambdaQueryWrapper.class);
      assertThat(query.getSqlSelect()).isEqualTo("jd_version_id");
      assertThat(query.getSqlSegment()).contains("current_marker =", "jd_version_id IN").doesNotContain("FOR UPDATE");
      assertThat(query.getParamNameValuePairs().values()).contains("Y").containsAll(ids);
      return ids.stream().map(JdFactBatchReadAccessTest::snapshot).toList();
    });
    when(demands.requireJdVersions(ids)).thenReturn(ids.stream().collect(Collectors.toMap(id -> id, id -> context(id, false, false))));
    var targets = ids.stream().map(id -> new AuthorizationTarget("JD_VERSION", id, "owner", "department", Set.of("collaborator"))).toList();
    when(authorization.accessibleObjectIds(actor, "demand.read", targets)).thenReturn(Set.copyOf(ids));

    adapter.requireCurrentFactsReadAccessBatch(ids);

    verify(persistence).list(any(LambdaQueryWrapper.class));
    verify(persistence, never()).findCurrent(anyString());
    verify(demands).requireJdVersions(ids);
    verify(demands, never()).requireJdVersion(anyString());
    verify(demands, never()).requireJdVersionForNewUse(anyString());
    verify(authorization).accessibleObjectIds(actor, "demand.read", targets);
    verify(authorization, never()).requireAccess(anyString(), any());
    verifyNoInteractions(json);
  }

  @Test
  void deduplicatesLegacyPublicAndPrivateJobsButStillRequiresBothToBeAccessible() {
    when(persistence.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(snapshot("public"), snapshot("private")));
    when(demands.requireJdVersions(List.of("public", "private"))).thenReturn(Map.of(
        "public", context("public", true, false), "private", context("private", false, false)));
    var targets = List.of(new AuthorizationTarget("JD_VERSION", "public", "owner", "department", Set.of("collaborator")),
        new AuthorizationTarget("JD_VERSION", "private", "owner", "department", Set.of("collaborator")));
    when(authorization.accessibleObjectIds(actor, "demand.read", targets)).thenReturn(Set.of("public"));

    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(List.of("public", "private", "public")))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
    verify(authorization).accessibleObjectIds(actor, "demand.read", targets);
    when(authorization.accessibleObjectIds(actor, "demand.read", targets)).thenReturn(Set.of("public", "private"));
    adapter.requireCurrentFactsReadAccessBatch(List.of("public", "private"));
    when(authorization.accessibleObjectIds(actor, "demand.read", targets)).thenReturn(Set.of());
    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(List.of("public", "private")))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
  }

  @Test
  void missingCurrentFactsRejectBeforeReadingJobMetadata() {
    when(persistence.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(snapshot("first")));
    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(List.of("first", "missing")))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("JD_FACTS_REQUIRED"));
    verifyNoInteractions(demands, authorization, json);
  }

  @Test
  void deletedJobRejectsBeforeObjectAuthorization() {
    when(persistence.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(snapshot("deleted")));
    when(demands.requireJdVersions(List.of("deleted"))).thenReturn(Map.of("deleted", context("deleted", true, true)));
    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(List.of("deleted")))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("JD_DELETED"));
    verifyNoInteractions(authorization, json);
  }

  @Test
  void rejectsInvalidOrOversizedBatchesAndSkipsEmptyCollections() {
    adapter.requireCurrentFactsReadAccessBatch(List.of());
    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(List.of(" "))).isInstanceOf(IllegalArgumentException.class);
    var nullId = new ArrayList<String>();
    nullId.add(null);
    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(nullId)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.requireCurrentFactsReadAccessBatch(IntStream.range(0, 201).mapToObj(index -> "id").toList()))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(persistence, demands, authorization, json);
  }

  private static JdFactSnapshotEntity snapshot(String id) {
    var snapshot = new JdFactSnapshotEntity();
    snapshot.setJdVersionId(id);
    return snapshot;
  }

  private static JdVersionSnapshot context(String id, boolean publicVisible, boolean deleted) {
    var context = new JdVersionSnapshot(id, "jd", null, "INTERNAL", "owner", "department", Set.of("collaborator"));
    context.setPublicVisible(publicVisible);
    context.setDeleted(deleted);
    return context;
  }
}
