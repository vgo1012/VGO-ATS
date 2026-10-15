package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlan;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingConfigurationMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchingConfigurationPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 匹配配置持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin persistence implementation. Business orchestration belongs to application managers.
 */
@Service
public class MatchingConfigurationPersistenceServiceImpl
    extends ServiceImpl<MatchingConfigurationMapper, MatchPlan>
    implements MatchingConfigurationPersistenceService {
  /** 更新匹配方案名称。 */
  @Override
  public int updateMatchPlanName(String id, String name, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(MatchPlan.class)
            .eq(MatchPlan::getId, id)
            .set(MatchPlan::getName, name)
            .set(MatchPlan::getUpdateId, actorId)
            .set(MatchPlan::getUpdateTime, now));
  }

  /** 更新匹配方案当前版本。 */
  @Override
  public int updateMatchPlanCurrentVersion(String id, int versionNo, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(MatchPlan.class)
            .eq(MatchPlan::getId, id)
            .set(MatchPlan::getCurrentVersionNo, versionNo)
            .set(MatchPlan::getUpdateId, actorId)
            .set(MatchPlan::getUpdateTime, now));
  }

  /** 删除匹配方案。 */
  @Override
  public int deleteMatchPlan(String id) {
    return baseMapper.deleteById(id);
  }

  /** 新增匹配方案。 */
  @Override
  public int insertMatchPlan(
      String id, String jdVersionId, String name, String actorId, Instant now) {
    MatchPlan plan = new MatchPlan();
    plan.setId(id);
    plan.setJdVersionId(jdVersionId);
    plan.setName(name);
    plan.setCurrentVersionNo(1);
    plan.setCreateId(actorId);
    plan.setCreateTime(now);
    plan.setUpdateId(actorId);
    plan.setUpdateTime(now);
    return baseMapper.insert(plan);
  }

  /** 查询匹配方案。 */
  @Override
  public MatchPlan findMatchPlan(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public int countWeightProfileReferences(String profileId) {
    return baseMapper.countWeightProfileReferences(profileId);
  }

  @Override
  public int insertCriterion(
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
      Instant now) {
    return baseMapper.insertCriterion(
        id,
        planVersionId,
        criterionCode,
        level,
        operator,
        expectedValueJson,
        bonusPoints,
        reason,
        evidencePolicy,
        selectableByHr,
        sequenceNo,
        now);
  }

  @Override
  public int findMaxRemainingMatchPlanVersionNo(String planId) {
    return baseMapper.findMaxRemainingMatchPlanVersionNo(planId);
  }

  @Override
  public int findMaxPersonalPreferenceVersion(String ownerUserId, String planVersionId) {
    return baseMapper.findMaxPersonalPreferenceVersion(ownerUserId, planVersionId);
  }

  @Override
  public int insertPersonalPreference(
      String id,
      String ownerUserId,
      String planVersionId,
      String weightProfileVersionId,
      int versionNo,
      String selectedIdsJson,
      Instant now) {
    return baseMapper.insertPersonalPreference(
        id, ownerUserId, planVersionId, weightProfileVersionId, versionNo, selectedIdsJson, now);
  }
}
