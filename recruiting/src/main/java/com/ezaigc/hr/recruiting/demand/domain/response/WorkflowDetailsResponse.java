package com.ezaigc.hr.recruiting.demand.domain.response;

import java.util.List;
import lombok.Data;

@Data
public class WorkflowDetailsResponse {
  /** 模板。 */
  private WorkflowTemplateResponse template;
  private List<WorkflowStepResponse> steps = List.of();
}
