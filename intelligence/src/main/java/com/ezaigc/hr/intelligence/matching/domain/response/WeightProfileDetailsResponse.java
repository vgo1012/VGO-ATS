package com.ezaigc.hr.intelligence.matching.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WeightProfileDetailsResponse {
  /** 配置方案。 */
  private WeightProfileResponse profile;
  /** 当前版本。 */
  private WeightProfileVersionResponse currentVersion;
}
