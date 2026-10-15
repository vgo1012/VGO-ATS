package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.IdempotencyRecordEntity;
import java.time.Instant;

/** IdempotencyRecordEntity 实体的持久化服务。 */
public interface DemandIdempotencyPersistenceService extends IService<IdempotencyRecordEntity> {

  /** 查询幂等。 */
  IdempotencyRecordEntity findIdempotency(String actorId, String key);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId,
      String key,
      String actionCode,
      String fingerprint,
      String targetId,
      Instant now);
}
