package com.ezaigc.hr.intelligence.matching.domain.request;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SELECTED_CRITERIA;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingConfigurationPersonalPreferenceRequest {
  /** 权重方案版本 ID。 */
  private @PlatformIdentifier String weightProfileVersionId;
  private @NotNull @Size(max = MAX_SELECTED_CRITERIA)
      List<@PlatformIdentifier String> selectedDefinitionIds;

}
