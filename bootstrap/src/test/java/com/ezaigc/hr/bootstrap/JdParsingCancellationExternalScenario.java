package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.recruiting.demand.application.manager.JdParsingApplicationManager;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ModelResult;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ModelUnavailableException;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 用独立线程取消真实解析任务，验证迟到失败原因和正式事实的事务保护。 */
@RequiredArgsConstructor
final class JdParsingCancellationExternalScenario {
  private final JdbcTemplate jdbc;
  private final AsyncTaskPort tasks;
  private final PlatformIdGenerator ids;
  private final JdParsingApplicationManager parsing;
  private final JdParseModelPort model;
  private final RawAiArchivePort archive;
  private final AsyncTaskService taskPersistence;

  private enum Stage { MODEL_UNAVAILABLE, MODEL_RUNTIME, EMPTY_RESULT, ARCHIVE_IO, INVALID_RESULT, FINAL_CHECK, SQL_REJECTION }

  void verify(String versionId, String actorId) throws Exception {
    SoftAssertions checks = new SoftAssertions();
    for (Stage stage : Stage.values()) verify(stage, versionId, actorId, checks);
    checks.assertAll();
  }

  private void verify(Stage stage, String versionId, String actorId, SoftAssertions checks) throws Exception {
    String taskId = ids.newId();
    String department = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, actorId);
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(taskId, "JD_PARSE", "JD_VERSION", versionId,
        actorId, actorId, department, "demand.read", "demand.manage", "jd-cancel-" + taskId, "c".repeat(64), 1, Instant.now()));
    var task = tasks.claim("JD_PARSE", 1).getFirst();
    assertThat(task.id()).isEqualTo(taskId);
    var factsBefore = jdbc.queryForList("SELECT * FROM demand_jd_fact_snapshot WHERE jd_version_id=? ORDER BY id", versionId);
    String trigger = "reject_jd_cancel_" + taskId;
    assertThat(taskId).matches("[0-9A-Z]{26}");
    AtomicInteger leaseReads = new AtomicInteger();
    AtomicReference<List<Map<String, Object>>> beforeRejectedWrite = new AtomicReference<>();
    try {
      if (stage == Stage.FINAL_CHECK) {
        doAnswer(call -> {
          Object active = call.callRealMethod();
          if (leaseReads.incrementAndGet() == 2) cancel(taskId);
          return active;
        }).when(taskPersistence).hasActiveLease(eq(taskId), anyInt(), any());
      } else if (stage == Stage.ARCHIVE_IO) {
        doAnswer(call -> {
          cancel(taskId);
          throw new IOException("Synthetic JD archive cancellation race");
        }).when(archive).archive(anyString(), anyString());
      } else {
        doAnswer(call -> {
          ModelResult result = (ModelResult) call.callRealMethod();
          cancel(taskId);
          return switch (stage) {
            case MODEL_UNAVAILABLE -> throw new ModelUnavailableException("Synthetic JD cancellation race");
            case MODEL_RUNTIME -> throw new IllegalStateException("Synthetic JD cancellation race");
            case EMPTY_RESULT -> null;
            case INVALID_RESULT -> new ModelResult(result.provider(), result.modelVersion(), "{}", 2, 1, 1);
            case SQL_REJECTION -> {
              String runId = jdbc.queryForObject("SELECT id FROM demand_jd_ai_model_run WHERE task_id=?", String.class, taskId);
              assertThat(runId).matches("[0-9A-Z]{26}");
              jdbc.execute("CREATE TRIGGER `" + trigger + "` BEFORE UPDATE ON demand_jd_ai_model_run FOR EACH ROW BEGIN "
                  + "IF NEW.id='" + runId + "' AND NEW.status='FAILED' THEN SIGNAL SQLSTATE '45000' "
                  + "SET MESSAGE_TEXT='Synthetic JD cancellation write rejection'; END IF; END");
              beforeRejectedWrite.set(history(taskId));
              throw new IllegalStateException("Synthetic JD cancellation race before SQL rejection");
            }
            default -> throw new AssertionError("Unexpected model interception stage");
          };
        }).when(model).parse(any());
      }
      Throwable failure = catchThrowable(() -> parsing.process(task));
      if (stage == Stage.SQL_REJECTION) {
        assertThat(failure).isInstanceOf(DataAccessException.class).hasStackTraceContaining("Synthetic JD cancellation write rejection");
        assertThat(tasks.get(taskId).status()).as("业务写入失败时取消完成必须回滚").isEqualTo("CANCELLING");
        assertThat(jdbc.queryForObject("SELECT status FROM reporting_async_task_attempt WHERE task_id=?", String.class, taskId)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("SELECT status FROM demand_jd_ai_model_run WHERE task_id=?", String.class, taskId)).isEqualTo("RUNNING");
        assertThat(history(taskId)).as("任务、尝试、模型的审计和状态必须完整回滚").isEqualTo(beforeRejectedWrite.get());
        jdbc.execute("DROP TRIGGER `" + trigger + "`");
        assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?", taskId)).isOne();
        assertThat(tasks.claim("JD_PARSE", 1)).isEmpty();
      } else {
        assertThat(failure).isNull();
      }
      assertThat(tasks.get(taskId).status()).isEqualTo("CANCELLED");
      assertThat(tasks.get(taskId).safeErrorDetail()).isEqualTo("JD 解析并发取消");
      var run = jdbc.queryForMap("SELECT * FROM demand_jd_ai_model_run WHERE task_id=?", taskId);
      assertThat(run.get("status")).isEqualTo("FAILED");
      checks.assertThat(run.get("error_code")).as(stage + " 共享取消胜出后业务原因必须一致").isEqualTo("TASK_CANCELLED");
      assertThat(run.get("finished_at")).isNotNull();
      String validation = switch (stage) {
        case MODEL_UNAVAILABLE, MODEL_RUNTIME -> "MODEL_UNAVAILABLE";
        case EMPTY_RESULT, INVALID_RESULT -> "SCHEMA_INVALID";
        case ARCHIVE_IO, SQL_REJECTION -> "PENDING";
        case FINAL_CHECK -> "VALID";
      };
      assertThat(run.get("validation_status")).as("取消原因不覆盖实际校验诊断").isEqualTo(validation);
      if (stage == Stage.INVALID_RESULT || stage == Stage.FINAL_CHECK) {
        assertThat(run.get("raw_output_object_key")).isNotNull();
        assertThat(run.get("raw_output_hash")).isNotNull();
      }
      assertThat(jdbc.queryForList("SELECT * FROM demand_jd_fact_snapshot WHERE jd_version_id=? ORDER BY id", versionId))
          .as(stage + " 取消后的解析不得发布或切换正式事实").isEqualTo(factsBefore);
      if (stage == Stage.FINAL_CHECK) assertThat(leaseReads.get()).isGreaterThanOrEqualTo(2);
    } finally {
      reset(model, archive, taskPersistence);
      if (stage == Stage.SQL_REJECTION) jdbc.execute("DROP TRIGGER IF EXISTS `" + trigger + "`");
      // 所有数据均为本次随机库内独立任务，由外层运行器整体销毁，避免拆除正式事实的外键。
    }
  }

  private void cancel(String taskId) throws Exception {
    try (var executor = Executors.newSingleThreadExecutor()) {
      executor.submit(() -> assertThat(tasks.cancel(taskId, "JD 解析并发取消").status()).isEqualTo("CANCELLING"))
          .get(30, TimeUnit.SECONDS);
    }
  }

  private List<Map<String, Object>> history(String taskId) {
    return List.of(jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", taskId),
        jdbc.queryForMap("SELECT * FROM reporting_async_task_attempt WHERE task_id=?", taskId),
        jdbc.queryForMap("SELECT * FROM demand_jd_ai_model_run WHERE task_id=?", taskId));
  }
}
