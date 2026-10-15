package com.ezaigc.hr.recruiting.interview.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewJobOption;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import com.ezaigc.hr.recruiting.interview.enums.InterviewCandidateConfirmationStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewStatus;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewMapper;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * 面试持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin persistence implementation; permissions and transactions stay in application managers.
 */
@Service
public class InterviewPersistenceServiceImpl extends ServiceImpl<InterviewMapper, Interview>
    implements InterviewPersistenceService {
  @Override
  public String findCandidateId(String id) {
    Interview value = baseMapper.selectOne(Wrappers.lambdaQuery(Interview.class)
        .select(Interview::getCandidateId).eq(Interview::getId, id));
    return value == null ? null : value.getCandidateId();
  }

  @Override
  public String findNotificationCandidateId(String notificationId) {
    return baseMapper.findNotificationCandidateId(notificationId);
  }

  @Override
  public Map<String, String> latestTypesByStepIds(Set<String> stepIds) {
    if (stepIds.isEmpty()) return Map.of();
    Map<String, String> types = new LinkedHashMap<>();
    list(Wrappers.lambdaQuery(Interview.class)
        .select(Interview::getWorkflowStepId, Interview::getInterviewType)
        .in(Interview::getWorkflowStepId, stepIds)
        .in(Interview::getStatus, InterviewStatus.SCHEDULED.getCode(), InterviewStatus.COMPLETED.getCode())
        .orderByDesc(Interview::getCreateTime, Interview::getId))
        .forEach(interview -> types.putIfAbsent(interview.getWorkflowStepId(), interview.getInterviewType()));
    return types;
  }

  @Override
  public Map<String, List<Integer>> activeRoundNosByApplicationIds(Set<String> applicationIds) {
    if (applicationIds.isEmpty()) return Map.of();
    Map<String, Set<Integer>> rounds = new LinkedHashMap<>();
    list(Wrappers.lambdaQuery(Interview.class)
        .select(Interview::getApplicationId, Interview::getRoundNo, Interview::getStatus)
        .in(Interview::getApplicationId, applicationIds)
        .eq(Interview::getStatus, InterviewStatus.SCHEDULED.getCode())
        .orderByAsc(Interview::getApplicationId, Interview::getRoundNo, Interview::getId))
        .stream()
        .filter(interview -> InterviewStatus.SCHEDULED.matches(interview.getStatus()))
        .forEach(interview -> rounds.computeIfAbsent(
            interview.getApplicationId(), ignored -> new TreeSet<>()).add(interview.getRoundNo()));
    Map<String, List<Integer>> result = new LinkedHashMap<>();
    rounds.forEach((applicationId, values) -> result.put(applicationId, List.copyOf(values)));
    return result;
  }

  @Override
  public Map<String, Integer> latestCompletedRoundNosByApplicationIds(Set<String> applicationIds) {
    if (applicationIds.isEmpty()) return Map.of();
    Map<String, Integer> rounds = new LinkedHashMap<>();
    list(Wrappers.lambdaQuery(Interview.class)
        .select(Interview::getApplicationId, Interview::getRoundNo)
        .in(Interview::getApplicationId, applicationIds)
        .eq(Interview::getStatus, InterviewStatus.COMPLETED.getCode())
        .orderByAsc(Interview::getApplicationId)
        .orderByDesc(Interview::getRoundNo, Interview::getId))
        .forEach(interview -> rounds.putIfAbsent(interview.getApplicationId(), interview.getRoundNo()));
    return rounds;
  }

  /** 新增面试。 */
  @Override
  public int insertInterview(
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
      Instant now) {
    Interview interview = new Interview();
    interview.setId(id);
    interview.setApplicationId(applicationId);
    interview.setCandidateId(candidateId);
    interview.setJdId(jdId);
    interview.setJdVersionId(jdVersionId);
    interview.setWorkflowTemplateVersionId(workflowTemplateVersionId);
    interview.setWorkflowStepId(workflowStepId);
    interview.setStepCode(stepCode);
    interview.setInterviewType(interviewType);
    interview.setRoundNo(roundNo);
    interview.setClientSubmissionId(clientSubmissionId);
    interview.setStatus(InterviewStatus.SCHEDULED.getCode());
    interview.setScheduledStartAt(startAt);
    interview.setScheduledEndAt(endAt);
    interview.setTimezoneId(timezoneId);
    interview.setMeetingMode(meetingMode);
    interview.setLocationText(locationText);
    interview.setMeetingUrl(meetingUrl);
    interview.setCandidateNotificationChannel(notificationChannel);
    interview.setCandidateConfirmationStatus(InterviewCandidateConfirmationStatus.PENDING.getCode());
    interview.setOwnerUserId(ownerUserId);
    interview.setOwnerDepartmentId(departmentId);
    interview.setNextResponsibleUserId(nextResponsibleUserId);
    interview.setVersion(0L);
    interview.setCreateId(actorId);
    interview.setCreateTime(now);
    interview.setUpdateId(actorId);
    interview.setUpdateTime(now);
    return save(interview) ? 1 : 0;
  }

  @Override
  public List<Interview> lockArrangedApplicationInterviews(String applicationId) {
    return list(Wrappers.lambdaQuery(Interview.class)
        .eq(Interview::getApplicationId, applicationId)
        .in(Interview::getStatus, InterviewStatus.SCHEDULED.getCode(), InterviewStatus.COMPLETED.getCode())
        .orderByAsc(Interview::getRoundNo, Interview::getId)
        .last("FOR UPDATE"));
  }

  /** 查询面试。 */
  @Override
  public Interview findInterview(String id) {
    return getById(id);
  }

  /** 统计生效操作人步骤。 */
  @Override
  public int countActiveByStep(String stepId) {
    return Math.toIntExact(
        count(
            Wrappers.lambdaQuery(Interview.class)
                .eq(Interview::getWorkflowStepId, stepId)
                .eq(Interview::getStatus, InterviewStatus.SCHEDULED.getCode())));
  }

  @Override
  public List<Interview> lockSubmissionInterviews(String applicationId, String submissionId, String afterId) {
    return list(Wrappers.lambdaQuery(Interview.class)
        .eq(Interview::getApplicationId, applicationId)
        .eq(Interview::getClientSubmissionId, submissionId)
        .in(Interview::getStatus, InterviewStatus.SCHEDULED.getCode(), InterviewStatus.COMPLETED.getCode())
        .gt(afterId != null, Interview::getId, afterId)
        .orderByAsc(Interview::getId)
        // 常量分页及当前读，避免撤回等待应用锁后仍读到旧快照。
        .last("LIMIT 100 FOR UPDATE"));
  }

  @Override
  public Interview lockScheduledByStep(String stepId) {
    return getOne(Wrappers.lambdaQuery(Interview.class)
        .eq(Interview::getWorkflowStepId, stepId)
        .eq(Interview::getStatus, InterviewStatus.SCHEDULED.getCode())
        .last("LIMIT 1 FOR UPDATE"));
  }

  @Override
  public List<Interview> lockScheduledApplicationInterviews(String applicationId, String afterId) {
    return list(Wrappers.lambdaQuery(Interview.class)
        .eq(Interview::getApplicationId, applicationId)
        .eq(Interview::getStatus, InterviewStatus.SCHEDULED.getCode())
        .gt(afterId != null, Interview::getId, afterId)
        .orderByAsc(Interview::getId)
        .last("LIMIT 100 FOR UPDATE"));
  }

  /** 更新排期。 */
  @Override
  public int updateSchedule(
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
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(Interview.class)
            .set(Interview::getScheduledStartAt, startAt)
            .set(Interview::getScheduledEndAt, endAt)
            .set(Interview::getTimezoneId, timezoneId)
            .set(Interview::getMeetingMode, meetingMode)
            .set(Interview::getLocationText, locationText)
            .set(Interview::getMeetingUrl, meetingUrl)
            .set(Interview::getCandidateNotificationChannel, channel)
            .set(Interview::getNextResponsibleUserId, nextResponsible)
            .set(Interview::getUpdateId, actorId)
            .set(Interview::getUpdateTime, now)
            .eq(Interview::getId, id)
            .eq(Interview::getStatus, InterviewStatus.SCHEDULED.getCode())
            .eq(Interview::getVersion, version)
            .setIncrBy(Interview::getVersion, 1));
  }

  @Override
  public int cancel(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(Interview.class)
            .set(Interview::getStatus, InterviewStatus.CANCELLED.getCode())
            .set(Interview::getUpdateId, actorId)
            .set(Interview::getUpdateTime, now)
            .eq(Interview::getId, id)
            .eq(Interview::getStatus, InterviewStatus.SCHEDULED.getCode())
            .eq(Interview::getVersion, version)
            .setIncrBy(Interview::getVersion, 1));
  }

  @Override
  public Interview lockInterview(String id) {
    return baseMapper.lockInterview(id);
  }

  @Override
  public List<Interview> findInterviews(
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
      int limit) {
    return baseMapper.findInterviews(
        jdId,
        jdVersionId,
        stepCode,
        roundNo,
        ownerUserId,
        status,
        result,
        applicationId,
        from,
        to,
        cursorTime,
        cursorId,
        workflowStepId,
        interviewType,
        clientId,
        limit);
  }

  @Override
  public Interview lockLatestByStep(String stepId) {
    return getOne(Wrappers.lambdaQuery(Interview.class)
        .eq(Interview::getWorkflowStepId, stepId)
                .in(Interview::getStatus, InterviewStatus.SCHEDULED.getCode(), InterviewStatus.COMPLETED.getCode())
        .orderByDesc(Interview::getCreateTime, Interview::getId)
        .last("LIMIT 1 FOR UPDATE"));
  }

  @Override
  public List<InterviewJobOption> findInvitedJobOptions(String userId, String query, String jdId,
      Instant cursorTime, String cursorId, int limit) {
    return baseMapper.findInvitedJobOptions(userId, query, jdId, cursorTime, cursorId, limit);
  }

  @Override
  public int complete(String id, String actorId, Instant now) {
    return baseMapper.complete(id, actorId, now);
  }

  @Override
  public int insertHistory(
      String id,
      String interviewId,
      String actionType,
      String beforeJson,
      String afterJson,
      String reason,
      String actorId,
      Instant now) {
    return baseMapper.insertHistory(
        id, interviewId, actionType, beforeJson, afterJson, reason, actorId, now);
  }

  @Override
  public TemplateVersion findActiveTemplate(String code) {
    return baseMapper.findActiveTemplate(code);
  }

  @Override
  public NotificationIntent lockNotification(String id) {
    return baseMapper.lockNotification(id);
  }

  @Override
  public List<Interview> findMyInterviewTasks(String userId, int limit) {
    return baseMapper.findMyInterviewTasks(userId, limit);
  }

  @Override
  public int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now) {
    return baseMapper.insertIdempotency(actorId, key, action, fingerprint, targetId, now);
  }
}
