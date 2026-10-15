package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUp;
import java.time.Instant;
import java.util.List;

/** FollowUp 实体的持久化服务。 */
public interface FollowUpPersistenceService extends IService<FollowUp> {

  /** 统计跟进后续列表。 */
  int countFollowUps(String stepId);

  /** 新增跟进后续。 */
  int insertFollowUp(
      String id,
      String applicationId,
      String stepId,
      String channel,
      String summary,
      String nextPlan,
      Instant contactedAt,
      String actorId,
      Instant now);

  /** 查询跟进后续列表。 */
  List<FollowUp> findFollowUps(String stepId);
}
