package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_APPROVED_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.config.SearchIndexRetentionProperties;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SearchIndexAuthorityApplicationManagerTest {
  private final SearchIndexAuthorityApplicationManager manager = new SearchIndexAuthorityApplicationManager();
  private final ProjectionCheckpointService checkpoints = mock(ProjectionCheckpointService.class);
  private final StateReplayCheckpointService state = mock(StateReplayCheckpointService.class);
  private final RebuildRunService rebuilds = mock(RebuildRunService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final SearchIndexPort index = mock(SearchIndexPort.class);
  private final AtomicReference<SearchIndexTarget> stored = new AtomicReference<>();
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "checkpoints", checkpoints);
    ReflectionTestUtils.setField(manager, "stateCheckpoints", state);
    ReflectionTestUtils.setField(manager, "rebuilds", rebuilds);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    when(checkpoints.approvedIndex()).thenAnswer(call -> stored.get());
    when(checkpoints.lockApprovedIndex()).thenAnswer(call -> stored.get());
    var resources = mock(SearchIndexResourceApplicationManager.class);
    when(resources.pin(any(SearchIndexTarget.class), anyLong())).thenAnswer(call ->
        call.getArgument(0, SearchIndexTarget.class).withDeadline(call.getArgument(1, Long.class)));
    ReflectionTestUtils.setField(manager, "resources", resources);
    ReflectionTestUtils.setField(manager, "retention", new SearchIndexRetentionProperties());
    doAnswer(call -> {
      var row = call.getArgument(0, ProjectionCheckpoint.class);
      if (RESUME_FACTS_APPROVED_INDEX.equals(row.getProjectionName())) {
        stored.compareAndSet(null, new SearchIndexTarget(row.getIndexName(), row.getVersion()));
      }
      return null;
    }).when(checkpoints).initialize(any(ProjectionCheckpoint.class));
    when(index.activeIndex()).thenReturn("resume-facts-vcandidate");
  }

  @Test
  void approvalUsesAnIndependentCurrentReadTransactionThatAllowsLocks() {
    stored.set(new SearchIndexTarget("resume-facts-vapproved", 7));
    var target = manager.currentTarget();
    assertThat(target.deadlineNanos()).isLessThan(Long.MAX_VALUE);
    verify(transactions, times(2)).getTransaction(argThat(definition ->
        definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
            && !definition.isReadOnly()));
  }

  @Test
  void anExistingApprovalNeverFollowsTheAliasAgain() {
    var approved = new SearchIndexTarget("resume-facts-vapproved", 7);
    stored.set(approved);
    assertThat(manager.currentTarget()).isEqualTo(approved);
    assertThat(manager.currentTarget()).isEqualTo(approved);
    verifyNoInteractions(index, rebuilds, tasks);
  }

  @Test
  void anExistingUnboundRowDoesNotSilentlyAdoptALaterAlias() {
    stored.set(new SearchIndexTarget(null, 0));
    assertUnavailable();
    verifyNoInteractions(index, rebuilds, tasks);
  }

  @Test
  void unknownOldBootstrapFailsClosedAndRemainsRecoverableByRebuild() {
    assertUnavailable();
    assertThat(stored.get()).isEqualTo(new SearchIndexTarget(null, 0));
    when(index.isManagedBootstrap("resume-facts-vcandidate")).thenReturn(true);
    assertUnavailable();
    assertThat(manager.rebuildBase()).isEqualTo(new SearchIndexTarget(null, 0));
    verify(index).activeIndex();
    verify(state).initialize(any());
  }

  @Test
  void aManagedNewBootstrapIsApprovedOnce() {
    when(index.isManagedBootstrap("resume-facts-vcandidate")).thenReturn(true);
    assertThat(manager.currentTarget()).isEqualTo(new SearchIndexTarget("resume-facts-vcandidate", 0));
    verify(rebuilds, never()).findSucceededTarget(any());
  }

  @Test
  void successfulRunAndItsExactSuccessfulTaskMaySeedTheInitialPointer() {
    when(rebuilds.findSucceededTarget("resume-facts-vcandidate")).thenReturn(run("SUCCEEDED"));
    TaskSnapshot successfulTask = task("SUCCEEDED", "run-1");
    when(tasks.get("task-1")).thenReturn(successfulTask);
    assertThat(manager.currentTarget()).isEqualTo(new SearchIndexTarget("resume-facts-vcandidate", 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"QUEUED", "RUNNING", "FAILED", "CANCELLED"})
  void nonSuccessfulAttemptMustNeverBecomeAnInitialApproval(String status) {
    when(rebuilds.findSucceededTarget("resume-facts-vcandidate")).thenReturn(run(status));
    assertUnavailable();
    verifyNoInteractions(tasks);
  }

  @ParameterizedTest
  @ValueSource(strings = {"FAILED", "CANCELLED", "RUNNING"})
  void aSuccessfulBusinessRecordCannotOverrideANonSuccessfulSharedTask(String status) {
    when(rebuilds.findSucceededTarget("resume-facts-vcandidate")).thenReturn(run("SUCCEEDED"));
    TaskSnapshot boundTask = task(status, "run-1");
    when(tasks.get("task-1")).thenReturn(boundTask);
    assertUnavailable();
  }

  @Test
  void wrongResourceBindingIsRejectedDuringInitialImport() {
    when(rebuilds.findSucceededTarget("resume-facts-vcandidate")).thenReturn(run("SUCCEEDED"));
    TaskSnapshot otherTask = task("SUCCEEDED", "another-run");
    when(tasks.get("task-1")).thenReturn(otherTask);
    assertUnavailable();
  }

  @Test
  void aMissingHistoricalTaskCannotAuthorizeItsSuccessfulBusinessRecord() {
    when(rebuilds.findSucceededTarget("resume-facts-vcandidate")).thenReturn(run("SUCCEEDED"));
    when(tasks.get("task-1")).thenThrow(
        new PlatformException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "synthetic missing task", false));
    assertUnavailable();
    assertThat(stored.get()).isEqualTo(new SearchIndexTarget(null, 0));
  }

  @Test
  void aConcurrentInitializationWinnerIsRereadInsteadOfOverwritten() {
    when(index.isManagedBootstrap("resume-facts-vcandidate")).thenAnswer(call -> {
      stored.set(new SearchIndexTarget("resume-facts-vwinner", 3));
      return true;
    });
    assertThat(manager.currentTarget()).isEqualTo(new SearchIndexTarget("resume-facts-vwinner", 3));
  }

  private void assertUnavailable() {
    assertThatThrownBy(manager::currentTarget).isInstanceOf(PlatformException.class)
        .satisfies(failure -> assertThat(((PlatformException) failure).code()).isEqualTo("SEARCH_INDEX_NOT_APPROVED"));
  }

  private RebuildRun run(String status) {
    var run = new RebuildRun();
    run.setId("run-1"); run.setTaskId("task-1"); run.setStatus(status);
    run.setTargetIndex("resume-facts-vcandidate");
    return run;
  }

  private TaskSnapshot task(String status, String resource) {
    var task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn("task-1"); when(task.taskType()).thenReturn("SEARCH_INDEX_REBUILD");
    when(task.resourceType()).thenReturn("SEARCH_INDEX_REBUILD");
    when(task.resourceId()).thenReturn(resource); when(task.status()).thenReturn(status);
    return task;
  }
}
