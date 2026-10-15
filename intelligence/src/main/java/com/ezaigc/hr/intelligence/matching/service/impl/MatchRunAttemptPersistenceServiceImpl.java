package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.intelligence.matching.constant.MatchingConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRunAttempt;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunAttemptStatus;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunAttemptMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** MatchRunAttempt 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class MatchRunAttemptPersistenceServiceImpl
    extends ServiceImpl<MatchRunAttemptMapper, MatchRunAttempt>
    implements MatchRunAttemptPersistenceService {

  /** 新增尝试。 */
  @Override
  public int insertAttempt(String id, String runId, int attempt, Instant now) {
    MatchRunAttempt value = new MatchRunAttempt();
    value.setId(id);
    value.setMatchRunId(runId);
    value.setAttemptNo(attempt);
    value.setStatus(MatchRunAttemptStatus.RUNNING.getCode());
    value.setStartedAt(now);
    value.setCreateId(PlatformActorConstants.SYSTEM_ACTOR_ID);
    value.setCreateTime(now);
    value.setUpdateId(PlatformActorConstants.SYSTEM_ACTOR_ID);
    value.setUpdateTime(now);
    return baseMapper.insert(value);
  }

  /** 完成尝试。 */
  @Override
  public void terminateWithoutWorker(String runId, int currentAttempt, MatchRunAttemptStatus status,
      String errorCode, Instant now, String actorId) {
    failPreviousAttempts(runId, currentAttempt, now, actorId);
    baseMapper.update(Wrappers.lambdaUpdate(MatchRunAttempt.class)
        .eq(MatchRunAttempt::getMatchRunId, runId).eq(MatchRunAttempt::getAttemptNo, currentAttempt)
        .eq(MatchRunAttempt::getStatus, MatchRunAttemptStatus.RUNNING.getCode())
        .set(MatchRunAttempt::getStatus, status.getCode())
        .set(MatchRunAttempt::getErrorCode, status == MatchRunAttemptStatus.CANCELLED
            ? null : errorCode)
        .set(MatchRunAttempt::getFinishedAt, now).setIncrBy(MatchRunAttempt::getVersion, 1)
        .set(MatchRunAttempt::getUpdateId, actorId).set(MatchRunAttempt::getUpdateTime, now));
  }

  /** 只结束比当前尝试更早且仍处于运行中的尝试，不覆盖历史终态。 */
  @Override
  public void failPreviousAttempts(String runId, int currentAttempt, Instant now, String actorId) {
    baseMapper.update(Wrappers.lambdaUpdate(MatchRunAttempt.class)
        .eq(MatchRunAttempt::getMatchRunId, runId).lt(MatchRunAttempt::getAttemptNo, currentAttempt)
        .eq(MatchRunAttempt::getStatus, MatchRunAttemptStatus.RUNNING.getCode())
        .set(MatchRunAttempt::getStatus, MatchRunAttemptStatus.FAILED.getCode())
        .set(MatchRunAttempt::getErrorCode, MatchingConstants.TASK_LEASE_EXPIRED)
        .set(MatchRunAttempt::getFinishedAt, now).setIncrBy(MatchRunAttempt::getVersion, 1)
        .set(MatchRunAttempt::getUpdateId, actorId).set(MatchRunAttempt::getUpdateTime, now));
  }

  /** 完成尝试。 */
  @Override
  public int finishAttempt(
      String runId,
      int attempt,
      MatchRunAttemptStatus status,
      String errorCode,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(MatchRunAttempt.class)
            .eq(MatchRunAttempt::getMatchRunId, runId)
            .eq(MatchRunAttempt::getAttemptNo, attempt)
            .eq(MatchRunAttempt::getStatus, MatchRunAttemptStatus.RUNNING.getCode())
            .set(MatchRunAttempt::getStatus, status.getCode())
            .set(MatchRunAttempt::getErrorCode, errorCode)
            .set(MatchRunAttempt::getFinishedAt, now)
            .setIncrBy(MatchRunAttempt::getVersion, 1)
            .set(MatchRunAttempt::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(MatchRunAttempt::getUpdateTime, now));
  }
}
