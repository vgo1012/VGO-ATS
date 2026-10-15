package com.ezaigc.hr.recruiting.submission.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.submission.domain.entity.IdempotencyRecord;

/** IdempotencyRecord 实体的持久化服务。 */
public interface SubmissionIdempotencyPersistenceService extends IService<IdempotencyRecord> {

  IdempotencyRecord idempotency(String actorId, String key);

  IdempotencyRecord idempotencyForUpdate(String actorId, String key);
}
