package com.ezaigc.hr.identity.application.adapter;

import com.ezaigc.hr.identity.config.IdentityLoginAliasProperties;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import jakarta.annotation.Resource;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserDetailsAdapter implements UserDetailsService {

  @Resource private IdentityAccountPersistenceService accountService;
  @Resource private IdentityLoginAliasProperties loginAliasProperties;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    String normalizedEmail = normalize(username);
    UserAccountRecord account = accountService.findByNormalizedEmail(normalizedEmail);
    if (account == null
        && "admin".equals(normalizedEmail)
        && !loginAliasProperties.getAdminWorkEmail().isBlank()) {
      account = accountService.findByNormalizedEmail(normalize(loginAliasProperties.getAdminWorkEmail()));
    }
    if (account == null) {
      throw new UsernameNotFoundException("账号或凭据错误");
    }
    return new IdentityPrincipal(
        account.getId(),
        account.getDisplayName(),
        account.getWorkEmail(),
        account.getCredentialHash(),
        account.getHomeDepartmentId(),
        Set.copyOf(accountService.findRoleCodes(account.getId())),
        Set.copyOf(accountService.findPermissionCodes(account.getId())),
        account.isMustChangePassword(),
        account.getVersion(),
        account.active());
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
  }
}
