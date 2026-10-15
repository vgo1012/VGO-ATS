package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.IdempotencyRecordEntity;
import com.ezaigc.hr.recruiting.demand.mapper.DemandIdempotencyMapper;
import com.ezaigc.hr.recruiting.demand.service.DemandIdempotencyPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** IdempotencyRecordEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DemandIdempotencyPersistenceServiceImpl
    extends ServiceImpl<DemandIdempotencyMapper, IdempotencyRecordEntity>
    implements DemandIdempotencyPersistenceService {

  /** 查询幂等。 */
  @Override
  public IdempotencyRecordEntity findIdempotency(String actorId, String key) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(IdempotencyRecordEntity.class)
            .eq(IdempotencyRecordEntity::getActorUserId, actorId)
            .eq(IdempotencyRecordEntity::getIdempotencyKey, key));
  }

  /** 新增幂等。 */
  @Override
  public int insertIdempotency(
      String actorId,
      String key,
      String actionCode,
      String fingerprint,
      String targetId,
      Instant now) {
    IdempotencyRecordEntity record = new IdempotencyRecordEntity();
    record.setActorUserId(actorId);
    record.setIdempotencyKey(key);
    record.setActionCode(actionCode);
    record.setRequestFingerprint(fingerprint);
    record.setTargetId(targetId);
    record.setCreateTime(now);
    return baseMapper.insert(record);
  }
}
