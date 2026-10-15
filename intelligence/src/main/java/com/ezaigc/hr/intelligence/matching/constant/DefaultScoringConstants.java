package com.ezaigc.hr.intelligence.matching.constant;

/** 公司统一评分基线的持久化版本标识，与部署种子保持一致。历史运行仍读取冻结版本。 */
public final class DefaultScoringConstants {
  public static final String PLAN_VERSION_ID = "00000000000000000000000012";
  public static final String WEIGHT_VERSION_ID = "00000000000000000000000022";
  private DefaultScoringConstants() {}
}
