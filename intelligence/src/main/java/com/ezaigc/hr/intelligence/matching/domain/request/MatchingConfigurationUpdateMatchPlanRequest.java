package com.ezaigc.hr.intelligence.matching.domain.request;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SELECTED_CRITERIA;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingConfigurationUpdateMatchPlanRequest {
  private @NotBlank @Size(max = 120) String name;
  /** 专业维度列表。 */
  private @NotNull @Valid MatchingConfigurationProfessionalDimensionRequest professionalDimensions;
  private @NotNull @Size(max = MAX_SELECTED_CRITERIA) List<@NotNull @Valid ControlledCriterionDraft>
      criteria;
  private @ValidResourceVersion String version;

public @NotNull @Valid MatchingConfigurationProfessionalDimensionRequest
      professionalDimensions() {
    return professionalDimensions;
  }

}
