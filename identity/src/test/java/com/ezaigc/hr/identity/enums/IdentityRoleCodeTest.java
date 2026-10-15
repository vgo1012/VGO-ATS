package com.ezaigc.hr.identity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityRoleCodeTest {

  @Test
  void onlyOrdinaryBusinessRolesAreAssignable() {
    assertThat(IdentityRoleCode.isAssignableCode("RECRUITER")).isTrue();
    assertThat(IdentityRoleCode.isAssignableCode("RECRUITMENT_MANAGER")).isTrue();
    assertThat(IdentityRoleCode.isAssignableCode("HR_DIRECTOR")).isTrue();
    assertThat(IdentityRoleCode.isAssignableCode("INTERVIEWER")).isTrue();
    assertThat(IdentityRoleCode.isAssignableCode("SUPER_ADMIN")).isFalse();
    assertThat(IdentityRoleCode.isAssignableCode("UNKNOWN")).isFalse();
  }
}
