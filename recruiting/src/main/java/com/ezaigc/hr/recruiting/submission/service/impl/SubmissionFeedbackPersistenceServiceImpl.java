package com.ezaigc.hr.recruiting.submission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.submission.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.submission.mapper.FeedbackMapper;
import com.ezaigc.hr.recruiting.submission.service.SubmissionFeedbackPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Feedback 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class SubmissionFeedbackPersistenceServiceImpl extends ServiceImpl<FeedbackMapper, Feedback>
    implements SubmissionFeedbackPersistenceService {

  /** 新增反馈。 */
  @Override
  public int insertFeedback(
      String id,
      String submissionId,
      String result,
      String category,
      String nextAction,
      String note,
      String source,
      Instant occurredAt,
      String actorId,
      Instant now) {
    Feedback feedback = new Feedback();
    feedback.setId(id);
    feedback.setSubmissionId(submissionId);
    feedback.setResult(result);
    feedback.setReasonCategory(category);
    feedback.setNextAction(nextAction);
    feedback.setNote(note);
    feedback.setSourceChannel(source);
    feedback.setSourceOccurredAt(occurredAt);
    feedback.setRecordedByUserId(actorId);
    feedback.setCreateTime(now);
    return baseMapper.insert(feedback);
  }

  @Override
  public List<Feedback> feedback(String submissionId) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(Feedback.class)
            .eq(Feedback::getSubmissionId, submissionId)
            .orderByAsc(Feedback::getSourceOccurredAt, Feedback::getId));
  }
}
