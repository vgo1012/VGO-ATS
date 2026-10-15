package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRunAttempt;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunAttemptStatus;
import java.time.Instant;

/** MatchRunAttempt 实体的持久化服务。 */
public interface MatchRunAttemptPersistenceService extends IService<MatchRunAttempt> {

  /** 新增尝试。 */
  int insertAttempt(String id, String runId, int attempt, Instant now);

  /** 新尝试持有任务行锁后，结束更早仍悬挂的失租尝试。 */
  void failPreviousAttempts(String runId, int currentAttempt, Instant now, String actorId);

  /** 终结已失去执行器的尝试；当前取消与更早失租分别保留其原因。 */
  void terminateWithoutWorker(String runId, int currentAttempt, MatchRunAttemptStatus status,
      String errorCode, Instant now, String actorId);

  /** 完成尝试。 */
  int finishAttempt(
      String runId,
      int attempt,
      MatchRunAttemptStatus status,
      String errorCode,
      Instant now);
}
