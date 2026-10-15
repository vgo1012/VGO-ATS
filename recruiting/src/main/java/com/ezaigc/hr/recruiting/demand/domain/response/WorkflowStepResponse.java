package com.ezaigc.hr.recruiting.demand.domain.response;

import lombok.Data;

@Data
public class WorkflowStepResponse {
  /** 主键 ID。 */
  private String id;
  /** 模板版本 ID。 */
  private String templateVersionId;
  /** 步骤编码。 */
  private String stepCode;
  private String stepCodeDesc;
  /** 顺序号。 */
  private Integer sequenceNo;
  /** 候选人是否可以跳过该步骤。 */
  private Boolean candidateOptional;
  /** 默认跟进后续渠道。 */
  private String defaultFollowUpChannel;
}
