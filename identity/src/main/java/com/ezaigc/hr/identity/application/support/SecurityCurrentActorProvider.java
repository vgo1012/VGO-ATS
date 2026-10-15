package com.ezaigc.hr.identity.application.support;

import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityCurrentActorProvider implements CurrentActorProvider {

  @Override
  public Optional<CurrentActor> currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
      return Optional.empty();
    }
    return Optional.of(
        new CurrentActor(principal.getUserId(), principal.getPermissions(), principal.superAdmin()));
  }
}
