package com.ezaigc.hr.intelligence.matching.domain.response;

import java.math.BigDecimal;
import lombok.Value;

/** 当前岗位可重新评分的精确准入版本；分数为准入时冻结的公司结果。 */
@Value
public class ScreeningSelectionCandidateResponse {
  String candidateId;
  String candidateName;
  String resumeVersionId;
  int resumeVersionNo;
  String originalFileName;
  String jdVersionId;
  String defaultMatchRunId;
  String admissionId;
  BigDecimal professionalScore;
  BigDecimal referenceScore;
}
