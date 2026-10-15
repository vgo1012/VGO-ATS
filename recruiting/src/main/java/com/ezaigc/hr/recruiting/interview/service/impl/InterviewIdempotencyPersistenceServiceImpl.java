package com.ezaigc.hr.recruiting.interview.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.interview.mapper.IdempotencyRecordMapper;
import com.ezaigc.hr.recruiting.interview.service.InterviewIdempotencyPersistenceService;
import org.springframework.stereotype.Service;

/** IdempotencyRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class InterviewIdempotencyPersistenceServiceImpl
    extends ServiceImpl<IdempotencyRecordMapper, IdempotencyRecord>
    implements InterviewIdempotencyPersistenceService {

  /** 查询幂等。 */
  @Override
  public IdempotencyRecord findIdempotency(String actorId, String key) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(IdempotencyRecord.class)
            .eq(IdempotencyRecord::getActorUserId, actorId)
            .eq(IdempotencyRecord::getIdempotencyKey, key));
  }

  /** 只供预留失败后的当前读；初始普通查询保持快照语义。 */
  @Override
  public IdempotencyRecord findIdempotencyForUpdate(String actorId, String key) {
    return baseMapper.findIdempotencyForUpdate(actorId, key);
  }
}
