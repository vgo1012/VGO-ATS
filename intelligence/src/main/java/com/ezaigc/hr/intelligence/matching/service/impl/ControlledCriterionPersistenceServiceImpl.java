package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.mapper.ControlledCriterionMapper;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** ControlledCriterion 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ControlledCriterionPersistenceServiceImpl
    extends ServiceImpl<ControlledCriterionMapper, ControlledCriterion>
    implements ControlledCriterionPersistenceService {

  /** 查询条件。 */
  @Override
  public List<ControlledCriterion> findCriteria(String planVersionId) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(ControlledCriterion.class)
            .eq(ControlledCriterion::getPlanVersionId, planVersionId)
            .orderByAsc(ControlledCriterion::getSequenceNo, ControlledCriterion::getId));
  }

  /** 删除条件。 */
  @Override
  public int deleteCriteria(String planVersionId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(ControlledCriterion.class)
            .eq(ControlledCriterion::getPlanVersionId, planVersionId));
  }
}
