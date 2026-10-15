package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUp;
import com.ezaigc.hr.recruiting.recruitment.mapper.FollowUpMapper;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** FollowUp 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class FollowUpPersistenceServiceImpl extends ServiceImpl<FollowUpMapper, FollowUp>
    implements FollowUpPersistenceService {

  /** 统计跟进后续列表。 */
  @Override
  public int countFollowUps(String stepId) {
    return Math.toIntExact(
        this.count(Wrappers.lambdaQuery(FollowUp.class).eq(FollowUp::getWorkflowStepId, stepId)));
  }

  /** 新增跟进后续。 */
  @Override
  public int insertFollowUp(
      String id,
      String applicationId,
      String stepId,
      String channel,
      String summary,
      String nextPlan,
      Instant contactedAt,
      String actorId,
      Instant now) {
    FollowUp followUp = new FollowUp();
    followUp.setId(id);
    followUp.setApplicationId(applicationId);
    followUp.setWorkflowStepId(stepId);
    followUp.setChannel(channel);
    followUp.setSummary(summary);
    followUp.setNextPlan(nextPlan);
    followUp.setContactedAt(contactedAt);
    followUp.setRecordedByUserId(actorId);
    followUp.setCreateTime(now);
    return affected(this.save(followUp));
  }

  /** 查询跟进后续列表。 */
  @Override
  public List<FollowUp> findFollowUps(String stepId) {
    return this.list(
        Wrappers.lambdaQuery(FollowUp.class)
            .eq(FollowUp::getWorkflowStepId, stepId)
            .orderByAsc(FollowUp::getContactedAt, FollowUp::getId));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
