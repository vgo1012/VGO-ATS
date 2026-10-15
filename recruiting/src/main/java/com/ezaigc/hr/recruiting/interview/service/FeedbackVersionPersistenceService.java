package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import java.time.Instant;
import java.util.Map;

/** FeedbackVersion 实体的持久化服务。 */
public interface FeedbackVersionPersistenceService extends IService<FeedbackVersion> {

  /** 新增反馈版本。 */
  int insertFeedbackVersion(
      String id,
      String feedbackId,
      int versionNo,
      String verdict,
      String comment,
      String externalSource,
      Instant occurredAt,
      String reason,
      String actorId,
      Instant now);

  /** 查询反馈版本。 */
  FeedbackVersion findFeedbackVersion(String feedbackId, int versionNo);

  /** 按面评 ID 与版本号精确配对批量读取，返回值以面评 ID 为键。 */
  Map<String, FeedbackVersion> findFeedbackVersions(Map<String, Integer> versions);
}
