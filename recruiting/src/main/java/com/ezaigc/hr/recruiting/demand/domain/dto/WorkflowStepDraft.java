package com.ezaigc.hr.recruiting.demand.domain.dto;

import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStepDraft {

  /** 业务编码。 */
  private WorkflowStepCode code;
  /** 默认跟进后续渠道。 */
  private String defaultFollowUpChannel;

}
