package com.ezaigc.hr.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.application.adapter.IdentityUserDetailsAdapter;
import com.ezaigc.hr.identity.config.IdentityLoginAliasProperties;
import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityUserDetailsServiceTest {

  @Test
  void normalizesEmailAndBuildsRoleAndPermissionAuthorities() {
    IdentityAccountPersistenceService mapper = mock(IdentityAccountPersistenceService.class);
    when(mapper.findByNormalizedEmail("hr@example.com"))
        .thenReturn(
            new UserAccountRecord(
                "user-id",
                "HR",
                "hr@example.com",
                "hr@example.com",
                "{noop}secret",
                "department-id",
                "ACTIVE",
                false,
                3));
    when(mapper.findRoleCodes("user-id")).thenReturn(List.of("RECRUITER"));
    when(mapper.findPermissionCodes("user-id")).thenReturn(List.of("talent.read"));

    IdentityPrincipal principal =
        (IdentityPrincipal) service(mapper).loadUserByUsername(" HR@Example.COM ");

    assertThat(principal.getRoles()).containsExactly("RECRUITER");
    assertThat(principal.getPermissions()).containsExactly("talent.read");
    assertThat(principal.getAuthorities())
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_RECRUITER", "talent.read");
  }

  @Test
  void hidesWhetherAnAccountExists() {
    IdentityAccountPersistenceService mapper = mock(IdentityAccountPersistenceService.class);

    assertThatThrownBy(() -> service(mapper).loadUserByUsername("missing@example.com"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessage("账号或凭据错误");
  }

  @Test
  void resolvesConfiguredBuiltInAdminAliasWithoutChangingTheWorkEmail() {
    IdentityAccountPersistenceService mapper = mock(IdentityAccountPersistenceService.class);
    when(mapper.findByNormalizedEmail("admin")).thenReturn(null);
    when(mapper.findByNormalizedEmail("owner@example.com"))
        .thenReturn(
            new UserAccountRecord(
                "admin-id",
                "系统管理员",
                "owner@example.com",
                "owner@example.com",
                "{noop}secret",
                "department-id",
                "ACTIVE",
                false,
                1));
    when(mapper.findRoleCodes("admin-id")).thenReturn(List.of("SUPER_ADMIN"));
    when(mapper.findPermissionCodes("admin-id")).thenReturn(List.of());

    IdentityPrincipal principal = (IdentityPrincipal) service(mapper).loadUserByUsername(" ADMIN ");

    assertThat(principal.getWorkEmail()).isEqualTo("owner@example.com");
    assertThat(principal.getRoles()).containsExactly("SUPER_ADMIN");
  }

  private static IdentityUserDetailsAdapter service(IdentityAccountPersistenceService mapper) {
    IdentityUserDetailsAdapter service = new IdentityUserDetailsAdapter();
    IdentityLoginAliasProperties properties = new IdentityLoginAliasProperties();
    properties.setAdminWorkEmail("owner@example.com");
    ReflectionTestUtils.setField(service, "accountService", mapper);
    ReflectionTestUtils.setField(service, "loginAliasProperties", properties);
    return service;
  }
}
