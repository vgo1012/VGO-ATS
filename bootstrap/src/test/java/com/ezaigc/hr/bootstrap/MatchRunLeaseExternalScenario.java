package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.application.manager.AiMatchingEvaluationManager;
import com.ezaigc.hr.intelligence.matching.service.MatchRunAttemptPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

/** 用真实领取、处理与租约回收验证匹配恢复，不以手工插入新尝试代替续跑。 */
@RequiredArgsConstructor
final class MatchRunLeaseExternalScenario {
  private final JdbcTemplate jdbc;
  private final AsyncTaskPort tasks;
  private final PlatformIdGenerator ids;
  private final MatchRunPersistenceService runs;
  private final MatchRunAttemptPersistenceService attempts;
  private final MatchRunApplicationManager manager;
  private final AiMatchingEvaluationManager engine;
  private final AsyncTaskService taskPersistence;

  private enum Interleaving { CANCEL_EXPIRED, CANCEL_LIVE, RECLAIM_SUCCESS, RECLAIM_FAILURE, RECLAIM_RANK }

  void verify(String sourceRunId) throws Exception {
    SoftAssertions checks = new SoftAssertions();
    verifyReclaim(sourceRunId, checks, false);
    verifyReclaim(sourceRunId, checks, true);
    for (Interleaving interleaving : Interleaving.values()) verifyLateWriter(sourceRunId, checks, interleaving);
    verifyCancellationWaitsForItemCommit(sourceRunId);
    checks.assertAll();
  }

  private void verifyReclaim(String sourceRunId, SoftAssertions checks, boolean rollback) {
    MatchRun run = enqueue(sourceRunId);
    try {
      var first = tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1).getFirst();
      assertThat(first.id()).isEqualTo(run.getTaskId());
      assertThat(runs.startRun(run.getId(), Instant.now())).isOne();
      assertThat(attempts.insertAttempt(ids.newId(), run.getId(), first.attemptCount(), Instant.now())).isOne();
      expire(run);
      var second = tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1).getFirst();
      assertThat(second.id()).isEqualTo(first.id());
      assertThat(second.attemptCount()).isEqualTo(2);

      if (rollback) {
        var beforeTask = jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", run.getTaskId());
        var beforeRun = jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", run.getId());
        var beforeAttempts = jdbc.queryForList("SELECT * FROM matching_match_run_attempt WHERE match_run_id=?", run.getId());
        assertThat(run.getId()).matches("[0-9A-Z]{26}");
        String trigger = "reject_reclaim_" + run.getId();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?",
            Integer.class, trigger)).isZero();
        jdbc.execute("CREATE TRIGGER `" + trigger + "` BEFORE INSERT ON matching_match_run_attempt FOR EACH ROW BEGIN "
            + "IF NEW.match_run_id='" + run.getId() + "' AND NEW.attempt_no=2 THEN SIGNAL SQLSTATE '45000' "
            + "SET MESSAGE_TEXT='Synthetic reclaim attempt failure'; END IF; END");
        try {
          assertThatThrownBy(() -> manager.process(second)).isInstanceOf(DataAccessException.class);
          assertThat(jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", run.getTaskId())).isEqualTo(beforeTask);
          assertThat(jdbc.queryForMap("SELECT * FROM matching_match_run WHERE id=?", run.getId())).isEqualTo(beforeRun);
          assertThat(jdbc.queryForList("SELECT * FROM matching_match_run_attempt WHERE match_run_id=?", run.getId())).isEqualTo(beforeAttempts);
        } finally {
          jdbc.execute("DROP TRIGGER `" + trigger + "`");
        }
      }
      checks.assertThatCode(() -> manager.process(second)).as("重领后必须能处理原 RUNNING 匹配记录").doesNotThrowAnyException();
      checks.assertThat(tasks.get(run.getTaskId()).status()).as("重领共享任务完成").isEqualTo("SUCCEEDED");
      checks.assertThat(runs.findRun(run.getId()).getStatus()).as("重领业务运行完成").isEqualTo("SUCCEEDED");
      checks.assertThat(jdbc.queryForObject("SELECT status FROM matching_match_run_attempt WHERE match_run_id=? AND attempt_no=1",
          String.class, run.getId())).as("旧失租匹配尝试结束").isEqualTo("FAILED");
    } finally {
      cleanup(run);
    }
  }

  private void verifyLateWriter(String sourceRunId, SoftAssertions checks, Interleaving mode) throws Exception {
    MatchRun run = enqueue(sourceRunId);
    CountDownLatch paused = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<Thread> oldThread = new AtomicReference<>();
    AtomicInteger renewals = new AtomicInteger();
    if (mode == Interleaving.RECLAIM_RANK) {
      doAnswer(call -> {
        if (Thread.currentThread() == oldThread.get() && renewals.incrementAndGet() == 2) {
          pause(paused, release);
        }
        return call.callRealMethod();
      }).when(taskPersistence).renewLease(eq(run.getTaskId()), anyInt(), any(), any(), any());
    } else {
      doAnswer(call -> {
        if (Thread.currentThread() == oldThread.get()) {
          pause(paused, release);
          if (mode == Interleaving.RECLAIM_FAILURE) throw new IllegalStateException("synthetic late evaluation failure");
        }
        return call.callRealMethod();
      }).when(engine).evaluate(any(), any(), any(), any(), any(), any(), anyInt());
    }
    try (var executor = Executors.newSingleThreadExecutor()) {
      var claimed = tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1).getFirst();
      assertThat(claimed.id()).isEqualTo(run.getTaskId());
      var oldWorker = executor.submit(() -> {
        oldThread.set(Thread.currentThread());
        try {
          manager.process(claimed);
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      try {
        assertThat(paused.await(30, TimeUnit.SECONDS)).as(mode + " 旧执行器已到达交错点").isTrue();
        boolean cancelled = mode == Interleaving.CANCEL_EXPIRED || mode == Interleaving.CANCEL_LIVE;
        if (cancelled) {
          assertThat(tasks.cancel(run.getTaskId(), "实库取消计算中的匹配").status()).isEqualTo("CANCELLING");
          if (mode == Interleaving.CANCEL_EXPIRED) {
            expire(run);
            assertThat(tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1)).isEmpty();
            assertThat(tasks.get(run.getTaskId()).status()).isEqualTo("CANCELLED");
          }
        } else {
          expire(run);
          var second = tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1).getFirst();
          assertThat(second.id()).isEqualTo(run.getTaskId());
          assertThat(second.attemptCount()).isEqualTo(2);
          manager.process(second);
          assertThat(tasks.get(run.getTaskId()).status()).isEqualTo("SUCCEEDED");
          assertThat(runs.findRun(run.getId()).getSucceededCount()).isOne();
        }
        var terminal = runs.findRun(run.getId());
        var results = jdbc.queryForList("SELECT * FROM matching_weighted_result WHERE match_run_id=?", run.getId());
        var outcomes = jdbc.queryForList("SELECT * FROM matching_match_run_item_outcome WHERE match_run_id=?", run.getId());
        release.countDown();
        RuntimeException stopped = oldWorker.get(30, TimeUnit.SECONDS);
        if (stopped != null) {
          checks.assertThat(stopped).as(mode + " 迟到执行器只能报告失租冲突").isInstanceOf(PlatformException.class);
          if (stopped instanceof PlatformException conflict) checks.assertThat(conflict.code()).isEqualTo("TASK_STATE_CONFLICT");
        }
        checks.assertThat(jdbc.queryForList("SELECT * FROM matching_match_run_item_outcome WHERE match_run_id=?", run.getId()))
            .as(mode + " 迟到条目不得插入或覆盖").isEqualTo(outcomes);
        checks.assertThat(jdbc.queryForList("SELECT * FROM matching_weighted_result WHERE match_run_id=?", run.getId()))
            .as(mode + " 迟到评分或排序不得插入或覆盖").isEqualTo(results);
        if (mode == Interleaving.CANCEL_LIVE) {
          assertThat(tasks.get(run.getTaskId()).status()).isEqualTo("CANCELLED");
          assertThat(runs.findRun(run.getId()).getStatus()).isEqualTo("CANCELLED");
          assertThat(runs.findRun(run.getId()).getFailedCount()).isZero();
        } else {
          assertThat(runs.findRun(run.getId())).usingRecursiveComparison().isEqualTo(terminal);
        }
        if (cancelled) {
          assertThat(results).isEmpty();
          assertThat(outcomes).isEmpty();
        } else {
          assertThat(results).hasSize(1);
          assertThat(outcomes).hasSize(1);
          assertThat(jdbc.queryForObject("SELECT status FROM matching_match_run_attempt WHERE match_run_id=? AND attempt_no=1",
              String.class, run.getId())).isEqualTo("FAILED");
        }
      } finally {
        release.countDown();
        oldWorker.get(30, TimeUnit.SECONDS);
      }
    } finally {
      reset(engine, taskPersistence);
      cleanup(run);
    }
  }

  private void verifyCancellationWaitsForItemCommit(String sourceRunId) throws Exception {
    MatchRun run = enqueue(sourceRunId);
    CountDownLatch guarded = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(call -> {
      pause(guarded, release);
      return call.callRealMethod();
    }).when(runs).upsertResult(any(), eq(run.getId()), any(), any(), any(), any(), anyInt(), any(), any(), any(), any());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var task = tasks.claim(MatchRunApplicationManager.TASK_TYPE, 1).getFirst();
      assertThat(task.id()).isEqualTo(run.getTaskId());
      var worker = executor.submit(() -> manager.process(task));
      try {
        assertThat(guarded.await(30, TimeUnit.SECONDS)).isTrue();
        var cancellation = executor.submit(() -> tasks.cancel(run.getTaskId(), "等待已进入事务的匹配项提交"));
        try {
          // 仅检查本次随机库和任务的真实数据库锁等待，不用睡眠时间猜测取消是否被阻塞。
          Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).untilAsserted(() ->
              assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w"
                  + " JOIN performance_schema.data_locks r ON r.ENGINE=w.ENGINE AND r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID"
                  + " WHERE r.OBJECT_SCHEMA=DATABASE() AND r.OBJECT_NAME='reporting_async_task'"
                  + " AND r.LOCK_TYPE='RECORD' AND r.LOCK_DATA LIKE ?", Integer.class, "%" + run.getTaskId() + "%"))
                  .isPositive());
          assertThat(cancellation.isDone()).isFalse();
          release.countDown();
          assertThat(cancellation.get(30, TimeUnit.SECONDS).status()).isEqualTo("CANCELLING");
          worker.get(30, TimeUnit.SECONDS);
          assertThat(tasks.get(run.getTaskId()).status()).isEqualTo("CANCELLED");
          assertThat(runs.findRun(run.getId()).getStatus()).isEqualTo("CANCELLED");
          assertThat(runs.findRun(run.getId()).getSucceededCount()).isOne();
          assertThat(runs.findRun(run.getId()).getFailedCount()).isZero();
          assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matching_weighted_result WHERE match_run_id=?",
              Integer.class, run.getId())).isOne();
        } finally {
          release.countDown();
          cancellation.get(30, TimeUnit.SECONDS);
        }
      } finally {
        release.countDown();
        worker.get(30, TimeUnit.SECONDS);
      }
    } finally {
      reset(runs);
      cleanup(run);
    }
  }

  private static void pause(CountDownLatch paused, CountDownLatch release) throws InterruptedException {
    paused.countDown();
    if (!release.await(60, TimeUnit.SECONDS)) throw new IllegalStateException("synthetic old worker was not released");
  }

  private MatchRun enqueue(String sourceRunId) {
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
        "lease-it-" + runId, "c".repeat(64), 2, now));
    return runs.findRun(runId);
  }

  private void expire(MatchRun run) {
    assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' WHERE id=?", run.getTaskId())).isOne();
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
