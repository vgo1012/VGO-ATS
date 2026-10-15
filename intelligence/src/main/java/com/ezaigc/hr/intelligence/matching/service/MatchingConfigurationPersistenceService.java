package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlan;
import java.time.Instant;

/**
 * 匹配配置持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary for matching configuration data and state-sensitive SQL.
 */
public interface MatchingConfigurationPersistenceService extends IService<MatchPlan> {

  /** 统计权重配置方案引用列表。 */
  int countWeightProfileReferences(String profileId);

  /** 新增匹配方案。 */
  int insertMatchPlan(String id, String jdVersionId, String name, String actorId, Instant now);

  /** 查询匹配方案。 */
  MatchPlan findMatchPlan(String id);

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

  /** 更新匹配方案名称。 */
  int updateMatchPlanName(String id, String name, String actorId, Instant now);

  /** 更新匹配方案当前版本。 */
  int updateMatchPlanCurrentVersion(String id, int versionNo, String actorId, Instant now);

  /** 查询最大剩余匹配方案版本序号。 */
  int findMaxRemainingMatchPlanVersionNo(String planId);

  /** 删除匹配方案。 */
  int deleteMatchPlan(String id);

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
