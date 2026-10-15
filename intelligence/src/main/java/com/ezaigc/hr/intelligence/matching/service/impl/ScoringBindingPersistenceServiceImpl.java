package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScoringBinding;
import com.ezaigc.hr.intelligence.matching.mapper.ScoringBindingMapper;
import com.ezaigc.hr.intelligence.matching.service.ScoringBindingPersistenceService;
import java.time.Instant;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.intelligence.matching.domain.dto.ScoringBindingDTO;
import org.springframework.stereotype.Service;

@Service
public class ScoringBindingPersistenceServiceImpl
    extends ServiceImpl<ScoringBindingMapper, ScoringBinding> implements ScoringBindingPersistenceService {
  @Override
  public int insertBinding(String jdVersionId, String planVersionId, String actorId, Instant now) {
    var value = new ScoringBinding();
    value.setId(jdVersionId);
    value.setPlanVersionId(planVersionId);
    value.setVersion(1);
    value.setCreateId(actorId);
    value.setUpdateId(actorId);
    value.setCreateTime(now);
    value.setUpdateTime(now);
    return baseMapper.insert(value);
  }

  @Override
  public int updateBinding(String jdVersionId, String planVersionId, long version, String actorId, Instant now) {
    return baseMapper.update(null, Wrappers.lambdaUpdate(ScoringBinding.class)
        .eq(ScoringBinding::getId, jdVersionId).eq(ScoringBinding::getVersion, version)
        .set(ScoringBinding::getPlanVersionId, planVersionId).setIncrBy(ScoringBinding::getVersion, 1)
        .set(ScoringBinding::getUpdateId, actorId).set(ScoringBinding::getUpdateTime, now));
  }
  @Override
  public ScoringBindingDTO binding(String jdVersionId) {
    var binding = getById(jdVersionId);
    if (binding == null) return null;
    var result = new ScoringBindingDTO();
    result.setJdVersionId(binding.getId());
    result.setPlanVersionId(binding.getPlanVersionId());
    result.setVersion(binding.getVersion());
    return result;
  }
  @Override
  public void bindLegacy(String jdVersionId, String planVersionId, String actorId, Instant now) {
    var current = binding(jdVersionId);
    if (current == null) insertBinding(jdVersionId, planVersionId, actorId, now);
    else if (updateBinding(jdVersionId, planVersionId, current.getVersion(), actorId, now) != 1)
      throw PlatformExceptions.conflict("SCORING_BINDING_CHANGED", "评分关联已变化，请刷新后重试");
  }
}
