package com.ezaigc.hr.talent.api;

import lombok.Value;
import lombok.experimental.Accessors;

public interface ResumeSubmissionQueryService {
  ResumeSubmissionSnapshot requireSubmissionSnapshot(String candidateId, String resumeVersionId);

  @Value
  @Accessors(fluent = true)
  class ResumeSubmissionSnapshot {
    String resumeVersionId;
    String candidateId;
    String originalFileName;
    String contentSha256;
  }
}
