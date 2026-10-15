package com.ezaigc.hr.intelligence.matching.domain.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingConfigurationWeightRequest {
  /** 技能。 */
  private int skills;
  /** 角色经历。 */
  private int roleExperience;
  /** 相关年限。 */
  private int relatedYears;
  /** 职责匹配。 */
  private int responsibilityMatch;
  /** 行业与项目列表。 */
  private int industryAndProjects;

}
