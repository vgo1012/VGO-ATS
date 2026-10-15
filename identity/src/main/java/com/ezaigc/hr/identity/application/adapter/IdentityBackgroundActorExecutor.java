package com.ezaigc.hr.identity.application.adapter;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import jakarta.annotation.Resource;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class IdentityBackgroundActorExecutor implements BackgroundActorExecutor {

  @Resource private IdentityAccountPersistenceService accountService;

  @Override
  public <T> T execute(String userId, Supplier<T> action) {
    String requiredUserId = requireUserId(userId);
    Objects.requireNonNull(action, "action");
    IdentityPrincipal principal = loadCurrentPrincipal(requiredUserId);
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    SecurityContext previousContext = SecurityContextHolder.getContext();
    SecurityContext backgroundContext = SecurityContextHolder.createEmptyContext();
    backgroundContext.setAuthentication(authentication);
    SecurityContextHolder.setContext(backgroundContext);
    try {
      return action.get();
    } finally {
      SecurityContextHolder.setContext(previousContext);
    }
  }

  private IdentityPrincipal loadCurrentPrincipal(String userId) {
    UserAccountRecord account = accountService.findById(userId);
    if (account == null || !account.active()) {
      throw new PlatformException(
          HttpStatus.UNAUTHORIZED, "SESSION_INVALIDATED", "请求人账号已失效", false);
    }
    return new IdentityPrincipal(
        account.getId(),
        account.getDisplayName(),
        account.getWorkEmail(),
        "",
        account.getHomeDepartmentId(),
        Set.copyOf(accountService.findRoleCodes(userId)),
        Set.copyOf(accountService.findPermissionCodes(userId)),
        account.isMustChangePassword(),
        account.getVersion(),
        true);
  }

  private static String requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    return userId;
  }
}
