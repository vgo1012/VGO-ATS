package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class SubmissionApplicationContext {
  String applicationId;
  String candidateId;
  String demandId;
  String jdId;
  String jdVersionId;
  String resumeVersionId;
  String recruitmentType;
  String currentStage;
  String ownerUserId;
  String ownerDepartmentId;
  Set<String> collaboratorUserIds;

  public SubmissionApplicationContext(
      String applicationId,
      String candidateId,
      String demandId,
      String jdId,
      String jdVersionId,
      String resumeVersionId,
      String recruitmentType,
      String currentStage,
      String ownerUserId,
      String ownerDepartmentId,
      Set<String> collaboratorUserIds) {
    this.applicationId = applicationId;
    this.candidateId = candidateId;
    this.demandId = demandId;
    this.jdId = jdId;
    this.jdVersionId = jdVersionId;
    this.resumeVersionId = resumeVersionId;
    this.recruitmentType = recruitmentType;
    this.currentStage = currentStage;
    this.ownerUserId = ownerUserId;
    this.ownerDepartmentId = ownerDepartmentId;
    this.collaboratorUserIds = Set.copyOf(collaboratorUserIds);
  }
}
