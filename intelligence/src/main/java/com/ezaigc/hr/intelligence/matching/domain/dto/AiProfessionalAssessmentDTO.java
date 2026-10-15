package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Validated model assessment before persistence. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProfessionalAssessmentDTO {
  private BigDecimal professionalScore;
  private String summary;
  private List<AiProfessionalDimensionDTO> dimensions;
}
