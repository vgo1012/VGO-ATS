package com.ezaigc.hr.intelligence.matching.domain.dto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class UpdateWeightProfileDTO {
  private String name;
  private ProfessionalWeights weights;
  private long version;
  private List<ControlledCriterionDraft> criteria;
  private String defaultSortMode;
  public UpdateWeightProfileDTO(String name, ProfessionalWeights weights, long version) {
    this(name, weights, version, List.of(), "REFERENCE_SCORE");
  }
}
