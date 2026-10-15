package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.recruitment.mapper.IdempotencyRecordMapper;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentIdempotencyPersistenceService;
import org.springframework.stereotype.Service;

/** IdempotencyRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class RecruitmentIdempotencyPersistenceServiceImpl
    extends ServiceImpl<IdempotencyRecordMapper, IdempotencyRecord>
    implements RecruitmentIdempotencyPersistenceService {

  @Override
  public IdempotencyRecord findIdempotencyForUpdate(String actorId, String key) {
    return getOne(Wrappers.<IdempotencyRecord>lambdaQuery()
        .eq(IdempotencyRecord::getActorUserId, actorId)
        .eq(IdempotencyRecord::getIdempotencyKey, key)
        .last("FOR UPDATE"));
  }

  /** 查询幂等。 */
  @Override
  public IdempotencyRecord findIdempotency(String actorId, String key) {
    return this.getOne(
        Wrappers.lambdaQuery(IdempotencyRecord.class)
            .eq(IdempotencyRecord::getActorUserId, actorId)
            .eq(IdempotencyRecord::getIdempotencyKey, key));
  }
}
