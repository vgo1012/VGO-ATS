package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord;

/** IdempotencyRecord 实体的持久化服务。 */
public interface RecruitmentIdempotencyPersistenceService extends IService<IdempotencyRecord> {

  /** 查询幂等。 */
  IdempotencyRecord findIdempotency(String actorId, String key);

  /** 在调用方事务中当前锁读，避免并发提交后继续使用旧快照。 */
  IdempotencyRecord findIdempotencyForUpdate(String actorId, String key);
}
