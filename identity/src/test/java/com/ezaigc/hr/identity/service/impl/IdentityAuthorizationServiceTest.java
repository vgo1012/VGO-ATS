package com.ezaigc.hr.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.application.adapter.IdentityAuthorizationAdapter;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import com.ezaigc.hr.identity.service.ScopeGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityAuthorizationServiceTest {

  private static final String USER_ID = "01JUSER0000000000000000000";
  private final IdentityAccountPersistenceService accounts =
      mock(IdentityAccountPersistenceService.class);
  private final ObjectGrantPersistenceService objectGrants =
      mock(ObjectGrantPersistenceService.class);
  private final ScopeGrantPersistenceService scopeGrants = mock(ScopeGrantPersistenceService.class);
  private final UserSupportDepartmentPersistenceService supportDepartments =
      mock(UserSupportDepartmentPersistenceService.class);

  @Test
  void recruiterCannotReadOthersThroughHomeSupportOrGrantedDepartments() {
    IdentityAuthorizationAdapter service = service("RECRUITER", Set.of("talent.read"), false);
    when(supportDepartments.findSupportDepartmentIds(USER_ID))
        .thenReturn(List.of("support-department"));

    when(scopeGrants.findGrantedDepartmentIds(USER_ID)).thenReturn(List.of("granted"));
    assertThat(service.canAccess("talent.read", target("owner", "support-department"))).isFalse();
    assertThat(service.canAccess("talent.read", target("owner", "home-department"))).isFalse();
    assertThat(service.canAccess("talent.read", target("owner", "granted"))).isFalse();
    assertThat(service.canAccess("talent.read", target("owner", "another-department"))).isFalse();
  }

  @Test
  void jdOwnershipIsIsolatedWithinHomeAndSupportDepartments() {
    var service = service("RECRUITER", Set.of("demand.read"), false);
    when(supportDepartments.findSupportDepartmentIds(USER_ID)).thenReturn(List.of("support-department"));
    for (String type : List.of("JD", "JD_VERSION", "DEMAND")) {
      for (String department : List.of("home-department", "support-department")) {
        var other = new AuthorizationTarget(type, "other-jd", "other-hr", department, Set.of());
        assertThat(service.canAccess("demand.read", other)).isFalse();
        assertThat(service.accessibleObjectIds(service.currentActor(), "demand.read", List.of(other))).isEmpty();
        assertThat(service.canAccess("demand.read", new AuthorizationTarget(type, "owned", USER_ID, department, Set.of()))).isTrue();
        assertThat(service.canAccess("demand.read", new AuthorizationTarget(type, "collaborated", "other-hr", department, Set.of(USER_ID)))).isTrue();
      }
    }
    when(accounts.findManagedUserIds(USER_ID)).thenReturn(List.of("report"));
    assertThat(service.canAccess("demand.read", new AuthorizationTarget("JD", "team-jd", "report", "support-department", Set.of()))).isFalse();
    when(scopeGrants.findGrantedDepartmentIds(USER_ID)).thenReturn(List.of("explicit-department"));
    assertThat(service.canAccess("demand.read", new AuthorizationTarget("JD", "granted-jd", "other-hr", "explicit-department", Set.of()))).isFalse();
  }

  @Test
  void directorHasCompanyBusinessScopeWithoutAdministratorIdentity() {
    var service = service("HR_DIRECTOR", Set.of("talent.read", "recruitment.manage"), false);
    assertThat(service.currentActor().hasCompanyWideScope()).isTrue();
    assertThat(service.currentActor().isSuperAdmin()).isFalse();
    assertThat(service.canAccess("talent.read", target("unrelated-owner", "other"))).isTrue();
    assertThat(service.canAccess("identity.role.assign", target("unrelated-owner", "other"))).isFalse();
    assertThat(service.accessibleObjectIds(service.currentActor(), "talent.read",
        List.of(target("unrelated-owner", "other")))).containsExactly("application-1");
  }

  @Test
  void legacyCompanyGrantDoesNotWidenAnOrdinaryRecruiter() {
    var service = service("RECRUITER", Set.of("talent.read"), false);
    when(scopeGrants.countCompanyScope(USER_ID)).thenReturn(1);
    assertThat(service.canAccess("talent.read", target("other-owner", "other"))).isFalse();
  }

  @Test
  void recruitmentManagerCanAccessAnIndirectReportsWork() {
    IdentityAuthorizationAdapter service =
        service("RECRUITMENT_MANAGER", Set.of("recruitment.manage"), false);
    when(accounts.findManagedUserIds(USER_ID)).thenReturn(List.of("indirect-report"));

    assertThat(
            service.canAccess(
                "recruitment.manage", target("indirect-report", "another-department")))
        .isTrue();
  }

  @Test
  void historicalReportScopeIncludesInactiveReportsWithoutChangingBusinessAuthorization() {
    var service=service("RECRUITMENT_MANAGER",Set.of("reporting.read-own","talent.read"),false);
    when(accounts.findManagedUserIds(USER_ID)).thenReturn(List.of("active-report"));
    when(accounts.findReportingManagedUserIds(USER_ID))
        .thenReturn(List.of("active-report","disabled-report","indirect-through-disabled"));

    assertThat(service.recruitmentReportManagedUserIds())
        .containsExactlyInAnyOrder("active-report","disabled-report","indirect-through-disabled");
    assertThat(service.currentActor().getManagedUserIds()).containsExactly("active-report");
    assertThat(service.canAccess("talent.read",target("disabled-report","other"))).isFalse();
    assertThat(service.currentActor().operationalScope().getManagedUserIds()).isEmpty();
  }

  @Test
  void ordinaryAndCompanyRolesNeverReadTheHistoricalSubordinateTree() {
    for (String role:List.of("RECRUITER","INTERVIEWER","HR_DIRECTOR","SUPER_ADMIN")) {
      var service=service(role,Set.of("reporting.read-own"),role.equals("SUPER_ADMIN"));
      assertThat(service.recruitmentReportManagedUserIds()).isEmpty();
    }
    verify(accounts,never()).findReportingManagedUserIds(anyString());
  }

  @Test
  void historicalManagementScopeRequiresTheCurrentReportingPermission() {
    var service=service("RECRUITMENT_MANAGER",Set.of("talent.read"),false);
    assertThatThrownBy(service::recruitmentReportManagedUserIds)
        .isInstanceOfSatisfying(PlatformException.class,
            exception->assertThat(exception.code()).isEqualTo("PERMISSION_REQUIRED"));
    verify(accounts,never()).findReportingManagedUserIds(anyString());
  }

  @Test
  void interviewerNeedsPermissionAndAnExplicitAssignment() {
    IdentityAuthorizationAdapter service =
        service("INTERVIEWER", Set.of("interview.read", "interview.evaluate"), false);
    when(objectGrants.countObjectGrant(USER_ID, "INTERVIEW", "interview-1", "interview.evaluate"))
        .thenReturn(1);

    assertThat(
            service.canAccess(
                "interview.evaluate",
                new AuthorizationTarget("INTERVIEW", "interview-1", "owner", "other", Set.of())))
        .isTrue();
    assertThat(service.canAccess("recruitment.manage", target(USER_ID, "home-department")))
        .isFalse();
  }

  @Test
  void superAdminBypassesBusinessScopeButNotAuthentication() {
    IdentityAuthorizationAdapter service = service("SUPER_ADMIN", Set.of(), true);

    assertThat(service.canAccess("any.future.permission", target("owner", "other"))).isTrue();
  }

  @Test
  void rejectsAuthenticatedUserOutsideDataScopeWithStableCode() {
    IdentityAuthorizationAdapter service = service("RECRUITER", Set.of("demand.read"), false);

    assertThatThrownBy(() -> service.requireAccess("demand.read", target("owner", "other")))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
  }

  @Test
  void batchAccessQueriesExplicitGrantsOnceForUnresolvedTargets() {
    IdentityAuthorizationAdapter service =
        service("RECRUITER", Set.of("recruitment.manage"), false);
    when(objectGrants.findGrantedObjectIds(
            eq(USER_ID), eq("APPLICATION"), anyCollection(), eq("recruitment.manage")))
        .thenReturn(List.of("application-granted"));
    var actor = service.currentActor();

    Set<String> visible =
        service.accessibleObjectIds(
            actor,
            "recruitment.manage",
            List.of(
                new AuthorizationTarget(
                    "APPLICATION", "application-owned", USER_ID, "other", Set.of()),
                new AuthorizationTarget(
                    "APPLICATION", "application-granted", "owner", "other", Set.of()),
                new AuthorizationTarget(
                    "APPLICATION", "application-hidden", "owner", "other", Set.of())));

    assertThat(visible).containsExactlyInAnyOrder("application-owned", "application-granted");
    verify(objectGrants)
        .findGrantedObjectIds(
            eq(USER_ID), eq("APPLICATION"), anyCollection(), eq("recruitment.manage"));
  }

  @Test
  void singleAndBatchAccessKeepOwnerCollaboratorAndManagedScopesBehindPermission() {
    var service = service("RECRUITMENT_MANAGER", Set.of("recruitment.manage"), false);
    when(accounts.findManagedUserIds(USER_ID)).thenReturn(List.of("managed-user"));
    var actor = service.currentActor();
    var targets = List.of(
        new AuthorizationTarget("APPLICATION", "owned", USER_ID, "other", Set.of()),
        new AuthorizationTarget("APPLICATION", "collaborated", "owner", "other", Set.of(USER_ID)),
        new AuthorizationTarget("APPLICATION", "managed", "managed-user", "other", Set.of()),
        new AuthorizationTarget("APPLICATION", "hidden", "owner", "other", Set.of()));

    assertThat(service.canAccess(actor, "recruitment.manage", targets.get(0))).isTrue();
    assertThat(service.canAccess(actor, "recruitment.manage", targets.get(1))).isTrue();
    assertThat(service.canAccess(actor, "recruitment.manage", targets.get(2))).isTrue();
    assertThat(service.canAccess(actor, "recruitment.manage", targets.get(3))).isFalse();
    assertThat(service.accessibleObjectIds(actor, "recruitment.manage", targets))
        .containsExactlyInAnyOrder("owned", "collaborated", "managed");
    verify(objectGrants).countObjectGrant(USER_ID, "APPLICATION", "hidden", "recruitment.manage");
    verify(objectGrants).findGrantedObjectIds(
        USER_ID, "APPLICATION", Set.of("hidden"), "recruitment.manage");

    for (AuthorizationTarget target : targets) {
      assertThat(service.canAccess(actor, "interview.evaluate", target)).isFalse();
    }
    assertThat(service.accessibleObjectIds(actor, "interview.evaluate", targets)).isEmpty();
  }

  private IdentityAuthorizationAdapter service(
      String role, Set<String> permissions, boolean superAdmin) {
    CurrentActorProvider provider =
        () -> Optional.of(new CurrentActor(USER_ID, permissions, superAdmin));
    when(accounts.findById(USER_ID))
        .thenReturn(
            new UserAccountRecord(
                USER_ID,
                "测试账号",
                "user@example.com",
                "user@example.com",
                "{noop}secret",
                "home-department",
                "ACTIVE",
                false,
                0));
    when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of(role));
    when(supportDepartments.findSupportDepartmentIds(USER_ID)).thenReturn(List.of());
    when(accounts.findManagedUserIds(USER_ID)).thenReturn(List.of());
    when(scopeGrants.findGrantedDepartmentIds(USER_ID)).thenReturn(List.of());
    IdentityAuthorizationAdapter service = new IdentityAuthorizationAdapter();
    ReflectionTestUtils.setField(service, "actorProvider", provider);
    ReflectionTestUtils.setField(service, "accountService", accounts);
    ReflectionTestUtils.setField(service, "objectGrantPersistenceService", objectGrants);
    ReflectionTestUtils.setField(
        service, "userSupportDepartmentPersistenceService", supportDepartments);
    return service;
  }

  private static AuthorizationTarget target(String ownerUserId, String departmentId) {
    return new AuthorizationTarget(
        "APPLICATION", "application-1", ownerUserId, departmentId, Set.of());
  }
}
