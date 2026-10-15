package com.ezaigc.hr.identity.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserResponse {
  /** 主键 ID。 */
  private String id;
  /** 用户展示名称。 */
  private String displayName;
  /** 工作邮箱。 */
  private String workEmail;
  /** 来源职位名称。 */
  private String sourceTitle;
  /** 用户主部门 ID。 */
  private String homeDepartmentId;
  /** 主属部门名称。 */
  private String homeDepartmentName;
  /** 直属领导用户 ID。 */
  private String directLeaderUserId;
  /** 直属领导名称。 */
  private String directLeaderName;
  /** 当前状态。 */
  private String status;
  /** 下次登录是否必须修改密码。 */
  private boolean mustChangePassword;
  /** 技术乐观锁版本号。 */
  private String version;
}
