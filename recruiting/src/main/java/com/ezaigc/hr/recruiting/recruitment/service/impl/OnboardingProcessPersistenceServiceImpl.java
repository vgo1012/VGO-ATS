package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingProcess;
import com.ezaigc.hr.recruiting.recruitment.enums.OnboardingProcessStatus;
import com.ezaigc.hr.recruiting.recruitment.mapper.OnboardingProcessMapper;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingProcessPersistenceService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OnboardingProcessPersistenceServiceImpl
    extends ServiceImpl<OnboardingProcessMapper, OnboardingProcess>
    implements OnboardingProcessPersistenceService {
  @Override
  public int insertProcess(String applicationId, Instant startedAt, String actorId, Instant now) {
    OnboardingProcess process = new OnboardingProcess();
    process.setApplicationId(applicationId);
    process.setStatus(OnboardingProcessStatus.IN_PROGRESS.getCode());
    process.setStartedAt(startedAt);
    process.setStartedByUserId(actorId);
    process.setCreateId(actorId);
    process.setCreateTime(now);
    process.setUpdateId(actorId);
    process.setUpdateTime(now);
    return affected(save(process));
  }

  @Override
  public OnboardingProcess findProcess(String applicationId) {
    return getById(applicationId);
  }

  @Override
  public OnboardingProcess lockProcess(String applicationId) {
    return baseMapper.lockProcess(applicationId);
  }

  @Override
  public List<OnboardingProcess> findProcesses(Collection<String> applicationIds) {
    if (applicationIds == null || applicationIds.isEmpty()) {
      return List.of();
    }
    return list(Wrappers.lambdaQuery(OnboardingProcess.class)
        .in(OnboardingProcess::getApplicationId, applicationIds));
  }

  @Override
  public int markOnboarded(
      String applicationId, Instant actualOnboardedAt, String actorId, long version, Instant now) {
    return affected(update(Wrappers.lambdaUpdate(OnboardingProcess.class)
        .eq(OnboardingProcess::getApplicationId, applicationId)
        .eq(OnboardingProcess::getVersion, version)
        .in(OnboardingProcess::getStatus,
            OnboardingProcessStatus.IN_PROGRESS.getCode(),
            OnboardingProcessStatus.ONBOARDED.getCode())
        .set(OnboardingProcess::getStatus, OnboardingProcessStatus.ONBOARDED.getCode())
        .set(OnboardingProcess::getActualOnboardedAt, actualOnboardedAt)
        .set(OnboardingProcess::getClosedAt, now)
        .set(OnboardingProcess::getCompletedByUserId, actorId)
        .setIncrBy(OnboardingProcess::getVersion, 1)
        .set(OnboardingProcess::getUpdateId, actorId)
        .set(OnboardingProcess::getUpdateTime, now)));
  }

  @Override
  public int markReturned(String applicationId, String actorId, long version, Instant now) {
    return affected(update(Wrappers.lambdaUpdate(OnboardingProcess.class)
        .eq(OnboardingProcess::getApplicationId, applicationId)
        .eq(OnboardingProcess::getVersion, version)
        .in(OnboardingProcess::getStatus,
            OnboardingProcessStatus.IN_PROGRESS.getCode(),
            OnboardingProcessStatus.ONBOARDED.getCode())
        .set(OnboardingProcess::getStatus, OnboardingProcessStatus.RETURNED.getCode())
        .set(OnboardingProcess::getClosedAt, now)
        .set(OnboardingProcess::getCompletedByUserId, actorId)
        .setIncrBy(OnboardingProcess::getVersion, 1)
        .set(OnboardingProcess::getUpdateId, actorId)
        .set(OnboardingProcess::getUpdateTime, now)));
  }

  private static int affected(boolean changed) {
    return changed ? 1 : 0;
  }
}
