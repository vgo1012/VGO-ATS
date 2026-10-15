package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class InterviewContext {
  String applicationId;
  String candidateId;
  String jdId;
  String jdVersionId;
  String workflowTemplateVersionId;
  String recruitmentType;
  String workflowStepId;
  String stepCode;
  String stepStatus;
  String ownerUserId;
  String ownerDepartmentId;
  Set<String> collaboratorUserIds;

  public InterviewContext(
      String applicationId,
      String candidateId,
      String jdId,
      String jdVersionId,
      String workflowTemplateVersionId,
      String recruitmentType,
      String workflowStepId,
      String stepCode,
      String stepStatus,
      String ownerUserId,
      String ownerDepartmentId,
      Set<String> collaboratorUserIds) {
    this.applicationId = applicationId;
    this.candidateId = candidateId;
    this.jdId = jdId;
    this.jdVersionId = jdVersionId;
    this.workflowTemplateVersionId = workflowTemplateVersionId;
    this.recruitmentType = recruitmentType;
    this.workflowStepId = workflowStepId;
    this.stepCode = stepCode;
    this.stepStatus = stepStatus;
    this.ownerUserId = ownerUserId;
    this.ownerDepartmentId = ownerDepartmentId;
    this.collaboratorUserIds = Set.copyOf(collaboratorUserIds);
  }
}
