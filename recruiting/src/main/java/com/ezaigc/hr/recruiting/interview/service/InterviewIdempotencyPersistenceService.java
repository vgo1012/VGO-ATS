package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.IdempotencyRecord;

/** IdempotencyRecord 实体的持久化服务。 */
public interface InterviewIdempotencyPersistenceService extends IService<IdempotencyRecord> {

  /** 查询幂等。 */
  IdempotencyRecord findIdempotency(String actorId, String key);

  /** 预留冲突后锁定 actor/key 的当前赢家，不复用普通查询的旧快照。 */
  IdempotencyRecord findIdempotencyForUpdate(String actorId, String key);
}
