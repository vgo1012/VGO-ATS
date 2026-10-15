package com.ezaigc.hr.intelligence.matching.mapper;

import java.time.Instant;

/**
 * 匹配配置显式 SQL 操作契约，承载联表、聚合和结构化写入。
 * Typed contract for SQL that remains Mapper-owned because it is state-sensitive or
 * projection-oriented.
 */
public interface MatchingConfigurationSqlOperations {
  /** 统计权重配置方案引用列表。 */
  int countWeightProfileReferences(String profileId);

  /** 新增条件。 */
  int insertCriterion(
      String id,
      String planVersionId,
      String criterionCode,
      String level,
      String operator,
      String expectedValueJson,
      int bonusPoints,
      String reason,
      String evidencePolicy,
      boolean selectableByHr,
      int sequenceNo,
      Instant now);

  /** 查询最大剩余匹配方案版本序号。 */
  int findMaxRemainingMatchPlanVersionNo(String planId);

  /** 查询最大个人偏好版本。 */
  int findMaxPersonalPreferenceVersion(String ownerUserId, String planVersionId);

  /** 新增个人偏好。 */
  int insertPersonalPreference(
      String id,
      String ownerUserId,
      String planVersionId,
      String weightProfileVersionId,
      int versionNo,
      String selectedIdsJson,
      Instant now);
}
