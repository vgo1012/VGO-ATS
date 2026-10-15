package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Intention;
import com.ezaigc.hr.recruiting.recruitment.mapper.IntentionMapper;
import com.ezaigc.hr.recruiting.recruitment.service.IntentionPersistenceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** Intention 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class IntentionPersistenceServiceImpl extends ServiceImpl<IntentionMapper, Intention>
    implements IntentionPersistenceService {

  /** 新增意向。 */
  @Override
  public int insertIntention(
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
      Instant now) {
    Intention intention = new Intention();
    intention.setId(id);
    intention.setCandidateId(candidateId);
    intention.setApplicationId(applicationId);
    intention.setExpectedSalaryMin(salaryMin);
    intention.setExpectedSalaryMax(salaryMax);
    intention.setSalaryPeriod(salaryPeriod);
    intention.setAvailability(availability);
    intention.setIntentionLocations(locations);
    intention.setReason(reason);
    intention.setRecordedByUserId(actorId);
    intention.setCreateTime(now);
    return affected(this.save(intention));
  }

  /** 查询意向列表。 */
  @Override
  public List<Intention> findIntentions(String applicationId) {
    return this.list(
        Wrappers.lambdaQuery(Intention.class)
            .eq(Intention::getApplicationId, applicationId)
            .orderByAsc(Intention::getCreateTime, Intention::getId));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
