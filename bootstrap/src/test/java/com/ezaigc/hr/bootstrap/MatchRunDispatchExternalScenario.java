package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实启动事务失败经过调度器后，共享任务与业务记录必须一起结束。 */
@RequiredArgsConstructor
final class MatchRunDispatchExternalScenario {
  private final JdbcTemplate jdbc;
  private final AsyncTaskPort tasks;
  private final PlatformIdGenerator ids;
  private final MatchRunPersistenceService runs;
  private final MatchRunApplicationManager manager;
  private final AsyncTaskBatchRunner runner;
  private final MatchRunAttemptPersistenceService attempts;
  private final TransactionTemplate transactions;

  private enum StartedFailure { FAILED, CANCELLED, ROLLBACK_RUN, ROLLBACK_ATTEMPT }

  void verify(String sourceRunId) {
    SoftAssertions checks = new SoftAssertions();
    for (int maxAttempts : new int[] {1, 2}) verifyStartupFailure(sourceRunId, maxAttempts, checks);
    for (StartedFailure mode : StartedFailure.values()) verifyStartedFailure(sourceRunId, mode);
    checks.assertAll();
  }

  private void verifyStartupFailure(String sourceRunId, int maxAttempts, SoftAssertions checks) {
    MatchRun run = enqueue(sourceRunId, maxAttempts);
    String runId = run.getId();
    String taskId = run.getTaskId();
    assertThat(runId).matches("[0-9A-Z]{26}");
    String trigger = "reject_dispatch_" + runId;
    try {
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",
          Integer.class, trigger)).isZero();
      jdbc.execute("CREATE TRIGGER `" + trigger + "` BEFORE INSERT ON matching_match_run_attempt FOR EACH ROW BEGIN "
          + "IF NEW.match_run_id='" + runId + "' THEN SIGNAL SQLSTATE '45000' "
          + "SET MESSAGE_TEXT='Synthetic dispatcher startup failure'; END IF; END");
      try {
        runner.run(MatchRunApplicationManager.TASK_TYPE, 1, claimed -> {
          assertThat(claimed.id()).isEqualTo(taskId);
          manager.process(claimed);
        });
      } finally {
        jdbc.execute("DROP TRIGGER `" + trigger + "`");
      }
      var task = tasks.get(taskId);
      checks.assertThat(task.status()).as("共享任务失败，最大尝试次数 %s", maxAttempts).isEqualTo("FAILED");
      checks.assertThat(task.errorCode()).isEqualTo("TASK_EXECUTION_FAILED");
      MatchRun failed = runs.findRun(runId);
      checks.assertThat(failed.getStatus()).as("业务运行同步失败，最大尝试次数 %s", maxAttempts).isEqualTo("FAILED");
      checks.assertThat(failed.getFinishedAt()).as("业务运行具有结束时间").isNotNull();
      checks.assertThat(failed.getErrorCode()).as("保留实际启动错误类别").isEqualTo("TASK_EXECUTION_FAILED");
      checks.assertThat(failed.getFailedCount()).as("未完成匹配项计入失败").isEqualTo(run.getTotalCount());
      checks.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_match_run_attempt WHERE match_run_id=?", Integer.class, runId))
          .as("启动已回滚，不虚构业务尝试").isZero();
      assertThat(task.retryable()).isEqualTo(maxAttempts > 1);
      if (maxAttempts > 1) {
        tasks.retryFailed(taskId, "启动故障已解除");
        runner.run(MatchRunApplicationManager.TASK_TYPE, 1, manager::process);
        assertThat(tasks.get(taskId).status()).isEqualTo("SUCCEEDED");
        assertThat(runs.findRun(runId).getStatus()).isEqualTo("SUCCEEDED");
      }
    } finally {
      cleanup(run);
    }
  }

  private void verifyStartedFailure(String sourceRunId, StartedFailure mode) {
    MatchRun run = enqueue(sourceRunId, 2);
    boolean rollback = mode == StartedFailure.ROLLBACK_RUN || mode == StartedFailure.ROLLBACK_ATTEMPT;
    String trigger = "reject_terminal_" + run.getId();
    AtomicReference<List<Object>> before = new AtomicReference<>();
    AtomicReference<AsyncTaskPort.TaskSnapshot> firstClaim = new AtomicReference<>();
    try {
      if (rollback) {
        String table = mode == StartedFailure.ROLLBACK_RUN ? "matching_match_run" : "matching_match_run_attempt";
        String key = mode == StartedFailure.ROLLBACK_RUN ? "id" : "match_run_id";
        assertThat(run.getId()).matches("[0-9A-Z]{26}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",
            Integer.class, trigger)).isZero();
        jdbc.execute("CREATE TRIGGER `" + trigger + "` BEFORE UPDATE ON " + table + " FOR EACH ROW BEGIN "
            + "IF NEW." + key + "='" + run.getId() + "' AND NEW.status IN ('FAILED','CANCELLED') THEN SIGNAL SQLSTATE '45000' "
            + "SET MESSAGE_TEXT='Synthetic dispatcher terminal failure'; END IF; END");
      }
      try {
        runner.run(MatchRunApplicationManager.TASK_TYPE, 1, claimed -> {
          assertThat(claimed.id()).isEqualTo(run.getTaskId());
          firstClaim.set(claimed);
          // 构造已启动后异常退出的处理器；写入使用真实租约和业务事务，不替换调度器或持久化实现。
          transactions.executeWithoutResult(ignored -> {
            assertThat(tasks.renewLease(claimed)).isTrue();
            assertThat(runs.startRun(run.getId(), Instant.now())).isOne();
            assertThat(attempts.insertAttempt(ids.newId(), run.getId(), claimed.attemptCount(), Instant.now())).isOne();
          });
          if (mode == StartedFailure.CANCELLED) tasks.cancel(run.getTaskId(), "处理器异常前取消");
          before.set(snapshot(run));
          throw new IllegalStateException("Synthetic initialized processor failure");
        });
      } finally {
        if (rollback) jdbc.execute("DROP TRIGGER `" + trigger + "`");
      }
      if (rollback) {
        assertThat(snapshot(run)).as(mode + " 业务回调失败，共享任务及两类尝试全部回滚").isEqualTo(before.get());
        assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?", run.getTaskId())).isOne();
      } else {
        String terminal = mode == StartedFailure.CANCELLED ? "CANCELLED" : "FAILED";
        String code = mode == StartedFailure.CANCELLED ? null : "TASK_EXECUTION_FAILED";
        assertThat(tasks.get(run.getTaskId()).status()).isEqualTo(terminal);
        assertThat(runs.findRun(run.getId()).getStatus()).isEqualTo(terminal);
        assertThat(runs.findRun(run.getId()).getErrorCode()).isEqualTo(code);
        assertThat(jdbc.queryForObject("SELECT status FROM matching_match_run_attempt WHERE match_run_id=? AND attempt_no=1",
            String.class, run.getId())).isEqualTo(terminal);
        assertThat(jdbc.queryForObject("SELECT error_code FROM matching_match_run_attempt WHERE match_run_id=? AND attempt_no=1",
            String.class, run.getId())).isEqualTo(code);
        if (mode == StartedFailure.FAILED) tasks.retryFailed(run.getTaskId(), "处理器故障已解除");
      }
      if (mode != StartedFailure.CANCELLED) {
        runner.run(MatchRunApplicationManager.TASK_TYPE, 1, manager::process);
        assertThat(tasks.get(run.getTaskId()).status()).isEqualTo("SUCCEEDED");
        assertThat(runs.findRun(run.getId()).getStatus()).isEqualTo("SUCCEEDED");
        var succeeded = snapshot(run);
        assertThatThrownBy(() -> tasks.failUnhandled(firstClaim.get(), "TASK_EXECUTION_FAILED", "迟到旧执行器", true))
            .isInstanceOf(PlatformException.class).extracting("code").isEqualTo("TASK_STATE_CONFLICT");
        assertThat(snapshot(run)).as("迟到兜底不得覆盖较新成功结果").isEqualTo(succeeded);
      }
    } finally {
      cleanup(run);
    }
  }

  private List<Object> snapshot(MatchRun run) {
    return List.of(jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", run.getTaskId()),
        jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=? ORDER BY attempt_no", run.getTaskId()),
        jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", run.getId()),
        jdbc.queryForList("SELECT * FROM matching_match_run_attempt WHERE match_run_id=? ORDER BY attempt_no", run.getId()),
        jdbc.queryForList("SELECT * FROM matching_weighted_result WHERE match_run_id=?", run.getId()),
        jdbc.queryForList("SELECT * FROM matching_match_run_item_outcome WHERE match_run_id=?", run.getId()));
  }

  private MatchRun enqueue(String sourceRunId, int maxAttempts) {
    var source = runs.findRun(sourceRunId);
    String runId = ids.newId();
    String taskId = ids.newId();
    Instant now = Instant.now();
    assertThat(runs.insertRun(runId, taskId, source.getJdVersionId(), source.getJdFactSnapshotId(), source.getPlanVersionId(),
        source.getCriterionPlanVersionId(), source.getWeightProfileVersionId(), source.getPersonalPreferenceVersionId(),
        source.getRunPurpose(), source.getSelectionSnapshotId(), source.getSelectedCriterionIdsJson(), source.getSortMode(),
        source.getTotalCount(), source.getOwnerUserId(), source.getOwnerDepartmentId(), now)).isOne();
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(taskId, MatchRunApplicationManager.TASK_TYPE, "MATCH_RUN", runId,
        source.getOwnerUserId(), source.getOwnerUserId(), source.getOwnerDepartmentId(), "matching.run", "matching.run",
        "dispatch-it-" + runId, "d".repeat(64), maxAttempts, now));
    return runs.findRun(runId);
  }

  private void cleanup(MatchRun run) {
    jdbc.update("DELETE FROM matching_weighted_result WHERE match_run_id=?", run.getId());
    jdbc.update("DELETE FROM matching_match_run_item_outcome WHERE match_run_id=?", run.getId());
    jdbc.update("DELETE FROM matching_match_run_attempt WHERE match_run_id=?", run.getId());
    jdbc.update("DELETE FROM matching_match_run WHERE id=?", run.getId());
    jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", run.getTaskId());
    jdbc.update("DELETE FROM reporting_async_task WHERE id=?", run.getTaskId());
  }
}
