package com.ezaigc.hr.identity.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import com.ezaigc.hr.identity.service.ScopeGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class IdentityAuthorizationRequestCacheTest {

  private static final String USER_ID = "01JUSER0000000000000000000";
  private static final String OTHER_USER_ID = "01JUSER0000000000000000001";

  private final IdentityAccountPersistenceService accounts =
      mock(IdentityAccountPersistenceService.class);
  private final ObjectGrantPersistenceService objectGrants =
      mock(ObjectGrantPersistenceService.class);
  private final ScopeGrantPersistenceService scopeGrants = mock(ScopeGrantPersistenceService.class);
  private final UserSupportDepartmentPersistenceService supportDepartments =
      mock(UserSupportDepartmentPersistenceService.class);
  private IdentityAuthorizationAdapter authorization;

  @BeforeEach
  void setUp() {
    when(accounts.findById(anyString())).thenAnswer(invocation -> activeAccount(invocation.getArgument(0)));
    when(accounts.findRoleCodes(anyString())).thenReturn(List.of("RECRUITER"));
    when(accounts.findManagedUserIds(anyString())).thenReturn(List.of());
    when(supportDepartments.findSupportDepartmentIds(anyString())).thenReturn(List.of());
    when(scopeGrants.findGrantedDepartmentIds(anyString())).thenReturn(List.of());
    when(scopeGrants.countCompanyScope(anyString())).thenReturn(0);

    CurrentActorProvider actorProvider =
        () -> {
          Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
          if (authentication == null
              || !authentication.isAuthenticated()
              || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            return Optional.empty();
          }
          return Optional.of(
              new CurrentActor(
                  principal.getUserId(), principal.getPermissions(), principal.superAdmin()));
        };
    authorization = new IdentityAuthorizationAdapter();
    ReflectionTestUtils.setField(authorization, "actorProvider", actorProvider);
    ReflectionTestUtils.setField(authorization, "accountService", accounts);
    ReflectionTestUtils.setField(authorization, "objectGrantPersistenceService", objectGrants);
    ReflectionTestUtils.setField(
        authorization, "userSupportDepartmentPersistenceService", supportDepartments);
  }

  @AfterEach
  void clearRequestAndSecurityContext() {
    RequestContextHolder.resetRequestAttributes();
    SecurityContextHolder.clearContext();
  }

  @Test
  void reusesTheAuthorizationSnapshotWithinOneHttpRequest() {
    bindAuthentication(USER_ID);
    beginRequest();

    authorization.currentActor();
    authorization.requirePermission("talent.read");

    verifySnapshotLoaded(USER_ID, 1);
  }

  @Test
  void reloadsTheAuthorizationSnapshotForTheNextHttpRequest() {
    bindAuthentication(USER_ID);
    beginRequest();
    authorization.currentActor();

    beginRequest();
    authorization.currentActor();

    verifySnapshotLoaded(USER_ID, 2);
  }

  @Test
  void doesNotReuseTheSnapshotAfterAuthenticationChangesWithinARequest() {
    bindAuthentication(USER_ID);
    beginRequest();
    authorization.currentActor();

    bindAuthentication(OTHER_USER_ID);

    assertThat(authorization.currentActor().getUserId()).isEqualTo(OTHER_USER_ID);
    verifySnapshotLoaded(USER_ID, 1);
    verifySnapshotLoaded(OTHER_USER_ID, 1);
  }

  @Test
  void doesNotCacheOutsideAnHttpRequest() {
    bindAuthentication(USER_ID);

    authorization.currentActor();
    authorization.currentActor();

    verifySnapshotLoaded(USER_ID, 2);
  }

  @Test
  void reloadsAccountStatusOnTheNextRequest() {
    bindAuthentication(USER_ID);
    beginRequest();
    authorization.currentActor();

    when(accounts.findById(USER_ID)).thenReturn(inactiveAccount(USER_ID));
    beginRequest();

    assertThatThrownBy(authorization::currentActor)
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("SESSION_INVALIDATED"));
    verify(accounts, times(2)).findById(USER_ID);
  }

  @Test
  void keepsObjectGrantDecisionsStronglyConsistentWithinARequest() {
    bindAuthentication(USER_ID);
    beginRequest();
    AuthorizationTarget target =
        new AuthorizationTarget(
            "APPLICATION", "application-1", "another-owner", "another-department", Set.of());
    when(objectGrants.countObjectGrant(
            USER_ID, "APPLICATION", "application-1", "talent.read"))
        .thenReturn(1);

    authorization.requireAccess("talent.read", target);
    authorization.requireAccess("talent.read", target);

    verifySnapshotLoaded(USER_ID, 1);
    verify(objectGrants, times(2))
        .countObjectGrant(USER_ID, "APPLICATION", "application-1", "talent.read");
  }

  private void verifySnapshotLoaded(String userId, int times) {
    verify(accounts, times(times)).findById(userId);
    verify(accounts, times(times)).findRoleCodes(userId);
    verify(accounts, times(0)).findManagedUserIds(userId);
    verify(supportDepartments, times(times)).findSupportDepartmentIds(userId);
    verify(scopeGrants, times(0)).findGrantedDepartmentIds(userId);
    verify(scopeGrants, times(0)).countCompanyScope(userId);
  }

  private static void beginRequest() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
  }

  @Test
  void explicitGrantPageUsesAuthenticatedUserAndRequiresPermission() {
    beginRequest();
    bindAuthentication(USER_ID);
    when(objectGrants.findGrantedObjectIdPage(USER_ID, "CANDIDATE", "talent.read", "previous"))
        .thenReturn(List.of("next"));
    assertThat(authorization.grantedObjectIds("talent.read", "CANDIDATE", "previous")).containsExactly("next");
    verify(objectGrants).findGrantedObjectIdPage(USER_ID, "CANDIDATE", "talent.read", "previous");
    assertThatThrownBy(() -> authorization.grantedObjectIds("recruitment.manage", "APPLICATION", null))
        .isInstanceOf(PlatformException.class);
    verify(objectGrants, times(0)).findGrantedObjectIdPage(USER_ID, "APPLICATION", "recruitment.manage", null);
  }

  private static void bindAuthentication(String userId) {
    IdentityPrincipal principal = principal(userId);
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, principal.getPassword(), principal.getAuthorities());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }

  private static IdentityPrincipal principal(String userId) {
    return new IdentityPrincipal(
        userId,
        "测试账号",
        "user@example.com",
        "{noop}secret",
        "home-department",
        Set.of("RECRUITER"),
        Set.of("talent.read"),
        false,
        0,
        true);
  }

  private static UserAccountRecord activeAccount(String userId) {
    return account(userId, "ACTIVE");
  }

  private static UserAccountRecord inactiveAccount(String userId) {
    return account(userId, "DISABLED");
  }

  private static UserAccountRecord account(String userId, String status) {
    return new UserAccountRecord(
        userId,
        "测试账号",
        "user@example.com",
        "user@example.com",
        "{noop}secret",
        "home-department",
        status,
        false,
        0);
  }
}
