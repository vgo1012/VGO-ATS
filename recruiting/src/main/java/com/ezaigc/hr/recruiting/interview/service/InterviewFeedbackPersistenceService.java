package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Feedback 实体的持久化服务。 */
public interface InterviewFeedbackPersistenceService extends IService<Feedback> {

  /** 查询反馈操作人填写人。 */
  Feedback findFeedbackByAuthor(String interviewId, String authorId, String kind);

  /** 在当前任务页内查询该面试官已提交内部评价的面试 ID，空页不访问数据库。 */
  Set<String> findSubmittedInterviewIds(Set<String> interviewIds, String authorId);

  /** 新增反馈。 */
  int insertFeedback(
      String id, String interviewId, String authorId, String kind, String status, Instant now);

  int advanceFeedback(String id, String status, int versionNo, String actorId, Instant now);

  /** 在数据库分页前过滤他人草稿，按创建时间和 ID 降序读取。 */
  List<Feedback> findVisibleFeedback(
      String interviewId, String actorId, Instant cursorTime, String cursorId, int limit);

  /** 共享面试额外包含所有作者草稿；共享集合必须来自当前共享库事实。 */
  List<Feedback> findVisibleFeedbackByInterviewIds(Set<String> interviewIds, String actorId,
      Set<String> sharedInterviewIds, Instant cursorTime, String cursorId, int limit);

  /** 在指定面试集合内分页读取当前操作者可见的面评。 */
  List<Feedback> findVisibleFeedbackByInterviewIds(
      Set<String> interviewIds, String actorId, Instant cursorTime, String cursorId, int limit);
}
