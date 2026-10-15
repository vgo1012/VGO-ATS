package com.ezaigc.hr.recruiting.interview.service.impl;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_PARTICIPANTS_PER_INTERVIEW;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_NOTIFICATION_INTERVIEWS_PER_BATCH;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.enums.InterviewParticipantResponseStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewParticipantRole;
import com.ezaigc.hr.recruiting.interview.mapper.ParticipantMapper;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import java.util.Collection;

/** Participant 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ParticipantPersistenceServiceImpl extends ServiceImpl<ParticipantMapper, Participant>
    implements ParticipantPersistenceService {

  @Override
  public boolean insertParticipants(
      String interviewId, List<String> userIds, String role, Instant now) {
    if (interviewId == null
        || interviewId.isBlank()
        || userIds == null
        || userIds.size() > MAX_PARTICIPANTS_PER_INTERVIEW
        || userIds.stream().anyMatch(userId -> userId == null || userId.isBlank())
        || !InterviewParticipantRole.INTERVIEWER.matches(role)
        || now == null) {
      throw new IllegalArgumentException("interview participant batch is invalid");
    }
    if (userIds.isEmpty()) {
      return true;
    }
    List<Participant> participants =
        userIds.stream().map(userId -> participant(interviewId, userId, role, now)).toList();
    return saveBatch(participants, MAX_PARTICIPANTS_PER_INTERVIEW);
  }

  /** 删除参与者列表。 */
  @Override
  public int deleteParticipants(String interviewId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(Participant.class).eq(Participant::getInterviewId, interviewId));
  }

  /** 查询参与者列表。 */
  @Override
  public List<Participant> findParticipants(String interviewId) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(Participant.class)
            .eq(Participant::getInterviewId, interviewId)
            .orderByAsc(Participant::getParticipantRole, Participant::getUserId));
  }

  @Override
  public List<Participant> findParticipantsByInterviewIds(
      Collection<String> interviewIds) {
    if (interviewIds == null || interviewIds.isEmpty()) {
      return List.of();
    }
    return baseMapper.selectList(
        Wrappers.lambdaQuery(Participant.class)
            .in(Participant::getInterviewId, interviewIds)
            .orderByAsc(
                Participant::getInterviewId,
                Participant::getParticipantRole,
                Participant::getUserId));
  }

  @Override
  public List<Participant> lockParticipantsByInterviewIds(Collection<String> interviewIds) {
    if (interviewIds == null || interviewIds.size() > MAX_NOTIFICATION_INTERVIEWS_PER_BATCH
        || interviewIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 26)) {
      throw new IllegalArgumentException("interview participant locking batch is invalid");
    }
    if (interviewIds.isEmpty()) return List.of();
    int maximum = interviewIds.size() * MAX_PARTICIPANTS_PER_INTERVIEW;
    List<Participant> current = baseMapper.selectList(Wrappers.lambdaQuery(Participant.class)
        .in(Participant::getInterviewId, interviewIds)
        .orderByAsc(Participant::getInterviewId, Participant::getParticipantRole, Participant::getUserId)
        // 固定读取上限为100场×50人再加一行；按实际批次检查超量，不能静默漏发通知。
        .last("LIMIT 5001 FOR UPDATE"));
    if (current.size() > maximum) {
      throw new IllegalStateException("interview participant count exceeds notification batch capacity");
    }
    return current;
  }

  private static Participant participant(
      String interviewId, String userId, String role, Instant now) {
    Participant participant = new Participant();
    participant.setInterviewId(interviewId);
    participant.setUserId(userId);
    participant.setParticipantRole(role);
    participant.setResponseStatus(InterviewParticipantResponseStatus.PENDING.getCode());
    participant.setCreateTime(now);
    return participant;
  }
}
