package com.ezaigc.hr.recruiting.demand.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

class JdParsingAdmissionPolicyTest {
  private final DemandQueryService queries = mock(DemandQueryService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final JdParsingAdmissionPolicy service = new JdParsingAdmissionPolicy();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "queries", queries);
    ReflectionTestUtils.setField(service, "authorization", authorization);
  }

  @Test
  void currentPrivateDemandMetadataControlsAdmission() {
    JdVersionSnapshot context = context();
    when(queries.requireJdVersionForNewUse("version-1")).thenReturn(context);

    assertThat(service.requireUserAccess("version-1")).isSameAs(context);

    var target = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization).requireAccess(eq("demand.manage"), target.capture());
    assertThat(target.getValue().getObjectType()).isEqualTo("DEMAND");
    assertThat(target.getValue().getObjectId()).isEqualTo("demand-1");
    assertThat(target.getValue().getOwnerUserId()).isEqualTo("owner-current");
    assertThat(target.getValue().getCollaboratorUserIds()).containsExactly("collaborator-current");
  }

  @Test
  void missingResourceFromLockedQueryCannotBeBypassed() {
    var rejection = PlatformExceptions.notFound("JD_NOT_FOUND", "JD 不存在");
    when(queries.requireJdVersionForNewUse("version-1")).thenThrow(rejection);
    assertThatThrownBy(() -> service.requireUserAccess("version-1")).isSameAs(rejection);
  }

  @Test
  void deletedSnapshotRemainsForbiddenForParsing() {
    JdVersionSnapshot context = context();
    context.setDeleted(true);
    when(queries.requireJdVersionForNewUse("version-1")).thenReturn(context);
    assertThatThrownBy(() -> service.requireUserAccess("version-1"))
        .isInstanceOfSatisfying(PlatformException.class, ex -> {
          assertThat(ex.code()).isEqualTo("JD_DELETED");
          assertThat(ex.recoverable()).isFalse();
        });
  }

  @Test
  void revokedCurrentAccessPropagatesUnchanged() {
    when(queries.requireJdVersionForNewUse("version-1")).thenReturn(context());
    var rejection = PlatformExceptions.forbidden("FORBIDDEN", "已撤销协作权限");
    doThrow(rejection).when(authorization).requireAccess(eq("demand.manage"), any());
    assertThatThrownBy(() -> service.requireUserAccess("version-1")).isSameAs(rejection);
  }

  @Test
  void legacyPublicFlagDoesNotGrantOrDenyParsingAccess() {
    JdVersionSnapshot context = publicContext(false);
    assertThat(service.requireUserAccess("version-1")).isSameAs(context);
    var target = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization).requireAccess(eq("demand.manage"), target.capture());
    assertThat(target.getValue().getCollaboratorUserIds()).containsExactly("collaborator-current");
  }

  private JdVersionSnapshot publicContext(boolean admin) {
    JdVersionSnapshot context = context();
    context.setDemandId(null);
    context.setPublicVisible(true);
    CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("current-actor");
    when(actor.isSuperAdmin()).thenReturn(admin);
    when(authorization.currentActor()).thenReturn(actor);
    when(queries.requireJdVersionForNewUse("version-1")).thenReturn(context);
    return context;
  }

  private static JdVersionSnapshot context() {
    return new JdVersionSnapshot("version-1", "jd-1", "demand-1", "INTERNAL",
        "owner-current", "department-current", Set.of("collaborator-current"));
  }
}
