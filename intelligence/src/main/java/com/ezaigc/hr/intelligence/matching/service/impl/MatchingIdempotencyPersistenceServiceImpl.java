package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingIdempotencyMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchingIdempotencyPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** IdempotencyRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class MatchingIdempotencyPersistenceServiceImpl
    extends ServiceImpl<MatchingIdempotencyMapper, IdempotencyRecord>
    implements MatchingIdempotencyPersistenceService {

  /** 查询幂等。 */
  @Override
  public IdempotencyRecord findIdempotency(String actorId, String key) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(IdempotencyRecord.class)
            .eq(IdempotencyRecord::getActorUserId, actorId)
            .eq(IdempotencyRecord::getIdempotencyKey, key));
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
    IdempotencyRecord record = new IdempotencyRecord();
    record.setActorUserId(actorId);
    record.setIdempotencyKey(key);
    record.setActionCode(actionCode);
    record.setRequestFingerprint(fingerprint);
    record.setTargetId(targetId);
    record.setCreateTime(now);
    return baseMapper.insert(record);
  }

  @Override
  public int insertIgnore(
      String actorId,
      String key,
      String actionCode,
      String fingerprint,
      String targetId,
      Instant now) {
    return baseMapper.insertIgnore(actorId, key, actionCode, fingerprint, targetId, now);
  }
}
