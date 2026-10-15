package com.ezaigc.hr.identity.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DepartmentResponse {
  /** 主键 ID。 */
  private String id;
  /** 业务编码。 */
  private String code;
  /** 名称。 */
  private String name;
  /** 上级部门 ID。 */
  private String parentDepartmentId;
  /** 部门分配队列负责人用户 ID。 */
  private String assignmentQueueOwnerUserId;
  /** 部门分配队列负责人姓名。 */
  private String assignmentQueueOwnerName;
  /** 当前状态。 */
  private String status;
  /** 技术乐观锁版本号。 */
  private String version;
}
