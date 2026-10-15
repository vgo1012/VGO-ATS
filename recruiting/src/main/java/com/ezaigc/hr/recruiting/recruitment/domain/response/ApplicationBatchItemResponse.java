package com.ezaigc.hr.recruiting.recruitment.domain.response;

import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationBatchItemStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationBatchItemResponse {
  private String candidateId;
  private String resumeVersionId;
  private String applicationId;
  private ApplicationBatchItemStatus status;
  private String errorCode;
  private String message;
}
