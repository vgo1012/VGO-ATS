package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewSqlOperations;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 面试持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary used by interview application managers.
 */
public interface InterviewPersistenceService extends IService<Interview>, InterviewSqlOperations {
  /** 批量读取当前步骤最近一次未取消排期的真实类型。 */
  Map<String, String> latestTypesByStepIds(Set<String> stepIds);

  /** 批量读取招聘活动下所有待进行面试轮次，按活动分组并去重升序。 */
  Map<String, List<Integer>> activeRoundNosByApplicationIds(Set<String> applicationIds);

  /** 批量读取招聘活动下已完成真实面试中的最高轮次。 */
  Map<String, Integer> latestCompletedRoundNosByApplicationIds(Set<String> applicationIds);

  /** 新增面试。 */
  int insertInterview(
      String id,
      String applicationId,
      String candidateId,
      String jdId,
      String jdVersionId,
      String workflowTemplateVersionId,
      String workflowStepId,
      String stepCode,
      String interviewType,
      int roundNo,
      String clientSubmissionId,
      Instant startAt,
      Instant endAt,
      String timezoneId,
      String meetingMode,
      String locationText,
      String meetingUrl,
      String notificationChannel,
      String ownerUserId,
      String departmentId,
      String nextResponsibleUserId,
      String actorId,
      Instant now);

  /** 锁定招聘活动内未取消的排期事实，用于统一轮次准入。 */
  List<Interview> lockArrangedApplicationInterviews(String applicationId);

  /** 查询面试。 */
  Interview findInterview(String id);
  String findCandidateId(String id);
  String findNotificationCandidateId(String notificationId);

  /** 统计生效操作人步骤。 */
  int countActiveByStep(String stepId);

  /** 当前读锁定步骤最近一次未取消的面试，避免用历史已完成面试绕过新排期。 */
  Interview lockLatestByStep(String stepId);

  /** 当前读分页锁定同一 Application、推荐下未取消的面试，供原子撤回及旧绑定修复。 */
  List<Interview> lockSubmissionInterviews(String applicationId, String submissionId, String afterId);

  /** 当前读分页锁定同一招聘活动的待进行面试，供活动终止时收束排期。 */
  List<Interview> lockScheduledApplicationInterviews(String applicationId, String afterId);

  Interview lockScheduledByStep(String stepId);

  /** 更新排期。 */
  int updateSchedule(
      String id,
      Instant startAt,
      Instant endAt,
      String timezoneId,
      String meetingMode,
      String locationText,
      String meetingUrl,
      String channel,
      String nextResponsible,
      long version,
      String actorId,
      Instant now);

  int cancel(String id, long version, String actorId, Instant now);
}
