package com.ezaigc.hr.talent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateIntakeBatchDTO {
  /** 来源渠道 ID。 */
  private String sourceChannelId;

  private String defaultOwnerUserId;

  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;

  /** 本批简历唯一绑定的岗位版本；用于入库后的公司默认评分。 */
  private String jdVersionId;

  public CreateIntakeBatchDTO(
      String sourceChannelId, String defaultOwnerUserId, String ownerDepartmentId) {
    this(sourceChannelId, defaultOwnerUserId, ownerDepartmentId, null);
  }
}
