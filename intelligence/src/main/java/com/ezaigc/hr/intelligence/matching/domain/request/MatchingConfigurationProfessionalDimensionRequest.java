package com.ezaigc.hr.intelligence.matching.domain.request;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingConfigurationProfessionalDimensionRequest {
  /** 技能。 */
  @AssertTrue(message = "核心技能必须参与评分")
  private boolean skills;
  /** 角色经历。 */
  @AssertTrue(message = "岗位经历必须参与评分")
  private boolean roleExperience;
  /** 相关年限。 */
  @AssertTrue(message = "相关年限必须参与评分")
  private boolean relatedYears;
  /** 职责匹配。 */
  @AssertTrue(message = "职责匹配必须参与评分")
  private boolean responsibilityMatch;
  /** 行业与项目列表。 */
  @AssertTrue(message = "行业与项目必须参与评分")
  private boolean industryAndProjects;

}
