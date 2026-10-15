package com.ezaigc.hr.intelligence.matching.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** 公司默认评分与人工准入状态的只读投影。 */
@Data
public class ScreeningCandidateRow {
  private String candidateId;
  private String candidateName;
  private String resumeVersionId;
  private Integer resumeVersionNo;
  private String originalFileName;
  private String jdVersionId;
  private String defaultMatchRunId;
  private BigDecimal professionalScore;
  private int bonusScore;
  private BigDecimal referenceScore;
  private String mustTier;
  private String admissionId;
  private Instant admittedAt;
  private Long admissionVersion;
}
