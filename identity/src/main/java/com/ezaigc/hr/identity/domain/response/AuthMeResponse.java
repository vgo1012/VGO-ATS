package com.ezaigc.hr.identity.domain.response;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;

import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthMeResponse {
  /** 用户 ID。 */
  private String userId;
  /** 用户展示名称。 */
  private String displayName;
  /** 工作邮箱。 */
  private String workEmail;
  /** 用户主部门 ID。 */
  private String homeDepartmentId;
  /** 支持部门 ID，仅用于本人可分配范围，不授予其他人的业务访问权。 */
  private Set<String> supportDepartmentIds;
  /** 角色列表。 */
  private Set<String> roles;
  /** 权限列表。 */
  private Set<String> permissions;
  /** 超级管理员。 */
  private boolean superAdmin;
  /** 下次登录是否必须修改密码。 */
  private boolean mustChangePassword;
  /** 技术乐观锁版本号。 */
  private String version;

  public static AuthMeResponse from(IdentityPrincipal principal, Set<String> supportDepartmentIds) {
    return new AuthMeResponse(
        principal.getUserId(),
        principal.getDisplayName(),
        principal.getWorkEmail(),
        principal.getHomeDepartmentId(),
        Set.copyOf(supportDepartmentIds),
        principal.getRoles(),
        principal.getPermissions(),
        principal.superAdmin(),
        principal.isMustChangePassword(),
        format(principal.getVersion()));
  }
}
