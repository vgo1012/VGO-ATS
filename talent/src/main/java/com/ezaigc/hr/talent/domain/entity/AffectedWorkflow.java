package com.ezaigc.hr.talent.domain.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AffectedWorkflow {
  /** 资源类型。 */
  private String resourceType;
  /** 资源 ID。 */
  private String resourceId;
  /** 应聘流程 ID。 */
  private String applicationId;
  /** 客户 ID。 */
  private String clientId;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 当前状态。 */
  private String status;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 更新时间。 */
  private Instant updateTime;
}
