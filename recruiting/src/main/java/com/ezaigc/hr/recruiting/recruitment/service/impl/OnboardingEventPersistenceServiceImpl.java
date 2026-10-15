package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingEvent;
import com.ezaigc.hr.recruiting.recruitment.mapper.OnboardingEventMapper;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingEventPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** OnboardingEvent 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class OnboardingEventPersistenceServiceImpl
    extends ServiceImpl<OnboardingEventMapper, OnboardingEvent>
    implements OnboardingEventPersistenceService {

  /** 新增入职。 */
  @Override
  public int insertOnboarding(
      String id,
      String applicationId,
      Instant onboardedAt,
      String source,
      String sourceReference,
      String reason,
      String actorId,
      Instant now) {
    OnboardingEvent event = new OnboardingEvent();
    event.setId(id);
    event.setApplicationId(applicationId);
    event.setOnboardedAt(onboardedAt);
    event.setSource(source);
    event.setSourceReference(sourceReference);
    event.setReason(reason);
    event.setOperatedByUserId(actorId);
    event.setCreateTime(now);
    return affected(this.save(event));
  }

  /** 查询入职。 */
  @Override
  public OnboardingEvent findOnboarding(String applicationId) {
    return this.getOne(
        Wrappers.lambdaQuery(OnboardingEvent.class)
            .eq(OnboardingEvent::getApplicationId, applicationId));
  }

  @Override
  public OnboardingEvent findOnboardingById(String applicationId, String id) {
    return this.getOne(
        Wrappers.lambdaQuery(OnboardingEvent.class)
            .eq(OnboardingEvent::getApplicationId, applicationId)
            .eq(OnboardingEvent::getId, id));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
