package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.domain.dto.UpdateMatchPlanDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterionPlan;
import com.ezaigc.hr.intelligence.matching.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlan;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.service.ScoringConfigurationValidator;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingConfigurationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingIdempotencyPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.PersonalPreferencePersistenceService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class MatchPlanUpdateIdempotencyTest {
  private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

  @Test
  @ResourceLock(Resources.LOCALE)
  void persistsTheCanonicalOperatorUnderTurkishLocale() {
    withDefaultLocale(Locale.forLanguageTag("tr-TR"), () -> {
      Fixture f = new Fixture();
      f.manager.updateMatchPlan("version-1", operatorCommand(" in "), "operator-key");
      verify(f.plans).insertCriterion(anyString(), eq("criteria-1"), eq("LANGUAGE"), eq("PREFER"),
          eq("IN"), eq("{\"values\":[\"英语\"]}"), eq(5), eq("岗位要求"), eq("EXPLICIT"), eq(true), eq(1), eq(NOW));
    });
  }

  @Test
  @ResourceLock(Resources.LOCALE)
  void replayWithOperatorAliasKeepsItsFingerprintAcrossDefaultLocales() {
    withDefaultLocale(Locale.ENGLISH, () -> {
      Fixture f = new Fixture();
      f.manager.updateMatchPlan("version-1", operatorCommand("IN"), "operator-key");
      withDefaultLocale(Locale.forLanguageTag("tr-TR"), () ->
          f.manager.updateMatchPlan("version-1", operatorCommand(" in "), "operator-key"));
      verify(f.versions, times(1)).updateMatchPlanVersion(anyString(), anyBoolean(), anyBoolean(),
          anyBoolean(), anyBoolean(), anyBoolean(), anyLong(), anyString(), any());
      verify(f.audit, times(1)).insertAudit(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    });
  }

  private static UpdateMatchPlanDTO operatorCommand(String operator) {
    return withCriterion(criterion(ControlledCriterionCode.LANGUAGE, ControlledCriterionLevel.PREFER,
        operator, "{\"values\":[\"英语\"]}", 5, "岗位要求", "EXPLICIT", true));
  }

  private static void withDefaultLocale(Locale locale, Runnable action) {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(locale);
      action.run();
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @Test
  void sameKeyAndBodyReplayWithoutUpdatingOrAuditingAgain() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", command(), "retry-key");
    f.version.setVersion(8);
    f.manager.updateMatchPlan("version-1", command(), "retry-key");

    verify(f.versions, times(1)).updateMatchPlanVersion(anyString(), anyBoolean(), anyBoolean(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyLong(), anyString(), any());
    verify(f.criteria, times(1)).deleteCriteria("criteria-1");
    verify(f.audit, times(1)).insertAudit(
        anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void sameKeyWithDifferentBodyExpectedVersionOrSourceIsRejected() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", command(), "retry-key");
    assertConflict(() -> f.manager.updateMatchPlan("version-1",
        new UpdateMatchPlanDTO("另一个方案", true, true, true, true, true, List.of(), 7), "retry-key"),
        "IDEMPOTENCY_KEY_REUSED");
    assertConflict(() -> f.manager.updateMatchPlan("version-1",
        new UpdateMatchPlanDTO("方案", true, true, true, true, true, List.of(), 6), "retry-key"),
        "IDEMPOTENCY_KEY_REUSED");
    when(f.versions.findMatchPlanVersion("version-2")).thenReturn(f.version("version-2"));
    assertConflict(() -> f.manager.updateMatchPlan("version-2", command(), "retry-key"),
        "IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void validatesEveryDimensionBeforeReplayAndFingerprintsEveryValidCriterionField() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", withCriterion(criterion()), "retry-key");
    List<UpdateMatchPlanDTO> disabledDimensions = List.of(
        new UpdateMatchPlanDTO("方案", false, true, true, true, true, List.of(criterion()), 7),
        new UpdateMatchPlanDTO("方案", true, false, true, true, true, List.of(criterion()), 7),
        new UpdateMatchPlanDTO("方案", true, true, false, true, true, List.of(criterion()), 7),
        new UpdateMatchPlanDTO("方案", true, true, true, false, true, List.of(criterion()), 7),
        new UpdateMatchPlanDTO("方案", true, true, true, true, false, List.of(criterion()), 7));
    assertThat(disabledDimensions).allSatisfy(variant -> assertThatThrownBy(
        () -> f.manager.updateMatchPlan("version-1", variant, "retry-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MATCH_PLAN_INVALID")));

    List<UpdateMatchPlanDTO> variants = List.of(
        new UpdateMatchPlanDTO("别名", true, true, true, true, true, List.of(criterion()), 7),
        withCriterion(criterion(ControlledCriterionCode.GENDER, ControlledCriterionLevel.PREFER,
            "EQ", "{\"value\":18}", 5, "原因", "EXPLICIT", true)),
        withCriterion(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.DISPLAY_ONLY,
            "EQ", "{\"value\":18}", 0, "原因", "EXPLICIT", true)),
        withCriterion(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER,
            "IN", "{\"value\":18}", 5, "原因", "EXPLICIT", true)),
        withCriterion(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER,
            "EQ", "{\"value\":19}", 5, "原因", "EXPLICIT", true)),
        withCriterion(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER,
            "EQ", "{\"value\":18}", 10, "原因", "EXPLICIT", true)),
        withCriterion(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER,
            "EQ", "{\"value\":18}", 5, "其他原因", "EXPLICIT", true)),
        withCriterion(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER,
            "EQ", "{\"value\":18}", 5, "原因", "VERIFIED", true)),
        withCriterion(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER,
            "EQ", "{\"value\":18}", 5, "原因", "EXPLICIT", false)));

    assertThat(variants).allSatisfy(variant -> assertConflict(
        () -> f.manager.updateMatchPlan("version-1", variant, "retry-key"),
        "IDEMPOTENCY_KEY_REUSED"));
    f.manager.updateMatchPlan("version-1", new UpdateMatchPlanDTO(
        " 方案 ", true, true, true, true, true, List.of(criterion()), 7), "retry-key");
  }

  @Test
  void fingerprintDoesNotCollideWhenNullCharactersMoveAcrossAdjacentFields() {
    Fixture f = new Fixture();
    UpdateMatchPlanDTO first = withCriterion(criterion(ControlledCriterionCode.AGE,
        ControlledCriterionLevel.PREFER, "EQ", "{\"value\":18}", 5, "a\0b", "c", true));
    UpdateMatchPlanDTO second = withCriterion(criterion(ControlledCriterionCode.AGE,
        ControlledCriterionLevel.PREFER, "EQ", "{\"value\":18}", 5, "a", "b\0c", true));

    f.manager.updateMatchPlan("version-1", first, "retry-key");
    assertConflict(() -> f.manager.updateMatchPlan("version-1", second, "retry-key"),
        "IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void differentKeyWithOldVersionStillConflicts() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", command(), "first-key");
    assertConflict(() -> f.manager.updateMatchPlan("version-1", command(), "new-key"),
        "MATCH_PLAN_VERSION_CONFLICT");
  }

  @Test
  void differentKeyWithCurrentVersionCreatesANewIntent() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", command(), "first-key");
    UpdateMatchPlanDTO current = new UpdateMatchPlanDTO(
        "新方案", true, true, true, true, true, List.of(), 8);

    f.manager.updateMatchPlan("version-1", current, "current-key");

    assertThat(f.version.getVersion()).isEqualTo(9);
    verify(f.audit, times(2)).insertAudit(
        anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
  }

  @Test
  void idempotencyKeyIsIsolatedByActor() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", command(), "shared-key");
    f.actor.setUserId("another-admin");
    UpdateMatchPlanDTO current = new UpdateMatchPlanDTO(
        "另一管理员方案", true, true, true, true, true, List.of(), 8);

    f.manager.updateMatchPlan("version-1", current, "shared-key");

    assertThat(f.saved).hasSize(2);
  }

  @Test
  void revokedPermissionRejectsReplay() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", command(), "retry-key");
    f.actor.setSuperAdmin(false);
    assertConflict(() -> f.manager.updateMatchPlan("version-1", command(), "retry-key"),
        "SUPER_ADMIN_REQUIRED");
  }

  @Test
  void revokedObjectVisibilityRejectsReplay() {
    Fixture f = new Fixture();
    f.version.setJdVersionId("jd-version-1");
    when(f.demandQuery.requireJdVersion("jd-version-1"))
        .thenReturn(new JdVersionSnapshot())
        .thenThrow(new PlatformException(
            HttpStatus.FORBIDDEN, "DATA_SCOPE_FORBIDDEN", "无权访问", false));
    f.manager.updateMatchPlan("version-1", command(), "retry-key");

    assertConflict(() -> f.manager.updateMatchPlan("version-1", command(), "retry-key"),
        "DATA_SCOPE_FORBIDDEN");
  }

  @Test
  void activatedDraftCanReplayButNewIntentRemainsImmutable() {
    Fixture f = new Fixture();
    f.manager.updateMatchPlan("version-1", command(), "retry-key");
    f.version.setStatus("ACTIVE");
    f.manager.updateMatchPlan("version-1", command(), "retry-key");
    assertConflict(() -> f.manager.updateMatchPlan("version-1", command(), "new-key"),
        "MATCH_PLAN_IMMUTABLE");
    verify(f.versions, times(1)).updateMatchPlanVersion(anyString(), anyBoolean(), anyBoolean(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyLong(), anyString(), any());
  }

  @Test
  void newIntentAgainstActivatedVersionDoesNotWrite() {
    Fixture f = new Fixture();
    f.version.setStatus("ACTIVE");
    assertConflict(() -> f.manager.updateMatchPlan("version-1", command(), "new-key"),
        "MATCH_PLAN_IMMUTABLE");
    verify(f.versions, never()).updateMatchPlanVersion(anyString(), anyBoolean(), anyBoolean(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyLong(), anyString(), any());
  }

  private static UpdateMatchPlanDTO command() {
    return new UpdateMatchPlanDTO("方案", true, true, true, true, true, List.of(), 7);
  }

  @Test
  void unsuccessfulDraftDeletionRejectsBeforeCleaningRulesOrAuditing() {
    Fixture f = new Fixture();
    when(f.versions.deleteMatchPlanVersion("version-1", 7)).thenReturn(0);

    assertConflict(() -> f.manager.deleteMatchPlan("version-1", new VersionReasonDTO(7, "清理草稿")),
        "MATCH_PLAN_VERSION_CONFLICT");

    verifyNoInteractions(f.criteria, f.criterionPlans, f.audit);
    verify(f.plans, never()).deleteMatchPlan(anyString());
    verify(f.plans, never()).updateMatchPlanCurrentVersion(anyString(), anyInt(), anyString(), any());
  }

  @Test
  void staleDeleteForwardsTheRequestedVersionAndPreservesTheNewerDraft() {
    Fixture f = new Fixture();
    when(f.versions.deleteMatchPlanVersion("version-1", 7)).thenReturn(1);

    assertConflict(() -> f.manager.deleteMatchPlan("version-1", new VersionReasonDTO(6, "旧页面删除")),
        "MATCH_PLAN_VERSION_CONFLICT");

    verify(f.versions).deleteMatchPlanVersion("version-1", 6);
    verifyNoInteractions(f.criteria, f.criterionPlans, f.audit);
    verify(f.plans, never()).deleteMatchPlan(anyString());
    verify(f.plans, never()).updateMatchPlanCurrentVersion(anyString(), anyInt(), anyString(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACTIVE", "SUPERSEDED"})
  void activatedScoringVersionsRemainImmutableOnDelete(String status) {
    Fixture f = new Fixture();
    f.version.setStatus(status);

    assertConflict(() -> f.manager.deleteMatchPlan("version-1", new VersionReasonDTO(7, "清理草稿")),
        "MATCH_PLAN_IMMUTABLE");

    verify(f.versions, never()).deleteMatchPlanVersion(anyString(), anyLong());
    verifyNoInteractions(f.preferences, f.criteria, f.criterionPlans, f.audit);
  }

  @Test
  void referencedDraftStillRejectsBeforeDeletingAnything() {
    Fixture f = new Fixture();
    when(f.preferences.countMatchPlanReferences("version-1")).thenReturn(1);

    assertConflict(() -> f.manager.deleteMatchPlan("version-1", new VersionReasonDTO(7, "清理草稿")),
        "MATCH_PLAN_REFERENCED");

    verify(f.versions, never()).deleteMatchPlanVersion(anyString(), anyLong());
    verifyNoInteractions(f.criteria, f.criterionPlans, f.audit);
  }

  @Test
  void nonAdminCannotDeleteAScoringDraft() {
    Fixture f = new Fixture();
    f.actor.setSuperAdmin(false);

    assertConflict(() -> f.manager.deleteMatchPlan("version-1", new VersionReasonDTO(7, "清理草稿")),
        "SUPER_ADMIN_REQUIRED");

    verifyNoInteractions(f.versions, f.plans, f.preferences, f.criteria, f.criterionPlans, f.audit);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void successfulDraftDeletionRemovesVersionBeforeRulesAndMaintainsParent(int remainingVersion) {
    Fixture f = new Fixture();
    when(f.versions.deleteMatchPlanVersion("version-1", 7)).thenReturn(1);
    when(f.plans.findMaxRemainingMatchPlanVersionNo("plan-1")).thenReturn(remainingVersion);

    f.manager.deleteMatchPlan("version-1", new VersionReasonDTO(7, "清理草稿"));

    var order = inOrder(f.versions, f.criteria, f.criterionPlans, f.plans, f.audit);
    order.verify(f.versions).deleteMatchPlanVersion("version-1", 7);
    order.verify(f.criteria).deleteCriteria("criteria-1");
    order.verify(f.criterionPlans).deleteCriterionPlan("criteria-1");
    order.verify(f.plans).findMaxRemainingMatchPlanVersionNo("plan-1");
    if (remainingVersion == 0) {
      order.verify(f.plans).deleteMatchPlan("plan-1");
    } else if (remainingVersion != 1) {
      order.verify(f.plans).updateMatchPlanCurrentVersion("plan-1", remainingVersion, "admin", NOW);
    } else {
      verify(f.plans, never()).deleteMatchPlan(anyString());
      verify(f.plans, never()).updateMatchPlanCurrentVersion(anyString(), anyInt(), anyString(), any());
    }
    order.verify(f.audit).insertAudit("audit-id", "MATCH_PLAN_DELETED", "admin", "MATCH_PLAN_VERSION",
        "version-1", "清理草稿", NOW);
  }

  private static ControlledCriterionDraft criterion() {
    return criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER,
        " eq ", "{\"value\":18}", 5, " 原因 ", " EXPLICIT ", true);
  }

  private static ControlledCriterionDraft criterion(ControlledCriterionCode code,
      ControlledCriterionLevel level, String operator, String expected, int bonus, String reason,
      String evidence, boolean selectable) {
    return new ControlledCriterionDraft(
        code, level, operator, expected, bonus, reason, evidence, selectable);
  }

  private static UpdateMatchPlanDTO withCriterion(ControlledCriterionDraft criterion) {
    return new UpdateMatchPlanDTO("方案", true, true, true, true, true, List.of(criterion), 7);
  }

  private static void assertConflict(Runnable call, String code) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(
        PlatformException.class, exception -> assertThat(exception.code()).isEqualTo(code));
  }

  private static final class Fixture {
    final MatchingConfigurationApplicationManager manager = new MatchingConfigurationApplicationManager();
    final AuthorizationService authorization = mock(AuthorizationService.class);
    final MatchPlanVersionPersistenceService versions = mock(MatchPlanVersionPersistenceService.class);
    final MatchingConfigurationPersistenceService plans = mock(MatchingConfigurationPersistenceService.class);
    final ControlledCriterionPlanPersistenceService criterionPlans = mock(ControlledCriterionPlanPersistenceService.class);
    final ControlledCriterionPersistenceService criteria = mock(ControlledCriterionPersistenceService.class);
    final MatchingAuditEventPersistenceService audit = mock(MatchingAuditEventPersistenceService.class);
    final MatchingIdempotencyPersistenceService idempotency = mock(MatchingIdempotencyPersistenceService.class);
    final PersonalPreferencePersistenceService preferences = mock(PersonalPreferencePersistenceService.class);
    final DemandQueryService demandQuery = mock(DemandQueryService.class);
    final CurrentActorSnapshot actor = new CurrentActorSnapshot();
    final MatchPlanVersion version = version("version-1");
    final Map<String, IdempotencyRecord> saved = new ConcurrentHashMap<>();

    Fixture() {
      actor.setUserId("admin");
      actor.setSuperAdmin(true);
      when(authorization.currentActor()).thenReturn(actor);
      when(versions.findMatchPlanVersion("version-1")).thenReturn(version);
      when(versions.updateMatchPlanVersion(anyString(), anyBoolean(), anyBoolean(), anyBoolean(),
          anyBoolean(), anyBoolean(), anyLong(), anyString(), any()))
          .thenAnswer(invocation -> {
            long expectedVersion = invocation.getArgument(6);
            if (expectedVersion != version.getVersion()) return 0;
            version.setVersion(expectedVersion + 1);
            return 1;
          });
      when(plans.findMatchPlan("plan-1")).thenReturn(new MatchPlan("plan-1", null, "方案", 1));
      when(criterionPlans.findCriterionPlan("criteria-1"))
          .thenReturn(new ControlledCriterionPlan("criteria-1", null, 1, 0));
      when(criteria.findCriteria("criteria-1")).thenReturn(List.of());
      when(plans.updateMatchPlanName(anyString(), anyString(), anyString(), any())).thenReturn(1);
      when(criterionPlans.updateCriterionPlan(anyString(), anyInt(), anyString(), any())).thenReturn(1);
      when(idempotency.findIdempotency(anyString(), anyString())).thenAnswer(invocation -> {
        return saved.get(invocation.getArgument(0) + "\0" + invocation.getArgument(1));
      });
      when(idempotency.insertIdempotency(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
          .thenAnswer(invocation -> {
            saved.put(invocation.getArgument(0) + "\0" + invocation.getArgument(1),
                new IdempotencyRecord(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4)));
            return 1;
          });
      ReflectionTestUtils.setField(manager, "authorization", authorization);
      ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", versions);
      ReflectionTestUtils.setField(manager, "persistence", plans);
      ReflectionTestUtils.setField(manager, "controlledCriterionPlanPersistenceService", criterionPlans);
      ReflectionTestUtils.setField(manager, "controlledCriterionPersistenceService", criteria);
      ReflectionTestUtils.setField(manager, "matchingAuditEventPersistenceService", audit);
      ReflectionTestUtils.setField(manager, "matchingIdempotencyPersistenceService", idempotency);
      ReflectionTestUtils.setField(manager, "personalPreferencePersistenceService", preferences);
      ReflectionTestUtils.setField(manager, "demandQuery", demandQuery);
      ReflectionTestUtils.setField(manager, "validator", new ScoringConfigurationValidator());
      ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
      ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> "audit-id");
    }

    static MatchPlanVersion version(String id) {
      MatchPlanVersion result = new MatchPlanVersion();
      result.setId(id);
      result.setPlanId("plan-1");
      result.setControlledCriterionPlanVersionId("criteria-1");
      result.setStatus("DRAFT");
      result.setVersion(7);
      return result;
    }
  }
}
