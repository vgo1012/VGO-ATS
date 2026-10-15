package com.ezaigc.hr.recruiting.recruitment.api;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 生效匹配方案查询业务服务契约。
 *
 * Matching capability required by the recruiting workflow.
 */
public interface ActiveMatchPlanQueryService {
  ActiveMatchPlanSnapshot requireActive(String jdVersionId);

  @Value
  @Accessors(fluent = true)
  class ActiveMatchPlanSnapshot {
    String matchPlanVersionId;
    String jdVersionId;
    String controlledCriterionPlanVersionId;
    int versionNo;
  }
}
