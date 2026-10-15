package com.ezaigc.hr.identity.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.application.support.SecurityCurrentActorProvider;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityBackgroundActorExecutorTest {

  private static final String USER_ID = "01JUSER0000000000000000000";
  private static final String OUTER_USER_ID = "01JUSER0000000000000000001";

  private final IdentityAccountPersistenceService accounts =
      mock(IdentityAccountPersistenceService.class);
  private final SecurityCurrentActorProvider actorProvider = new SecurityCurrentActorProvider();
  private final IdentityBackgroundActorExecutor executor = new IdentityBackgroundActorExecutor();

  IdentityBackgroundActorExecutorTest() {
    ReflectionTestUtils.setField(executor, "accountService", accounts);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsDisabledUserBeforeBindingBackgroundIdentity() {
    when(accounts.findById(USER_ID)).thenReturn(account(USER_ID, "DISABLED"));
    Authentication original = bind(USER_ID, Set.of("original.permission"));

    assertThatThrownBy(() -> executor.execute(USER_ID, () -> "unexpected"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("SESSION_INVALIDATED"));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(original);
  }

  @Test
  void reloadsLatestPermissionsForEveryExecutionWithoutSuperAdminEscalation() {
    when(accounts.findById(USER_ID)).thenReturn(account(USER_ID, "ACTIVE"));
    when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of("RECRUITER"));
    when(accounts.findPermissionCodes(USER_ID))
        .thenReturn(List.of("talent.read"), List.of("talent.import"));

    CurrentActor first = executor.execute(USER_ID, () -> actorProvider.currentActor().orElseThrow());
    CurrentActor second = executor.execute(USER_ID, () -> actorProvider.currentActor().orElseThrow());

    assertThat(first.getPermissions()).containsExactly("talent.read");
    assertThat(second.getPermissions()).containsExactly("talent.import");
    assertThat(first.isSuperAdmin()).isFalse();
    assertThat(second.isSuperAdmin()).isFalse();
  }

  @Test
  void restoresOriginalContextWhenActionFails() {
    when(accounts.findById(USER_ID)).thenReturn(account(USER_ID, "ACTIVE"));
    when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of("RECRUITER"));
    when(accounts.findPermissionCodes(USER_ID)).thenReturn(List.of("talent.import"));
    SecurityContext originalContext = SecurityContextHolder.createEmptyContext();
    originalContext.setAuthentication(bind(USER_ID, Set.of("original.permission")));
    SecurityContextHolder.setContext(originalContext);

    assertThatThrownBy(
            () ->
                executor.execute(
                    USER_ID,
                    () -> {
                      throw new IllegalStateException("failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("failed");
    assertThat(SecurityContextHolder.getContext()).isSameAs(originalContext);
  }

  @Test
  void nestedExecutionRestoresEachEnclosingIdentity() {
    when(accounts.findById(USER_ID)).thenReturn(account(USER_ID, "ACTIVE"));
    when(accounts.findById(OUTER_USER_ID)).thenReturn(account(OUTER_USER_ID, "ACTIVE"));
    when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of("RECRUITER"));
    when(accounts.findRoleCodes(OUTER_USER_ID)).thenReturn(List.of("RECRUITER"));
    when(accounts.findPermissionCodes(USER_ID)).thenReturn(List.of("inner.permission"));
    when(accounts.findPermissionCodes(OUTER_USER_ID)).thenReturn(List.of("outer.permission"));
    Authentication original = bind("original-user", Set.of("original.permission"));

    executor.execute(
        OUTER_USER_ID,
        () -> {
          assertCurrentActor(OUTER_USER_ID, "outer.permission");
          assertThatThrownBy(
                  () ->
                      executor.execute(
                          USER_ID,
                          () -> {
                            assertCurrentActor(USER_ID, "inner.permission");
                            throw new IllegalStateException("inner failed");
                          }))
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("inner failed");
          assertCurrentActor(OUTER_USER_ID, "outer.permission");
          return null;
        });

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(original);
  }

  private void assertCurrentActor(String userId, String permission) {
    CurrentActor actor = actorProvider.currentActor().orElseThrow();
    assertThat(actor.getUserId()).isEqualTo(userId);
    assertThat(actor.getPermissions()).containsExactly(permission);
  }

  private static Authentication bind(String userId, Set<String> permissions) {
    IdentityPrincipal principal =
        new IdentityPrincipal(
            userId,
            "测试账号",
            userId + "@example.com",
            "",
            "home-department",
            Set.of("RECRUITER"),
            permissions,
            false,
            0,
            true);
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    return authentication;
  }

  private static UserAccountRecord account(String userId, String status) {
    return new UserAccountRecord(
        userId,
        "测试账号",
        userId + "@example.com",
        userId + "@example.com",
        null,
        "home-department",
        status,
        false,
        0);
  }
}
