package com.ezaigc.hr.talent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class UpdateCandidateDTO {
  /** 姓名。 */
  private String fullName;
  /** 邮箱地址。 */
  @ToString.Exclude private String email;
  /** 联系电话。 */
  @ToString.Exclude private String phone;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  /** 技术乐观锁版本号。 */
  private long version;
}
