package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.application.assembler.JdParsingAssembler;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class JdFactAccessTest {

  private static final String ACTOR = "actor";
  private static final String JD_VERSION = "jd-version";
  private static final String SNAPSHOT = "snapshot";

  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final DemandQueryService demands = mock(DemandQueryService.class);
  private final JdParsingPersistenceService persistence = mock(JdParsingPersistenceService.class);

  @Test
  void legacyPublicFlagDoesNotExpandCurrentFactsReadScope() {
    when(persistence.findCurrent(JD_VERSION)).thenReturn(snapshot());
    when(demands.requireJdVersionForNewUse(JD_VERSION)).thenReturn(context(true, false));
    when(authorization.currentActor()).thenReturn(actor());

    var result = adapter().currentFacts(JD_VERSION);

    assertThat(result.getSnapshotId()).isEqualTo(SNAPSHOT);
    verify(authorization)
        .requireAccess(
            "demand.read",
            new AuthorizationTarget(
                "JD_VERSION", JD_VERSION, "owner", "department", Set.of("collaborator")));
  }

  @Test
  void currentFactsReadAccessUsesNonLockingDemandQuery() {
    when(persistence.findCurrent(JD_VERSION)).thenReturn(snapshot());
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(context(true, false));
    when(authorization.currentActor()).thenReturn(actor());

    adapter().requireCurrentFactsReadAccess(JD_VERSION);

    verify(demands).requireJdVersion(JD_VERSION);
    verify(demands, never()).requireJdVersionForNewUse(any());
    verify(authorization)
        .requireAccess(
            "demand.read",
            new AuthorizationTarget(
                "JD_VERSION", JD_VERSION, "owner", "department", Set.of("collaborator")));
  }

  @Test
  void currentFactsRejectsUnauthorizedPrivateJd() {
    when(persistence.findCurrent(JD_VERSION)).thenReturn(snapshot());
    when(demands.requireJdVersionForNewUse(JD_VERSION)).thenReturn(context(false, false));
    AuthorizationTarget target =
        new AuthorizationTarget(
            "JD_VERSION", JD_VERSION, "owner", "department", Set.of("collaborator"));
    doThrow(new PlatformException(HttpStatus.FORBIDDEN, "DATA_SCOPE_FORBIDDEN", "无权访问", false))
        .when(authorization)
        .requireAccess("demand.read", target);

    assertThatThrownBy(() -> adapter().currentFacts(JD_VERSION))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
  }

  @Test
  void currentFactsRejectsDeletedJdBeforeStartingANewMatch() {
    when(persistence.findCurrent(JD_VERSION)).thenReturn(snapshot());
    when(demands.requireJdVersionForNewUse(JD_VERSION)).thenReturn(context(true, true));

    assertThatThrownBy(() -> adapter().currentFacts(JD_VERSION))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("JD_DELETED"));
    verify(authorization, never()).requireAccess(eq("demand.read"), any());
  }

  @Test
  void historicalWorkerCanReadFactsBySnapshotIdAfterJdDeletion() {
    when(persistence.findSnapshot(SNAPSHOT)).thenReturn(snapshot());

    var result = adapter().factsBySnapshotId(SNAPSHOT);

    assertThat(result.getJdVersionId()).isEqualTo(JD_VERSION);
    verifyNoInteractions(demands, authorization);
  }

  @ParameterizedTest
  @ValueSource(strings = {"72.700000000000001", "3.000000000000000001", "1e-400", "72.70", "0", "80", "null"})
  void currentAndHistoricalFactsKeepStoredDecimalValues(String number) {
    var snapshot = decimalSnapshot(number);
    String original = snapshot.getFactsJson();
    when(persistence.findCurrent(JD_VERSION)).thenReturn(snapshot);
    when(persistence.findSnapshot(SNAPSHOT)).thenReturn(snapshot);
    when(demands.requireJdVersionForNewUse(JD_VERSION)).thenReturn(context(false, false));

    for (var result : List.of(adapter().currentFacts(JD_VERSION), adapter().factsBySnapshotId(SNAPSHOT))) {
      for (BigDecimal actual : new BigDecimal[] {
          result.getProfessional().minimumYears(), result.getProfessional().preferredYears(),
          result.getFulfillment().salaryMin(), result.getFulfillment().salaryMax()}) {
        assertDecimal(actual, number);
      }
      assertThat(result.getSnapshotId()).isEqualTo(SNAPSHOT);
      assertThat(result.getJdVersionId()).isEqualTo(JD_VERSION);
      assertThat(result.getSnapshotVersion()).isEqualTo(3);
      assertThat(result.getControlled().ageMin()).isEqualTo(16);
      assertThat(result.getControlled().ageMax()).isEqualTo(80);
    }
    assertThat(snapshot.getFactsJson()).isEqualTo(original);
    verify(authorization).requireAccess("demand.read",
        new AuthorizationTarget("JD_VERSION", JD_VERSION, "owner", "department", Set.of("collaborator")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"72.700000000000001", "3.000000000000000001", "1e-400", "72.70", "0", "80", "null"})
  void factsResponseKeepsStoredDecimalValuesWhenSerialized(String number) {
    var snapshot = decimalSnapshot(number);
    String original = snapshot.getFactsJson();
    var json = new ObjectMapper();
    var assembler = new JdParsingAssembler();
    ReflectionTestUtils.setField(assembler, "objectMapper", json);

    var response = assembler.toResponse(snapshot);

    for (String section : List.of("professional", "fulfillmentConditions")) {
      String serialized = json.writeValueAsString(response.getFacts().get(section));
      Map<String, BigDecimal> numbers = json.readValue(serialized, new TypeReference<Map<String, BigDecimal>>() {});
      assertThat(numbers).hasSize(2);
      numbers.values().forEach(value -> assertDecimal(value, number));
    }
    assertThat(response.getFactSnapshotId()).isEqualTo(SNAPSHOT);
    assertThat(response.getJdVersionId()).isEqualTo(JD_VERSION);
    assertThat(response.getSnapshotVersion()).isEqualTo(3);
    assertThat(response.getSourceType()).isEqualTo(snapshot.getSourceType());
    assertThat(response.getSchemaVersion()).isEqualTo(snapshot.getSchemaVersion());
    assertThat(response.getCreateTime()).isEqualTo(snapshot.getCreateTime());
    assertThat(snapshot.getFactsJson()).isEqualTo(original);
  }

  private static void assertDecimal(BigDecimal actual, String number) {
    if ("null".equals(number)) assertThat(actual).isNull();
    else assertThat(actual).isEqualByComparingTo(new BigDecimal(number));
  }

  private static JdFactSnapshotEntity decimalSnapshot(String number) {
    var snapshot = snapshot();
    snapshot.setSnapshotVersion(3);
    snapshot.setSourceType("MODEL");
    snapshot.setSchemaVersion("jd-facts-test");
    snapshot.setCreateTime(Instant.parse("2026-10-02T00:00:00Z"));
    snapshot.setFactsJson("""
        {"professional":{"minimumYears":%s,"preferredYears":%s},
         "controlledConditions":{"ageMin":16,"ageMax":80},
         "fulfillmentConditions":{"salaryMin":%s,"salaryMax":%s}}
        """.formatted(number, number, number, number));
    return snapshot;
  }

  private JdFactQueryAdapter adapter() {
    var adapter = new JdFactQueryAdapter();
    ReflectionTestUtils.setField(adapter, "authorization", authorization);
    ReflectionTestUtils.setField(adapter, "demandQuery", demands);
    ReflectionTestUtils.setField(adapter, "persistence", persistence);
    ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
    return adapter;
  }

  private static JdFactSnapshotEntity snapshot() {
    var snapshot = new JdFactSnapshotEntity();
    snapshot.setId(SNAPSHOT);
    snapshot.setJdVersionId(JD_VERSION);
    snapshot.setSnapshotVersion(1);
    snapshot.setFactsJson(
        "{\"professional\":{},\"controlledConditions\":{},\"fulfillmentConditions\":{},\"evidence\":[]}");
    return snapshot;
  }

  private static JdVersionSnapshot context(boolean publicVisible, boolean deleted) {
    var context =
        new JdVersionSnapshot(
            JD_VERSION,
            "jd",
            null,
            "INTERNAL",
            "owner",
            "department",
            Set.of("collaborator"));
    context.setPublicVisible(publicVisible);
    context.setDeleted(deleted);
    return context;
  }

  private static CurrentActorSnapshot actor() {
    var actor = new CurrentActorSnapshot();
    actor.setUserId(ACTOR);
    return actor;
  }
}
