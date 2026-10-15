package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import java.util.List;

/** ControlledCriterion 实体的持久化服务。 */
public interface ControlledCriterionPersistenceService extends IService<ControlledCriterion> {

  /** 查询条件。 */
  List<ControlledCriterion> findCriteria(String planVersionId);

  /** 删除条件。 */
  int deleteCriteria(String planVersionId);
}
