package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterionPlan;
import com.ezaigc.hr.intelligence.matching.mapper.ControlledCriterionPlanMapper;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** ControlledCriterionPlan 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ControlledCriterionPlanPersistenceServiceImpl
    extends ServiceImpl<ControlledCriterionPlanMapper, ControlledCriterionPlan>
    implements ControlledCriterionPlanPersistenceService {

  /** 新增条件方案。 */
  @Override
  public int insertCriterionPlan(
      String id, String jdVersionId, int versionNo, int maxBonus, String actorId, Instant now) {
    ControlledCriterionPlan plan = new ControlledCriterionPlan();
    plan.setId(id);
    plan.setJdVersionId(jdVersionId);
    plan.setVersionNo(versionNo);
    plan.setPlanMaxBonusPoints(maxBonus);
    plan.setCreateId(actorId);
    plan.setCreateTime(now);
    plan.setUpdateId(actorId);
    plan.setUpdateTime(now);
    return baseMapper.insert(plan);
  }

  /** 查询条件方案。 */
  @Override
  public ControlledCriterionPlan findCriterionPlan(String id) {
    return baseMapper.selectById(id);
  }

  /** 更新条件方案。 */
  @Override
  public int updateCriterionPlan(String id, int maxBonus, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ControlledCriterionPlan.class)
            .eq(ControlledCriterionPlan::getId, id)
            .set(ControlledCriterionPlan::getPlanMaxBonusPoints, maxBonus)
            .set(ControlledCriterionPlan::getUpdateId, actorId)
            .set(ControlledCriterionPlan::getUpdateTime, now));
  }

  /** 删除条件方案。 */
  @Override
  public int deleteCriterionPlan(String id) {
    return baseMapper.deleteById(id);
  }
}
