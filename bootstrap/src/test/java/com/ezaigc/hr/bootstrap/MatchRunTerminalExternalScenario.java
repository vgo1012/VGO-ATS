package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** 在隔离平台实库中复用真实评分配置，模拟执行器未启动或启动后退出的共享终态入口。 */
@RequiredArgsConstructor
final class MatchRunTerminalExternalScenario {
  private final JdbcTemplate jdbc;
  private final AsyncTaskPort tasks;
  private final PlatformIdGenerator ids;
  private final MatchRunPersistenceService runs;
  private final MatchRunAttemptPersistenceService attempts;
  private final MockMvc mvc;
  private final Cookie[] session;
  private final JsonNode csrf;
  private final TransactionTemplate transactions;

  void verify(String sourceRunId) throws Exception {
    SoftAssertions checks = new SoftAssertions();
    for (String scenario : List.of("queued-cancel", "running-failed", "running-cancelled",
        "unstarted-failed", "unstarted-cancelled", "running-partial-failed", "running-partial-cancelled",
        "running-rollback-failed", "running-rollback-cancelled", "queued-cancel-rollback",
        "running-protected-succeeded-failed", "running-protected-cancelled-failed",
        "running-mismatched-failed", "running-older-cancelled", "running-stale-partial-failed")) {
      verify(sourceRunId, scenario, checks);
    }
    checks.assertAll();
  }

  private void verify(String sourceRunId, String scenario, SoftAssertions checks) throws Exception {
    var source = runs.findRun(sourceRunId);
    String runId = ids.newId();
    String taskId = ids.newId();
    Instant now = Instant.now();
    assertThat(runs.insertRun(runId, taskId, source.getJdVersionId(), source.getJdFactSnapshotId(),
        source.getPlanVersionId(), source.getCriterionPlanVersionId(), source.getWeightProfileVersionId(),
        source.getPersonalPreferenceVersionId(), source.getRunPurpose(), source.getSelectionSnapshotId(),
        source.getSelectedCriterionIdsJson(), source.getSortMode(), source.getTotalCount(), source.getOwnerUserId(),
        source.getOwnerDepartmentId(), now))
        .isOne();
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(taskId, MatchRunApplicationManager.TASK_TYPE,
        "MATCH_RUN", runId, source.getOwnerUserId(), source.getOwnerUserId(), source.getOwnerDepartmentId(),
        "matching.run", "matching.run", "terminal-it-" + runId, "b".repeat(64), 1, now));
    try {
      boolean started = scenario.startsWith("running-");
      boolean queued = scenario.startsWith("queued-");
      boolean partial = scenario.contains("partial");
      boolean protectedRun = scenario.contains("protected") || scenario.contains("mismatched");
      boolean olderAttempt = scenario.contains("older");
      String expected = scenario.endsWith("failed") ? "FAILED" : "CANCELLED";
      if (!queued) {
        if (olderAttempt) {
          assertThat(jdbc.update("UPDATE reporting_async_task SET max_attempts=2 WHERE id=?", taskId)).isOne();
        }
        var claimed = tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1).getFirst();
        assertThat(claimed.id()).isEqualTo(taskId);
        if (started) {
          assertThat(runs.startRun(runId, now)).isOne();
          assertThat(attempts.insertAttempt(ids.newId(), runId, claimed.attemptCount(), now)).isOne();
        }
        if (olderAttempt) {
          assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?", taskId)).isOne();
          var second = tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1).getFirst();
          assertThat(second.id()).isEqualTo(taskId);
          assertThat(second.attemptCount()).isEqualTo(2);
          // 单独建立第二次业务尝试以验证终态区分；这不证明 process 已能自动恢复旧运行。
          assertThat(attempts.insertAttempt(ids.newId(), runId, second.attemptCount(), now)).isOne();
        }
        if (partial && !scenario.contains("stale")) {
          assertThat(jdbc.update("INSERT INTO matching_match_run_item_outcome"
              + "(match_run_id,candidate_id,resume_version_id,status,last_attempt_no)"
              + " SELECT ?,candidate_id,resume_version_id,status,1 FROM matching_match_run_item_outcome"
              + " WHERE match_run_id=?", runId, sourceRunId)).isOne();
          assertThat(jdbc.update("INSERT INTO matching_weighted_result"
              + "(id,match_run_id,base_evaluation_id,candidate_id,resume_version_id,professional_score,bonus_score,"
              + "reference_score,must_tier,stable_rank,result_json) SELECT ?,?,base_evaluation_id,candidate_id,"
              + "resume_version_id,professional_score,bonus_score,reference_score,must_tier,stable_rank,result_json"
              + " FROM matching_weighted_result WHERE match_run_id=?", ids.newId(), runId, sourceRunId)).isOne();
        }
        assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00',"
            + "status=? WHERE id=?", expected.equals("CANCELLED") ? "CANCELLING" : "RUNNING", taskId)).isOne();
      }
      if (scenario.contains("protected")) {
        assertThat(jdbc.update("UPDATE matching_match_run SET status=?,finished_at=NOW(6) WHERE id=?",
            scenario.contains("succeeded") ? "SUCCEEDED" : "CANCELLED", runId)).isOne();
      }
      if (scenario.contains("mismatched")) {
        assertThat(jdbc.update("UPDATE reporting_async_task SET resource_id=? WHERE id=?", sourceRunId, taskId)).isOne();
      }
      var guardedRun = jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", runId);
      var guardedAttempts = jdbc.queryForList("SELECT * FROM matching_match_run_attempt WHERE match_run_id=?", runId);
      var sourceBefore = jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", sourceRunId);
      var committedOutcomes = jdbc.queryForList("SELECT * FROM matching_match_run_item_outcome WHERE match_run_id=?", runId);
      var committedResults = jdbc.queryForList("SELECT * FROM matching_weighted_result WHERE match_run_id=?", runId);
      if (scenario.contains("stale")) {
        transactions.executeWithoutResult(ignored -> {
          // 先建立可重复读视图，再由另一个真实事务提交已成功项。
          assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_match_run_item_outcome WHERE match_run_id=?",
              Integer.class, runId)).isZero();
          try (var executor = Executors.newSingleThreadExecutor()) {
            var completion = executor.submit(() -> jdbc.update("INSERT INTO matching_match_run_item_outcome"
                + "(match_run_id,candidate_id,resume_version_id,status,last_attempt_no)"
                + " SELECT ?,candidate_id,resume_version_id,status,1 FROM matching_match_run_item_outcome"
                + " WHERE match_run_id=?", runId, sourceRunId));
            try {
              assertThat(completion.get(30, TimeUnit.SECONDS)).isOne();
            } catch (Exception exception) {
              throw new IllegalStateException("concurrent synthetic outcome did not commit", exception);
            } finally {
              if (!completion.isDone()) completion.cancel(true);
            }
          }
          assertThat(tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1)).isEmpty();
          var terminal = runs.findRun(runId);
          checks.assertThat(terminal.getStatus()).as("并发提交成功项应形成 PARTIAL").isEqualTo("PARTIAL");
          checks.assertThat(terminal.getSucceededCount()).as("终态计数必须包含加锁前已提交的成功项").isOne();
          checks.assertThat(terminal.getFailedCount()).as("已成功项不能重复统计为失败").isZero();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_match_run_item_outcome WHERE match_run_id=?",
            Integer.class, runId)).isOne();
        return;
      }
      if (scenario.contains("rollback")) {
        var beforeTask = jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", taskId);
        var beforeRun = jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", runId);
        var beforeAttempts = jdbc.queryForList("SELECT * FROM matching_match_run_attempt WHERE match_run_id=?", runId);
        var beforeSharedAttempts = jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=?", taskId);
        assertThat(runId).matches("[0-9A-Z]{26}");
        String trigger = "reject_match_terminal_" + runId;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()"
            + " AND trigger_name=?", Integer.class, trigger)).isZero();
        // 在业务尝试的最后写入点故障，必须连已执行的运行及共享任务终态一起回滚。
        String table = queued ? "matching_match_run" : "matching_match_run_attempt";
        String field = queued ? "id" : "match_run_id";
        jdbc.execute("CREATE TRIGGER `" + trigger + "` BEFORE UPDATE ON " + table
            + " FOR EACH ROW BEGIN IF NEW." + field + "='" + runId
            + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic match terminal failure'; END IF; END");
        try {
          assertThatThrownBy(() -> {
            if (queued) tasks.cancel(taskId, "实库回滚验证");
            else tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1);
          }).isInstanceOf(DataAccessException.class);
          assertThat(jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", taskId)).isEqualTo(beforeTask);
          assertThat(jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", runId)).isEqualTo(beforeRun);
          assertThat(jdbc.queryForList("SELECT * FROM matching_match_run_attempt WHERE match_run_id=?", runId)).isEqualTo(beforeAttempts);
          assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=?", taskId)).isEqualTo(beforeSharedAttempts);
        } finally {
          jdbc.execute("DROP TRIGGER `" + trigger + "`");
        }
      }
      if (queued) {
        // 走鉴权后的全局任务入口，不能用匹配专用 cancel 掩盖入口差异。
        mvc.perform(post("/api/v1/tasks/{id}/cancel", taskId).cookie(session)
                .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"实库共享入口取消\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
      }
      assertThat(tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1)).isEmpty();
      assertThat(tasks.get(taskId).status()).isEqualTo(expected);
      assertThat(tasks.get(taskId).finishedAt()).isNotNull();
      assertThat(jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", sourceRunId)).isEqualTo(sourceBefore);
      if (protectedRun) {
        assertThat(jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", runId)).isEqualTo(guardedRun);
        assertThat(jdbc.queryForList("SELECT * FROM matching_match_run_attempt WHERE match_run_id=?", runId)).isEqualTo(guardedAttempts);
        return;
      }
      var recovered = runs.findRun(runId);
      checks.assertThat(recovered.getStatus()).as(scenario + " matching run terminal")
          .isEqualTo(partial && expected.equals("FAILED") ? "PARTIAL" : expected);
      checks.assertThat(recovered.getFinishedAt()).as(scenario + " matching run finishedAt").isNotNull();
      assertThat(recovered.getSucceededCount()).isEqualTo(partial ? 1 : 0);
      assertThat(recovered.getFailedCount()).isEqualTo(expected.equals("FAILED") && !partial ? 1 : 0);
      assertThat(jdbc.queryForList("SELECT * FROM matching_match_run_item_outcome WHERE match_run_id=?", runId)).isEqualTo(committedOutcomes);
      assertThat(jdbc.queryForList("SELECT * FROM matching_weighted_result WHERE match_run_id=?", runId)).isEqualTo(committedResults);
      assertThat(tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1)).isEmpty();
      if (queued) tasks.cancel(taskId, "重复取消");
      assertThat(runs.findRun(runId)).usingRecursiveComparison().isEqualTo(recovered);
      if (started) {
        checks.assertThat(jdbc.queryForObject("SELECT status FROM matching_match_run_attempt WHERE match_run_id=? AND attempt_no=?",
            String.class, runId, tasks.get(taskId).attemptCount())).as(scenario + " matching attempt terminal").isEqualTo(expected);
        if (olderAttempt) {
          var old = jdbc.queryForMap("SELECT status,error_code,finished_at FROM matching_match_run_attempt WHERE match_run_id=? AND attempt_no=1", runId);
          assertThat(old).containsEntry("status", "FAILED").containsEntry("error_code", "TASK_LEASE_EXPIRED");
          assertThat(old.get("finished_at")).isNotNull();
        }
      } else {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_match_run_attempt WHERE match_run_id=?",
            Integer.class, runId)).isZero();
      }
    } finally {
      jdbc.update("DELETE FROM matching_weighted_result WHERE match_run_id=?", runId);
      jdbc.update("DELETE FROM matching_match_run_item_outcome WHERE match_run_id=?", runId);
      jdbc.update("DELETE FROM matching_match_run_attempt WHERE match_run_id=?", runId);
      jdbc.update("DELETE FROM matching_match_run WHERE id=?", runId);
      jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", taskId);
      jdbc.update("DELETE FROM reporting_async_task WHERE id=?", taskId);
    }
  }
}
