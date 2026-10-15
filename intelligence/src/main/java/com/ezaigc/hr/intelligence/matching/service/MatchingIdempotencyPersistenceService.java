package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.IdempotencyRecord;
import java.time.Instant;

/** IdempotencyRecord 实体的持久化服务。 */
public interface MatchingIdempotencyPersistenceService extends IService<IdempotencyRecord> {

  /** 查询幂等。 */
  IdempotencyRecord findIdempotency(String actorId, String key);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId,
      String key,
      String actionCode,
      String fingerprint,
      String targetId,
      Instant now);

  /** 原子保留命令幂等键；已存在时返回零。 */
  int insertIgnore(
      String actorId,
      String key,
      String actionCode,
      String fingerprint,
      String targetId,
      Instant now);
}
