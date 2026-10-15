package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationDetailsResponse {
  /** 应聘流程。 */
  private ApplicationResponse application;
  private List<String> collaboratorUserIds;
  /** 流程步骤列表。 */
  private List<ApplicationWorkflowStepResponse> workflowSteps;
  /** 决定列表。 */
  private List<ApplicationDecisionResponse> decisions;
  private List<OfferStatusResponse> offerStatusRecords;
  /** 已经耗时的秒数。 */
  private long elapsedSeconds;
}
