package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class DemandResponse {
  /** 主键 ID。 */
  private String id;
  /** 业务编码。 */
  private String code;
  /** 标题。 */
  private String title;
  /** 客户 ID。 */
  private String clientId;
  /** 项目 ID。 */
  private String projectId;
  /** 职位 ID。 */
  private String positionId;
  /** 招聘类型。 */
  private String recruitmentType;
  /** 计划招聘人数。 */
  private Integer headcount;
  /** 描述。 */
  private String description;
  /** 当前状态。 */
  private String status;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 更新时间。 */
  private Instant updateTime;
}
