package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetails {
  /** 应聘流程。 */
  private Application application;
  private List<String> collaboratorUserIds;
  /** 流程步骤列表。 */
  private List<WorkflowStep> workflowSteps;
  /** 决定列表。 */
  private List<Decision> decisions;
  private List<OfferStatusRecord> offerStatusRecords;
  /** 已经耗时的秒数。 */
  private long elapsedSeconds;
}
