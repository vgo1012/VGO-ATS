package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingProcess;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 入职流程当前事实持久化边界。 */
public interface OnboardingProcessPersistenceService extends IService<OnboardingProcess> {
  int insertProcess(String applicationId, Instant startedAt, String actorId, Instant now);

  OnboardingProcess findProcess(String applicationId);

  OnboardingProcess lockProcess(String applicationId);

  List<OnboardingProcess> findProcesses(Collection<String> applicationIds);

  int markOnboarded(
      String applicationId, Instant actualOnboardedAt, String actorId, long version, Instant now);

  int markReturned(String applicationId, String actorId, long version, Instant now);
}
