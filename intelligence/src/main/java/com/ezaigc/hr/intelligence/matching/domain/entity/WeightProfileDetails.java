package com.ezaigc.hr.intelligence.matching.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeightProfileDetails {
  /** 配置方案。 */
  private WeightProfile profile;
  /** 当前版本。 */
  private WeightProfileVersion currentVersion;
}
