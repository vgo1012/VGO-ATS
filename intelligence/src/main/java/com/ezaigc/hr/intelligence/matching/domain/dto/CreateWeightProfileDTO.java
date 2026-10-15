package com.ezaigc.hr.intelligence.matching.domain.dto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class CreateWeightProfileDTO {
  private String name;
  private ProfessionalWeights weights;
  private List<ControlledCriterionDraft> criteria;
  private String defaultSortMode;
  public CreateWeightProfileDTO(String name, ProfessionalWeights weights) {
    this(name, weights, List.of(), "REFERENCE_SCORE");
  }
}
