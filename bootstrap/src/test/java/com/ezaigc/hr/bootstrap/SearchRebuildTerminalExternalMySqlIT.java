package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.intelligence.search.constant.SearchConstants;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.dao.DataAccessException;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 无存活执行器时的租约终态恢复；仅使用本次新建空MySQL库，不访问真实搜索索引。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@SpringBootTest(properties = {
    "spring.datasource.url=${HR_DB_URL}",
    "spring.datasource.username=${HR_DB_USER}",
    "spring.datasource.password=${HR_DB_PASSWORD}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "hr.object-storage.mode=local",
    "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
    "hr.search.mode=memory",
    "hr.malware-scan.mode=development",
    "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
    "hr.bootstrap-admin.enabled=true",
    "hr.bootstrap-admin.department-code=ROOT",
    "hr.bootstrap-admin.department-name=测试总部",
    "hr.bootstrap-admin.display-name=重建终态测试管理员",
    "hr.bootstrap-admin.work-email=search-terminal-it@example.com",
    "hr.bootstrap-admin.password=Test-Only-Search-123!",
    "hr.resume-parsing.worker-enabled=false",
    "hr.jd-parsing.worker-enabled=false",
    "hr.matching.worker-enabled=false",
    "hr.search.worker-enabled=false",
    "hr.reporting.audit-capture.enabled=false"
})
class SearchRebuildTerminalExternalMySqlIT {
  @Resource private JdbcTemplate jdbc;
  @Resource private PlatformIdGenerator ids;
  @Resource private AsyncTaskPort tasks;
  @Resource private RebuildRunService rebuilds;
  @MockitoSpyBean private AsyncTaskService taskPersistence;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unhandledFailurePreservesItsCauseAndCommittedRebuildProgress(boolean cancelling) {
    RebuildRun run = enqueue();
    try {
      var claimed = tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst();
      assertThat(claimed.id()).isEqualTo(run.getTaskId());
      assertThat(rebuilds.start(run.getId(), new SearchRebuildAttempt(run.getId(), claimed.attemptCount()).indexName(),
          run.getRequestedByUserId(), Instant.now())).isTrue();
      assertThat(jdbc.update("UPDATE search_index_rebuild_run SET projected_count=37 WHERE id=?", run.getId())).isOne();
      RebuildRun before = rebuilds.getById(run.getId());
      if (cancelling) tasks.cancel(run.getTaskId(), "异常前取消重建");

      var completed = tasks.failUnhandled(claimed, "TASK_EXECUTION_FAILED", "合成未捕获错误", true);

      String expected = cancelling ? "CANCELLED" : "FAILED";
      assertThat(completed.status()).isEqualTo(expected);
      var actual = rebuilds.getById(run.getId());
      assertThat(actual.getStatus()).isEqualTo(expected);
      assertThat(actual.getErrorCode()).isEqualTo(cancelling ? null : "TASK_EXECUTION_FAILED");
      assertThat(actual.getFinishedAt()).isEqualTo(completed.finishedAt());
      assertThat(actual.getTargetIndex()).isEqualTo(before.getTargetIndex());
      assertThat(actual.getProjectedCount()).isEqualTo(37);
      assertThat(actual.getVersion()).isEqualTo(before.getVersion() + 1);
    } finally {
      cleanup(run);
    }
  }

  @Test
  void cancellingAnUnclaimedTaskCompletesBothTaskAndRebuild() {
    RebuildRun run = enqueue();
    try {
      var cancelled = tasks.cancel(run.getTaskId(), "合成排队任务取消");

      assertThat(cancelled.status()).isEqualTo("CANCELLED");
      assertThat(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1)).isEmpty();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_async_task_attempt WHERE task_id=?",
          Integer.class, run.getTaskId())).isZero();
      assertSoftly(check -> {
        check.assertThat(cancelled.finishedAt()).as("排队任务直接终结必须记录完成时间").isNotNull();
        check.assertThat(rebuilds.getById(run.getId()).getStatus())
            .as("不会再有执行器领取已取消任务，重建必须同事务终结").isEqualTo("CANCELLED");
      });
      var completedRun = rebuilds.getById(run.getId());
      assertThat(tasks.cancel(run.getTaskId(), "重复取消")).isEqualTo(cancelled);
      assertThat(rebuilds.getById(run.getId())).usingRecursiveComparison().isEqualTo(completedRun);
    } finally {
      cleanup(run);
    }
  }

  @Test
  void claimBetweenCancellationReadAndWriteKeepsTheBusinessRecordRunning() {
    RebuildRun run = enqueue();
    try {
      AtomicReference<AsyncTaskPort.TaskSnapshot> claimed = new AtomicReference<>();
      doAnswer(call -> {
        try (var executor = Executors.newSingleThreadExecutor()) {
          var claiming = executor.submit(() -> {
            var task = tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst();
            assertThat(task.id()).isEqualTo(run.getTaskId());
            claimed.set(task);
            assertThat(rebuilds.start(run.getId(), new SearchRebuildAttempt(run.getId(), task.attemptCount()).indexName(),
                run.getRequestedByUserId(), Instant.now())).isTrue();
            return null;
          });
          try {
            claiming.get(30, TimeUnit.SECONDS);
          } finally {
            if (!claiming.isDone()) claiming.cancel(true);
          }
        }
        return call.callRealMethod();
      }).when(taskPersistence).requestCancel(eq(run.getTaskId()), eq("合成并发取消"), any(), any());

      var cancelled = tasks.cancel(run.getTaskId(), "合成并发取消");

      assertThat(cancelled.status()).isEqualTo("CANCELLING");
      assertThat(cancelled.attemptCount()).isEqualTo(claimed.get().attemptCount());
      assertThat(cancelled.finishedAt()).isNull();
      assertThat(jdbc.queryForObject("SELECT lease_until FROM reporting_async_task WHERE id=?",
          Object.class, run.getTaskId())).isNotNull();
      assertThat(rebuilds.getById(run.getId()).getStatus()).isEqualTo("RUNNING");
      assertThat(jdbc.queryForObject("SELECT status FROM reporting_async_task_attempt WHERE task_id=?",
          String.class, run.getTaskId())).isEqualTo("RUNNING");
    } finally {
      cleanup(run);
    }
  }

  @ParameterizedTest
  @CsvSource({"FAILED,RUNNING", "CANCELLED,RUNNING", "FAILED,QUEUED", "CANCELLED,QUEUED"})
  void terminalLeaseRecoveryClosesTheRebuildWithoutTheOriginalWorker(String expectedStatus, String initialStatus) {
    RebuildRun run = enqueue();
    try {
      var claimed = tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst();
      assertThat(claimed.id()).isEqualTo(run.getTaskId());
      if (initialStatus.equals("RUNNING")) {
        assertThat(rebuilds.start(run.getId(), new SearchRebuildAttempt(run.getId(), claimed.attemptCount()).indexName(),
            run.getRequestedByUserId(), Instant.now())).isTrue();
      }
      assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00',status=? WHERE id=?",
          expectedStatus.equals("CANCELLED") ? "CANCELLING" : "RUNNING", run.getTaskId())).isOne();
      long expectedCount = initialStatus.equals("RUNNING") ? 37 : 0;
      assertThat(jdbc.update("UPDATE search_index_rebuild_run SET projected_count=? WHERE id=?",
          expectedCount, run.getId())).isOne();
      RebuildRun before = rebuilds.getById(run.getId());

      assertThat(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1)).isEmpty();
      assertThat(tasks.get(run.getTaskId()).status()).isEqualTo(expectedStatus);
      assertThat(jdbc.queryForObject("SELECT status FROM reporting_async_task_attempt WHERE task_id=?",
          String.class, run.getTaskId())).isEqualTo(expectedStatus);
      RebuildRun recovered = rebuilds.getById(run.getId());
      assertThat(recovered.getStatus())
          .as("共享任务已终结且不会再被领取，重建记录不能继续悬挂为运行中")
          .isEqualTo(expectedStatus);
      assertThat(recovered.getProjectedCount()).isEqualTo(expectedCount);
      assertThat(recovered.getTargetIndex()).isEqualTo(before.getTargetIndex());
      assertThat(recovered.getStartedAt()).isEqualTo(before.getStartedAt());
      assertThat(recovered.getVersion()).isEqualTo(before.getVersion() + 1);
      assertThat(recovered.getFinishedAt()).isNotNull();
      assertThat(recovered.getErrorCode()).isEqualTo(expectedStatus.equals("FAILED") ? "TASK_LEASE_EXPIRED" : null);
      assertThat(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1)).isEmpty();
      assertThat(rebuilds.getById(run.getId())).usingRecursiveComparison().isEqualTo(recovered);
    } finally {
      cleanup(run);
    }
  }

  @ParameterizedTest
  @CsvSource({"FAILED,false", "CANCELLED,false", "CANCELLED,true"})
  void rebuildUpdateFailureRollsBackTaskAndAttemptRecovery(String expectedStatus, boolean queuedCancellation) {
    RebuildRun run = enqueue();
    String trigger = "fail_rebuild_" + run.getId().toLowerCase(Locale.ROOT);
    boolean triggerCreated = false;
    try {
      if (!queuedCancellation) {
        var claimed = tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst();
        assertThat(rebuilds.start(run.getId(), new SearchRebuildAttempt(run.getId(), claimed.attemptCount()).indexName(),
            run.getRequestedByUserId(), Instant.now())).isTrue();
        assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00',status=? WHERE id=?",
            expectedStatus.equals("CANCELLED") ? "CANCELLING" : "RUNNING", run.getTaskId())).isOne();
      }
      var beforeTask = taskState(run);
      var beforeAttempt = queuedCancellation ? Map.of() : attemptState(run);
      var beforeRun = rebuilds.getById(run.getId());
      assertThat(trigger).matches("[a-z0-9_]{1,64}");
      assertThat(run.getId()).matches("[A-Z0-9]{26}");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TRIGGERS "
          + "WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?", Integer.class, trigger)).isZero();
      jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON search_index_rebuild_run FOR EACH ROW BEGIN "
          + "IF NEW.id='" + run.getId() + "' THEN SIGNAL SQLSTATE '45000' "
          + "SET MESSAGE_TEXT='synthetic rebuild terminal failure'; END IF; END");
      triggerCreated = true;

      Runnable complete = () -> {
        if (queuedCancellation) tasks.cancel(run.getTaskId(), "合成取消回滚");
        else tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1);
      };
      assertThatThrownBy(complete::run)
          .isInstanceOf(DataAccessException.class).hasStackTraceContaining("synthetic rebuild terminal failure");
      assertThat(taskState(run)).isEqualTo(beforeTask);
      if (queuedCancellation) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_async_task_attempt WHERE task_id=?",
            Integer.class, run.getTaskId())).isZero();
      } else {
        assertThat(attemptState(run)).isEqualTo(beforeAttempt);
      }
      assertThat(rebuilds.getById(run.getId())).usingRecursiveComparison().isEqualTo(beforeRun);
      jdbc.execute("DROP TRIGGER " + trigger);
      triggerCreated = false;
      complete.run();
      assertThat(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1)).isEmpty();
      assertThat(tasks.get(run.getTaskId()).status()).isEqualTo(expectedStatus);
      assertThat(rebuilds.getById(run.getId()).getStatus()).isEqualTo(expectedStatus);
    } finally {
      if (triggerCreated) jdbc.execute("DROP TRIGGER " + trigger);
      cleanup(run);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCEEDED", "CANCELLED", "FOREIGN_TASK"})
  void recoveryDoesNotOverwriteExistingTerminalsOrAnotherTaskBinding(String existing) {
    RebuildRun run = enqueue();
    RebuildRun foreign = null;
    try {
      tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1);
      if (existing.equals("FOREIGN_TASK")) {
        foreign = enqueue();
        assertThat(jdbc.update("UPDATE reporting_async_task SET resource_id=? WHERE id=?",
            foreign.getId(), run.getTaskId())).isOne();
        // 任务错误指向另一资源，但该资源绑定另一任务；不能只按resourceId覆盖。
      } else {
        assertThat(jdbc.update("UPDATE search_index_rebuild_run SET status=? WHERE id=?", existing, run.getId())).isOne();
      }
      assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?",
          run.getTaskId())).isOne();
      String observedId = foreign == null ? run.getId() : foreign.getId();
      var before = rebuilds.getById(observedId);

      tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1);

      assertThat(tasks.get(run.getTaskId()).status()).isEqualTo("FAILED");
      assertThat(rebuilds.getById(observedId)).usingRecursiveComparison().isEqualTo(before);
    } finally {
      cleanup(run);
      if (foreign != null) cleanup(foreign);
    }
  }

  private Map<String, Object> taskState(RebuildRun run) {
    return jdbc.queryForMap("SELECT status,attempt_count,retryable,error_code,safe_error_detail,lease_until,"
        + "version,finished_at,update_id,update_time FROM reporting_async_task WHERE id=?", run.getTaskId());
  }

  private Map<String, Object> attemptState(RebuildRun run) {
    return jdbc.queryForMap("SELECT status,error_code,safe_error_detail,started_at,finished_at,"
        + "update_id,update_time FROM reporting_async_task_attempt WHERE task_id=?", run.getTaskId());
  }

  private RebuildRun enqueue() {
    String userId = jdbc.queryForObject("SELECT id FROM identity_user_account WHERE normalized_email=?",
        String.class, "search-terminal-it@example.com");
    String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
        String.class, userId);
    var run = new RebuildRun();
    run.setId(ids.newId());
    run.setTaskId(ids.newId());
    run.setTargetIndex("resume-facts-v" + run.getId().toLowerCase(Locale.ROOT));
    run.setStatus("QUEUED");
    run.setRequestedByUserId(userId);
    run.setCreateId(userId);
    run.setUpdateId(userId);
    run.setCreateTime(Instant.now());
    run.setUpdateTime(run.getCreateTime());
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(run.getTaskId(), SearchConstants.REBUILD_TASK_TYPE,
        "SEARCH_INDEX_REBUILD", run.getId(), userId, userId, departmentId, "talent.read", "recruitment.manage",
        "search-terminal-" + run.getId(), "a".repeat(64), 1, run.getCreateTime()));
    assertThat(rebuilds.save(run)).isTrue();
    return run;
  }

  private void cleanup(RebuildRun run) {
    jdbc.update("DELETE FROM search_index_rebuild_run WHERE id=?", run.getId());
    jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", run.getTaskId());
    jdbc.update("DELETE FROM reporting_async_task WHERE id=?", run.getTaskId());
  }
}
