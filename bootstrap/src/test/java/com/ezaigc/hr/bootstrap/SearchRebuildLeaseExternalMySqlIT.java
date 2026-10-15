package com.ezaigc.hr.bootstrap;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ezaigc.hr.bootstrap.web.TraceIdFilter;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.search.application.manager.SearchProjectionApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchTaskApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchStateReplayApplicationManager;
import com.ezaigc.hr.intelligence.search.application.support.SearchProjectionWorker;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.application.manager.SearchIndexAuthorityApplicationManager;
import com.ezaigc.hr.intelligence.search.application.manager.SearchIndexResourceApplicationManager;
import com.ezaigc.hr.intelligence.search.constant.SearchConstants;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import com.ezaigc.hr.talent.application.manager.ResumeFactPersistenceManager;
import com.ezaigc.hr.talent.application.manager.ResumeSearchProjectionRecorder;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import jakarta.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

/** 真实 MySQL 领取批次与真实 ES 别名发布；只使用新建空库及预检不存在的随机索引。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_SEARCH_LEASE_IT", matches = "true")
@ActiveProfiles("external-it")
@ContextConfiguration(initializers = ExternalMySqlIsolationInitializer.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "spring.datasource.url=${HR_DB_URL}",
    "spring.datasource.username=${HR_DB_USER}",
    "spring.datasource.password=${HR_DB_PASSWORD}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.hikari.maximum-pool-size=4",
    "spring.datasource.hikari.connection-timeout=5000",
    "hr.object-storage.mode=local",
    "hr.object-storage.local-root=${HR_EXTERNAL_OBJECT_ROOT}",
    "hr.search.mode=elasticsearch",
    "hr.search.elasticsearch.base-url=${HR_ES_URL}",
    "hr.search.elasticsearch.api-key=${HR_ES_API_KEY:}",
    "hr.search.elasticsearch.username=${HR_ES_USERNAME:}",
    "hr.search.elasticsearch.password=${HR_ES_PASSWORD:}",
    "hr.malware-scan.mode=development",
    "hr.pagination.cursor-signing-key=external-it-only-cursor-signing-key-32-bytes",
    "hr.bootstrap-admin.enabled=true",
    "hr.bootstrap-admin.department-code=ROOT",
    "hr.bootstrap-admin.department-name=测试总部",
    "hr.bootstrap-admin.display-name=搜索恢复测试管理员",
    "hr.bootstrap-admin.work-email=search-lease-it@example.com",
    "hr.bootstrap-admin.password=Test-Only-Search-123!",
    "hr.resume-parsing.worker-enabled=false",
    "hr.jd-parsing.worker-enabled=false",
    "hr.matching.worker-enabled=false",
    "hr.search.worker-enabled=false",
    "hr.messaging.worker-enabled=false",
    "hr.reporting.audit-capture.enabled=false"
})
class SearchRebuildLeaseExternalMySqlIT {
  private static final String ALIAS = "hr-search-lease-it-" + UUID.randomUUID().toString().replace("-", "");
  @Resource private JdbcTemplate jdbc;
  @Resource private PlatformIdGenerator ids;
  @Resource private AsyncTaskPort tasks;
  @Resource private RebuildRunService rebuilds;
  @Resource private SearchProjectionApplicationManager manager;
  @Resource private ApplicationContext context;
  @Resource private TransactionTemplate transactions;
  @Resource private ProjectionCheckpointService checkpoints;
  @Resource private StateReplayCheckpointService stateCheckpoints;
  @Resource private MockMvc mockMvc;
  @Resource private UserDetailsService userDetails;
  @MockitoSpyBean private SearchIndexPort index;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  @Resource private SearchIndexResourceApplicationManager resources;
  private final ObjectMapper json = new ObjectMapper();
  private final List<RebuildRun> ownedRuns = new ArrayList<>();

  @AfterEach
  void removeOnlyThisScenariosRebuildTasks() {
    // 包括未消费和已失败的合成任务，不能让下一场景领取本场景剩余队列项。
    for (RebuildRun run : ownedRuns) {
      transactions.executeWithoutResult(ignored -> {
        jdbc.update("DELETE FROM search_index_rebuild_run WHERE id=? AND task_id=?", run.getId(), run.getTaskId());
        jdbc.update("DELETE FROM reporting_async_task_attempt WHERE task_id=?", run.getTaskId());
        jdbc.update("DELETE FROM reporting_async_task WHERE id=?", run.getTaskId());
      });
    }
    // 本类仅在预检的新建空测试库中运行；逐场景释放本类搜索账本、权威和 observed 行。
    assertThat(index.resourceScope()).isEqualTo(ALIAS);
    transactions.executeWithoutResult(ignored -> {
      jdbc.update("DELETE FROM search_index_resource WHERE scope_name=?", ALIAS);
      jdbc.update("DELETE FROM search_projection_checkpoint WHERE projection_name IN (?,?)",
          SearchConstants.RESUME_FACTS_APPROVED_INDEX, SearchConstants.RESUME_FACTS_PROJECTION);
      jdbc.update("DELETE FROM search_state_replay_checkpoint WHERE projection_name=?",
          SearchConstants.RESUME_FACTS_PROJECTION);
    });
  }

  /** 台账提交先于显式创建，远端成功确认后才允许准备围栏；只登记本例预检目标。 */
  private void createFixtureTarget(SearchRebuildAttempt attempt, Set<String> allowed) {
    assertThat(allowed).contains(attempt.indexName(), writeAlias(attempt.indexName()));
    assertThat(index.resourceScope()).isEqualTo(ALIAS);
    authority.rebuildBase();
    String resourceId = ids.newId();
    transactions.executeWithoutResult(ignored -> assertThat(jdbc.update(
        "INSERT INTO search_index_resource(id,index_name,scope_name,resource_kind,attempt_count,status,managed) "
            + "VALUES (?,?,?,'LEGACY_ATTEMPT',?,'CREATING',FALSE)",
        resourceId, attempt.indexName(), ALIAS, attempt.attempt())).isOne());
    try {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      index.createRebuildTarget(attempt, resourceId);
      transactions.executeWithoutResult(ignored -> assertThat(jdbc.update(
          "UPDATE search_index_resource SET status='READY',managed=TRUE,ready_at=CURRENT_TIMESTAMP(6),version=version+1 "
              + "WHERE id=? AND index_name=? AND scope_name=? AND status='CREATING'",
          resourceId, attempt.indexName(), ALIAS)).isOne());
    } catch (RuntimeException failure) {
      transactions.executeWithoutResult(ignored -> jdbc.update(
          "UPDATE search_index_resource SET status='CREATE_UNKNOWN',version=version+1 "
              + "WHERE id=? AND index_name=? AND scope_name=? AND status='CREATING'",
          resourceId, attempt.indexName(), ALIAS));
      throw failure;
    }
  }

  /** 仅在本例所有 worker 返回之后回收已登记的精确资源，不删除别的 scope。 */
  private void retireFixtureTarget(HttpClient control, Set<String> allowed, String name) throws Exception {
    assertThat(allowed).contains(name, writeAlias(name));
    var registered = jdbc.queryForList(
        "SELECT id FROM search_index_resource WHERE index_name=? AND scope_name=?", String.class, name, ALIAS);
    if (registered.isEmpty()) {
      request(control, allowed, "HEAD", name, 404);
      return;
    }
    assertThat(registered).singleElement();
    // 精确 marker 校验由生产 adapter 执行；不能因随机名称预检通过就盲删后来出现的资源。
    index.retireIndex(name, registered.getFirst());
  }

  /** 批次未批准时只做隔离的准备写；当前目标使用实际批准代际及请求 pin。 */
  private SearchIndexTarget fixtureTarget(String name) {
    SearchIndexTarget approved = authority.rebuildBase();
    return name.equals(approved.indexName()) ? authority.currentTarget() : new SearchIndexTarget(name, 0);
  }

  private static Set<String> fixtureResources(Set<String> resources) {
    var names = new HashSet<>(resources);
    resources.stream().filter(name -> name.startsWith("resume-facts-v"))
        .map(SearchRebuildLeaseExternalMySqlIT::writeAlias).forEach(names::add);
    return Set.copyOf(names);
  }

  private static String writeAlias(String indexName) {
    return "resume-write-" + Sha256Utils.hashUtf8(ALIAS + ":" + indexName);
  }

  /** 显式批准本类预检拥有的合成目标，不把任意 alias 当作生产初始化来源。 */
  private void approveFixture(SearchRebuildAttempt attempt) {
    authority.rebuildBase();
    transactions.executeWithoutResult(ignored -> {
      var base = checkpoints.lockApprovedIndex();
      resources.requireReady(attempt.indexName(), null, attempt.attempt());
      Instant now = Instant.now();
      assertThat(checkpoints.approveIndex(attempt.indexName(), base.generation(), "SEARCH_IT", now)).isTrue();
      assertThat(checkpoints.resetForApprovedIndex(attempt.indexName(), SearchConstants.PROJECTION_EPOCH,
          "SEARCH_IT", now)).isTrue();
      assertThat(stateCheckpoints.resetForApprovedIndex(attempt.indexName(), "SEARCH_IT", now)).isTrue();
    });
  }

  @DynamicPropertySource
  static void isolatedAlias(DynamicPropertyRegistry properties) {
    properties.add("hr.search.elasticsearch.alias", () -> ALIAS);
  }

  @Test
  void httpDeletionTraceReachesDurableWorkerAndElasticsearch() throws Exception {
    RebuildRun run = enqueue();
    tasks.fail(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst(),
        "SYNTHETIC_CONTROL_ONLY", "Synthetic naming fixture", false);
    var target = new SearchRebuildAttempt("http-trace-" + run.getId(), 1);
    Set<String> allowed = fixtureResources(Set.of(ALIAS, target.indexName(),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + target.runId())));
    boolean owned = false;
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        owned = true;
        createFixtureTarget(target, allowed);
        index.beginRebuild(target);
        index.activateRebuild(target);
        approveFixture(target);
        String userId = run.getRequestedByUserId();
        String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
            String.class, userId);
        try (var fixture = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId)) {
          var source = context.getBean(ResumeSearchStateSource.class);
          var factory = context.getBean(SearchDocumentFactory.class);
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(factory.create(source.currentState(fixture.candidateId()))));
          request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
              .extracting(SearchDocument::getCandidateId).containsExactly(fixture.candidateId());

          var principal = userDetails.loadUserByUsername("search-lease-it@example.com");
          var administrator = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
          mockMvc.perform(post("/api/v1/me/password").with(authentication(administrator)).with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"currentPassword\":\"Test-Only-Search-123!\",\"newPassword\":\"Changed-Trace-456!\"}"))
              .andExpect(status().isOk());
          principal = userDetails.loadUserByUsername("search-lease-it@example.com");
          administrator = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
          String trace = "http-search-" + ids.newId();
          mockMvc.perform(delete("/api/v1/candidates/" + fixture.candidateId())
              .with(authentication(administrator)).with(csrf()).header(TraceIdFilter.HEADER, trace)
              .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"0\",\"reason\":\"合成候选人删除追踪验证\"}"))
              .andExpect(status().isNoContent()).andExpect(header().string(TraceIdFilter.HEADER, trace));
          assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
          var event = jdbc.queryForMap("SELECT trace_id,work_task_id FROM reporting_outbox_event WHERE aggregate_id=?",
              fixture.candidateId());
          assertThat(event.get("trace_id")).isEqualTo(trace);
          String workId = (String) event.get("work_task_id");
          assertThat(tasks.get(workId).status()).isEqualTo("QUEUED");

          AtomicReference<Map<String, String>> writingContext = new AtomicReference<>();
          AtomicReference<Thread> writingThread = new AtomicReference<>();
          AtomicInteger durableWrites = new AtomicInteger();
          doAnswer(call -> {
            if (workId.equals(MDC.get("taskId"))) {
              writingContext.set(MDC.getCopyOfContextMap());
              writingThread.set(Thread.currentThread());
              durableWrites.incrementAndGet();
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            }
            return call.callRealMethod();
          }).when(index).upsertBatch(eq(fixtureTarget(target.indexName())), any());

          Map<String, String> previous = Map.of("traceId", "worker-prior-trace-0123456789", "sentinel", "preserve");
          AtomicReference<Map<String, String>> restored = new AtomicReference<>();
          AtomicReference<Future<?>> submitted = new AtomicReference<>();
          var pollingWorker = new SearchProjectionWorker();
          ReflectionTestUtils.setField(pollingWorker, "manager", manager);
          ReflectionTestUtils.setField(pollingWorker, "taskManager", context.getBean(SearchTaskApplicationManager.class));
          ReflectionTestUtils.setField(pollingWorker, "stateReplay", context.getBean(SearchStateReplayApplicationManager.class));
          ReflectionTestUtils.setField(pollingWorker, "taskBatchRunner", context.getBean(AsyncTaskBatchRunner.class));
          var logger = (Logger) LoggerFactory.getLogger(SearchTaskApplicationManager.class);
          var logEvents = new ListAppender<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) {
              event.prepareForDeferredProcessing();
              super.append(event);
            }
          };
          logEvents.setContext(logger.getLoggerContext());
          logEvents.start();
          logger.addAppender(logEvents);
          try (var executor = Executors.newSingleThreadExecutor()) {
            ReflectionTestUtils.setField(pollingWorker, "taskExecutor", (TaskExecutor) command ->
                submitted.set(executor.submit(() -> {
                  MDC.setContextMap(previous);
                  try { command.run(); }
                  finally { restored.set(MDC.getCopyOfContextMap()); MDC.clear(); }
                })));
            try {
              request(control, allowed, "PUT", target.indexName() + "/_settings", "{\"index.blocks.write\":true}", 200);
              pollingWorker.poll();
              submitted.get().get(30, TimeUnit.SECONDS);
              assertThat(restored.get()).isEqualTo(previous);
              assertThat(tasks.get(workId).status()).isEqualTo("QUEUED");
              assertThat(tasks.get(workId).attemptCount()).isOne();
              assertThat(logEvents.list).filteredOn(eventLog -> workId.equals(eventLog.getMDCPropertyMap().get("taskId")))
                  .singleElement().satisfies(eventLog -> assertThat(eventLog.getMDCPropertyMap())
                      .containsEntry("traceId", trace).containsEntry("taskId", workId)
                      .containsEntry("taskType", ResumeFactProjectionSource.TASK_TYPE).containsEntry("attemptNo", "1"));
              request(control, allowed, "PUT", target.indexName() + "/_settings", "{\"index.blocks.write\":false}", 200);
              assertThat(jdbc.update("UPDATE reporting_async_task SET next_retry_at='2000-01-01 00:00:00' WHERE id=? AND status='QUEUED'",
                  workId)).isOne();
              pollingWorker.poll();
              submitted.get().get(30, TimeUnit.SECONDS);
              assertThat(restored.get()).isEqualTo(previous);
              pollingWorker.poll();
              submitted.get().get(30, TimeUnit.SECONDS);
              assertThat(restored.get()).isEqualTo(previous);
            } finally {
              pollingWorker.stop();
              if (submitted.get() != null && !submitted.get().isDone()) submitted.get().cancel(true);
            }
          } finally {
            logger.detachAppender(logEvents);
            logEvents.stop();
          }
          assertThat(writingThread.get()).isNotSameAs(Thread.currentThread());
          assertThat(writingContext.get()).containsEntry("traceId", trace).containsEntry("taskId", workId)
              .containsEntry("taskType", ResumeFactProjectionSource.TASK_TYPE).containsEntry("attemptNo", "2");
          assertThat(durableWrites.get()).isEqualTo(2);
          assertThat(tasks.get(workId).status()).isEqualTo("SUCCEEDED");
          assertThat(tasks.get(workId).attemptCount()).isEqualTo(2);
          request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems()).isEmpty();
          var tombstone = json.readTree(request(control, allowed, "GET", target.indexName() + "/_doc/" + fixture.candidateId(), 200));
          assertThat(tombstone.get("_version").asLong()).isEqualTo(Long.MAX_VALUE);
        }
      } finally {
        if (owned) {
          retireFixtureTarget(control, allowed, target.indexName());
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  @Test
  void durableStateReplaySurvivesCutoverCrashAndPartiallyWrittenPage() throws Exception {
    RebuildRun run = enqueue();
    var initial = new SearchRebuildAttempt("state-old-" + run.getId(), 1);
    var rebuilt = new SearchRebuildAttempt("state-new-" + run.getId(), 1);
    Set<String> allowed = fixtureResources(Set.of(ALIAS, initial.indexName(), rebuilt.indexName(),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + initial.runId()),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + rebuilt.runId())));
    boolean owned = false;
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        owned = true;
        createFixtureTarget(initial, allowed);
        index.beginRebuild(initial);
        index.activateRebuild(initial);
        approveFixture(initial);
        createFixtureTarget(rebuilt, allowed);
        index.beginRebuild(rebuilt);
        String userId = run.getRequestedByUserId();
        String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?", String.class, userId);
        var states = context.getBean(ResumeSearchStateSource.class);
        var factory = context.getBean(SearchDocumentFactory.class);
        var recorder = context.getBean(ResumeSearchProjectionRecorder.class);
        var checkpointService = context.getBean(StateReplayCheckpointService.class);
        try (var deleted = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
            var unparsed = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
            var survivor = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
            var merged = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId)) {
          List<String> candidates = List.of(deleted.candidateId(), unparsed.candidateId(), survivor.candidateId(), merged.candidateId());
          var stale = candidates.stream().map(states::currentState).map(factory::create).toList();
          index.upsertBatch(fixtureTarget(initial.indexName()), stale);
          index.upsertBatch(fixtureTarget(rebuilt.indexName()), stale);
          assertThat(newStateReplayManager().replayNext(200)).isEqualTo(4);
          assertThat(checkpointService.checkpoint().isCompleted()).isTrue();
          // 直接构造已提交的生命周期终态；入口原子性由 TalentSearchLifecycleExternalMySqlIT 单独验证。
          transactions.executeWithoutResult(ignored -> {
            jdbc.update("UPDATE talent_candidate SET status='DELETED' WHERE id=?", deleted.candidateId());
            jdbc.update("UPDATE talent_resume_fact_snapshot SET current_marker=NULL WHERE id=?", unparsed.factId());
            jdbc.update("UPDATE talent_resume_version SET version_no=2 WHERE id=?", unparsed.resumeVersionId());
            jdbc.update("UPDATE talent_resume SET current_version_no=2 WHERE id=(SELECT resume_id FROM talent_resume_version WHERE id=?)", unparsed.resumeVersionId());
            merged.becomeNextVersionOf(survivor);
            jdbc.update("UPDATE talent_candidate SET status='DELETED' WHERE id=?", merged.candidateId());
            for (String candidateId : candidates) recorder.candidateChanged(candidateId, "synthetic-cutover", userId, userId, departmentId);
          });
          assertThat(context.getBean(AsyncTaskBatchRunner.class).run(ResumeFactProjectionSource.TASK_TYPE, 4,
              context.getBean(SearchTaskApplicationManager.class)::process)).isEqualTo(4);
          assertThat(jdbc.queryForList("SELECT status FROM reporting_async_task WHERE task_type='RESUME_SEARCH_PROJECT'", String.class))
              .hasSize(4).containsOnly("SUCCEEDED");
          request(control, allowed, "POST", initial.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems()).extracting(SearchDocument::getFactSnapshotId)
              .containsExactly(merged.factId());
          index.activateRebuild(rebuilt);
          approveFixture(rebuilt);
          request(control, allowed, "POST", rebuilt.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems()).hasSize(4);
          // 模拟激活后进程中断，新的管理器必须识别物理索引变化，而不是沿用旧索引的完成标志。
          assertThat(newStateReplayManager().replayNext(1)).isOne();
          var first = checkpointService.checkpoint();
          assertThat(first.getIndexName()).isEqualTo(rebuilt.indexName());
          assertThat(first.getLastCandidateId()).isEqualTo(deleted.candidateId());
          assertThat(first.getProjectedCount()).isOne();
          assertThat(first.isCompleted()).isFalse();
          AtomicBoolean failOnce = new AtomicBoolean(true);
          doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Object result = call.callRealMethod();
            if (failOnce.getAndSet(false)) throw new IllegalStateException("Synthetic post-write interruption");
            return result;
          }).when(index).upsertBatch(eq(fixtureTarget(rebuilt.indexName())), any());
          assertThatThrownBy(() -> newStateReplayManager().replayNext(1)).hasMessage("Synthetic post-write interruption");
          assertThat(checkpointService.checkpoint().getLastCandidateId()).isEqualTo(first.getLastCandidateId());
          assertThat(checkpointService.checkpoint().getVersion()).isEqualTo(first.getVersion());
          var restarted = newStateReplayManager();
          for (int page = 0; page < 5 && restarted.replayNext(1) > 0; page++) {}
          var completed = checkpointService.checkpoint();
          assertThat(completed.isCompleted()).isTrue();
          assertThat(completed.getProjectedCount()).isEqualTo(4);
          assertThat(completed.getLastCandidateId()).isEqualTo(merged.candidateId());
          // 重复页和迟到的旧事实不能越过占位、墓碑及合并后的版本屏障。
          index.upsertBatch(fixtureTarget(rebuilt.indexName()), stale);
          request(control, allowed, "POST", rebuilt.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems()).singleElement().satisfies(document -> {
            assertThat(document.getCandidateId()).isEqualTo(survivor.candidateId());
            assertThat(document.getFactSnapshotId()).isEqualTo(merged.factId());
          });
        }
      } finally {
        if (owned) {
          retireFixtureTarget(control, allowed, rebuilt.indexName());
          retireFixtureTarget(control, allowed, initial.indexName());
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  @Test
  void lateStateReplayPageCannotBindOrAdvanceANewerPhysicalIndexCheckpoint() {
    var service = context.getBean(StateReplayCheckpointService.class);
    var checkpoint = new StateReplayCheckpoint();
    checkpoint.setProjectionName(SearchConstants.RESUME_FACTS_PROJECTION);
    checkpoint.setLastCandidateId("00000000000000000000000000");
    checkpoint.setCreateTime(Instant.now());
    checkpoint.setUpdateTime(Instant.now());
    service.initialize(checkpoint);
    long oldVersion = service.checkpoint().getVersion();
    assertThat(service.bindIndex("synthetic-state-index-new", oldVersion, "synthetic-actor", Instant.now())).isTrue();
    assertThat(service.advance("synthetic-new-candidate", true, 1, oldVersion + 1, "synthetic-actor", Instant.now())).isTrue();
    var expected = service.checkpoint();
    assertThat(service.bindIndex("synthetic-state-index-old", oldVersion, "synthetic-actor", Instant.now())).isFalse();
    assertThat(service.advance("synthetic-old-candidate", true, 99, oldVersion, "synthetic-actor", Instant.now())).isFalse();
    assertThat(service.checkpoint()).usingRecursiveComparison().isEqualTo(expected);
  }

  private SearchStateReplayApplicationManager newStateReplayManager() {
    var replay = new SearchStateReplayApplicationManager();
    context.getAutowireCapableBeanFactory().autowireBean(replay);
    return replay;
  }

  @Test
  void onlyLatestAvailableVersionParticipatesEvenWhenOldFactsArriveLater() throws Exception {
    RebuildRun run = enqueue();
    var target = new SearchRebuildAttempt("latest-version-" + run.getId(), 1);
    Set<String> allowed = fixtureResources(Set.of(ALIAS, target.indexName(),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + target.runId())));
    boolean owned = false;
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        owned = true;
        createFixtureTarget(target, allowed);
        index.beginRebuild(target);
        index.activateRebuild(target);
        approveFixture(target);
        String userId = run.getRequestedByUserId();
        String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
            String.class, userId);
        var source = context.getBean(ResumeFactProjectionSource.class);
        var factory = context.getBean(SearchDocumentFactory.class);
        var persistence = context.getBean(ResumeParsingPersistenceService.class);
        try (var old = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
            var latest = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId)) {
          latest.becomeNextVersionOf(old);
          old.appendCorrection(userId);
          var page = source.currentFactsAfter(SearchConstants.PROJECTION_EPOCH, "", 200);
          assertThat(page.items()).extracting(item -> item.facts().getSnapshotId()).containsExactly(latest.factId());
          var item = page.items().getFirst();
          assertThat(item.resumeVersionNo()).isEqualTo(2);
          var published = factory.create(item.facts(), item.createTime(), item.sourceChannel(), item.resumeVersionNo());
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(published));
          var stale = new SearchDocument();
          stale.setCandidateId(old.candidateId());
          stale.setFactSnapshotId(old.factId());
          stale.setFullName("Late old resume");
          stale.setUpdateTime(Instant.now().plusSeconds(10));
          stale.setProjectionVersion((1L << 32) | Integer.MAX_VALUE);
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(stale));
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(published));
          request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
              .extracting(SearchDocument::getFactSnapshotId).containsExactly(latest.factId());
          assertThat(persistence.findLatestAvailableFactContacts(List.of(old.resumeVersionId(), latest.resumeVersionId())))
              .extracting(contact -> contact.getId()).containsExactly(latest.factId());
          // 最新可用版本尚无当前事实时，源与命中回查都不能退回旧版本。
          assertThat(jdbc.update("UPDATE talent_resume_fact_snapshot SET current_marker=NULL WHERE id=?", latest.factId())).isOne();
          assertThat(source.currentFactsAfter(SearchConstants.PROJECTION_EPOCH, "", 200).items()).isEmpty();
          assertThat(persistence.findLatestAvailableFactContacts(List.of(old.resumeVersionId(), latest.resumeVersionId())))
              .isEmpty();
          var states = context.getBean(ResumeSearchStateSource.class);
          // 模拟后续可用业务版本尚未解析；同一已存在事实版本的占位不能倒退外部版本。
          jdbc.update("UPDATE talent_resume_version SET version_no=3 WHERE id=?", latest.resumeVersionId());
          jdbc.update("UPDATE talent_resume SET current_version_no=3 WHERE id=(SELECT resume_id FROM talent_resume_version WHERE id=?)",
              latest.resumeVersionId());
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(factory.create(states.currentState(old.candidateId()))));
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(stale));
          request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems()).isEmpty();
          // 事实仍可补齐；删除墓碑则永久高于所有正常简历与事实版本。
          published.setProjectionVersion((3L << 32) | 1);
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(published));
          request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
              .extracting(SearchDocument::getFactSnapshotId).containsExactly(latest.factId());
          jdbc.update("UPDATE talent_candidate SET status='DELETED' WHERE id=?", old.candidateId());
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(factory.create(states.currentState(old.candidateId()))));
          index.upsertBatch(fixtureTarget(target.indexName()), List.of(published, stale));
          request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems()).isEmpty();
        }
      } finally {
        if (owned) {
          retireFixtureTarget(control, allowed, target.indexName());
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  @Test
  void lateCommittedFactCannotFallBehindTheProjectionCheckpoint() throws Exception {
    RebuildRun run = enqueue();
    // 本例使用 run 生成隔离索引名称，轮询时不应执行这个无关的控制重建。
    tasks.fail(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst(),
        "SYNTHETIC_CONTROL_ONLY", "Synthetic naming fixture", false);
    var target = new SearchRebuildAttempt("commit-order-" + run.getId(), 1);
    Set<String> allowed = fixtureResources(Set.of(ALIAS, target.indexName(),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + target.runId())));
    boolean owned = false;
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        owned = true;
        createFixtureTarget(target, allowed);
        index.beginRebuild(target);
        index.activateRebuild(target);
        approveFixture(target);
        assertThat(manager.projectNext(200)).isZero();
        String userId = run.getRequestedByUserId();
        String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
            String.class, userId);
          try (var first = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
              var boundary = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
              var second = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId)) {
            // 为两份并发样本隔开两个唯一索引的物理间隙；边界候选人不可检索，原结果仍只有两份。
            jdbc.update("UPDATE talent_candidate SET status='DELETED' WHERE id=?", boundary.candidateId());
          assertThat(manager.projectNext(200)).isEqualTo(2);
          CountDownLatch inserted = new CountDownLatch(1);
          CountDownLatch commitAllowed = new CountDownLatch(1);
          AtomicReference<String> lateSnapshot = new AtomicReference<>();
          AtomicReference<String> stage = new AtomicReference<>("await first insert");
          String earlySnapshot;
          try (var worker = Executors.newSingleThreadExecutor()) {
            var pendingCommit = worker.submit(() -> transactions.executeWithoutResult(ignored -> {
              // 生产写入器包含当前事实锁；较后版本的边界锁不应让不相邻版本被迫串行。
              lateSnapshot.set(second.appendCorrection(context.getBean(ResumeFactPersistenceManager.class), userId));
              inserted.countDown();
              try {
                assertThat(commitAllowed.await(30, TimeUnit.SECONDS)).as("main stage: %s", stage.get()).isTrue();
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Synthetic correction transaction interrupted", interrupted);
              }
            }));
            try {
              assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
              stage.set("verify uncommitted visibility");
              assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talent_resume_fact_snapshot WHERE id=?",
                  Integer.class, lateSnapshot.get())).isZero();
              stage.set("insert second correction");
              earlySnapshot = first.appendCorrection(context.getBean(ResumeFactPersistenceManager.class), userId);
              stage.set("project committed second correction");
              assertThat(manager.projectNext(200)).isOne();
              assertThat(jdbc.queryForObject("SELECT last_snapshot_id FROM search_projection_checkpoint WHERE projection_name=?",
                  String.class, SearchConstants.RESUME_FACTS_PROJECTION)).isEqualTo(earlySnapshot);
              stage.set("release first commit");
              commitAllowed.countDown();
              pendingCommit.get(30, TimeUnit.SECONDS);
            } finally {
              commitAllowed.countDown();
              if (!pendingCommit.isDone()) pendingCommit.cancel(true);
            }
          }
          assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM talent_resume_fact_snapshot late "
              + "JOIN talent_resume_fact_snapshot early ON early.id=? "
              + "WHERE late.id=? AND late.current_marker='Y' AND late.create_time<early.create_time",
              Integer.class, earlySnapshot, lateSnapshot.get())).isOne();
          // 多次正常轮询及重新取得应用服务均须能恢复已经提交的当前事实。
          for (int poll = 0; poll < 3; poll++) {
            var pollingWorker = new SearchProjectionWorker();
            ReflectionTestUtils.setField(pollingWorker, "manager", context.getBean(SearchProjectionApplicationManager.class));
            ReflectionTestUtils.setField(pollingWorker, "taskManager", context.getBean(SearchTaskApplicationManager.class));
            ReflectionTestUtils.setField(pollingWorker, "stateReplay", context.getBean(SearchStateReplayApplicationManager.class));
            ReflectionTestUtils.setField(pollingWorker, "taskBatchRunner", context.getBean(AsyncTaskBatchRunner.class));
            ReflectionTestUtils.setField(pollingWorker, "taskExecutor", (TaskExecutor) Runnable::run);
            pollingWorker.poll();
          }
          request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
              .as("先生成后提交的当前事实不能永久落在游标之后而丢失")
              .extracting(SearchDocument::getFactSnapshotId)
              .containsExactlyInAnyOrder(lateSnapshot.get(), earlySnapshot);
        }
      } finally {
        if (owned) {
          retireFixtureTarget(control, allowed, target.indexName());
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  @Test
  void lateOlderFactCannotReplaceANewerSnapshotInTheSameIndex() throws Exception {
    RebuildRun run = enqueue();
    var target = new SearchRebuildAttempt("ordering-" + run.getId(), 1);
    Set<String> allowed = fixtureResources(Set.of(ALIAS, target.indexName(),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + target.runId())));
    boolean owned = false;
    SearchFactExternalFixture fixture = null;
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        owned = true;
        createFixtureTarget(target, allowed);
        index.beginRebuild(target);
        index.activateRebuild(target);
        approveFixture(target);
        assertThat(manager.projectNext(200)).isZero();
        String userId = run.getRequestedByUserId();
        String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
            String.class, userId);
        fixture = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
        SearchFactExternalFixture facts = fixture;
        AtomicBoolean interceptFirstPage = new AtomicBoolean(true);
        AtomicReference<String> newSnapshot = new AtomicReference<>();
        AtomicReference<Long> newCheckpointVersion = new AtomicReference<>();
        doAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
          if (interceptFirstPage.compareAndSet(true, false)) {
            try (var worker = Executors.newSingleThreadExecutor()) {
              var newer = worker.submit(() -> {
                newSnapshot.set(facts.appendCorrection(userId));
                assertThat(manager.projectNext(200)).isOne();
                newCheckpointVersion.set(jdbc.queryForObject("SELECT version FROM search_projection_checkpoint WHERE projection_name=?",
                    Long.class, SearchConstants.RESUME_FACTS_PROJECTION));
                return null;
              });
              try {
                newer.get(30, TimeUnit.SECONDS);
              } finally {
                if (!newer.isDone()) newer.cancel(true);
              }
            }
            request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
            assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
                .extracting(SearchDocument::getFactSnapshotId).containsExactly(newSnapshot.get());
          }
          return call.callRealMethod();
        }).when(index).upsertBatch(eq(fixtureTarget(target.indexName())), any());

        assertThat(catchThrowable(() -> manager.projectNext(200))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM search_projection_checkpoint WHERE projection_name=?",
            Long.class, SearchConstants.RESUME_FACTS_PROJECTION)).isEqualTo(newCheckpointVersion.get());
        assertThat(jdbc.queryForObject("SELECT last_snapshot_id FROM search_projection_checkpoint WHERE projection_name=?",
            String.class, SearchConstants.RESUME_FACTS_PROJECTION)).isEqualTo(newSnapshot.get());
        assertThat(manager.projectNext(200)).isZero();
        request(control, allowed, "POST", target.indexName() + "/_refresh", 200);
        assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
            .as("检查点已指向新事实，迟到旧批次不能把实际索引写回旧快照")
            .extracting(SearchDocument::getFactSnapshotId).containsExactly(newSnapshot.get());
      } finally {
        if (fixture != null) fixture.close();
        if (owned) {
          retireFixtureTarget(control, allowed, target.indexName());
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  @Test
  void lateProjectionPageCannotAdvanceTheCheckpointOfTheNewIndex() throws Exception {
    RebuildRun run = enqueue();
    var initial = new SearchRebuildAttempt("initial-" + run.getId(), 1);
    var rebuilt = new SearchRebuildAttempt(run.getId(), 1);
    Set<String> allowed = fixtureResources(Set.of(ALIAS, initial.indexName(), rebuilt.indexName(),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + initial.runId()),
        "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + rebuilt.runId())));
    List<String> owned = new ArrayList<>();
    SearchFactExternalFixture fixture = null;
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        owned.addAll(List.of(initial.indexName(), rebuilt.indexName()));
        createFixtureTarget(initial, allowed);
        index.beginRebuild(initial);
        index.activateRebuild(initial);
        approveFixture(initial);
        assertThat(manager.projectNext(200)).isZero();
        String userId = run.getRequestedByUserId();
        String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
            String.class, userId);
        fixture = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
        AtomicReference<Long> publishedVersion = new AtomicReference<>();
        doAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
          try (var worker = Executors.newSingleThreadExecutor()) {
            var cutover = worker.submit(() -> {
              manager.process(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst());
              assertThat(tasks.get(run.getTaskId()).status()).isEqualTo("SUCCEEDED");
              assertThat(manager.projectNext(200)).isOne();
              publishedVersion.set(jdbc.queryForObject("SELECT version FROM search_projection_checkpoint WHERE projection_name=?",
                  Long.class, SearchConstants.RESUME_FACTS_PROJECTION));
              return null;
            });
            try {
              cutover.get(30, TimeUnit.SECONDS);
            } finally {
              if (!cutover.isDone()) cutover.cancel(true);
            }
          }
          return call.callRealMethod();
        }).when(index).upsertBatch(eq(fixtureTarget(initial.indexName())), any());
        assertThat(catchThrowable(() -> manager.projectNext(200))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM search_projection_checkpoint WHERE projection_name=?",
            Long.class, SearchConstants.RESUME_FACTS_PROJECTION)).isEqualTo(publishedVersion.get());
        assertThat(jdbc.queryForObject("SELECT index_name FROM search_projection_checkpoint WHERE projection_name=?",
            String.class, SearchConstants.RESUME_FACTS_PROJECTION)).isEqualTo(rebuilt.indexName());
        assertThat(manager.projectNext(200)).isZero();
        request(control, allowed, "POST", rebuilt.indexName() + "/_refresh", 200);
        assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
            .extracting(SearchDocument::getCandidateId).containsExactly(fixture.candidateId());
      } finally {
        if (fixture != null) fixture.close();
        for (String target : owned.reversed()) {
          retireFixtureTarget(control, allowed, target);
          request(control, allowed, "HEAD", target, 404);
        }
        if (!owned.isEmpty()) {
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  @ParameterizedTest(name = "增量与重建交错，发布后失败模式={0}")
  @ValueSource(strings = {"NONE", "RESPONSE_LOST", "COMPLETION_ROLLBACK"})
  void incrementalFactsRemainDiscoverableAfterCutoverAndRestart(String failureMode) throws Exception {
    RebuildRun run = enqueue();
    var initial = new SearchRebuildAttempt("initial-" + run.getId(), 1);
    var rebuilt = new SearchRebuildAttempt(run.getId(), 1);
    String initialFence = "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + initial.runId());
    String rebuiltFence = "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + rebuilt.runId());
    Set<String> allowed = fixtureResources(Set.of(ALIAS, initial.indexName(), rebuilt.indexName(), initialFence, rebuiltFence));
    List<String> owned = new ArrayList<>();
    AtomicReference<SearchFactExternalFixture> fixture = new AtomicReference<>();
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        owned.addAll(List.of(initial.indexName(), rebuilt.indexName()));
        createFixtureTarget(initial, allowed);
        index.beginRebuild(initial);
        index.activateRebuild(initial);
        approveFixture(initial);
        assertThat(manager.projectNext(200)).isZero();
        if ("COMPLETION_ROLLBACK".equals(failureMode)) {
          jdbc.execute("CREATE TRIGGER search_it_reject_completion BEFORE UPDATE ON search_index_rebuild_run "
              + "FOR EACH ROW BEGIN IF NEW.status='SUCCEEDED' THEN SIGNAL SQLSTATE '45000' "
              + "SET MESSAGE_TEXT='synthetic rebuild completion failure'; END IF; END");
        }
        doAnswer(call -> {
          assertThat(call.getArgument(0, SearchRebuildAttempt.class)).isEqualTo(rebuilt);
          try (var worker = Executors.newSingleThreadExecutor()) {
            var incremental = worker.submit(() -> {
              String userId = run.getRequestedByUserId();
              String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
                  String.class, userId);
              var facts = new SearchFactExternalFixture(jdbc, ids, tasks, transactions, userId, departmentId);
              fixture.set(facts);
              assertThat(manager.projectNext(200)).isOne();
              assertThat(jdbc.queryForObject("SELECT last_snapshot_id FROM search_projection_checkpoint WHERE projection_name=?",
                  String.class, SearchConstants.RESUME_FACTS_PROJECTION)).isEqualTo(facts.factId());
              return null;
            });
            try {
              incremental.get(30, TimeUnit.SECONDS);
            } finally {
              if (!incremental.isDone()) incremental.cancel(true);
            }
          }
          request(control, allowed, "POST", initial.indexName() + "/_refresh", 200);
          assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
              .extracting(SearchDocument::getCandidateId).contains(fixture.get().candidateId());
          call.callRealMethod();
          if ("RESPONSE_LOST".equals(failureMode)) {
            throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_INDEX_UNAVAILABLE",
                "synthetic committed publication response lost", true);
          }
          return null;
        }).when(index).activateRebuild(rebuilt);
        manager.process(tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst());
        assertThat(tasks.get(run.getTaskId()).status())
            .isEqualTo("NONE".equals(failureMode) ? "SUCCEEDED" : "FAILED");
        assertAlias(control, allowed, rebuilt.indexName());
        String approved = authority.currentTarget().indexName();
        assertThat(approved).isEqualTo("NONE".equals(failureMode) ? rebuilt.indexName() : initial.indexName());
        var restarted = new SearchProjectionApplicationManager();
        context.getAutowireCapableBeanFactory().autowireBean(restarted);
        // 重启后继续有界增量投影，不依赖上次重建调用的栈或内存状态。
        for (int page = 0; page < 3 && restarted.projectNext(200) > 0; page++) {}
        request(control, allowed, "POST", approved + "/_refresh", 200);
        assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
            .as("成功批准后回放；响应丢失或终态回滚时仍从原批准目标发现新事实")
            .extracting(SearchDocument::getCandidateId).contains(fixture.get().candidateId());
      } finally {
        jdbc.execute("DROP TRIGGER IF EXISTS search_it_reject_completion");
        if (fixture.get() != null) fixture.get().close();
        for (String target : owned.reversed()) {
          retireFixtureTarget(control, allowed, target);
          request(control, allowed, "HEAD", target, 404);
        }
        if (!owned.isEmpty()) {
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  @ParameterizedTest(name = "旧尝试在围栏准备前迟到={0}")
  @ValueSource(booleans = {false, true})
  void expiredAttemptCannotReplaceTheIndexAlreadyPublishedByItsRecovery(boolean delayBeforeFence) throws Exception {
    RebuildRun run = enqueue();
    String prefix = "resume-facts-v" + run.getId().toLowerCase(Locale.ROOT) + "-attempt-";
    String oldIndex = prefix + "1";
    String newIndex = prefix + "2";
    Set<String> allowed = fixtureResources(Set.of(ALIAS, oldIndex, newIndex, "resume-rebuild-" + Sha256Utils.hashUtf8(ALIAS + ":" + run.getId())));
    List<String> owned = new ArrayList<>();
    try (var control = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      try {
        for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        // 名称全部通过不存在预检之后才登记；包括响应丢失时可能已经创建的目标。
        owned.addAll(List.of(oldIndex, newIndex));
        var first = tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst();
        assertThat(first.id()).isEqualTo(run.getTaskId());
        doAnswer(call -> {
          String target = call.getArgument(0, SearchRebuildAttempt.class).indexName();
          assertThat(allowed).contains(target);
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
          assertThat(jdbc.queryForMap("SELECT status,managed,scope_name FROM search_index_resource WHERE index_name=?", target))
              .containsEntry("status", "READY").containsEntry("managed", true).containsEntry("scope_name", ALIAS);
          if (delayBeforeFence && oldIndex.equals(target)) {
            recoverAndPublish(run, first, control, allowed, newIndex);
          }
          call.callRealMethod();
          var marker = new SearchDocument();
          marker.setCandidateId(target);
          marker.setProjectionVersion((1L << 32) | 1);
          marker.setResumeVersionId("synthetic-resume");
          marker.setFactSnapshotId("synthetic-fact");
          marker.setFullName("Synthetic Candidate");
          marker.setSearchText("Synthetic lease recovery marker");
          marker.setUpdateTime(Instant.parse("2026-09-01T00:00:00Z"));
          index.upsertBatch(fixtureTarget(target), List.of(marker));
          request(control, allowed, "POST", target + "/_refresh", 200);
          return null;
        }).when(index).beginRebuild(any(SearchRebuildAttempt.class));
        doAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
          if (!delayBeforeFence && oldIndex.equals(call.getArgument(0, SearchRebuildAttempt.class).indexName())) {
            recoverAndPublish(run, first, control, allowed, newIndex);
          }
          return call.callRealMethod();
        }).when(index).activateRebuild(any(SearchRebuildAttempt.class));

        Throwable lateFailure = catchThrowable(() -> manager.process(first));
        assertThat(lateFailure).isInstanceOfSatisfying(PlatformException.class,
            failure -> assertThat(failure.code()).isEqualTo("TASK_STATE_CONFLICT"));
        assertCommittedWinner(run, newIndex);
        // 数据库围栏成功仍不足够：实际搜索也必须保持第二批次的结果。
        assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
            .as("旧批次迟到后搜索仍应返回已成功的新批次索引")
            .extracting(SearchDocument::getCandidateId).containsExactly(newIndex);
        assertAlias(control, allowed, newIndex);
      } finally {
        for (String target : owned.reversed()) {
          retireFixtureTarget(control, allowed, target);
          request(control, allowed, "HEAD", target, 404);
        }
        if (!owned.isEmpty()) {
          for (String resource : allowed) request(control, allowed, "HEAD", resource, 404);
        }
      }
    }
  }

  /** 真正的另一 worker 恢复租约并提交新批次，返回前检查远程发布已生效。 */
  private void recoverAndPublish(RebuildRun run, AsyncTaskPort.TaskSnapshot first,
      HttpClient control, Set<String> allowed, String newIndex) throws Exception {
    try (var worker = Executors.newSingleThreadExecutor()) {
      var recovered = worker.submit(() -> {
        assertThat(jdbc.update("UPDATE reporting_async_task SET lease_until='2000-01-01 00:00:00' "
            + "WHERE id=? AND attempt_count=1 AND status='RUNNING'", run.getTaskId())).isOne();
        var second = tasks.claim(SearchConstants.REBUILD_TASK_TYPE, 1).getFirst();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.attemptCount()).isEqualTo(2);
        manager.process(second);
        assertCommittedWinner(run, newIndex);
        return null;
      });
      try {
        recovered.get(30, TimeUnit.SECONDS);
      } finally {
        if (!recovered.isDone()) recovered.cancel(true);
      }
    }
    assertAlias(control, allowed, newIndex);
    assertThat(index.search(authority.currentTarget(), new SearchCriteria(), null, 10).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly(newIndex);
  }

  private void assertCommittedWinner(RebuildRun run, String target) {
    assertThat(jdbc.queryForObject("SELECT status FROM reporting_async_task WHERE id=?",
        String.class, run.getTaskId())).isEqualTo("SUCCEEDED");
    assertThat(jdbc.queryForObject("SELECT attempt_count FROM reporting_async_task WHERE id=?",
        Integer.class, run.getTaskId())).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT status FROM search_index_rebuild_run WHERE id=?",
        String.class, run.getId())).isEqualTo("SUCCEEDED");
    assertThat(jdbc.queryForObject("SELECT target_index FROM search_index_rebuild_run WHERE id=?",
        String.class, run.getId())).isEqualTo(target);
  }

  private void assertAlias(HttpClient control, Set<String> allowed, String target) throws Exception {
    var aliases = json.readTree(request(control, allowed, "GET", ALIAS + "/_alias", 200));
    assertThat(aliases.size()).isOne();
    assertThat(aliases.path(target).path("aliases").path(ALIAS).path("is_write_index").asBoolean()).isTrue();
  }

  private String request(HttpClient control, Set<String> allowed, String method, String path, Integer... expected)
      throws Exception {
    return request(control, allowed, method, path, "", expected);
  }

  private String request(HttpClient control, Set<String> allowed, String method, String path, String body, Integer... expected)
      throws Exception {
    assertThat(allowed).contains(path.split("/", 2)[0]);
    String base = System.getenv("HR_ES_URL");
    var request = HttpRequest.newBuilder(URI.create(base.endsWith("/") ? base : base + "/").resolve(path))
        .timeout(Duration.ofSeconds(15)).method(method, HttpRequest.BodyPublishers.ofString(body));
    if (!body.isEmpty()) request.header("Content-Type", "application/json");
    String apiKey = System.getenv("HR_ES_API_KEY");
    String username = System.getenv("HR_ES_USERNAME");
    if (apiKey != null && !apiKey.isBlank()) request.header("Authorization", "ApiKey " + apiKey);
    else if (username != null && !username.isBlank()) request.header("Authorization", "Basic "
        + Base64.getEncoder().encodeToString((username + ":" + System.getenv("HR_ES_PASSWORD"))
            .getBytes(StandardCharsets.UTF_8)));
    var pending = control.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    try {
      var response = pending.get(20, TimeUnit.SECONDS);
      assertThat(response.statusCode()).as("隔离搜索资源控制请求").isIn(List.of(expected));
      return response.body();
    } finally {
      if (!pending.isDone()) pending.cancel(true);
    }
  }

  private RebuildRun enqueue() {
    String userId = jdbc.queryForObject("SELECT id FROM identity_user_account WHERE normalized_email=?",
        String.class, "search-lease-it@example.com");
    String departmentId = jdbc.queryForObject("SELECT home_department_id FROM identity_user_account WHERE id=?",
        String.class, userId);
    var run = new RebuildRun();
    run.setId(ids.newId());
    run.setTaskId(ids.newId());
    ownedRuns.add(run);
    run.setTargetIndex("resume-facts-v" + run.getId().toLowerCase(Locale.ROOT));
    run.setStatus("QUEUED");
    run.setRequestedByUserId(userId);
    run.setCreateId(userId);
    run.setUpdateId(userId);
    run.setCreateTime(Instant.now());
    run.setUpdateTime(run.getCreateTime());
    tasks.enqueue(new AsyncTaskPort.EnqueueTask(run.getTaskId(), SearchConstants.REBUILD_TASK_TYPE,
        "SEARCH_INDEX_REBUILD", run.getId(), userId, userId, departmentId, "talent.read", "recruitment.manage",
        "search-lease-" + run.getId(), "a".repeat(64), 2, run.getCreateTime()));
    assertThat(rebuilds.save(run)).isTrue();
    return run;
  }
}
