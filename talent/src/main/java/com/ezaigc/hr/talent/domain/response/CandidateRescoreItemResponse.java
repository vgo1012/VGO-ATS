package com.ezaigc.hr.talent.domain.response;

import lombok.Value;

@Value
public class CandidateRescoreItemResponse {
  String scoreBindingId;
  String candidateId;
  String resumeVersionId;
  String jdId;
  String jdVersionId;
  String defaultMatchRunId;
  String taskId;
}
