package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.config.IdentityLoginAliasProperties;
import com.ezaigc.hr.identity.config.IdentityLoginProtectionProperties;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityAuthenticationApplicationManagerTest {
  private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
  private static final String USER_ID = "01JADMIN000000000000000000";
  private final IdentityAuthenticationApplicationManager manager =
      new IdentityAuthenticationApplicationManager();
  private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
  private final IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
  private final IdentityAuditEventPersistenceService auditEvents =
      mock(IdentityAuditEventPersistenceService.class);

  @BeforeEach
  void setUp() {
    var alias = new IdentityLoginAliasProperties();
    alias.setAdminWorkEmail("admin@example.com");
    var protection = new IdentityLoginProtectionProperties();
    protection.setMaxFailures(5);
    protection.setFailureWindow(Duration.ofMinutes(15));
    protection.setLockDuration(Duration.ofMinutes(15));
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("01JAUDIT00000000000000000");
    when(auditEvents.insertAudit(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any())).thenReturn(1);
    ReflectionTestUtils.setField(manager, "authenticationManager", authenticationManager);
    ReflectionTestUtils.setField(manager, "accounts", accounts);
    ReflectionTestUtils.setField(manager, "loginAliasProperties", alias);
    ReflectionTestUtils.setField(manager, "protection", protection);
    ReflectionTestUtils.setField(manager, "auditEvents", auditEvents);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void emergencyAdminAliasRemainsAvailableAfterFailedAttempts() {
    var account = account("admin@example.com");
    account.setLoginLockedUntil(NOW.plus(Duration.ofMinutes(10)));
    when(accounts.lockLoginAccount("admin@example.com")).thenReturn(account);
    when(accounts.isUsableBootstrappedSuperAdmin(USER_ID)).thenReturn(true);
    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

    assertThatThrownBy(() -> manager.authenticate("admin", "wrong"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("AUTHENTICATION_FAILED"));

    verify(accounts).lockLoginAccount("admin");
    verify(accounts).lockLoginAccount("admin@example.com");
    verify(accounts, never()).updateLoginFailure(eq(USER_ID), anyInt(), any(), any());

    var principal = mock(IdentityPrincipal.class);
    when(principal.getUserId()).thenReturn(USER_ID);
    var authenticated = mock(Authentication.class);
    when(authenticated.getPrincipal()).thenReturn(principal);
    doReturn(authenticated).when(authenticationManager).authenticate(any());
    when(accounts.clearLoginFailures(USER_ID)).thenReturn(1);
    assertThat(manager.authenticate("admin@example.com", "correct")).isSameAs(authenticated);
  }

  @Test
  void remainingSuperAdminCanRecoverAfterBootstrappedAdminIsRevoked() {
    var account = account("successor@example.com");
    account.setLoginLockedUntil(NOW.plus(Duration.ofMinutes(10)));
    when(accounts.lockLoginAccount("successor@example.com")).thenReturn(account);
    when(accounts.isFallbackEmergencySuperAdmin(USER_ID)).thenReturn(true);
    var principal = mock(IdentityPrincipal.class);
    when(principal.getUserId()).thenReturn(USER_ID);
    var authenticated = mock(Authentication.class);
    when(authenticated.getPrincipal()).thenReturn(principal);
    when(authenticationManager.authenticate(any())).thenReturn(authenticated);
    when(accounts.clearLoginFailures(USER_ID)).thenReturn(1);

    assertThat(manager.authenticate("successor@example.com", "correct"))
        .isSameAs(authenticated);
    verify(accounts, never()).updateLoginFailure(eq(USER_ID), anyInt(), any(), any());
  }

  @Test
  void configuredAliasDoesNotExemptAnOrdinaryAccount() {
    var account = account("admin@example.com");
    account.setLoginLockedUntil(NOW.plus(Duration.ofMinutes(10)));
    when(accounts.lockLoginAccount("admin@example.com")).thenReturn(account);

    assertThatThrownBy(() -> manager.authenticate("admin", "correct"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("AUTHENTICATION_FAILED"));
    verifyNoInteractions(authenticationManager);
  }

  @Test
  void expiredWindowStartsAtOneFailureAndSuccessClearsState() {
    var account = account("ordinary@example.com");
    account.setFailedLoginCount(4);
    account.setFailedLoginWindowStartedAt(NOW.minus(Duration.ofMinutes(16)));
    when(accounts.lockLoginAccount("ordinary@example.com")).thenReturn(account);
    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
    when(accounts.updateLoginFailure(eq(USER_ID), anyInt(), any(), any())).thenReturn(1);

    assertThatThrownBy(() -> manager.authenticate("ordinary@example.com", "wrong"))
        .isInstanceOf(PlatformException.class);
    verify(accounts).updateLoginFailure(USER_ID, 1, NOW, null);

    var principal = mock(IdentityPrincipal.class);
    when(principal.getUserId()).thenReturn(USER_ID);
    var authenticated = mock(Authentication.class);
    when(authenticated.getPrincipal()).thenReturn(principal);
    doReturn(authenticated).when(authenticationManager).authenticate(any());
    when(accounts.clearLoginFailures(USER_ID)).thenReturn(1);
    assertThat(manager.authenticate("ordinary@example.com", "correct")).isSameAs(authenticated);
    verify(accounts).clearLoginFailures(USER_ID);
  }

  @Test
  void lockedAccountSkipsPasswordCheckAndUsesGenericError() {
    var account = account("ordinary@example.com");
    account.setLoginLockedUntil(NOW.plus(Duration.ofMinutes(10)));
    when(accounts.lockLoginAccount("ordinary@example.com")).thenReturn(account);

    assertThatThrownBy(() -> manager.authenticate("ordinary@example.com", "correct"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("AUTHENTICATION_FAILED"));
    verifyNoInteractions(authenticationManager);
    verify(accounts, never()).updateLoginFailure(eq(USER_ID), anyInt(), any(), any());
  }

  @Test
  void unknownAccountStillHasGenericAuditedFailure() {
    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

    assertThatThrownBy(() -> manager.authenticate("unknown@example.com", "wrong"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("AUTHENTICATION_FAILED"));
    verify(accounts).lockLoginAccount("unknown@example.com");
    verify(accounts, never()).updateLoginFailure(any(), anyInt(), any(), any());
  }

  private static UserAccountRecord account(String email) {
    var account = new UserAccountRecord();
    account.setId(USER_ID);
    account.setNormalizedEmail(email);
    return account;
  }
}
