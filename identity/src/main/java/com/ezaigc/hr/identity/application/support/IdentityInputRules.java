package com.ezaigc.hr.identity.application.support;

import static com.ezaigc.hr.identity.constant.IdentityConstants.DEPARTMENT_CODE_INPUT_PATTERN;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ROLE_CODE_INPUT_LENGTH;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import java.util.Locale;

/** 身份管理命令的输入规范化与稳定错误契约。 */
public final class IdentityInputRules {

  private IdentityInputRules() {}

  public static void validateNewPassword(String value) {
    if (value == null || value.isBlank()) {
      throw PlatformExceptions.badRequest("PASSWORD_POLICY_VIOLATION", "新密码不能为空");
    }
  }

  public static String normalizeEmail(String value) {
    requireText(value, "工作邮箱不能为空");
    String normalized = value.strip().toLowerCase(Locale.ROOT);
    if (normalized.length() > 320 || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw PlatformExceptions.badRequest("WORK_EMAIL_INVALID", "工作邮箱格式无效");
    }
    return normalized;
  }

  public static String normalizeCode(String value) {
    requireText(value, "部门编码不能为空");
    String normalized = value.strip().toUpperCase(Locale.ROOT);
    if (!normalized.matches(DEPARTMENT_CODE_INPUT_PATTERN)) {
      throw PlatformExceptions.badRequest(
          "DEPARTMENT_CODE_INVALID", "部门编码只能包含大写字母、数字、下划线或短横线");
    }
    return normalized;
  }

  public static String normalizeRole(String value) {
    requireText(value, "角色编码不能为空");
    if (value.length() > MAX_ROLE_CODE_INPUT_LENGTH) {
      throw PlatformExceptions.badRequest("ROLE_CODE_INVALID", "角色编码长度不能超过 64 位");
    }
    return value.strip().toUpperCase(Locale.ROOT);
  }

  public static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw PlatformExceptions.badRequest("REQUIRED_FIELD_MISSING", message);
    }
  }
}
