package com.ezaigc.hr.recruiting.submission.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateSubmissionDTO {
  /** 应聘流程 ID。 */
  private String applicationId;
}
