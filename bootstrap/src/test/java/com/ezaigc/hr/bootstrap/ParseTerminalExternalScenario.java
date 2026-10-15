package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.recruiting.demand.application.manager.JdParsingApplicationManager;
import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

/** 实际解析处理器中断后的共享终态恢复；合成错误用于中断，不代表真实进程强杀验收。 */
@RequiredArgsConstructor
final class ParseTerminalExternalScenario {
  private final JdbcTemplate jdbc;
  private final AsyncTaskPort tasks;
  private final PlatformIdGenerator ids;
  private final ResumeParsingApplicationManager resumes;
  private final JdParsingApplicationManager jds;
  private final AsyncTaskBatchRunner runner;
  private final RawAiArchivePort archive;
  private final ResumeDocumentExtractionPort extraction;
  private final AsyncTaskService taskPersistence;

  private enum Stage { RESUME_EXTRACTION, RESUME_MODEL, JD_MODEL }
  private enum Probe { NORMAL, ROLLBACK, MISMATCH }

  void verify(String resumeVersionId, String jdVersionId, String actorId) throws Exception {
    SoftAssertions checks = new SoftAssertions();
    for (Stage stage : Stage.values()) {
      for (boolean cancelling : new boolean[] {false, true}) {
        verifyTerminal(stage, cancelling, false, Probe.NORMAL, resumeVersionId, jdVersionId, actorId, checks);
      }
      verifyTerminal(stage, false, false, Probe.ROLLBACK, resumeVersionId, jdVersionId, actorId, checks);
      verifyTerminal(stage, false, false, Probe.MISMATCH, resumeVersionId, jdVersionId, actorId, checks);
    }
    verifyTerminal(Stage.JD_MODEL, false, true, Probe.NORMAL, resumeVersionId, jdVersionId, actorId, checks);
    verifyCancellationBeforeStartup(false, resumeVersionId, actorId, checks);
    verifyCancellationBeforeStartup(true, jdVersionId, actorId, checks);
    for (Stage stage : Stage.values()) verifyReclaim(stage, resumeVersionId, jdVersionId, actorId, checks);
    checks.assertAll();
  }

  private void verifyReclaim(Stage stage, String resumeVersionId, String jdVersionId,
      String actorId, SoftAssertions checks) throws Exception {
    boolean jd = stage == Stage.JD_MODEL;
    String taskType = jd ? "JD_PARSE" : "RESUME_PARSE";
    String taskId = ids.newId();
    String versionId = jd ? jdVersionId : resumeVersionId;
    String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, actorId);
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(taskId, taskType, jd ? "JD_VERSION" : "RESUME_VERSION", versionId,
        actorId, actorId, departmentId, jd ? "demand.read" : "talent.read", jd ? "demand.manage" : "talent.manage",
        "parse-reclaim-" + taskId, "a".repeat(64), 2, Instant.now()));
    var interrupted = new AssertionError("Synthetic first parse attempt interruption");
    try {
      if (stage == Stage.RESUME_EXTRACTION) doThrow(interrupted).when(extraction).extract(anyString(), any());
      else doThrow(interrupted).when(archive).archive(anyString(), anyString());
      var first = tasks.claim(taskType, 1).getFirst();
      assertThat(first.id()).isEqualTo(taskId);
      assertThatThrownBy(() -> {
        if (jd) jds.process(first); else resumes.process(first);
      }).isSameAs(interrupted);
    } finally {
      reset(archive, extraction);
    }
    var oldBusiness = business(stage, taskId);
    assertThat(oldBusiness).hasSize(1);
    assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?", taskId)).isOne();
    var second = tasks.claim(taskType, 1).getFirst();
    assertThat(second.id()).isEqualTo(taskId);
    assertThat(second.attemptCount()).isEqualTo(2);
    var beforeTask = jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", taskId);
    String trigger = "reject_parse_start_" + taskId;
    assertThat(taskId).matches("[0-9A-Z]{26}");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",
        Integer.class, trigger)).isZero();
    String startupTable = jd ? "demand_jd_ai_model_run" : "talent_resume_extraction_run";
    jdbc.execute("CREATE TRIGGER `" + trigger + "` BEFORE INSERT ON " + startupTable + " FOR EACH ROW BEGIN "
        + "IF NEW.task_id='" + taskId + "' THEN SIGNAL SQLSTATE '45000' "
        + "SET MESSAGE_TEXT='Synthetic reclaimed parse startup failure'; END IF; END");
    try {
      assertThatThrownBy(() -> {
        if (jd) jds.process(second); else resumes.process(second);
      }).isInstanceOf(DataAccessException.class).hasStackTraceContaining("Synthetic reclaimed parse startup failure");
      assertThat(jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", taskId)).isEqualTo(beforeTask);
      assertThat(business(stage, taskId)).as("重领启动失败必须回滚旧运行状态").isEqualTo(oldBusiness);
    } finally {
      jdbc.execute("DROP TRIGGER `" + trigger + "`");
    }
    if (jd) jds.process(second); else resumes.process(second);
    assertThat(tasks.get(taskId).status()).as("重领解析失败码: %s", tasks.get(taskId).errorCode()).isEqualTo("SUCCEEDED");
    String oldTable = switch (stage) {
      case JD_MODEL -> "demand_jd_ai_model_run";
      case RESUME_EXTRACTION -> "talent_resume_extraction_run";
      case RESUME_MODEL -> "talent_resume_ai_model_run";
    };
    var oldRun = jdbc.queryForMap("SELECT * FROM " + oldTable + " WHERE id=?", oldBusiness.getFirst().get("id"));
    checks.assertThat(oldRun.get("status")).as(stage + " 重领成功后旧运行必须结束").isEqualTo("FAILED");
    checks.assertThat(oldRun.get("error_code")).as(stage + " 旧运行记录失租原因").isEqualTo("TASK_LEASE_EXPIRED");
    checks.assertThat(oldRun.get("finished_at")).as(stage + " 旧运行完成时间").isNotNull();
    // 成功解析产生正式事实及引用，不手工拆除业务外键；与平台链一起由运行器销毁本次随机库和对象目录。
  }

  private void verifyCancellationBeforeStartup(boolean jd, String versionId, String actorId, SoftAssertions checks) {
    String taskType = jd ? "JD_PARSE" : "RESUME_PARSE";
    String taskId = ids.newId();
    String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, actorId);
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(taskId, taskType, jd ? "JD_VERSION" : "RESUME_VERSION", versionId,
        actorId, actorId, departmentId, jd ? "demand.read" : "talent.read", jd ? "demand.manage" : "talent.manage",
        "parse-start-" + taskId, "f".repeat(64), 1, Instant.now()));
    AtomicBoolean interrupted = new AtomicBoolean();
    try {
      var claimed = tasks.claim(taskType, 1).getFirst();
      assertThat(claimed.id()).isEqualTo(taskId);
      doAnswer(call -> {
        Object active = call.callRealMethod();
        if (interrupted.compareAndSet(false, true)) {
          // 原执行器读到有效租约后，另一线程真实取消并回收；返回旧查询结果暴露检查与启动间隙。
          try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
              assertThat(tasks.cancel(taskId, "启动前取消解析").status()).isEqualTo("CANCELLING");
              assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?", taskId)).isOne();
              assertThat(tasks.claim(taskType, 1)).isEmpty();
            }).get(30, TimeUnit.SECONDS);
          }
        }
        return active;
      }).when(taskPersistence).hasActiveLease(eq(taskId), anyInt(), any());
      Throwable stopped = catchThrowable(() -> {
        if (jd) jds.process(claimed); else resumes.process(claimed);
      });
      if (stopped != null) {
        assertThat(stopped).isInstanceOf(PlatformException.class).extracting("code").isEqualTo("TASK_STATE_CONFLICT");
      }
      assertThat(interrupted).isTrue();
      assertThat(tasks.get(taskId).status()).isEqualTo("CANCELLED");
      String table = jd ? "demand_jd_ai_model_run" : "talent_resume_extraction_run";
      checks.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE task_id=?", Integer.class, taskId))
          .as(taskType + " 最终取消后不能再启动业务运行").isZero();
    } finally {
      reset(taskPersistence);
      cleanup(taskId);
    }
  }

  private void verifyTerminal(Stage stage, boolean cancelling, boolean dispatcher, Probe probe,
      String resumeVersionId, String jdVersionId, String actorId, SoftAssertions checks) throws Exception {
    boolean jd = stage == Stage.JD_MODEL;
    String taskType = jd ? "JD_PARSE" : "RESUME_PARSE";
    String versionId = jd ? jdVersionId : resumeVersionId;
    String taskId = ids.newId();
    String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, actorId);
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(taskId, taskType, jd ? "JD_VERSION" : "RESUME_VERSION", versionId,
        actorId, actorId, departmentId, jd ? "demand.read" : "talent.read", jd ? "demand.manage" : "talent.manage",
        "parse-terminal-" + taskId, "e".repeat(64), 1, Instant.now()));
    Throwable interrupted = dispatcher ? new IllegalStateException("Synthetic JD archive runtime failure")
        : new AssertionError("Synthetic parse processor interruption");
    try {
      if (stage == Stage.RESUME_EXTRACTION) {
        doThrow(interrupted).when(extraction).extract(anyString(), any());
      } else {
        doThrow(interrupted).when(archive).archive(anyString(), anyString());
      }
      if (dispatcher) {
        runner.run(taskType, 1, task -> {
          assertThat(task.id()).isEqualTo(taskId);
          jds.process(task);
        });
      } else {
        var claimed = tasks.claim(taskType, 1).getFirst();
        assertThat(claimed.id()).isEqualTo(taskId);
        assertThatThrownBy(() -> {
          if (jd) jds.process(claimed); else resumes.process(claimed);
        }).isSameAs(interrupted);
        assertThat(tasks.get(taskId).status()).isEqualTo("RUNNING");
      }
      var completedExtractions = jdbc.queryForList("SELECT * FROM talent_resume_extraction_run WHERE task_id=? AND status='SUCCEEDED'", taskId);
      var beforeBusiness = business(stage, taskId);
      assertThat(beforeBusiness).hasSize(1);
      if (!dispatcher) {
        if (cancelling) assertThat(tasks.cancel(taskId, "中断后取消解析").status()).isEqualTo("CANCELLING");
        assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?", taskId)).isOne();
        if (probe == Probe.MISMATCH) {
          assertThat(jdbc.update("UPDATE reporting_async_task SET resource_id=? WHERE id=?", "Z".repeat(26), taskId)).isOne();
        }
        if (probe == Probe.ROLLBACK) {
          String runId = beforeBusiness.getFirst().get("id").toString();
          assertThat(runId).matches("[0-9A-Z]{26}");
          String trigger = "reject_parse_" + runId;
          String table = switch (stage) {
            case JD_MODEL -> "demand_jd_ai_model_run";
            case RESUME_EXTRACTION -> "talent_resume_extraction_run";
            case RESUME_MODEL -> "talent_resume_ai_model_run";
          };
          assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",
              Integer.class, trigger)).isZero();
          var beforeTask = jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", taskId);
          var beforeAttempt = jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=?", taskId);
          jdbc.execute("CREATE TRIGGER `" + trigger + "` BEFORE UPDATE ON " + table + " FOR EACH ROW BEGIN "
              + "IF NEW.id='" + runId + "' AND NEW.status='FAILED' THEN SIGNAL SQLSTATE '45000' "
              + "SET MESSAGE_TEXT='Synthetic parse terminal write failure'; END IF; END");
          try {
            assertThatThrownBy(() -> tasks.claim(taskType, 1)).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("Synthetic parse terminal write failure");
            assertThat(jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", taskId)).isEqualTo(beforeTask);
            assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=?", taskId)).isEqualTo(beforeAttempt);
            assertThat(business(stage, taskId)).isEqualTo(beforeBusiness);
          } finally {
            jdbc.execute("DROP TRIGGER `" + trigger + "`");
          }
        }
        assertThat(tasks.claim(taskType, 1)).isEmpty();
      }
      String taskStatus = cancelling ? "CANCELLED" : "FAILED";
      assertThat(tasks.get(taskId).status()).isEqualTo(taskStatus);
      var afterBusiness = business(stage, taskId);
      assertThat(afterBusiness).hasSize(1);
      if (probe == Probe.MISMATCH) {
        assertThat(afterBusiness).as("任务版本绑定不匹配，不能终结其他业务记录").isEqualTo(beforeBusiness);
      } else {
        var actual = afterBusiness.getFirst();
        String label = stage + " " + taskStatus + " dispatcher=" + dispatcher;
        checks.assertThat(actual.get("status")).as(label + " 业务运行不得悬挂").isEqualTo("FAILED");
        checks.assertThat(actual.get("finished_at")).as(label + " 记录终结时间").isNotNull();
        checks.assertThat(actual.get("error_code")).as(label + " 保留实际终结原因")
            .isEqualTo(cancelling ? "TASK_CANCELLED" : dispatcher ? "TASK_EXECUTION_FAILED" : "TASK_LEASE_EXPIRED");
      }
      assertThat(jdbc.queryForList("SELECT * FROM talent_resume_extraction_run WHERE task_id=? AND status='SUCCEEDED'", taskId))
          .as("已完成的抽取事实保持原样").isEqualTo(completedExtractions);
    } finally {
      reset(archive, extraction);
      cleanup(taskId);
    }
  }

  private List<Map<String, Object>> business(Stage stage, String taskId) {
    return switch (stage) {
      case JD_MODEL -> jdbc.queryForList("SELECT * FROM demand_jd_ai_model_run WHERE task_id=?", taskId);
      case RESUME_EXTRACTION -> jdbc.queryForList("SELECT * FROM talent_resume_extraction_run WHERE task_id=?", taskId);
      case RESUME_MODEL -> jdbc.queryForList("SELECT m.* FROM talent_resume_ai_model_run m JOIN talent_resume_extraction_run e "
          + "ON e.id=m.extraction_run_id WHERE e.task_id=?", taskId);
    };
  }

  private void cleanup(String taskId) {
    jdbc.update("DELETE FROM demand_jd_ai_model_run WHERE task_id=?", taskId);
    jdbc.update("DELETE m FROM talent_resume_ai_model_run m JOIN talent_resume_extraction_run e ON e.id=m.extraction_run_id WHERE e.task_id=?", taskId);
    jdbc.update("DELETE p FROM talent_resume_extraction_page p JOIN talent_resume_extraction_run e ON e.id=p.extraction_run_id WHERE e.task_id=?", taskId);
    jdbc.update("DELETE FROM talent_resume_extraction_run WHERE task_id=?", taskId);
    jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", taskId);
    jdbc.update("DELETE FROM reporting_async_task WHERE id=?", taskId);
  }
}
