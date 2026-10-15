package com.ezaigc.hr.talent.mapper;

import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 人才接收显式 SQL 操作契约，承载联表、锁定和条件更新。
 *
 * Mapper-owned atomic aggregates, visibility projections, joins, and idempotent writes.
 */
public interface TalentIntakeSqlOperations {
  /** 递增批次。 */
  int incrementBatch(String id, int succeeded, int failed, String actorId, Instant now);

  /** 完成批次。 */
  int completeBatch(String id, String actorId, Instant now);

  List<ResumeIntake> findPendingIntakes(String processingStatus, int limit);

  int advanceIntake(String id, String expectedStatus, String nextStatus, String failureCode,
      String actorId, Instant now);

  int updateScore(String id, long expectedVersion, String runId, String scoreStatus, String failureCode,
      String actorId, Instant now);

  /** 查询接收。 */
  ResumeIntake findIntake(String id);

  /** 统计同过滤条件及可见范围内的全部收件。 */
  long countIntakes(
      String batchId, String status, String importStatus, Boolean duplicateContent, String query, String name,
      Instant from, Instant to,
      String actorId, boolean scopeAll, Set<String> managedUserIds, Set<String> visibleDepartmentIds);

  /** 查询接收记录列表。 */
  List<ResumeIntake> findIntakes(
      String batchId,
      String status,
      String importStatus,
      Boolean duplicateContent,
      String query,
      String name,
      Instant from,
      Instant to,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 查询候选人列表。 */
  List<Candidate> findCandidates(
      String query,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      boolean readyOnly,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 按精确名称查询候选人列表。 */
  List<Candidate> findCandidatesByExactName(
      String name,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds);

  /** 删除重复被合并身份列表。 */
  int deleteDuplicateMergedIdentities(String survivorId, String mergedId);

  /** 查询最大简历版本。 */
  int findMaxResumeVersion(String resumeId);

  /** 查询候选人简历版本。 */
  ResumeVersion findCandidateResumeVersion(String candidateId, String resumeVersionId);

  /** 查询简历版本列表。 */
  List<ResumeVersion> findResumeVersions(
      String candidateId, Integer cursorVersionNo, String cursorId, int limit);

  int moveResumeVersions(String survivorResumeId, String mergedResumeId, int offset);

  /** 查询冲突列表。 */
  List<IdentityConflict> findConflicts(
      String status,
      List<String> intakeIds,
      String candidateId,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> visibleDepartmentIds,
      Instant cursorTime,
      String cursorId,
      int limit);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
