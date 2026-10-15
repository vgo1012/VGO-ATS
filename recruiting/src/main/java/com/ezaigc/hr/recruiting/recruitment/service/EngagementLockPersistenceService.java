package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 跟进归属锁定持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary used by engagement-lock managers and same-JVM contracts.
 */
public interface EngagementLockPersistenceService extends IService<EngagementLock> {
  /** 查询操作人候选人。 */
  EngagementLock findByCandidate(String candidateId);

  /** 批量查询仍在有效期内的候选人跟进锁。 */
  List<EngagementLock> activeByCandidates(Collection<String> candidateIds, Instant now);

  EngagementLock findById(String id);

  int transfer(
      String id,
      String ownerId,
      String departmentId,
      String contactName,
      String status,
      Instant expiresAt,
      long version,
      String actorId,
      Instant now);

  int renew(
      String id, String progressType, Instant expiresAt, long version, String actorId, Instant now);

  List<EngagementLock> activeByOwner(String ownerId, Instant now);

  List<EngagementLock> due(Instant now, int limit);

  int expire(String id, long version, Instant now);
}
