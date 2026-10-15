package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 冻结 AI 判断的来源；权重重算不得修改此记录。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiAssessmentMetadataDTO {
  private String assessmentId;
  private String provider;
  private String model;
  private String promptVersion;
  private String schemaVersion;
  private LocalDate evaluatedOn;
  private BigDecimal defaultProfessionalScore;
  private String summary;
}
