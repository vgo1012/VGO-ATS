package com.ezaigc.hr.recruiting.interview.mapper;

import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewJobOption;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import java.time.Instant;
import java.util.List;

/**
 * 面试显式 SQL 操作契约，承载联表、锁定和条件更新。
 *
 * Mapper-owned guarded writes, locks, joins, and immutable fact projections.
 */
public interface InterviewSqlOperations {
  List<InterviewJobOption> findInvitedJobOptions(String userId, String query, String jdId,
      Instant cursorTime, String cursorId, int limit);
  Interview lockInterview(String id);

  /** 查询面试列表。 */
  List<Interview> findInterviews(
      String jdId,
      String jdVersionId,
      String stepCode,
      Integer roundNo,
      String ownerUserId,
      String status,
      String result,
      String applicationId,
      Instant from,
      Instant to,
      Instant cursorTime,
      String cursorId,
      String workflowStepId,
      String interviewType,
      String clientId,
      int limit);

  int complete(String id, String actorId, Instant now);

  /** 新增历史。 */
  int insertHistory(
      String id,
      String interviewId,
      String actionType,
      String beforeJson,
      String afterJson,
      String reason,
      String actorId,
      Instant now);


  /** 查询生效模板。 */
  TemplateVersion findActiveTemplate(String code);

  NotificationIntent lockNotification(String id);

  List<Interview> findMyInterviewTasks(String userId, int limit);

  /** 新增幂等。 */
  int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now);
}
