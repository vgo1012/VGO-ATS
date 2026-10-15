package com.ezaigc.hr.recruiting.demand.domain.response;

import java.util.List;
import lombok.Data;

@Data
public class RecruitmentReadinessResponse {
  private String jdId;
  /** 稳定的岗位业务编号。 */
  private String jdCode;
  private String jdVersionId;
  private String jdTitle;
  private String demandId;
  private String recruitmentType;
  private String clientId;
  private String clientName;
  private Integer headcount;
  private int occupiedHeadcount;
  private Integer remainingHeadcount;
  private boolean canProcess;
  private boolean canManageWorkflow;
  private String activeWorkflowId;
  private WorkflowDetailsResponse activeWorkflow;
  private List<WorkflowDetailsResponse> draftWorkflows;
  private List<String> standardSteps;
  public RecruitmentReadinessResponse(String jdId, String jdVersionId, String jdTitle, String demandId, String recruitmentType,
      boolean canProcess, String activeWorkflowId, WorkflowDetailsResponse activeWorkflow,
      List<WorkflowDetailsResponse> draftWorkflows, List<String> standardSteps) {
    this.jdId = jdId; this.jdVersionId = jdVersionId; this.jdTitle = jdTitle; this.demandId = demandId;
    this.recruitmentType = recruitmentType; this.canProcess = canProcess; this.activeWorkflowId = activeWorkflowId;
    this.activeWorkflow = activeWorkflow; this.draftWorkflows = draftWorkflows; this.standardSteps = standardSteps;
  }
}
