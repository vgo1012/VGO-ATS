package com.ezaigc.hr.identity.domain.response;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserOrganizationAssignmentsResponse {
  /** 用户 ID。 */
  private String userId;
  private Set<String> supportDepartmentIds;
  /** 角色列表。 */
  private Set<String> roles;
}
