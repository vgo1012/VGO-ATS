package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchProjectionAssembler;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.IndexPage;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource.ProjectionPage;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.CurrentFactContact;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class SearchRebuildTransactionTest {
  private static final Instant NOW = Instant.parse("2026-09-07T10:00:00Z");
  private final SearchProjectionApplicationManager manager = new SearchProjectionApplicationManager();
  private final RebuildRunService rebuilds = mock(RebuildRunService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final SearchIndexPort index = mock(SearchIndexPort.class);
  private final SearchIndexAuthorityApplicationManager authority = mock(SearchIndexAuthorityApplicationManager.class);
  private final ProjectionCheckpointService checkpoints = mock(ProjectionCheckpointService.class);
  private final StateReplayCheckpointService stateCheckpoints = mock(StateReplayCheckpointService.class);
  private final ResumeFactProjectionSource source = mock(ResumeFactProjectionSource.class);
  private final TaskSnapshot task = mock(TaskSnapshot.class);
  private final AtomicReference<String> taskState = new AtomicReference<>("RUNNING");
  private final AtomicReference<String> runState = new AtomicReference<>("RUNNING");
  private final AtomicReference<SearchIndexTarget> approvedState =
      new AtomicReference<>(new SearchIndexTarget("resume-facts-vapproved", 0));
  private final SnapshotTransactions transactions = new SnapshotTransactions(taskState, runState, approvedState);
  private boolean acceptFinish = true;
  private String failureOutcome = "FAILED";

  @BeforeEach
  void setUp() {
    RebuildRun run = new RebuildRun();
    run.setId("run-1");
    run.setTargetIndex("resume-facts-v1");
    when(task.id()).thenReturn("task-1");
    when(task.attemptCount()).thenReturn(1);
    when(rebuilds.findByTaskId("task-1")).thenReturn(run);
    when(rebuilds.start(eq("run-1"), anyString(), eq(SYSTEM_ACTOR_ID), eq(NOW))).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    when(tasks.renewLease(task)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return true;
    });
    when(rebuilds.finish(eq("run-1"), any(), anyLong(), any(), eq(SYSTEM_ACTOR_ID), eq(NOW)))
        .thenAnswer(call -> {
          if (acceptFinish) runState.set(call.getArgument(1, SearchRebuildStatus.class).getCode());
          return acceptFinish;
        });
    when(rebuilds.finishApproved(eq("run-1"), eq("task-1"), anyString(), anyLong(), eq(SYSTEM_ACTOR_ID), eq(NOW)))
        .thenAnswer(call -> {
          if (acceptFinish) runState.set("SUCCEEDED");
          return acceptFinish;
        });
    when(authority.currentTarget()).thenAnswer(call -> approvedState.get());
    when(authority.rebuildBase()).thenAnswer(call -> approvedState.get());
    when(checkpoints.lockApprovedIndex()).thenAnswer(call -> approvedState.get());
    when(checkpoints.approveIndex(anyString(), anyLong(), anyString(), any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      SearchIndexTarget old = approvedState.get();
      long expected = call.getArgument(1, Long.class);
      return old.generation() == expected && approvedState.compareAndSet(old,
          new SearchIndexTarget(call.getArgument(0), expected + 1));
    });
    when(checkpoints.resetForApprovedIndex(anyString(), any(), anyString(), any())).thenReturn(true);
    when(stateCheckpoints.resetForApprovedIndex(anyString(), anyString(), any())).thenReturn(true);
    when(source.currentFactsAfter(any(), anyString(), anyInt()))
        .thenReturn(new ProjectionPage(List.of(), false));
    when(tasks.succeed(task, "run-1")).thenAnswer(call -> terminalTask("SUCCEEDED"));
    when(tasks.cancelled(task)).thenAnswer(call -> terminalTask("CANCELLED"));
    when(tasks.fail(task, "SEARCH_REBUILD_FAILED", "搜索索引重建失败，可重试", true))
        .thenAnswer(call -> terminalTask(failureOutcome));
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return null;
    }).when(index).prepareRebuild(any(SearchRebuildAttempt.class));
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return null;
    }).when(index).activateRebuild(any(SearchRebuildAttempt.class));
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "rebuilds", rebuilds);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "authority", authority);
    ReflectionTestUtils.setField(manager, "resources", mock(SearchIndexResourceApplicationManager.class));
    ReflectionTestUtils.setField(manager, "checkpoints", checkpoints);
    ReflectionTestUtils.setField(manager, "stateCheckpoints", stateCheckpoints);
    ReflectionTestUtils.setField(manager, "source", source);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
  }

  @ParameterizedTest
  @CsvSource({"SUCCEEDED,true", "CANCELLED,true", "FAILED,true",
      "SUCCEEDED,false", "CANCELLED,false", "FAILED,false"})
  void terminalWritesCommitTogetherOrRollbackTogether(String outcome, boolean saved) {
    prepareOutcome(outcome);
    acceptFinish = saved;

    if (saved) {
      manager.process(task);
      assertThat(taskState.get()).isEqualTo(outcome);
      assertThat(runState.get()).isEqualTo(outcome);
      assertThat(transactions.commits).isEqualTo(2);
    } else {
      assertThatThrownBy(() -> manager.process(task)).isInstanceOf(IllegalStateException.class);
      assertThat(taskState.get()).isEqualTo("RUNNING");
      assertThat(runState.get()).isEqualTo("RUNNING");
      assertThat(transactions.rollbacks).isPositive();
      assertThat(transactions.commits).isEqualTo(1);
      assertThat(approvedState.get()).isEqualTo(new SearchIndexTarget("resume-facts-vapproved", 0));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCEEDED", "CANCELLED", "FAILED"})
  void rejectedTaskAttemptCannotOverwriteRebuildTerminalState(String outcome) {
    prepareOutcome(outcome);
    PlatformException conflict = PlatformExceptions.conflict("TASK_STATE_CONFLICT", "领取批次已过期");
    doThrow(conflict).when(tasks).succeed(task, "run-1");
    doThrow(conflict).when(tasks).cancelled(task);
    doThrow(conflict).when(tasks).fail(task, "SEARCH_REBUILD_FAILED", "搜索索引重建失败，可重试", true);

    assertThatThrownBy(() -> manager.process(task)).isSameAs(conflict);

    assertThat(runState.get()).isEqualTo("RUNNING");
    assertThat(taskState.get()).isEqualTo("RUNNING");
    assertThat(transactions.commits).isEqualTo(1);
  }

  @Test
  void rebuildSuspendsAmbientTransactionAroundSearchCalls() throws Exception {
    var method = SearchProjectionApplicationManager.class.getMethod("process", TaskSnapshot.class);
    var attribute = new AnnotationTransactionAttributeSource()
        .getTransactionAttribute(method, SearchProjectionApplicationManager.class);
    assertThat(attribute).isNotNull();
    assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void creatingRebuildAndEnqueuingTaskCommitOrRollbackTogether(boolean saved) {
    taskState.set("ABSENT");
    runState.set("ABSENT");
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("admin-1");
    actor.setHomeDepartmentId("department-1");
    actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    when(tasks.enqueue(any())).thenAnswer(call -> {
      taskState.set("QUEUED");
      return task;
    });
    when(rebuilds.save(any(RebuildRun.class))).thenAnswer(call -> {
      if (saved) runState.set("QUEUED");
      return saved;
    });
    Iterator<String> generatedIds = List.of("run-1", "task-1").iterator();
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) generatedIds::next);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "assembler", new SearchProjectionAssembler());
    ProxyFactory proxyFactory = new ProxyFactory(manager);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    SearchProjectionApplicationManager proxy = (SearchProjectionApplicationManager) proxyFactory.getProxy();

    if (saved) {
      var response = proxy.createRebuild("create-rebuild-1");
      assertThat(response.getStatus()).isEqualTo("QUEUED");
      assertThat(response.getId()).isEqualTo("run-1");
      assertThat(response.getTaskId()).isEqualTo("task-1");
      assertThat(taskState.get()).isEqualTo("QUEUED");
      assertThat(runState.get()).isEqualTo("QUEUED");
      assertThat(transactions.commits).isEqualTo(1);
    } else {
      assertThatThrownBy(() -> proxy.createRebuild("create-rebuild-1"))
          .isInstanceOf(IllegalStateException.class);
      assertThat(taskState.get()).isEqualTo("ABSENT");
      assertThat(runState.get()).isEqualTo("ABSENT");
      assertThat(transactions.rollbacks).isEqualTo(1);
      assertThat(transactions.commits).isZero();
    }
  }

  @Test
  void rebuildsCreatedAtTheSameInstantUseDistinctValidIndexNames() {
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId("admin-1");
    actor.setHomeDepartmentId("department-1");
    actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    TaskSnapshot secondTask = mock(TaskSnapshot.class);
    when(secondTask.id()).thenReturn("task-2");
    when(tasks.enqueue(any())).thenReturn(task, secondTask);
    when(rebuilds.save(any(RebuildRun.class))).thenReturn(true);
    Iterator<String> generatedIds = List.of(
        "01J" + "A".repeat(23), "task-1", "01J" + "B".repeat(23), "task-2").iterator();
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) generatedIds::next);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "assembler", new SearchProjectionAssembler());

    var first = manager.createRebuild("create-rebuild-1");
    var second = manager.createRebuild("create-rebuild-2");

    assertThat(first.getCreateTime()).isEqualTo(second.getCreateTime());
    assertThat(first.getId()).isNotEqualTo(second.getId());
    assertThat(first.getTargetIndex()).isNotEqualTo(second.getTargetIndex());
    assertThat(List.of(first.getTargetIndex(), second.getTargetIndex())).allSatisfy(name ->
        assertThat(name).startsWith("resume-facts-v").matches("[a-z0-9][a-z0-9._-]{0,118}"));
  }

  @Test
  void cancellationWinningFailureKeepsBothRecordsCancelled() {
    prepareOutcome("FAILED");
    failureOutcome = "CANCELLED";

    manager.process(task);

    assertThat(taskState.get()).isEqualTo("CANCELLED");
    assertThat(runState.get()).isEqualTo("CANCELLED");
    assertThat(transactions.commits).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CANCELLED", "FAILED"})
  void lateOldActivationCannotChangeBusinessSearchAfterTheTaskIsTerminal(String terminal) {
    String approvedIndex = "resume-facts-vapproved";
    AtomicReference<String> remoteAlias = new AtomicReference<>(approvedIndex);
    SearchDocument approvedDocument = new SearchDocument();
    approvedDocument.setCandidateId("candidate-1");
    approvedDocument.setResumeVersionId("resume-1");
    approvedDocument.setFactSnapshotId("snapshot-1");
    var criteria = new SearchCriteria();
    var library = mock(CandidateLibraryQueryService.class);
    var facts = mock(ResumeFactQueryService.class);
    when(library.eligibleCandidateIds(any())).thenReturn(Set.of("candidate-1"));
    when(facts.currentFactContacts(any())).thenReturn(Map.of(
        "resume-1", new CurrentFactContact("snapshot-1", null, null)));
    when(index.search(any(SearchIndexTarget.class), eq(criteria), any(), anyInt())).thenAnswer(call -> new IndexPage(
        call.getArgument(0, SearchIndexTarget.class).indexName().equals(approvedIndex)
            ? List.of(approvedDocument) : List.of(), null, false));
    when(index.activeIndex()).thenAnswer(call -> remoteAlias.get());
    var reader = new TalentSearchApplicationManager();
    ReflectionTestUtils.setField(reader, "search", index);
    ReflectionTestUtils.setField(reader, "authority", authority);
    ReflectionTestUtils.setField(reader, "library", library);
    ReflectionTestUtils.setField(reader, "facts", facts);
    ReflectionTestUtils.setField(reader, "authorization", mock(AuthorizationService.class));
    assertThat(reader.count(criteria, null).getCount()).isEqualTo(1);

    PlatformException expired = PlatformExceptions.conflict("TASK_STATE_CONFLICT", "领取批次已终结");
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      // 模拟另一实例已提交取消/耗尽租约终态，随后原 HTTP alias 请求才完成。
      taskState.set(terminal);
      runState.set(terminal);
      remoteAlias.set(call.getArgument(0, SearchRebuildAttempt.class).indexName());
      return null;
    }).when(index).activateRebuild(any(SearchRebuildAttempt.class));
    doThrow(expired).when(tasks).succeed(task, "run-1");
    doThrow(expired).when(tasks).fail(task, "SEARCH_REBUILD_FAILED", "搜索索引重建失败，可重试", true);

    assertThatThrownBy(() -> manager.process(task)).isSameAs(expired);
    assertThat(taskState.get()).isEqualTo(terminal);
    assertThat(runState.get()).isEqualTo(terminal);
    assertThat(remoteAlias.get()).isNotEqualTo(approvedIndex);
    assertThat(reader.count(criteria, null).getCount())
        .as("任务终结后迟到 alias 只能改变未批准的准备状态，公开查询仍使用原批准索引")
        .isEqualTo(1);
  }

  @Test
  void aConcurrentApprovalCannotBeOverwrittenByAnEarlierPreparedRebuild() {
    var winner = new SearchIndexTarget("resume-facts-vother-approved", 1);
    doAnswer(call -> { approvedState.set(winner); return null; })
        .when(index).activateRebuild(any(SearchRebuildAttempt.class));
    manager.process(task);
    assertThat(approvedState.get()).isEqualTo(winner);
    assertThat(taskState.get()).isEqualTo("FAILED");
    assertThat(runState.get()).isEqualTo("FAILED");
    verify(checkpoints, never()).approveIndex(anyString(), anyLong(), anyString(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"facts", "state"})
  void checkpointResetFailureRollsBackTheApprovalAndSuccessBeforeRecordingFailure(String checkpoint) {
    if (checkpoint.equals("facts")) {
      when(checkpoints.resetForApprovedIndex(anyString(), any(), anyString(), any())).thenReturn(false);
    } else {
      when(stateCheckpoints.resetForApprovedIndex(anyString(), anyString(), any())).thenReturn(false);
    }
    manager.process(task);
    assertThat(approvedState.get()).isEqualTo(new SearchIndexTarget("resume-facts-vapproved", 0));
    assertThat(taskState.get()).isEqualTo("FAILED");
    assertThat(runState.get()).isEqualTo("FAILED");
    assertThat(transactions.rollbacks).isEqualTo(1);
  }

  private void prepareOutcome(String outcome) {
    if (outcome.equals("CANCELLED")) when(tasks.shouldStopProcessing(task)).thenReturn(true);
    if (outcome.equals("FAILED")) {
      doThrow(new IllegalStateException("index unavailable")).when(index).beginRebuild(any(SearchRebuildAttempt.class));
    }
  }

  private TaskSnapshot terminalTask(String status) {
    taskState.set(status);
    TaskSnapshot result = mock(TaskSnapshot.class);
    when(result.status()).thenReturn(status);
    return result;
  }

  private static final class SnapshotTransactions extends AbstractPlatformTransactionManager {
    private final AtomicReference<String> taskState;
    private final AtomicReference<String> runState;
    private final AtomicReference<SearchIndexTarget> approvedState;
    private String previousTask;
    private String previousRun;
    private SearchIndexTarget previousApproved;
    private int commits;
    private int rollbacks;

    private SnapshotTransactions(AtomicReference<String> taskState, AtomicReference<String> runState,
        AtomicReference<SearchIndexTarget> approvedState) {
      this.taskState = taskState;
      this.runState = runState;
      this.approvedState = approvedState;
    }

    @Override
    protected Object doGetTransaction() { return new Object(); }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      previousTask = taskState.get();
      previousRun = runState.get();
      previousApproved = approvedState.get();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) { commits++; }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      taskState.set(previousTask);
      runState.set(previousRun);
      approvedState.set(previousApproved);
      rollbacks++;
    }
  }
}
