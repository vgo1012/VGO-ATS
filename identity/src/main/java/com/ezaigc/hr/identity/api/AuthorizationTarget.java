package com.ezaigc.hr.identity.api;

import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthorizationTarget {
  /** 授权对象类型。 */
  private String objectType;
  /** 授权对象 ID。 */
  private String objectId;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 部门 ID。 */
  private String departmentId;
  private Set<String> collaboratorUserIds;

  public AuthorizationTarget(
      String objectType,
      String objectId,
      String ownerUserId,
      String departmentId,
      Set<String> collaboratorUserIds) {
    collaboratorUserIds = collaboratorUserIds == null ? Set.of() : Set.copyOf(collaboratorUserIds);
    this.objectType = objectType;
    this.objectId = objectId;
    this.ownerUserId = ownerUserId;
    this.departmentId = departmentId;
    this.collaboratorUserIds = collaboratorUserIds;
  }

}
