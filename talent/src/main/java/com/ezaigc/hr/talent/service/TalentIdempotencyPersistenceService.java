package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;

/** IdempotencyRecord 实体的持久化服务。 */
public interface TalentIdempotencyPersistenceService extends IService<IdempotencyRecord> {

  /** 查询幂等。 */
  IdempotencyRecord findIdempotency(String actorId, String key);
}
