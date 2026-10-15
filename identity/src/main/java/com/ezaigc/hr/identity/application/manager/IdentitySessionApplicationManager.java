package com.ezaigc.hr.identity.application.manager;

import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import jakarta.annotation.Resource;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 每次请求检查账号状态和版本，阻止撤权之后迟到保存的旧会话。 */
@Component
public class IdentitySessionApplicationManager {

  @Resource private IdentityAccountPersistenceService accounts;
  @Resource private UserSupportDepartmentPersistenceService supportDepartments;

  public Set<String> supportDepartmentIds(String userId) {
    return Set.copyOf(supportDepartments.findSupportDepartmentIds(userId));
  }

  public boolean isCurrentSession(IdentityPrincipal principal) {
    UserAccountRecord account = accounts.findById(principal.getUserId());
    return account != null && account.active() && account.getVersion() == principal.getVersion();
  }
}
