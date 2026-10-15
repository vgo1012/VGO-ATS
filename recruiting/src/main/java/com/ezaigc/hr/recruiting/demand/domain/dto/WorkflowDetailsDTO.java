package com.ezaigc.hr.recruiting.demand.domain.dto;

import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowTemplateEntity;
import java.util.List;
import lombok.Data;

@Data
public class WorkflowDetailsDTO {
  /** 模板。 */
  private WorkflowTemplateEntity template;
  /** 步骤列表。 */
  private List<WorkflowStepEntity> steps;
}
