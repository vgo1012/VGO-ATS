package com.ezaigc.hr.intelligence.matching.domain.response;

import java.time.Instant;
import lombok.Value;

/** 我的简历可准入项或已进入智能筛选的精确版本上下文。 */
@Value
public class ScreeningCandidateResponse {
  String candidateId;
  String resumeVersionId;
  String jdVersionId;
  String defaultMatchRunId;
  DefaultScreeningResultResponse defaultResult;
  String admissionStatus;
  String admissionId;
  Instant admittedAt;
  String admissionVersion;
  boolean ownedByCurrentActor;
}
