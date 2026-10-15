package com.ezaigc.hr.recruiting.recruitment.mapper;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItemRow;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 应聘流程显式 SQL 操作契约，承载联表、锁定和条件更新。
 *
 * Mapper-owned locks, joins, JSON writes, and atomic insert/upsert operations.
 */
public interface ApplicationSqlOperations {
  Application lockApplication(String id);

  /** 查询应聘流程列表。 */
  List<Application> findApplications(
      String jdId,
      String jdVersionId,
      String ownerUserId,
      String stage,
      String workflowStepCode,
      String lifecycle,
      Instant from,
      Instant to,
      String interviewType,
      boolean onboarding,
      Instant cursorTime,
      String cursorId,
      int limit);

  int markOfferInProgress(
      String applicationId, String ownerUserId, Instant dueAt, String actorId, Instant now);

  int insertPathChange(
      String id,
      String applicationId,
      String type,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now);

  /** 新增协作人变更。 */
  int insertCollaboratorChange(
      String id,
      String applicationId,
      String beforeJson,
      String afterJson,
      String reason,
      long version,
      String actorId,
      Instant now);

  /** 查询工作条目列表。 */
  List<WorkItemRow> findWorkItems(
      String actorId, boolean scopeAll, Set<String> managedUserIds, int limit);

  List<String> findMyTaskCandidateIds(String actorId, List<String> candidateIds, String jdId);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
