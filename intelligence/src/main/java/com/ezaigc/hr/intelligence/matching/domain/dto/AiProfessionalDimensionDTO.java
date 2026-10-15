package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One AI-judged professional dimension. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProfessionalDimensionDTO {
  private String code;
  private String status;
  private BigDecimal score;
  private String explanation;
  private List<String> highlights;
}
