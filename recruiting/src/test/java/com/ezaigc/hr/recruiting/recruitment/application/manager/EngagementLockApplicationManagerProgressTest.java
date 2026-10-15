package com.ezaigc.hr.recruiting.recruitment.application.manager;

import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockPolicy;
import com.ezaigc.hr.recruiting.recruitment.enums.EngagementLockStatus;
import com.ezaigc.hr.recruiting.recruitment.enums.LockRenewingProgressType;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockPolicyPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ResourceLock(Resources.LOCALE)
class EngagementLockApplicationManagerProgressTest {

  private static final Instant NOW = Instant.parse("2026-10-03T01:00:00Z");
  private static final Instant RENEWED_EXPIRY = NOW.plus(7, ChronoUnit.DAYS);
  private static final AuthorizationTarget TARGET =
      new AuthorizationTarget("ENGAGEMENT_LOCK", "lock-1", "owner-1", "department-1", Set.of());

  private final EngagementLockPersistenceService persistence =
      mock(EngagementLockPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final LockHistoryPersistenceService histories = mock(LockHistoryPersistenceService.class);
  private final LockPolicyPersistenceService policies = mock(LockPolicyPersistenceService.class);
  private final EngagementLockApplicationManager manager = manager();

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("localesAndProgressTypes")
  void renewsAndAuditsNormalizedProgressIndependentlyOfLocale(
      String languageTag, LockRenewingProgressType progress) {
    withLocale(languageTag, () -> {
      String input = " \t" + progress.getCode().toLowerCase(Locale.ROOT) + "\u2003";
      assertThatCode(() -> manager.recordProgress("candidate-1", input, " \t有效进展\t "))
          .doesNotThrowAnyException();
      verifyRenewalAndHistory(progress.getCode());
    });
  }

  @ParameterizedTest
  @EnumSource(LockRenewingProgressType.class)
  void preservesUppercaseBusinessCallerCodesInTurkishLocale(LockRenewingProgressType progress) {
    withLocale("tr-TR", () -> {
      manager.recordProgress("candidate-1", progress.getCode(), "有效进展");
      verifyRenewalAndHistory(progress.getCode());
    });
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" \t ", "NOTE_ADDED", "candidate_viewed"})
  void rejectsNonRenewingProgressBeforeAuthorizationOrPersistence(String progress) {
    withLocale("tr-TR", () -> {
      assertThatThrownBy(() -> manager.recordProgress("candidate-1", progress, "有效进展"))
          .isInstanceOfSatisfying(PlatformException.class, error -> {
            assertThat(error.code()).isEqualTo("ENGAGEMENT_PROGRESS_NOT_RENEWING");
            assertThat(error.status().value()).isEqualTo(422);
            assertThat(error.recoverable()).isFalse();
          });
      verifyNoInteractions(persistence, authorization, policies, histories);
    });
  }

  @Test
  void requiresAnActiveLockBeforeRenewing() {
    when(persistence.findByCandidate("candidate-1")).thenReturn(null);
    assertThatThrownBy(() -> manager.recordProgress("candidate-1", "CANDIDATE_REPLY", "有效进展"))
        .isInstanceOfSatisfying(PlatformException.class, error ->
            assertThat(error.code()).isEqualTo("ENGAGEMENT_LOCK_REQUIRED"));
    verify(persistence).findByCandidate("candidate-1");
    verifyNoMoreInteractions(persistence);
    verifyNoInteractions(authorization, policies, histories);
  }

  @Test
  void preservesObjectAuthorizationBeforeWrites() {
    PlatformException denied =
        new PlatformException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权限", false);
    doThrow(denied).when(authorization).requireAccess("recruitment.manage", TARGET);
    assertThatThrownBy(() -> manager.recordProgress("candidate-1", "CANDIDATE_REPLY", "有效进展"))
        .isSameAs(denied);
    verify(persistence).findByCandidate("candidate-1");
    verifyNoMoreInteractions(persistence);
    verifyNoInteractions(policies, histories);
  }

  @Test
  void rejectsLostVersionWithoutRecordingSuccessfulRenewal() {
    when(persistence.renew("lock-1", "CANDIDATE_REPLY", RENEWED_EXPIRY, 3, "actor-1", NOW))
        .thenReturn(0);
    assertThatThrownBy(() -> manager.recordProgress("candidate-1", "CANDIDATE_REPLY", "有效进展"))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("ENGAGEMENT_LOCK_STATE_CHANGED");
          assertThat(error.status().value()).isEqualTo(409);
          assertThat(error.recoverable()).isTrue();
        });
    verify(authorization).requireAccess("recruitment.manage", TARGET);
    verify(persistence).renew("lock-1", "CANDIDATE_REPLY", RENEWED_EXPIRY, 3, "actor-1", NOW);
    verifyNoInteractions(histories);
  }

  private void verifyRenewalAndHistory(String code) {
    InOrder writes = inOrder(authorization, persistence, histories);
    writes.verify(persistence).findByCandidate("candidate-1");
    writes.verify(authorization).requireAccess("recruitment.manage", TARGET);
    writes.verify(persistence).renew("lock-1", code, RENEWED_EXPIRY, 3, "actor-1", NOW);
    writes.verify(histories).insertHistory(
        "history-1", "lock-1", "candidate-1", "RENEW", "owner-1", "owner-1",
        code, "有效进展", "actor-1", NOW);
    verifyNoMoreInteractions(persistence, histories);
  }

  private EngagementLockApplicationManager manager() {
    EngagementLock lock = new EngagementLock();
    lock.setId("lock-1");
    lock.setCandidateId("candidate-1");
    lock.setOwnerUserId("owner-1");
    lock.setOwnerDepartmentId("department-1");
    lock.setStatus(EngagementLockStatus.ACTIVE.getCode());
    lock.setExpiresAt(NOW.plusSeconds(60));
    lock.setVersion(3);
    when(persistence.findByCandidate("candidate-1")).thenReturn(lock);
    when(persistence.renew(anyString(), anyString(), any(), anyLong(), anyString(), eq(NOW)))
        .thenReturn(1);
    when(histories.insertHistory(anyString(), anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyString(), anyString(), eq(NOW))).thenReturn(1);
    LockPolicy policy = new LockPolicy();
    policy.setDurationDays(7);
    when(policies.policy()).thenReturn(policy);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("actor-1");
    when(authorization.currentActor()).thenReturn(actor);
    EngagementLockApplicationManager result = new EngagementLockApplicationManager();
    ReflectionTestUtils.setField(result, "candidateCommands", mock(CompanyBlacklistQueryService.class));
    ReflectionTestUtils.setField(result, "persistence", persistence);
    ReflectionTestUtils.setField(result, "authorization", authorization);
    ReflectionTestUtils.setField(result, "lockHistoryPersistenceService", histories);
    ReflectionTestUtils.setField(result, "lockPolicyPersistenceService", policies);
    ReflectionTestUtils.setField(result, "ids", (PlatformIdGenerator) () -> "history-1");
    ReflectionTestUtils.setField(result, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    return result;
  }

  private static Stream<Arguments> localesAndProgressTypes() {
    return Stream.of("en-US", "tr-TR").flatMap(languageTag ->
        Arrays.stream(LockRenewingProgressType.values())
            .map(progress -> Arguments.of(languageTag, progress)));
  }

  private static void withLocale(String languageTag, Runnable action) {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag(languageTag));
      action.run();
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }
}
