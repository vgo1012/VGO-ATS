package com.ezaigc.hr.recruiting.submission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.submission.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.submission.mapper.IdempotencyRecordMapper;
import com.ezaigc.hr.recruiting.submission.service.SubmissionIdempotencyPersistenceService;
import org.springframework.stereotype.Service;

/** IdempotencyRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class SubmissionIdempotencyPersistenceServiceImpl
    extends ServiceImpl<IdempotencyRecordMapper, IdempotencyRecord>
    implements SubmissionIdempotencyPersistenceService {

  @Override
  public IdempotencyRecord idempotencyForUpdate(String actorId, String key) {
    return baseMapper.findForUpdate(actorId, key);
  }

  @Override
  public IdempotencyRecord idempotency(String actorId, String key) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(IdempotencyRecord.class)
            .eq(IdempotencyRecord::getActorUserId, actorId)
            .eq(IdempotencyRecord::getIdempotencyKey, key));
  }
}
