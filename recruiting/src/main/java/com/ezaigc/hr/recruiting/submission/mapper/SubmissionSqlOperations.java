package com.ezaigc.hr.recruiting.submission.mapper;

import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import java.time.Instant;

/**
 * 客户推荐显式 SQL 操作契约，承载联表、锁定和条件更新。
 *
 * Mapper-owned guarded writes, locks, joins, and immutable fact projections.
 */
public interface SubmissionSqlOperations {
  ClientSubmission lock(String id);

  int insertRestrictionProjection(String clientId, String candidateId, String recordId,
      boolean active, Instant expiresAt, String actorId, Instant now);

  int projectRestriction(
      String clientId,
      String candidateId,
      String recordId,
      boolean active,
      Instant expiresAt,
      String actorId,
      Instant now);

  ClientRestriction currentRestriction(String clientId, String candidateId);

  /** 仅当当前事实仍为预期记录时应用更正或停用，防止覆盖并发修改。 */
  int replaceRestrictionProjection(
      String clientId,
      String candidateId,
      String expectedRecordId,
      String recordId,
      boolean active,
      Instant expiresAt,
      String actorId,
      Instant now);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
