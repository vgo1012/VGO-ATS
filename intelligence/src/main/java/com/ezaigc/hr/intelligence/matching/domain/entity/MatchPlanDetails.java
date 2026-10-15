package com.ezaigc.hr.intelligence.matching.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchPlanDetails {
  /** 方案。 */
  private MatchPlan plan;
  /** 技术乐观锁版本号。 */
  private MatchPlanVersion version;
  /** 条件方案。 */
  private ControlledCriterionPlan criterionPlan;
  /** 条件。 */
  private List<ControlledCriterion> criteria;
}
