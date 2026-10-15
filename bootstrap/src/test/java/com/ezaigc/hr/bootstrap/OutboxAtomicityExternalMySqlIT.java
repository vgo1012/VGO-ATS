package com.ezaigc.hr.bootstrap;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxDeliveryPort;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.reporting.outbox.application.manager.OutboxPublicationApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchTaskApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchIndexAuthorityApplicationManager;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import com.ezaigc.hr.talent.application.manager.ResumeFactPersistenceManager;
import com.ezaigc.hr.talent.application.manager.ResumeSearchProjectionRecorder;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 在新建隔离库验证实际事实写入器、任务和 Outbox 原子性；不把替身 Broker 当成外部协议证据。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@SpringBootTest(properties = {
    "spring.datasource.url=${HR_DB_URL}",
    "spring.datasource.username=${HR_DB_USER}",
    "spring.datasource.password=${HR_DB_PASSWORD}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.hikari.maximum-pool-size=4",
    "spring.datasource.hikari.connection-timeout=5000",
    "hr.object-storage.mode=local",
    "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
    "hr.search.mode=memory",
    "hr.malware-scan.mode=development",
    "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
    "hr.bootstrap-admin.enabled=true",
    "hr.bootstrap-admin.department-code=ROOT",
    "hr.bootstrap-admin.department-name=测试总部",
    "hr.bootstrap-admin.display-name=Outbox 测试管理员",
    "hr.bootstrap-admin.work-email=outbox-it@example.com",
    "hr.bootstrap-admin.password=Test-Only-Outbox-123!",
    "hr.resume-parsing.worker-enabled=false",
    "hr.jd-parsing.worker-enabled=false",
    "hr.matching.worker-enabled=false",
    "hr.search.worker-enabled=false",
    "hr.messaging.worker-enabled=false",
    "hr.reporting.audit-capture.enabled=false"
})
class OutboxAtomicityExternalMySqlIT {
  @Resource private JdbcTemplate jdbc;
  @Resource private PlatformIdGenerator ids;
  @Resource private AsyncTaskPort tasks;
  @Resource private TransactionTemplate transactions;
  @Resource private ResumeFactPersistenceManager writer;
  @Resource private ResumeSearchProjectionRecorder recorder;
  @Resource private Clock clock;
  @Resource private OutboxPublicationApplicationManager publications;
  @Resource private OutboxDeliveryPort deliveries;
  @Resource private AsyncTaskBatchRunner runner;
  @Resource private ResumeSearchStateSource searchStates;
  @Resource private SearchTaskApplicationManager searchTasks;
  @MockitoSpyBean private SearchIndexPort searchIndex;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  @MockitoBean private OutboxMessagePublisher publisher;

  @Test
  void indexFailuresRetainFiveAttemptsAndStopAutomaticRetry() {
    withFixture(fixture -> {
      correct(fixture);
      var event = oneEvent(fixture);
      String workId = (String) event.get("work_task_id");
      doAnswer(call -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(MDC.get("traceId")).isEqualTo(event.get("trace_id"));
        throw new IllegalStateException("synthetic index outage");
      }).when(searchIndex).upsertBatch(any(SearchIndexTarget.class), any());
      for (int attempt = 1; attempt <= 5; attempt++) {
        var task = tasks.claim("RESUME_SEARCH_PROJECT", 1).getFirst();
        assertThat(task.id()).isEqualTo(workId);
        assertThat(task.attemptCount()).isEqualTo(attempt);
        searchTasks.process(task);
        assertThat(tasks.get(workId).status()).isEqualTo(attempt < 5 ? "QUEUED" : "FAILED");
        assertThat(tasks.claim("RESUME_SEARCH_PROJECT", 1)).isEmpty();
        if (attempt < 5) jdbc.update("UPDATE reporting_async_task SET next_retry_at=? WHERE id=?",
            Timestamp.from(clock.instant().minusSeconds(1)), workId);
      }
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_async_task_attempt WHERE task_id=?",
          Integer.class, workId)).isEqualTo(5);
      assertThat(tasks.get(workId).errorCode()).isEqualTo("SEARCH_PROJECTION_FAILED");
      assertThat(oneEvent(fixture)).isEqualTo(event);
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void lateIndexOutcomeDoesNotOverwriteCancellation(boolean indexFails) {
    withFixture(fixture -> {
      correct(fixture);
      var task = tasks.claim("RESUME_SEARCH_PROJECT", 1).getFirst();
      doAnswer(call -> {
        assertThat(tasks.cancel(task.id(), "Synthetic in-flight cancellation").status()).isEqualTo("CANCELLING");
        if (indexFails) throw new IllegalStateException("synthetic late index failure");
        return null;
      }).when(searchIndex).upsertBatch(any(SearchIndexTarget.class), any());
      searchTasks.process(task);
      assertThat(tasks.get(task.id()).status()).isIn("CANCELLING", "CANCELLED");
      // 未完成的取消由租约恢复收敛；旧完成与旧失败都不能回到成功或重试队列。
      jdbc.update("UPDATE reporting_async_task SET lease_until=? WHERE id=?",
          Timestamp.from(clock.instant().minusSeconds(1)), task.id());
      assertThat(tasks.claim("RESUME_SEARCH_PROJECT", 1)).isEmpty();
      assertThat(tasks.get(task.id()).status()).isEqualTo("CANCELLED");
    });
  }

  @Test
  void sqlPollingProjectsTheLatestFactsEvenBeforeAnyBrokerPublication() {
    withFixture(fixture -> {
      fixture.appendCorrection(writer, userId());
      String latest = fixture.appendCorrection(writer, userId());
      assertThat(searchStates.currentState(fixture.candidateId()).facts().getSnapshotId()).isEqualTo(latest);
      assertThat(runner.run("RESUME_SEARCH_PROJECT", 1, searchTasks::process)).isOne();
      assertThat(runner.run("RESUME_SEARCH_PROJECT", 1, searchTasks::process)).isOne();
      assertThat(searchIndex.search(authority.currentTarget(), new SearchCriteria(), null, 100).getItems())
          .filteredOn(document -> fixture.candidateId().equals(document.getCandidateId()))
          .extracting(SearchDocument::getFactSnapshotId).containsExactly(latest);
      assertThat(jdbc.queryForList("SELECT status FROM reporting_async_task WHERE task_type='OUTBOX_PUBLISH'",
          String.class)).containsOnly("QUEUED");
    });
  }

  @Test
  void currentSearchStateIncludesUnparsedLatestAvailableVersionAndDeletedCandidate() {
    withFixture(first -> withFixture(second -> {
      second.becomeNextVersionOf(first);
      assertThat(searchStates.currentState(first.candidateId()).resumeVersionId()).isEqualTo(second.resumeVersionId());
      assertThat(searchStates.currentState(first.candidateId()).facts().getSnapshotId()).isEqualTo(second.factId());
      jdbc.update("UPDATE talent_resume_fact_snapshot SET current_marker=NULL WHERE id=?", second.factId());
      var unparsed = searchStates.currentState(first.candidateId());
      assertThat(unparsed.resumeVersionNo()).isEqualTo(2);
      assertThat(unparsed.facts()).isNull();
      assertThat(searchStates.currentState(second.candidateId()).resumeVersionNo()).isZero();
      assertThat(searchStates.currentState(second.candidateId()).facts()).isNull();
      jdbc.update("UPDATE talent_candidate SET status='DELETED' WHERE id=?", first.candidateId());
      var deleted = searchStates.currentState(first.candidateId());
      assertThat(deleted.deleted()).isTrue();
      assertThat(deleted.facts()).isNull();
      assertThat(deleted.resumeVersionId()).isNull();
      assertThat(searchStates.currentState(ids.newId())).isNull();
    }));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void publisherRunsOutsideTheDatabaseTransactionAndRestoresTheActualLogbackTrace(boolean fails) {
    withFixture(fixture -> {
      correct(fixture);
      var event = oneEvent(fixture);
      var publication = tasks.claim("OUTBOX_PUBLISH", 1).getFirst();
      String previous = MDC.get("traceId");
      try {
        MDC.put("traceId", "caller-trace-0123456789");
        doAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
          OutboxEnvelope envelope = call.getArgument(0);
          assertThat(envelope.getEventId()).isEqualTo(event.get("id"));
          assertThat(MDC.get("traceId")).isEqualTo(event.get("trace_id"));
          if (fails) throw new OutboxPublishException("BROKER_TIMEOUT", "Synthetic confirmation timeout", true, null);
          return null;
        }).when(publisher).publish(any());
        publications.process(publication);
        assertThat(MDC.get("traceId")).isEqualTo("caller-trace-0123456789");
        assertThat(tasks.get(publication.id()).status()).isEqualTo(fails ? "QUEUED" : "SUCCEEDED");
        assertThat(oneEvent(fixture)).isEqualTo(event);
      } finally {
        if (previous == null) MDC.remove("traceId"); else MDC.put("traceId", previous);
      }
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCEEDED", "FAILED", "CANCELLED"})
  void duplicateDeliveryKeepsTheOriginalPersistentWorkTerminalState(String status) {
    withFixture(fixture -> {
      correct(fixture);
      var envelope = new AtomicReference<OutboxEnvelope>();
      doAnswer(call -> { envelope.set(call.getArgument(0)); return null; }).when(publisher).publish(any());
      publications.process(tasks.claim("OUTBOX_PUBLISH", 1).getFirst());
      var work = tasks.claim("RESUME_SEARCH_PROJECT", 1).getFirst();
      if (status.equals("SUCCEEDED")) {
        tasks.succeed(work, fixture.candidateId());
      } else if (status.equals("FAILED")) {
        tasks.fail(work, "SYNTHETIC_PERMANENT_FAILURE", "Synthetic permanent failure", false);
      } else {
        tasks.cancel(work.id(), "Synthetic cancellation");
        tasks.cancelled(work);
      }
      var before = state(fixture);
      assertThat(deliveries.verify(envelope.get()).status()).isEqualTo(status);
      assertThat(deliveries.verify(envelope.get()).status()).isEqualTo(status);
      assertThat(state(fixture)).isEqualTo(before);
      assertThat(tasks.claim("RESUME_SEARCH_PROJECT", 1)).isEmpty();
    });
  }

  @Test
  void confirmedPublicationReplaysTheSameEventAfterDatabaseAcknowledgementRollsBack() {
    withFixture(fixture -> {
      correct(fixture);
      var first = tasks.claim("OUTBOX_PUBLISH", 1).getFirst();
      var delivered = new ArrayList<String>();
      doAnswer(call -> { delivered.add(call.getArgument(0, OutboxEnvelope.class).getEventId()); return null; })
          .when(publisher).publish(any());
      jdbc.execute("CREATE TRIGGER outbox_it_reject_ack BEFORE UPDATE ON reporting_async_task FOR EACH ROW "
          + "BEGIN IF NEW.task_type='OUTBOX_PUBLISH' AND NEW.status='SUCCEEDED' THEN SIGNAL SQLSTATE '45000' "
          + "SET MESSAGE_TEXT='synthetic publication acknowledgement rejection'; END IF; END");
      try {
        publications.process(first);
        assertThat(tasks.get(first.id()).status()).isEqualTo("QUEUED");
        assertThat(tasks.get(first.id()).errorCode()).isEqualTo("OUTBOX_PERSISTENCE_UNAVAILABLE");
      } finally {
        jdbc.execute("DROP TRIGGER outbox_it_reject_ack");
      }
      makeDue(first.id());
      publications.process(tasks.claim("OUTBOX_PUBLISH", 1).getFirst());
      assertThat(delivered).containsExactly(first.id(), first.id());
      assertThat(tasks.get(first.id()).status()).isEqualTo("SUCCEEDED");
      assertThat(jdbc.queryForList("SELECT status FROM reporting_async_task_attempt WHERE task_id=? ORDER BY attempt_no",
          String.class, first.id())).containsExactly("FAILED", "SUCCEEDED");
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"cancel-confirm", "cancel-failure", "reclaim-confirm", "reclaim-failure"})
  void lateBrokerOutcomeCannotOverwriteCancellationOrTheNextAttempt(String scenario) {
    withFixture(fixture -> {
      correct(fixture);
      String eventId = (String) oneEvent(fixture).get("id");
      var sending = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      doAnswer(call -> {
        sending.countDown();
        await(release);
        if (scenario.endsWith("failure")) {
          throw new OutboxPublishException("BROKER_TIMEOUT", "Synthetic late failure", true, null);
        }
        return null;
      }).when(publisher).publish(any());
      try (var executor = Executors.newSingleThreadExecutor()) {
        var pending = executor.submit(() -> runner.run("OUTBOX_PUBLISH", 1, publications::process));
        try {
          await(sending);
          if (scenario.startsWith("cancel")) {
            assertThat(tasks.cancel(eventId, "Synthetic in-flight cancellation").status()).isEqualTo("CANCELLING");
          } else {
            expireLease(eventId);
            var newer = tasks.claim("OUTBOX_PUBLISH", 1).getFirst();
            assertThat(newer.attemptCount()).isEqualTo(2);
            tasks.succeed(newer, eventId);
          }
        } finally {
          release.countDown();
        }
        assertThat(pending.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(tasks.get(eventId).status()).isEqualTo(scenario.startsWith("cancel") ? "CANCELLED" : "SUCCEEDED");
        assertThat(jdbc.queryForList("SELECT status FROM reporting_async_task_attempt WHERE task_id=? ORDER BY attempt_no",
            String.class, eventId)).containsExactlyElementsOf(scenario.startsWith("cancel")
                ? List.of("CANCELLED") : List.of("FAILED", "SUCCEEDED"));
        assertThat(jdbc.queryForObject("SELECT next_retry_at FROM reporting_async_task WHERE id=?", Timestamp.class, eventId)).isNull();
      } catch (Exception failure) {
        throw new AssertionError("isolated publication race failed", failure);
      } finally {
        release.countDown();
      }
    });
  }

  @Test
  void repeatedDatabaseFinalizationFailureLeavesARecoverableLeaseAndStableEvent() {
    withFixture(fixture -> {
      correct(fixture);
      String eventId = (String) oneEvent(fixture).get("id");
      var delivered = new ArrayList<String>();
      doAnswer(call -> { delivered.add(call.getArgument(0, OutboxEnvelope.class).getEventId()); return null; })
          .when(publisher).publish(any());
      jdbc.execute("CREATE TRIGGER outbox_it_reject_finalize BEFORE UPDATE ON reporting_async_task FOR EACH ROW "
          + "BEGIN IF OLD.task_type='OUTBOX_PUBLISH' AND OLD.status='RUNNING' AND NEW.status<>'RUNNING' "
          + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic finalization rejection'; END IF; END");
      try {
        assertThat(runner.run("OUTBOX_PUBLISH", 1, publications::process)).isEqualTo(1);
        assertThat(tasks.get(eventId).status()).isEqualTo("RUNNING");
        assertThat(jdbc.queryForList("SELECT status FROM reporting_async_task_attempt WHERE task_id=?", String.class, eventId))
            .containsExactly("RUNNING");
      } finally {
        jdbc.execute("DROP TRIGGER outbox_it_reject_finalize");
      }
      expireLease(eventId);
      assertThat(runner.run("OUTBOX_PUBLISH", 1, publications::process)).isEqualTo(1);
      assertThat(delivered).containsExactly(eventId, eventId);
      assertThat(tasks.get(eventId).status()).isEqualTo("SUCCEEDED");
      assertThat(jdbc.queryForList("SELECT status FROM reporting_async_task_attempt WHERE task_id=? ORDER BY attempt_no",
          String.class, eventId)).containsExactly("FAILED", "SUCCEEDED");
    });
  }

  private void expireLease(String taskId) {
    jdbc.update("UPDATE reporting_async_task SET lease_until=? WHERE id=?", Timestamp.from(clock.instant().minusSeconds(1)), taskId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OUTBOX_PUBLISH", "RESUME_SEARCH_PROJECT"})
  void retryDeadlineSurvivesReadsAndOnlyOneNewAttemptCanBeClaimed(String taskType) {
    withFixture(fixture -> {
      correct(fixture);
      var first = tasks.claim(taskType, 1).getFirst();
      Instant due = clock.instant().plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
      var waiting = tasks.failAndRetryAt(first, "TRANSIENT_TEST", "Synthetic transient failure", due);
      assertThat(waiting.status()).isEqualTo("QUEUED");
      assertThat(waiting.attemptCount()).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT next_retry_at FROM reporting_async_task WHERE id=?", Timestamp.class, first.id()).toInstant())
          .isEqualTo(due);
      assertThat(tasks.claim(taskType, 1)).isEmpty();
      assertThat(jdbc.queryForList("SELECT status,error_code FROM reporting_async_task_attempt WHERE task_id=?", first.id()))
          .singleElement().satisfies(row -> {
            assertThat(row.get("status")).isEqualTo("FAILED");
            assertThat(row.get("error_code")).isEqualTo("TRANSIENT_TEST");
          });
      makeDue(first.id());
      try (var executor = Executors.newFixedThreadPool(2)) {
        var one = executor.submit(() -> tasks.claim(taskType, 1));
        var two = executor.submit(() -> tasks.claim(taskType, 1));
        var claimed = new ArrayList<>(one.get(15, TimeUnit.SECONDS));
        claimed.addAll(two.get(15, TimeUnit.SECONDS));
        assertThat(claimed).singleElement().satisfies(task -> {
          assertThat(task.id()).isEqualTo(first.id());
          assertThat(task.attemptCount()).isEqualTo(2);
        });
        var beforeStale = state(fixture);
        assertThatThrownBy(() -> tasks.failAndRetryAt(first, "STALE_TEST", "Synthetic stale attempt", due))
            .isInstanceOf(RuntimeException.class);
        assertThat(state(fixture)).isEqualTo(beforeStale);
      } catch (Exception failure) {
        throw new AssertionError("isolated retry coordination failed", failure);
      }
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"OUTBOX_PUBLISH", "RESUME_SEARCH_PROJECT"})
  void queuedRetryCanBeCancelledBeforeItsDeadline(String taskType) {
    withFixture(fixture -> {
      correct(fixture);
      var first = tasks.claim(taskType, 1).getFirst();
      tasks.failAndRetryAt(first, "TRANSIENT_TEST", "Synthetic transient failure", clock.instant().plusSeconds(300));
      assertThat(tasks.cancel(first.id(), "Synthetic cancellation during backoff").status()).isEqualTo("CANCELLED");
      assertThat(jdbc.queryForObject("SELECT next_retry_at FROM reporting_async_task WHERE id=?", Timestamp.class, first.id()))
          .isNull();
      assertThat(tasks.claim(taskType, 1)).isEmpty();
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"OUTBOX_PUBLISH", "RESUME_SEARCH_PROJECT"})
  void automaticRetriesStopAtTheAttemptLimit(String taskType) {
    withFixture(fixture -> {
      correct(fixture);
      String taskId = null;
      for (int attempt = 1; attempt <= 5; attempt++) {
        var current = tasks.claim(taskType, 1).getFirst();
        taskId = current.id();
        assertThat(current.attemptCount()).isEqualTo(attempt);
        var outcome = tasks.failAndRetryAt(current, "TRANSIENT_TEST", "Synthetic repeated failure", clock.instant().plusSeconds(300));
        assertThat(outcome.status()).isEqualTo(attempt == 5 ? "FAILED" : "QUEUED");
        assertThat(outcome.retryable()).isFalse();
        if (attempt < 5) makeDue(taskId);
      }
      assertThat(tasks.claim(taskType, 1)).isEmpty();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_async_task_attempt WHERE task_id=? AND status='FAILED'",
          Integer.class, taskId)).isEqualTo(5);
      assertThat(jdbc.queryForObject("SELECT next_retry_at FROM reporting_async_task WHERE id=?", Timestamp.class, taskId)).isNull();
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"OUTBOX_PUBLISH", "RESUME_SEARCH_PROJECT"})
  void retrySchedulingFailureRollsBackTaskAndAttemptTogether(String taskType) {
    withFixture(fixture -> {
      correct(fixture);
      var first = tasks.claim(taskType, 1).getFirst();
      var before = state(fixture);
      var attempts = jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=?", first.id());
      jdbc.execute("CREATE TRIGGER outbox_it_reject_retry BEFORE UPDATE ON reporting_async_task FOR EACH ROW "
          + "BEGIN IF NEW.next_retry_at IS NOT NULL THEN SIGNAL SQLSTATE '45000' "
          + "SET MESSAGE_TEXT='synthetic retry schedule rejection'; END IF; END");
      try {
        assertThatThrownBy(() -> tasks.failAndRetryAt(first, "TRANSIENT_TEST", "Synthetic transient failure", clock.instant().plusSeconds(300)))
            .isInstanceOf(DataAccessException.class);
        assertThat(state(fixture)).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=?", first.id())).isEqualTo(attempts);
      } finally {
        jdbc.execute("DROP TRIGGER outbox_it_reject_retry");
      }
    });
  }

  private void makeDue(String taskId) {
    // 只推进本用例拥有的随机任务，不通过真实等待或修改系统时钟验证到期边界。
    jdbc.update("UPDATE reporting_async_task SET next_retry_at=? WHERE id=?", Timestamp.from(clock.instant().minusSeconds(1)), taskId);
  }

  @Test
  void correctionCommitsWorkAndOutboxAndPreservesTheOriginalTraceOnReplay() {
    withFixture(fixture -> {
      String previousTrace = MDC.get("traceId");
      String trace = "outbox-native-trace-0123456789";
      try {
        MDC.put("traceId", trace);
        String fact = correct(fixture);
        var event = oneEvent(fixture);
        assertThat(event.get("trace_id")).isEqualTo(trace);
        assertBinding(fixture, fact, event);
        MDC.put("traceId", "different-replay-trace-0123456789");
        var replay = transactions.execute(ignored -> recorder.factChanged(fixture.resumeVersionId(), fact, userId()));
        assertThat(replay.id()).isEqualTo(event.get("work_task_id"));
        assertThat(oneEvent(fixture)).isEqualTo(event);
      } finally {
        if (previousTrace == null) MDC.remove("traceId"); else MDC.put("traceId", previousTrace);
      }
    });
  }

  @Test
  void aiFactsAndCacheReplayKeepOneDurableEvent() {
    withFixture(fixture -> {
      String cacheKey = ids.newId() + "0".repeat(38);
      String fact = ai(fixture, cacheKey);
      var event = oneEvent(fixture);
      assertBinding(fixture, fact, event);
      var before = state(fixture);
      assertThat(ai(fixture, cacheKey)).isEqualTo(fact);
      assertThat(state(fixture)).isEqualTo(before);
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedOutboxInsertRollsBackFactsParseCorrectionAndBothTasks(boolean ai) {
    withFixture(fixture -> {
      var before = state(fixture);
      jdbc.execute("CREATE TRIGGER outbox_it_reject BEFORE INSERT ON reporting_outbox_event "
          + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic outbox write rejection'");
      try {
        assertThatThrownBy(() -> {
          if (ai) ai(fixture, ids.newId() + "0".repeat(38)); else correct(fixture);
        }).isInstanceOf(DataAccessException.class);
        assertThat(state(fixture)).isEqualTo(before);
      } finally {
        jdbc.execute("DROP TRIGGER outbox_it_reject");
      }
    });
  }

  @Test
  void rollbackAfterEventCreationAlsoRollsBackTheBusinessTransaction() {
    withFixture(fixture -> {
      var before = state(fixture);
      assertThatThrownBy(() -> transactions.executeWithoutResult(ignored -> {
        String fact = correct(fixture);
        assertBinding(fixture, fact, oneEvent(fixture));
        throw new IllegalStateException("synthetic failure after outbox creation");
      })).isInstanceOf(IllegalStateException.class).hasMessageContaining("after outbox");
      assertThat(state(fixture)).isEqualTo(before);
    });
  }

  @Test
  void uncommittedFactAndItsDeliveryIntentBecomeVisibleTogether() {
    withFixture(fixture -> {
      var before = state(fixture);
      CountDownLatch written = new CountDownLatch(1);
      CountDownLatch commit = new CountDownLatch(1);
      AtomicReference<String> fact = new AtomicReference<>();
      try (var executor = Executors.newSingleThreadExecutor()) {
        var pending = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
          fact.set(correct(fixture));
          assertBinding(fixture, fact.get(), oneEvent(fixture));
          written.countDown();
          await(commit);
        }));
        try {
          assertThat(written.await(15, TimeUnit.SECONDS)).isTrue();
          assertThat(state(fixture)).isEqualTo(before);
          commit.countDown();
          pending.get(15, TimeUnit.SECONDS);
          assertBinding(fixture, fact.get(), oneEvent(fixture));
        } catch (Exception failure) {
          throw new AssertionError("isolated transaction coordination failed", failure);
        } finally {
          commit.countDown();
          if (!pending.isDone()) pending.cancel(true);
        }
      }
    });
  }

  private String correct(SearchFactExternalFixture fixture) {
    return writer.storeCorrection(fixture.resumeVersionId(), fixture.factId(), userId(),
        "Synthetic outbox atomicity correction", "HUMAN", List.of("SEARCH"), List.of(), facts()).getFactSnapshotId();
  }

  private String ai(SearchFactExternalFixture fixture, String cacheKey) {
    var parse = jdbc.queryForMap("SELECT p.extraction_run_id,p.model_run_id FROM talent_resume_parse_snapshot p "
        + "JOIN talent_resume_fact_snapshot f ON f.parse_snapshot_id=p.id WHERE f.id=?", fixture.factId());
    return writer.storeAiFacts(fixture.resumeVersionId(), (String) parse.get("extraction_run_id"),
        (String) parse.get("model_run_id"), "v1", "synthetic", cacheKey, facts()).getId();
  }

  private void assertBinding(SearchFactExternalFixture fixture, String fact, Map<String, Object> event) {
    assertThat(jdbc.queryForObject("SELECT id FROM talent_resume_fact_snapshot WHERE resume_version_id=? AND current_marker='Y'",
        String.class, fixture.resumeVersionId())).isEqualTo(fact);
    assertThat(event.get("aggregate_id")).isEqualTo(fixture.candidateId());
    assertThat(event.get("aggregate_type")).isEqualTo("CANDIDATE");
    assertThat(event.get("event_type")).isEqualTo("TASK_AVAILABLE");
    assertThat(event.get("event_version")).isEqualTo(1);
    var work = tasks.get((String) event.get("work_task_id"));
    var publication = tasks.get((String) event.get("id"));
    assertThat(work.taskType()).isEqualTo("RESUME_SEARCH_PROJECT");
    assertThat(publication.taskType()).isEqualTo("OUTBOX_PUBLISH");
    assertThat(work.status()).isEqualTo("QUEUED");
    assertThat(publication.status()).isEqualTo("QUEUED");
    assertThat(publication.resourceId()).isEqualTo(work.id());
    assertThat(work.id()).isNotEqualTo(publication.id());
  }

  private Map<String, Object> oneEvent(SearchFactExternalFixture fixture) {
    var events = jdbc.queryForList("SELECT * FROM reporting_outbox_event WHERE aggregate_id=?", fixture.candidateId());
    assertThat(events).hasSize(1);
    return events.getFirst();
  }

  private Map<String, Object> state(SearchFactExternalFixture fixture) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String table : List.of("talent_resume_fact_snapshot", "talent_resume_parse_snapshot", "talent_fact_correction")) {
      result.put(table, jdbc.queryForList("SELECT * FROM " + table + " WHERE resume_version_id=? ORDER BY id", fixture.resumeVersionId()));
    }
    result.put("events", jdbc.queryForList("SELECT * FROM reporting_outbox_event WHERE aggregate_id=? ORDER BY id", fixture.candidateId()));
    result.put("tasks", jdbc.queryForList("SELECT * FROM reporting_async_task ORDER BY id"));
    result.put("process", jdbc.queryForList("SELECT * FROM talent_process_event WHERE target_id=? ORDER BY id", fixture.resumeVersionId()));
    return result;
  }

  private void withFixture(Consumer<SearchFactExternalFixture> action) {
    String user = userId();
    String department = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, user);
    try (var fixture = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, user, department)) {
      try {
        action.accept(fixture);
      } finally {
        removeGeneratedRows(fixture);
      }
    }
  }

  private void removeGeneratedRows(SearchFactExternalFixture fixture) {
    var taskIds = new ArrayList<String>();
    jdbc.queryForList("SELECT id,work_task_id FROM reporting_outbox_event WHERE aggregate_id=?", fixture.candidateId())
        .forEach(row -> { taskIds.add((String) row.get("id")); taskIds.add((String) row.get("work_task_id")); });
    jdbc.update("DELETE FROM reporting_outbox_event WHERE aggregate_id=?", fixture.candidateId());
    for (String task : taskIds) {
      jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", task);
      jdbc.update("DELETE FROM reporting_async_task WHERE id=?", task);
    }
    jdbc.update("DELETE FROM talent_fact_correction WHERE resume_version_id=?", fixture.resumeVersionId());
    jdbc.update("DELETE FROM talent_process_event WHERE target_id=?", fixture.resumeVersionId());
    for (String fact : jdbc.queryForList("SELECT id FROM talent_resume_fact_snapshot WHERE resume_version_id=? AND id<>? "
        + "ORDER BY snapshot_version DESC", String.class, fixture.resumeVersionId(), fixture.factId())) {
      jdbc.update("DELETE FROM talent_resume_fact_snapshot WHERE id=?", fact);
    }
    jdbc.update("DELETE FROM talent_resume_parse_snapshot WHERE resume_version_id=? AND id NOT IN "
        + "(SELECT parse_snapshot_id FROM talent_resume_fact_snapshot WHERE resume_version_id=?)",
        fixture.resumeVersionId(), fixture.resumeVersionId());
  }

  private String userId() {
    return jdbc.queryForObject("SELECT id FROM identity_user_account WHERE normalized_email='outbox-it@example.com'", String.class);
  }

  private static ResumeFacts facts() {
    return new ResumeFacts(new CandidateIdentity("Synthetic outbox candidate", null, null, "Engineer", "Shanghai", null, null, null),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("isolated transaction interrupted", failure);
    }
  }
}
