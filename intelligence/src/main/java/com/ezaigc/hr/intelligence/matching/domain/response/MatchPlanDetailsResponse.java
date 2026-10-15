package com.ezaigc.hr.intelligence.matching.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchPlanDetailsResponse {
  /** 方案。 */
  private MatchPlanResponse plan;
  /** 技术乐观锁版本号。 */
  private MatchPlanVersionResponse version;
  /** 条件方案。 */
  private ControlledCriterionPlanResponse criterionPlan;
  /** 条件。 */
  private List<ControlledCriterionResponse> criteria;
}
