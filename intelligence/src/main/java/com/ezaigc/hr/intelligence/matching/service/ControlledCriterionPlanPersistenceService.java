package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterionPlan;
import java.time.Instant;

/** ControlledCriterionPlan 实体的持久化服务。 */
public interface ControlledCriterionPlanPersistenceService
    extends IService<ControlledCriterionPlan> {

  /** 新增条件方案。 */
  int insertCriterionPlan(
      String id, String jdVersionId, int versionNo, int maxBonus, String actorId, Instant now);

  /** 查询条件方案。 */
  ControlledCriterionPlan findCriterionPlan(String id);

  /** 更新条件方案。 */
  int updateCriterionPlan(String id, int maxBonus, String actorId, Instant now);

  /** 删除条件方案。 */
  int deleteCriterionPlan(String id);
}
