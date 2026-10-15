package com.ezaigc.hr.recruiting.recruitment.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import lombok.Value;

public interface EngagementLockQueryService {
  LockSummary activeLock(String candidateId);

  /** 批量查询候选人的有效跟进锁，返回值以候选人 ID 为键。 */
  Map<String, LockSummary> activeLocks(Collection<String> candidateIds);

  @Value
  class LockSummary {
    String lockId;
    String candidateId;
    String ownerUserId;
    String ownerDepartmentId;
    String contactName;
    Instant acquiredAt;
    Instant expiresAt;
    boolean canContact;
    String collaborationStatus;

}
}
