package com.ezaigc.hr.recruiting.submission.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.submission.domain.entity.Feedback;
import java.time.Instant;
import java.util.List;

/** Feedback 实体的持久化服务。 */
public interface SubmissionFeedbackPersistenceService extends IService<Feedback> {

  /** 新增反馈。 */
  int insertFeedback(
      String id,
      String submissionId,
      String result,
      String category,
      String nextAction,
      String note,
      String source,
      Instant occurredAt,
      String actorId,
      Instant now);

  List<Feedback> feedback(String submissionId);
}
