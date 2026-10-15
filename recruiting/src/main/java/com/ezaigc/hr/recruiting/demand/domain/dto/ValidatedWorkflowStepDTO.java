package com.ezaigc.hr.recruiting.demand.domain.dto;

import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidatedWorkflowStepDTO {
  /** 业务编码。 */
  private WorkflowStepCode code;
  /** 顺序号。 */
  private int sequenceNo;
  /** 候选人是否可以跳过该步骤。 */
  private boolean candidateOptional;
  /** 默认跟进后续渠道。 */
  private String defaultFollowUpChannel;
}
