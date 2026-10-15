package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationBatchCreateDTO {
  private List<CreateApplicationDTO> items;
  private List<WorkflowStepCode> selectedStepCodes;
}
