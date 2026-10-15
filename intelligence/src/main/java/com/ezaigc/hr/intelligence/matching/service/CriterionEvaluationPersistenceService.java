package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.CriterionEvaluation;
import java.util.List;

/** 受控条件评价快照的持久化边界。 */
public interface CriterionEvaluationPersistenceService extends IService<CriterionEvaluation> {

  /** 批量新增单个基础评价的受控条件结果；空集合视为无需写入。 */
  boolean insertCriterionEvaluationBatch(List<CriterionEvaluation> evaluations);
}
