package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingEvent;
import java.time.Instant;

/** OnboardingEvent 实体的持久化服务。 */
public interface OnboardingEventPersistenceService extends IService<OnboardingEvent> {

  /** 新增入职。 */
  int insertOnboarding(
      String id,
      String applicationId,
      Instant onboardedAt,
      String source,
      String sourceReference,
      String reason,
      String actorId,
      Instant now);

  /** 查询入职。 */
  OnboardingEvent findOnboarding(String applicationId);

  OnboardingEvent findOnboardingById(String applicationId, String id);
}
