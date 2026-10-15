package com.ezaigc.hr.intelligence.matching.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingConfigurationVersionReasonRequest {
  private @ValidResourceVersion String version;
  private @NotBlank @Size(max = 500) String reason;

}
