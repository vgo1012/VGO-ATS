package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.talent.mapper.TalentIdempotencyMapper;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import org.springframework.stereotype.Service;

/** IdempotencyRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class TalentIdempotencyPersistenceServiceImpl
    extends ServiceImpl<TalentIdempotencyMapper, IdempotencyRecord>
    implements TalentIdempotencyPersistenceService {

  /** 查询幂等。 */
  @Override
  public IdempotencyRecord findIdempotency(String actorId, String key) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(IdempotencyRecord.class)
            .eq(IdempotencyRecord::getActorUserId, actorId)
            .eq(IdempotencyRecord::getIdempotencyKey, key));
  }
}
