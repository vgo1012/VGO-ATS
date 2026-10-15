package com.ezaigc.hr.recruiting.demand.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class CreateDemandDTO {
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
  private int headcount;
  /** 描述。 */
  private String description;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  private List<String> collaboratorUserIds;
}
