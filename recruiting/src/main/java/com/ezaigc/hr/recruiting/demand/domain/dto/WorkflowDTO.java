package com.ezaigc.hr.recruiting.demand.domain.dto;

import java.util.List;
import lombok.Data;

@Data
public class WorkflowDTO {
  /** 招聘类型。 */
  private String recruitmentType;
  /** 步骤列表。 */
  private List<WorkflowStepDraft> steps;
  /** 技术乐观锁版本号。 */
  private long version;
}
