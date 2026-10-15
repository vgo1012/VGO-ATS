package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityBootstrapAdminCollisionTest {
  @Test
  void existingBootstrapEmailMustStillBelongToUsableBootstrapAdmin() {
    var manager = new IdentityAdministrationApplicationManager();
    var accounts = mock(IdentityAccountPersistenceService.class);
    ReflectionTestUtils.setField(manager, "accounts", accounts);
    var existing = new UserAccountRecord();
    existing.setId("existing");
    when(accounts.findByNormalizedEmail("admin@example.com")).thenReturn(existing);

    assertThatThrownBy(() -> provision(manager))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("初始管理员邮箱已被非有效初始化超级管理员账号占用");
    when(accounts.isUsableBootstrappedSuperAdmin("existing")).thenReturn(true);
    assertThatCode(() -> provision(manager)).doesNotThrowAnyException();
  }

  private static void provision(IdentityAdministrationApplicationManager manager) {
    manager.provisionInitialSuperAdmin("ROOT", "总部", "管理员", " Admin@Example.com ",
        "Test-Only-Secret-123!");
  }
}
