package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.BaseEvaluation;

/** BaseEvaluation 实体的持久化服务。 */
public interface BaseEvaluationPersistenceService extends IService<BaseEvaluation> {

  /** 查询基础操作人缓存。 */
  BaseEvaluation findBaseByCache(String cacheKey);

  BaseEvaluation findBase(String id);
}
