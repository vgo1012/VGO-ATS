package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Intention;
import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

/** Intention 实体的持久化服务。 */
public interface IntentionPersistenceService extends IService<Intention> {

  /** 新增意向。 */
  int insertIntention(
      String id,
      String candidateId,
      String applicationId,
      BigDecimal salaryMin,
      BigDecimal salaryMax,
      String salaryPeriod,
      String availability,
      String locations,
      String reason,
      String actorId,
      Instant now);

  /** 查询意向列表。 */
  List<Intention> findIntentions(String applicationId);
}
