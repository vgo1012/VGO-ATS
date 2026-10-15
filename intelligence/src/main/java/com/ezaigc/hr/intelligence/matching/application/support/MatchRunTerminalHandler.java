package com.ezaigc.hr.intelligence.matching.application.support;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalHandler;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.intelligence.matching.constant.MatchingConstants;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunAttemptStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunItemOutcomeStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunItemOutcomePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在共享任务终态事务内收敛匹配运行及尝试，保留已提交的评分事实。 */
@Component
public class MatchRunTerminalHandler implements AsyncTaskTerminalHandler {
  @Resource private AsyncTaskPort tasks;
  @Resource private MatchRunPersistenceService runs;
  @Resource private MatchRunAttemptPersistenceService attempts;
  @Resource private MatchRunItemOutcomePersistenceService outcomes;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void terminalWithoutWorker(String taskType, String taskId, String resourceId,
      AsyncTaskStatus status, String errorCode, Instant finishedAt, String actorId) {
    if (!MatchingConstants.MATCH_RUN_TASK_TYPE.equals(taskType)) return;
    if (status != AsyncTaskStatus.FAILED && status != AsyncTaskStatus.CANCELLED) {
      throw new IllegalArgumentException("workerless matching completion requires a terminal task");
    }
    var run = runs.findBoundRunForUpdate(resourceId, taskId);
    if (run == null || MatchRunStatus.SUCCEEDED.matches(run.getStatus())
        || MatchRunStatus.CANCELLED.matches(run.getStatus())) return;
    boolean cancelled = status == AsyncTaskStatus.CANCELLED;
    var committed = outcomes.lockOutcomeStatuses(resourceId);
    int succeeded = Math.toIntExact(committed.stream().filter(value -> value == MatchRunItemOutcomeStatus.SUCCEEDED).count());
    int failed = cancelled
        ? Math.toIntExact(committed.stream().filter(value -> value == MatchRunItemOutcomeStatus.FAILED).count())
        : Math.max(0, run.getTotalCount() - succeeded);
    MatchRunStatus terminal = cancelled ? MatchRunStatus.CANCELLED
        : succeeded > 0 ? MatchRunStatus.PARTIAL : MatchRunStatus.FAILED;
    if (runs.terminateWithoutWorker(resourceId, taskId, terminal, succeeded, failed,
        cancelled ? null : errorCode, finishedAt, actorId) != 1) {
      throw new IllegalStateException("workerless matching completion did not update the bound run");
    }
    // 共享任务行仍由调用方锁定；读取本次尝试号，不把旧失租尝试误记为本次主动取消。
    attempts.terminateWithoutWorker(resourceId, tasks.get(taskId).attemptCount(),
        cancelled ? MatchRunAttemptStatus.CANCELLED : MatchRunAttemptStatus.FAILED,
        cancelled ? null : errorCode, finishedAt, actorId);
  }
}
