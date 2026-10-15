package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 身份域内置角色编码。 */
@Getter
@RequiredArgsConstructor
public enum IdentityRoleCode implements CodeDescEnum {
  SUPER_ADMIN("超级管理员", false),
  RECRUITER("招聘人员", true),
  RECRUITMENT_MANAGER("部门管理", true),
  HR_DIRECTOR("人力负责人", true),
  INTERVIEWER("面试官", true);

  private final String desc;
  private final boolean assignable;
  public static boolean isAssignableCode(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.assignable && value.matches(code));
  }
}
