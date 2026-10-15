package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateWeightProfileDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.ProfessionalWeights;
import com.ezaigc.hr.intelligence.matching.domain.dto.UpdateWeightProfileDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.VersionReasonDTO;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfile;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.service.WeightProfilePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingConfigurationPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;

class WeightProfileAuthorizationTest {
  @ParameterizedTest
  @ValueSource(strings = {"create", "update"})
  void rejectsMutableNegativeWeightsBeforeWriting(String action) {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var versions = mock(WeightProfileVersionPersistenceService.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", versions);
    when(authorization.currentActor()).thenReturn(actor("recruiter", Set.of(), false));
    WeightProfile profile = profile("recruiter");
    profile.setStatus("ACTIVE");
    when(profiles.findWeightProfile("profile")).thenReturn(profile);
    ProfessionalWeights weights = ProfessionalWeights.systemDefault();
    weights.setRelatedYears(-1);
    weights.setSkills(42);

    assertThatThrownBy(() -> {
      if ("create".equals(action)) {
        manager.createWeightProfile(new CreateWeightProfileDTO("个人方案", weights), "weights-1");
      } else {
        manager.updateWeightProfile("profile", new UpdateWeightProfileDTO("个人方案", weights, 1));
      }
    }).hasMessageContaining("五维权重");

    verifyNoInteractions(versions);
    verify(profiles, never()).insertWeightProfile(anyString(), anyString(), anyString(), any());
    verify(profiles, never()).updateWeightProfile(
        anyString(), anyString(), anyString(), anyInt(), anyLong(), any());
  }

  @Test
  void managerListLoadsOnlyOwnCurrentVersionsInOneBatch() {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var versions = mock(WeightProfileVersionPersistenceService.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
    when(authorization.currentActor()).thenReturn(actor("manager", Set.of("recruiter"), false));
    WeightProfile mine = profile("manager");
    mine.setId("mine"); mine.setUpdateTime(Instant.parse("2026-09-12T10:00:00Z"));
    when(profiles.findWeightProfiles(eq("manager"), eq(false), eq(Set.of()),
        any(), any(), anyInt())).thenReturn(List.of(mine));
    when(versions.findCurrentWeightProfileVersions(Set.of("mine")))
        .thenReturn(List.of(version("mine")));

    manager.listWeightProfiles(false, null, 10);

    verify(versions).findCurrentWeightProfileVersions(Set.of("mine"));
    verify(versions, never()).findWeightProfileVersionByNumber(anyString(), anyInt());
  }

  @Test
  void countUsesTheSameOwnerAndManagementScopeAsTheList() {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    var actor = actor("manager", Set.of("recruiter"), false);
    when(authorization.currentActor()).thenReturn(actor);
    when(profiles.countWeightProfiles(
        "manager", false, actor.getOperationalManagedUserIds())).thenReturn(43L);

    var result = manager.countWeightProfiles(false);

    assertThat(result.getCount()).isEqualTo(43);
    assertThat(result.isHasMore()).isFalse();
    verify(authorization).requirePermission("matching.run");
    verify(profiles).countWeightProfiles(
        "manager", false, actor.getOperationalManagedUserIds());
  }

  @Test
  void managerCannotReadOrEditSubordinatePersonalProfile() {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var versions = mock(WeightProfileVersionPersistenceService.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", versions);
    when(authorization.currentActor()).thenReturn(actor("manager", Set.of("recruiter"), false));
    WeightProfile profile = profile("recruiter");
    when(profiles.findWeightProfile("profile")).thenReturn(profile);

    assertThatThrownBy(() -> manager.getWeightProfile("profile"))
        .hasMessageContaining("无权查看该个人配置");
    assertThatThrownBy(() -> manager.updateWeightProfile("profile", null))
        .hasMessageContaining("个人配置只允许本人维护");
  }

  @Test
  void companyWideActorCanReadAnyProfileButCannotEditIt() {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var versions = mock(WeightProfileVersionPersistenceService.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", versions);
    when(authorization.currentActor()).thenReturn(actor("director", Set.of(), true));
    WeightProfile profile = profile("recruiter");
    when(profiles.findWeightProfile("profile")).thenReturn(profile);

    assertThat(manager.getWeightProfile("profile").getProfile()).isSameAs(profile);
    assertThatThrownBy(() -> manager.updateWeightProfile("profile", null))
        .hasMessageContaining("个人配置只允许本人维护");
  }

  @ParameterizedTest
  @ValueSource(strings = {"list", "get", "create", "update", "delete"})
  void rejectsActorsOutsideMatchingWorkspaceBeforeReadingOrChangingProfiles(String action) {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    doThrow(PlatformExceptions.forbidden("PERMISSION_REQUIRED", "缺少智能筛选权限"))
        .when(authorization).requirePermission("matching.run");
    assertThatThrownBy(() -> {
      switch (action) {
        case "list" -> manager.listWeightProfiles(false, null, 10);
        case "get" -> manager.getWeightProfile("profile");
        case "create" -> manager.createWeightProfile(null, null);
        case "update" -> manager.updateWeightProfile("profile", null);
        case "delete" -> manager.deleteWeightProfile("profile", null);
        default -> throw new AssertionError(action);
      }
    }).hasMessageContaining("缺少智能筛选权限");
    verify(authorization).requirePermission("matching.run");
    verifyNoInteractions(profiles);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 4})
  void referencedProfileRejectsDeletionWithoutDeletingVersionsOrWritingAudit(int references) {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var versions = mock(WeightProfileVersionPersistenceService.class);
    var persistence = mock(MatchingConfigurationPersistenceService.class);
    var audit = mock(MatchingAuditEventPersistenceService.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchingAuditEventPersistenceService", audit);
    when(authorization.currentActor()).thenReturn(actor("owner", Set.of(), false));
    when(profiles.findWeightProfile("profile")).thenReturn(profile("owner"));
    when(persistence.countWeightProfileReferences("profile")).thenReturn(references);

    assertThatThrownBy(() -> manager.deleteWeightProfile("profile", new VersionReasonDTO(7, "清理模板")))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("WEIGHT_PROFILE_REFERENCED");
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.getMessage()).contains("历史匹配");
        });
    verifyNoInteractions(versions, audit);
    verify(profiles, never()).deleteWeightProfile(anyString(), anyString(), anyLong());
  }

  @Test
  void unreferencedOwnedProfileStillDeletesItsVersionsAndRecordsAudit() {
    var authorization = mock(AuthorizationService.class);
    var profiles = mock(WeightProfilePersistenceService.class);
    var versions = mock(WeightProfileVersionPersistenceService.class);
    var persistence = mock(MatchingConfigurationPersistenceService.class);
    var audit = mock(MatchingAuditEventPersistenceService.class);
    var ids = mock(PlatformIdGenerator.class);
    var manager = new MatchingConfigurationApplicationManager();
    Instant now = Instant.parse("2026-10-02T00:00:00Z");
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "weightProfilePersistenceService", profiles);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "matchingAuditEventPersistenceService", audit);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    when(authorization.currentActor()).thenReturn(actor("owner", Set.of(), false));
    WeightProfile owned = profile("owner");
    owned.setVersion(7);
    when(profiles.findWeightProfile("profile")).thenReturn(owned);
    when(persistence.countWeightProfileReferences("profile")).thenReturn(0);
    when(versions.deleteWeightProfileVersions("profile")).thenReturn(1);
    when(profiles.deleteWeightProfile("profile", "owner", 7)).thenReturn(1);
    when(ids.newId()).thenReturn("audit-1");

    manager.deleteWeightProfile("profile", new VersionReasonDTO(7, "清理模板"));

    verify(versions).deleteWeightProfileVersions("profile");
    verify(profiles).deleteWeightProfile("profile", "owner", 7);
    verify(audit).insertAudit("audit-1", "WEIGHT_PROFILE_DELETED", "owner", "WEIGHT_PROFILE", "profile", "清理模板", now);
  }

  private static CurrentActorSnapshot actor(
      String userId, Set<String> managedUserIds, boolean companyScope) {
    return new CurrentActorSnapshot(
        userId, userId, userId + "@example.com", "department", Set.of(), managedUserIds,
        Set.of(), Set.of("RECRUITMENT_MANAGER"), Set.of("matching.run"), companyScope, false);
  }

  private static WeightProfile profile(String ownerUserId) {
    WeightProfile profile = new WeightProfile();
    profile.setId("profile");
    profile.setOwnerUserId(ownerUserId);
    profile.setCurrentVersionNo(1);
    return profile;
  }

  private static WeightProfileVersion version(String profileId) {
    WeightProfileVersion version = new WeightProfileVersion();
    version.setId(profileId + "-version");
    version.setProfileId(profileId);
    version.setVersionNo(1);
    return version;
  }
}
