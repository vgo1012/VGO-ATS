package com.ezaigc.hr.bootstrap;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.search.application.manager.SearchTaskApplicationManager;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.application.manager.SearchIndexAuthorityApplicationManager;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.reporting.outbox.application.manager.OutboxPublicationApplicationManager;
import com.ezaigc.hr.reporting.outbox.application.manager.SearchTaskRecoveryApplicationManager;
import com.ezaigc.hr.reporting.outbox.domain.response.SearchTaskRecoveryResponse;
import com.ezaigc.hr.reporting.outbox.constant.OutboxConstants;
import com.ezaigc.hr.talent.application.manager.ResumeSearchProjectionRecorder;
import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 实际 MySQL、授权、HTTP 和任务链；网络故障由替身注入，不声明真实 Broker 协议已通过。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "spring.datasource.url=${HR_DB_URL}", "spring.datasource.username=${HR_DB_USER}",
    "spring.datasource.password=${HR_DB_PASSWORD}", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.connection-timeout=5000",
    "hr.object-storage.mode=local", "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
    "hr.search.mode=memory", "hr.malware-scan.mode=development",
    "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
    "hr.bootstrap-admin.enabled=true", "hr.bootstrap-admin.department-code=ROOT",
    "hr.bootstrap-admin.department-name=测试总部", "hr.bootstrap-admin.display-name=恢复测试管理员",
    "hr.bootstrap-admin.work-email=search-recovery-it@example.com", "hr.bootstrap-admin.password=Test-Only-Recovery-123!",
    "hr.resume-parsing.worker-enabled=false", "hr.jd-parsing.worker-enabled=false", "hr.matching.worker-enabled=false",
    "hr.search.worker-enabled=false", "hr.messaging.worker-enabled=false", "hr.reporting.audit-capture.enabled=false"
})
class SearchTaskRecoveryExternalMySqlIT {
  @Resource private JdbcTemplate jdbc;
  @Resource private PlatformIdGenerator ids;
  @Resource private AsyncTaskPort tasks;
  @Resource private TransactionTemplate transactions;
  @Resource private ResumeSearchProjectionRecorder recorder;
  @Resource private SearchTaskRecoveryApplicationManager recovery;
  @Resource private OutboxPublicationApplicationManager publications;
  @Resource private SearchTaskApplicationManager searchTasks;
  @Resource private UserDetailsService userDetails;
  @Resource private MockMvc mockMvc;
  @MockitoBean private OutboxMessagePublisher publisher;
  @MockitoSpyBean private SearchIndexPort index;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  private SecurityContext previousContext;
  private Authentication administrator;

  @BeforeEach
  void authenticate() {
    previousContext = SecurityContextHolder.getContext();
    var principal = userDetails.loadUserByUsername("search-recovery-it@example.com");
    administrator = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    setAuthentication(administrator);
  }

  @AfterEach
  void restoreContext() { SecurityContextHolder.setContext(previousContext); }

  @Test
  void exhaustedPublicationRecoversOriginalEnvelopeWithoutResettingSucceededWorkOrFailureHistory() {
    withFixture(fixture -> {
      String work = record(fixture);
      String original = publicationFor(work);
      searchTasks.process(tasks.claim("RESUME_SEARCH_PROJECT", 1).getFirst());
      exhaust(original, true);
      var old = taskState(original);
      var oldWork = taskState(work);
      var events = jdbc.queryForList("SELECT * FROM reporting_outbox_event ORDER BY id");
      var created = recovery.create(original, "修复 Broker 配置后重投", "publication-recovery");
      assertThat(created.getOutboxEventId()).isEqualTo(original);
      assertThat(created.getRecoveryTask().getMaxAttempts()).isEqualTo(5);
      assertThat(created.getRecoveryTask().getAttemptCount()).isZero();
      assertThat(recovery.create(original, "修复 Broker 配置后重投", "publication-recovery").getRecoveryTask().getTaskId())
          .isEqualTo(created.getRecoveryTask().getTaskId());
      assertCode(() -> recovery.create(original, "different", "publication-recovery"), "IDEMPOTENCY_KEY_REUSED");
      assertCode(() -> recovery.create(original, "修复 Broker 配置后重投", "different-key"), "SEARCH_TASK_ALREADY_RECOVERED");
      assertCode(() -> tasks.retryFailed(original, "原任务不可复活"), "TASK_NOT_RETRYABLE");
      doAnswer(call -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var envelope = call.getArgument(0, OutboxEnvelope.class);
        assertThat(envelope.getEventId()).isEqualTo(original);
        assertThat(envelope.getPayload().getTaskId()).isEqualTo(work);
        return null;
      }).when(publisher).publish(any());
      var claimed = tasks.claim("OUTBOX_PUBLISH", 1).getFirst();
      assertThat(claimed.id()).isEqualTo(created.getRecoveryTask().getTaskId());
      publications.process(claimed);
      assertThat(recovery.get(original).getRecoveryTask().getStatus()).isEqualTo("SUCCEEDED");
      assertThat(taskState(original)).isEqualTo(old);
      assertThat(taskState(work)).isEqualTo(oldWork);
      assertThat(jdbc.queryForList("SELECT * FROM reporting_outbox_event ORDER BY id")).isEqualTo(events);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_audit_event WHERE source_module='REPORTING_SEARCH_RECOVERY'", Integer.class)).isOne();
    });
  }

  @Test
  void eachNewIndexRecoveryHasFiveAttemptsAndItsOwnEventAndCanRecoverAgain() {
    withFixture(fixture -> {
      String original = record(fixture);
      exhaust(original, false);
      var old = taskState(original);
      var child = recovery.create(original, "修复索引连接", "index-recovery");
      assertThat(child.getOutboxEventId()).isNotEqualTo(publicationFor(original));
      exhaust(child.getRecoveryTask().getTaskId(), false);
      var childHistory = taskState(child.getRecoveryTask().getTaskId());
      var grandchild = recovery.create(child.getRecoveryTask().getTaskId(), "再次修复索引", "index-recovery-next");
      assertThat(grandchild.getRecoveryTask().getMaxAttempts()).isEqualTo(5);
      doCallRealMethod().when(index).upsertBatch(any(SearchIndexTarget.class), any());
      var claimed = tasks.claim("RESUME_SEARCH_PROJECT", 1).getFirst();
      assertThat(claimed.id()).isEqualTo(grandchild.getRecoveryTask().getTaskId());
      searchTasks.process(claimed);
      assertThat(tasks.get(claimed.id()).status()).isEqualTo("SUCCEEDED");
      assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems()).extracting(SearchDocument::getFactSnapshotId)
          .contains(fixture.factId());
      assertThat(taskState(original)).isEqualTo(old);
      assertThat(taskState(child.getRecoveryTask().getTaskId())).isEqualTo(childHistory);
      assertThat(jdbc.queryForList("SELECT max_attempts FROM reporting_async_task WHERE task_type='OUTBOX_PUBLISH'", Integer.class)).containsOnly(5);
    });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void concurrentAdministratorsCanCreateOnlyOneDirectRecovery(boolean sameKey) {
    withFixture(fixture -> {
      String original = record(fixture);
      exhaust(original, false);
      CountDownLatch start = new CountDownLatch(1);
      try (var pool = Executors.newFixedThreadPool(2)) {
        var first = pool.submit(() -> concurrentRecovery(original, "race-one", start));
        var second = pool.submit(() -> concurrentRecovery(original, sameKey ? "race-one" : "race-two", start));
        start.countDown();
        var outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        var successes = outcomes.stream().filter(SearchTaskRecoveryResponse.class::isInstance)
            .map(SearchTaskRecoveryResponse.class::cast).toList();
        assertThat(successes).hasSize(sameKey ? 2 : 1);
        assertThat(successes.stream().map(value -> value.getRecoveryTask().getTaskId()).distinct()).hasSize(1);
        if (!sameKey) assertThat(outcomes.stream().filter(PlatformException.class::isInstance).map(PlatformException.class::cast))
            .singleElement().satisfies(error -> assertThat(error.code()).isEqualTo("SEARCH_TASK_ALREADY_RECOVERED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_search_task_recovery WHERE original_task_id=?", Integer.class, original)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_audit_event WHERE target_id=? AND source_module='REPORTING_SEARCH_RECOVERY'", Integer.class, original)).isOne();
      } catch (Exception failure) { throw new AssertionError(failure); }
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"reporting_search_task_recovery", "reporting_audit_event", "reporting_outbox_event"})
  void anyPersistenceFailureRollsBackNewTasksAssociationEventAndAudit(String table) {
    withFixture(fixture -> {
      String original = record(fixture);
      exhaust(original, false);
      var before = allRecoveryState();
      jdbc.execute("CREATE TRIGGER search_recovery_it_reject BEFORE INSERT ON " + table
          + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic recovery persistence failure'");
      try {
        assertThatThrownBy(() -> recovery.create(original, "验证完整回滚", "rollback-recovery"))
            .isInstanceOf(RuntimeException.class).hasStackTraceContaining("Synthetic recovery persistence failure");
        assertThat(allRecoveryState()).isEqualTo(before);
      } finally { jdbc.execute("DROP TRIGGER IF EXISTS search_recovery_it_reject"); }
    });
  }

  @Test
  void httpRequiresAuthenticationCsrfAdministratorReasonAndIdempotencyAndPublishesItsContract() {
    withFixture(fixture -> {
      String original = record(fixture);
      exhaust(original, false);
      String url = "/api/v1/tasks/" + original + "/search-recovery";
      String recruiter = seedRecruiter();
      try {
        mockMvc.perform(post(url).with(anonymous()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "http-key").content("{\"reason\":\"修复后恢复\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).with(authentication(administrator)).contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "http-key").content("{\"reason\":\"修复后恢复\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(authentication(administrator)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "http-key").content("{\"reason\":\"恢复\"}")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(post("/api/v1/me/password").with(authentication(administrator)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"Test-Only-Recovery-123!\",\"newPassword\":\"Changed-Recovery-456!\"}"))
            .andExpect(status().isOk());
        var changedPrincipal = userDetails.loadUserByUsername("search-recovery-it@example.com");
        administrator = new UsernamePasswordAuthenticationToken(changedPrincipal, null, changedPrincipal.getAuthorities());
        for (String body : List.of("{\"reason\":\" \"}", "{\"reason\":\"" + "r".repeat(501) + "\"}")) {
          mockMvc.perform(post(url).with(authentication(administrator)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", "http-key").content(body)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(url).with(authentication(administrator)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "bad key").content("{\"reason\":\"恢复\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(url).with(authentication(administrator)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"恢复\"}")).andExpect(status().isBadRequest());
        var principal = userDetails.loadUserByUsername(recruiter + "@example.com");
        var recruiterAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        mockMvc.perform(post(url).with(authentication(recruiterAuth)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "http-key").content("{\"reason\":\"恢复\"}")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SEARCH_RECOVERY_ADMIN_REQUIRED"));
        mockMvc.perform(post(url).with(authentication(administrator)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "http-key").content("{\"reason\":\"修复后恢复\"}")).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.originalTaskId").value(original)).andExpect(jsonPath("$.recoveryTask.maxAttempts").value(5));
        mockMvc.perform(get(url).with(authentication(administrator))).andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("修复后恢复"));
        mockMvc.perform(get(url).with(authentication(recruiterAuth))).andExpect(status().isForbidden());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}/search-recovery'].post.responses['202']").exists());
      } catch (Exception failure) { throw new AssertionError(failure); }
      finally {
        setAuthentication(administrator);
        jdbc.update("DELETE FROM identity_user_role WHERE user_id=?", recruiter);
        jdbc.update("DELETE FROM identity_user_account WHERE id=?", recruiter);
      }
    });
  }

  @Test
  void queuedRecoveryCanBeCancelledWithoutRevivingItsParent() {
    withFixture(fixture -> {
      String original = record(fixture); exhaust(original, false);
      var before = taskState(original);
      var child = recovery.create(original, "修复后恢复", "cancel-recovery");
      assertThat(tasks.cancel(child.getRecoveryTask().getTaskId(), "人工停止恢复").status()).isEqualTo("CANCELLED");
      assertCode(() -> recovery.create(child.getRecoveryTask().getTaskId(), "不能绕过取消", "cancelled-recovery"), "SEARCH_TASK_NOT_EXHAUSTED");
      assertThat(taskState(original)).isEqualTo(before);
    });
  }

  private Object concurrentRecovery(String original, String key, CountDownLatch start) throws Exception {
    setAuthentication(administrator);
    try {
      assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
      try { return recovery.create(original, "并发恢复", key); }
      catch (PlatformException failure) { return failure; }
    } finally { SecurityContextHolder.clearContext(); }
  }

  private void exhaust(String id, boolean publication) {
    if (publication) doAnswer(call -> { throw new OutboxPublishException("SYNTHETIC_OUTAGE", "合成投递故障", true, null); }).when(publisher).publish(any());
    else doAnswer(call -> { throw new IllegalStateException("Synthetic index outage"); }).when(index).upsertBatch(any(SearchIndexTarget.class), any());
    for (int attempt = 1; attempt <= 5; attempt++) {
      jdbc.update("UPDATE reporting_async_task SET next_retry_at=? WHERE id=? AND status='QUEUED'", Timestamp.from(Instant.now().minusSeconds(1)), id);
      var task = tasks.claim(publication ? "OUTBOX_PUBLISH" : "RESUME_SEARCH_PROJECT", 1).getFirst();
      assertThat(task.id()).isEqualTo(id); assertThat(task.attemptCount()).isEqualTo(attempt);
      if (publication) publications.process(task); else searchTasks.process(task);
    }
    assertThat(tasks.get(id).status()).isEqualTo("FAILED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reporting_async_task_attempt WHERE task_id=?", Integer.class, id)).isEqualTo(5);
  }

  private String record(SearchFactExternalFixture fixture) {
    return transactions.execute(ignored -> recorder.candidateChanged(fixture.candidateId(), "synthetic-recovery",
        userId(), userId(), departmentId()).id());
  }

  private String publicationFor(String work) {
    return jdbc.queryForObject("SELECT id FROM reporting_outbox_event WHERE work_task_id=?", String.class, work);
  }

  private Map<String, Object> taskState(String id) {
    return Map.of("task", jdbc.queryForMap("SELECT * FROM reporting_async_task WHERE id=?", id),
        "attempts", jdbc.queryForList("SELECT * FROM reporting_async_task_attempt WHERE task_id=? ORDER BY attempt_no", id));
  }

  private Map<String, Object> allRecoveryState() {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String table : List.of("reporting_async_task", "reporting_async_task_attempt", "reporting_outbox_event", "reporting_search_task_recovery", "reporting_audit_event"))
      result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
    return result;
  }

  private void withFixture(Consumer<SearchFactExternalFixture> action) {
    try (var fixture = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId(), departmentId())) {
      try { action.accept(fixture); }
      finally {
        var recoveryIds = jdbc.queryForList("SELECT r.id FROM reporting_search_task_recovery r JOIN reporting_outbox_event e"
            + " ON e.id=r.outbox_event_id WHERE e.aggregate_id=?", String.class, fixture.candidateId());
        for (String id : recoveryIds) {
          jdbc.update("DELETE FROM reporting_audit_event WHERE source_module='REPORTING_SEARCH_RECOVERY' AND source_event_id=?", id);
          jdbc.update("DELETE FROM reporting_search_task_recovery WHERE id=?", id);
        }
        // 发布恢复没有独立事件行，由本场景精确清理；索引恢复由公共 fixture 按候选人事件清理。
        for (String id : recoveryIds) {
          if (OutboxConstants.PUBLISH_TASK_TYPE.equals(tasks.get(id).taskType())) {
            jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", id);
            jdbc.update("DELETE FROM reporting_async_task WHERE id=?", id);
          }
        }
      }
    }
  }

  private String seedRecruiter() {
    String id = ids.newId();
    jdbc.update("INSERT INTO identity_user_account(id,display_name,work_email,normalized_email,credential_hash,home_department_id,status,must_change_password,registered_at)"
        + " SELECT ?,'Synthetic recruiter',?,?,credential_hash,home_department_id,'ACTIVE',FALSE,CURRENT_TIMESTAMP(6) FROM identity_user_account WHERE id=?",
        id, id + "@example.com", id.toLowerCase(Locale.ROOT) + "@example.com", userId());
    jdbc.update("INSERT INTO identity_user_role(user_id,role_id,assigned_by_user_id,assigned_at,reason)"
        + " SELECT ?,id,?,CURRENT_TIMESTAMP(6),'Synthetic recovery permission check' FROM identity_role WHERE code='RECRUITER'", id, userId());
    return id;
  }

  private String userId() { return jdbc.queryForObject("SELECT id FROM identity_user_account WHERE normalized_email='search-recovery-it@example.com'", String.class); }
  private String departmentId() { return jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, userId()); }
  private static void setAuthentication(Authentication authentication) {
    var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
  }
  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(PlatformException.class, failure -> assertThat(failure.code()).isEqualTo(code));
  }
}
