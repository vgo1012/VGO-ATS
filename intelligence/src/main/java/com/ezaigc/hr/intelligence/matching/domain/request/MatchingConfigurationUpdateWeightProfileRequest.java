package com.ezaigc.hr.intelligence.matching.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SORT_MODE_LENGTH;

import jakarta.validation.Valid;
import java.util.List;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingConfigurationUpdateWeightProfileRequest {
  private @NotBlank @Size(max = 120) String name;
  /** 权重列表。 */
  private @NotNull @Valid MatchingConfigurationWeightRequest weights;
  private @ValidResourceVersion String version;

  private @Valid @Size(max = 3) List<@NotNull ControlledCriterionDraft> criteria;
  private @Size(max = MAX_SORT_MODE_LENGTH) String defaultSortMode;
}
