package com.ezaigc.hr.recruiting.demand.domain.dto;

import lombok.Data;

@Data
public class CreateJdVersionDTO {
  /** 仅新增时使用的明确工作区域，自动分配编号维度。 */
  private String workRegionName;
  /** 历史兼容字段；首次创建 JD 由岗位名称生成。 */
  private String jobTypeCode;
  /** 历史兼容字段；首次创建 JD 由工作地区生成。 */
  private String workRegionCode;

  private String ownerUserId;
  private String ownerDepartmentId;
  private String recruitmentType;
  private String clientId;
  private String appliedFromJdVersionId;
  private String distinction;
  private boolean publicVisible;
  private Boolean contentShared;
  private boolean forceNew;
  private Integer headcount;
  private Integer recruitmentCycleDays;
  private Long version;
  /** 标题。 */
  private String title;
  /** 原始文本内容。 */
  private String rawText;
  /** 数据来源类型。 */
  private String sourceType;
}
