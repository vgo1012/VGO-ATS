package com.ezaigc.hr.intelligence.matching.constant;

/** AI 职业评分版本与有界调用约束；变更提示词须同时升级版本以隔离旧缓存。 */
public final class AiScoringConstants {
  public static final String PROMPT_VERSION = "professional-scoring-v7";
  public static final String SCHEMA_VERSION = "professional-scoring-v2";
  public static final String ALGORITHM_VERSION = "ai-professional-v2";
  public static final int MAX_INPUT_CHARACTERS = 180_000;
  public static final int CLAIM_LEASE_SECONDS = 300;
  private AiScoringConstants() {}
}
