package com.ezaigc.hr.recruiting.interview.service.impl;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_MY_INTERVIEW_TASKS;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackKind;
import com.ezaigc.hr.recruiting.interview.enums.InterviewFeedbackStatus;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackMapper;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Feedback 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class InterviewFeedbackPersistenceServiceImpl extends ServiceImpl<FeedbackMapper, Feedback>
    implements InterviewFeedbackPersistenceService {

  @Override
  public Set<String> findSubmittedInterviewIds(Set<String> interviewIds, String authorId) {
    if (authorId == null || authorId.isBlank() || interviewIds == null
        || interviewIds.size() > MAX_MY_INTERVIEW_TASKS
        || interviewIds.stream().anyMatch(id -> id == null || id.isBlank())) {
      throw new IllegalArgumentException("面试任务面评查询参数无效或超过单页上限");
    }
    if (interviewIds.isEmpty()) {
      return Set.of();
    }
    return baseMapper.selectList(Wrappers.lambdaQuery(Feedback.class)
            .select(Feedback::getInterviewId)
            .in(Feedback::getInterviewId, interviewIds)
            .eq(Feedback::getAuthorUserId, authorId)
            .eq(Feedback::getFeedbackKind, InterviewFeedbackKind.INTERNAL.getCode())
            .eq(Feedback::getStatus, InterviewFeedbackStatus.SUBMITTED.getCode()))
        .stream().map(Feedback::getInterviewId).collect(Collectors.toUnmodifiableSet());
  }

  /** 查询反馈操作人填写人。 */
  @Override
  public Feedback findFeedbackByAuthor(String interviewId, String authorId, String kind) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(Feedback.class)
            .eq(Feedback::getInterviewId, interviewId)
            .eq(Feedback::getAuthorUserId, authorId)
            .eq(Feedback::getFeedbackKind, kind));
  }

  /** 新增反馈。 */
  @Override
  public int insertFeedback(
      String id, String interviewId, String authorId, String kind, String status, Instant now) {
    Feedback feedback = new Feedback();
    feedback.setId(id);
    feedback.setInterviewId(interviewId);
    feedback.setAuthorUserId(authorId);
    feedback.setFeedbackKind(kind);
    feedback.setStatus(status);
    feedback.setCurrentVersionNo(1);
    feedback.setCreateId(authorId);
    feedback.setCreateTime(now);
    feedback.setUpdateId(authorId);
    feedback.setUpdateTime(now);
    return baseMapper.insert(feedback);
  }

  @Override
  public int advanceFeedback(String id, String status, int versionNo, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(Feedback.class)
            .set(Feedback::getStatus, status)
            .setIncrBy(Feedback::getCurrentVersionNo, 1)
            .setIncrBy(Feedback::getVersion, 1)
            .set(Feedback::getUpdateId, actorId)
            .set(Feedback::getUpdateTime, now)
            .eq(Feedback::getId, id)
            .eq(Feedback::getCurrentVersionNo, versionNo));
  }

  /** 查询反馈。 */
  @Override
  public List<Feedback> findVisibleFeedback(
      String interviewId, String actorId, Instant cursorTime, String cursorId, int limit) {
    PageLimits.resolve(limit, 1, PageLimits.MAXIMUM + 1);
    var query = Wrappers.lambdaQuery(Feedback.class)
            .eq(Feedback::getInterviewId, interviewId)
            .and(visible -> visible.eq(Feedback::getStatus, InterviewFeedbackStatus.SUBMITTED.getCode())
                .or().eq(Feedback::getAuthorUserId, actorId))
            .and(cursorTime != null, cursor -> cursor.lt(Feedback::getCreateTime, cursorTime)
                .or(tie -> tie.eq(Feedback::getCreateTime, cursorTime).lt(Feedback::getId, cursorId)))
            .orderByDesc(Feedback::getCreateTime, Feedback::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  @Override
  public List<Feedback> findVisibleFeedbackByInterviewIds(
      Set<String> interviewIds, String actorId, Instant cursorTime, String cursorId, int limit) {
    return findVisibleFeedbackByInterviewIds(interviewIds, actorId, Set.of(), cursorTime, cursorId, limit);
  }

  @Override
  public List<Feedback> findVisibleFeedbackByInterviewIds(Set<String> interviewIds, String actorId,
      Set<String> sharedInterviewIds, Instant cursorTime, String cursorId, int limit) {
    if (interviewIds == null || interviewIds.size() > PageLimits.MAXIMUM
        || interviewIds.stream().anyMatch(id -> id == null || id.isBlank())
        || actorId == null || actorId.isBlank()) {
      throw new IllegalArgumentException("面评批量查询参数无效或超过上限");
    }
    PageLimits.resolve(limit, 1, PageLimits.MAXIMUM + 1);
    if (interviewIds.isEmpty()) {
      return List.of();
    }
    var query = Wrappers.lambdaQuery(Feedback.class)
        .in(Feedback::getInterviewId, interviewIds)
        .and(visible -> {
          visible.eq(Feedback::getStatus, InterviewFeedbackStatus.SUBMITTED.getCode())
              .or().eq(Feedback::getAuthorUserId, actorId);
          if (!sharedInterviewIds.isEmpty()) visible.or().in(Feedback::getInterviewId, sharedInterviewIds);
        })
        .and(cursorTime != null, cursor -> cursor.lt(Feedback::getCreateTime, cursorTime)
            .or(tie -> tie.eq(Feedback::getCreateTime, cursorTime).lt(Feedback::getId, cursorId)))
        .orderByDesc(Feedback::getCreateTime, Feedback::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }
}
