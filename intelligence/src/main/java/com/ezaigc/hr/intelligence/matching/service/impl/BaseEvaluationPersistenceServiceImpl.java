package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.BaseEvaluation;
import com.ezaigc.hr.intelligence.matching.mapper.BaseEvaluationMapper;
import com.ezaigc.hr.intelligence.matching.service.BaseEvaluationPersistenceService;
import org.springframework.stereotype.Service;

/** BaseEvaluation 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class BaseEvaluationPersistenceServiceImpl
    extends ServiceImpl<BaseEvaluationMapper, BaseEvaluation>
    implements BaseEvaluationPersistenceService {

  /** 查询基础操作人缓存。 */
  @Override
  public BaseEvaluation findBaseByCache(String cacheKey) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(BaseEvaluation.class).eq(BaseEvaluation::getCacheKey, cacheKey));
  }

  @Override
  public BaseEvaluation findBase(String id) {
    return baseMapper.selectById(id);
  }
}
