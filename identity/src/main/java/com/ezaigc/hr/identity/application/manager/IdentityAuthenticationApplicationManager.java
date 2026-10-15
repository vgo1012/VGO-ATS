package com.ezaigc.hr.identity.application.manager;

import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.config.IdentityLoginAliasProperties;
import com.ezaigc.hr.identity.config.IdentityLoginProtectionProperties;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 登录认证应用管理器，保证认证结果与安全审计使用同一用例边界。 */
@Component
public class IdentityAuthenticationApplicationManager {

  @Resource private AuthenticationManager authenticationManager;
  @Resource private IdentityAccountPersistenceService accounts;
  @Resource private IdentityLoginAliasProperties loginAliasProperties;
  @Resource private IdentityLoginProtectionProperties protection;
  @Resource private IdentityAuditEventPersistenceService auditEvents;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  /** 认证凭据并持久化成功或失败审计；失败审计不得随认证异常回滚。 */
  @Transactional(noRollbackFor = PlatformException.class)
  public Authentication authenticate(String workEmail, String password) {
    UserAccountRecord account = lockAccount(workEmail);
    boolean emergencyAccount = account != null
        && (accounts.isUsableBootstrappedSuperAdmin(account.getId())
            || accounts.isFallbackEmergencySuperAdmin(account.getId()));
    Instant now = clock.instant();
    if (account != null && !emergencyAccount && account.getLoginLockedUntil() != null
        && now.isBefore(account.getLoginLockedUntil())) {
      audit("LOGIN_FAILED", PlatformActorConstants.SYSTEM_ACTOR_ID,
          failedPrincipalReference(workEmail));
      throw authenticationFailed();
    }
    try {
      Authentication authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(workEmail, password));
      IdentityPrincipal principal = (IdentityPrincipal) authentication.getPrincipal();
      if (account != null && accounts.clearLoginFailures(account.getId()) != 1) {
        throw new IllegalStateException("login failure state was not cleared");
      }
      audit("LOGIN_SUCCEEDED", principal.getUserId(), principal.getUserId());
      return authentication;
    } catch (AuthenticationException exception) {
      if (account != null && !emergencyAccount) {
        recordFailure(account, now);
      }
      audit(
          "LOGIN_FAILED",
          PlatformActorConstants.SYSTEM_ACTOR_ID,
          failedPrincipalReference(workEmail));
      throw authenticationFailed();
    }
  }

  private UserAccountRecord lockAccount(String workEmail) {
    String normalized = workEmail.strip().toLowerCase(Locale.ROOT);
    UserAccountRecord account = accounts.lockLoginAccount(normalized);
    if (account == null && "admin".equals(normalized)
        && !loginAliasProperties.getAdminWorkEmail().isBlank()) {
      account = accounts.lockLoginAccount(
          loginAliasProperties.getAdminWorkEmail().strip().toLowerCase(Locale.ROOT));
    }
    return account;
  }

  private void recordFailure(UserAccountRecord account, Instant now) {
    Instant windowStartedAt = account.getFailedLoginWindowStartedAt();
    int count = account.getFailedLoginCount();
    if (windowStartedAt == null || !now.isBefore(windowStartedAt.plus(protection.getFailureWindow()))
        || account.getLoginLockedUntil() != null) {
      windowStartedAt = now;
      count = 0;
    }
    count++;
    Instant lockedUntil = count >= protection.getMaxFailures()
        ? now.plus(protection.getLockDuration()) : null;
    if (accounts.updateLoginFailure(account.getId(), count, windowStartedAt, lockedUntil) != 1) {
      throw new IllegalStateException("login failure state was not persisted");
    }
  }

  private static PlatformException authenticationFailed() {
    return new PlatformException(
        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "账号或凭据错误", true);
  }

  private void audit(String eventType, String actorId, String targetId) {
    Instant now = clock.instant();
    int inserted =
        auditEvents.insertAudit(
            ids.newId(),
            eventType,
            actorId,
            "AUTHENTICATION",
            targetId,
            null,
            null,
            null,
            MDC.get("traceId"),
            now);
    if (inserted != 1) {
      throw new IllegalStateException("authentication audit was not persisted");
    }
  }

  private static String failedPrincipalReference(String workEmail) {
    String normalized = workEmail.strip().toLowerCase(Locale.ROOT);
    return Sha256Utils.hashUtf8(normalized).substring(0, 26);
  }
}
