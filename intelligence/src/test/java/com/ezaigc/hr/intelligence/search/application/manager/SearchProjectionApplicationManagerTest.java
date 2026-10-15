package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.PROJECTION_EPOCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.ProjectionCheckpoint;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource.ProjectionItem;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource.ProjectionPage;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SearchProjectionApplicationManagerTest {

  @Test
  void projectionBatchDoesNotHoldDatabaseTransactionAroundSearchIndexIo() throws Exception {
    var method = SearchProjectionApplicationManager.class.getMethod("projectNext", int.class);
    var transactionAttributes = new AnnotationTransactionAttributeSource();

    assertThat(
            transactionAttributes.getTransactionAttribute(
                method, SearchProjectionApplicationManager.class))
        .as("搜索索引可能是远端 HTTP 服务，批量写入期间不得占用数据库事务连接")
        .isNull();
  }

  @Test
  void missingCheckpointAfterInitializationIsRecreated() {
    ProjectionCheckpoint checkpoint = checkpoint(PROJECTION_EPOCH);
    ResumeFactProjectionSource source = mock(ResumeFactProjectionSource.class);
    SearchDocumentFactory factory = mock(SearchDocumentFactory.class);
    SearchIndexPort index = mock(SearchIndexPort.class);
    ProjectionCheckpointService checkpoints = mock(ProjectionCheckpointService.class);
    when(checkpoints.checkpoint()).thenReturn(null, checkpoint);
    when(source.currentFactsAfter(PROJECTION_EPOCH, checkpoint.getLastSnapshotId(), 20))
        .thenReturn(new ProjectionPage(List.of(), false));

    assertThat(manager(source, factory, index, checkpoints).projectNext(20)).isZero();

    verify(checkpoints).initialize(any(ProjectionCheckpoint.class));
  }

  @Test
  void indexFailureLeavesCheckpointForIdempotentRetry() {
    Instant cursorTime = Instant.parse("2026-09-02T00:00:00Z");
    Instant itemTime = cursorTime.plusSeconds(1);
    ProjectionCheckpoint checkpoint = checkpoint(cursorTime);
    ResumeFactSnapshotData facts = mock(ResumeFactSnapshotData.class);
    when(facts.getSnapshotId()).thenReturn("S".repeat(26));
    SearchDocument document = mock(SearchDocument.class);
    ResumeFactProjectionSource source = mock(ResumeFactProjectionSource.class);
    SearchDocumentFactory factory = mock(SearchDocumentFactory.class);
    SearchIndexPort index = mock(SearchIndexPort.class);
    ProjectionCheckpointService checkpoints = mock(ProjectionCheckpointService.class);
    when(checkpoints.checkpoint()).thenReturn(checkpoint);
    var item = new ProjectionItem(facts, itemTime, "MANUAL_UPLOAD", 1);
    when(source.currentFactsAfter(cursorTime, checkpoint.getLastSnapshotId(), 20))
        .thenReturn(new ProjectionPage(List.of(item), false));
    when(factory.create(item)).thenReturn(document);
    RuntimeException failure = new RuntimeException("index unavailable");
    doThrow(failure).when(index).upsertBatch(new SearchIndexTarget("test-index", 0), List.of(document));

    assertThatThrownBy(() -> manager(source, factory, index, checkpoints).projectNext(20))
        .isSameAs(failure);

    verify(checkpoints, never())
        .advance(any(), anyString(), anyInt(), anyLong(), anyString(), any());
  }

  @Test
  void advancesCheckpointOnlyAfterEveryIndexWriteSucceeds() {
    Instant cursorTime = Instant.parse("2026-09-02T00:00:00Z");
    Instant itemTime = cursorTime.plusSeconds(1);
    ProjectionCheckpoint checkpoint = checkpoint(cursorTime);
    ResumeFactSnapshotData facts = mock(ResumeFactSnapshotData.class);
    when(facts.getSnapshotId()).thenReturn("S".repeat(26));
    SearchDocument document = mock(SearchDocument.class);
    ResumeFactProjectionSource source = mock(ResumeFactProjectionSource.class);
    SearchDocumentFactory factory = mock(SearchDocumentFactory.class);
    SearchIndexPort index = mock(SearchIndexPort.class);
    ProjectionCheckpointService checkpoints = mock(ProjectionCheckpointService.class);
    when(checkpoints.checkpoint()).thenReturn(checkpoint);
    var item = new ProjectionItem(facts, itemTime, "MANUAL_UPLOAD", 1);
    when(source.currentFactsAfter(cursorTime, checkpoint.getLastSnapshotId(), 20))
        .thenReturn(new ProjectionPage(List.of(item), false));
    when(factory.create(item)).thenReturn(document);
    when(checkpoints.advance(
            itemTime,
            facts.getSnapshotId(),
            1,
            checkpoint.getVersion(),
            SYSTEM_ACTOR_ID,
            itemTime))
        .thenReturn(true);

    assertThat(manager(source, factory, index, checkpoints).projectNext(20)).isOne();

    var ordered = inOrder(index, checkpoints);
    ordered.verify(index).upsertBatch(new SearchIndexTarget("test-index", 0), List.of(document));
    ordered
        .verify(checkpoints)
        .advance(
            itemTime,
            facts.getSnapshotId(),
            1,
            checkpoint.getVersion(),
            SYSTEM_ACTOR_ID,
            itemTime);
  }

  @Test
  void rebuildIndexesOneSourcePageWithOneBatchCall() {
    SearchProjectionApplicationManager manager = new SearchProjectionApplicationManager();
    SearchIndexPort index = mock(SearchIndexPort.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    RebuildRunService rebuilds = mock(RebuildRunService.class);
    ResumeFactProjectionSource source = mock(ResumeFactProjectionSource.class);
    SearchDocumentFactory factory = mock(SearchDocumentFactory.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    RebuildRun run = new RebuildRun();
    run.setId("run-1");
    run.setTargetIndex("resume-facts-v1");
    ResumeFactSnapshotData firstFacts = mock(ResumeFactSnapshotData.class);
    ResumeFactSnapshotData secondFacts = mock(ResumeFactSnapshotData.class);
    when(firstFacts.getSnapshotId()).thenReturn("A".repeat(26));
    when(secondFacts.getSnapshotId()).thenReturn("B".repeat(26));
    Instant firstTime = Instant.parse("2026-09-02T00:00:01Z");
    Instant secondTime = firstTime.plusSeconds(1);
    ProjectionPage page =
        new ProjectionPage(
            List.of(
                new ProjectionItem(firstFacts, firstTime, "MANUAL_UPLOAD", 1),
                new ProjectionItem(secondFacts, secondTime, "MANUAL_UPLOAD", 1)),
            false);
    SearchDocument firstDocument = mock(SearchDocument.class);
    SearchDocument secondDocument = mock(SearchDocument.class);
    when(task.id()).thenReturn("task-1");
    when(rebuilds.findByTaskId("task-1")).thenReturn(run);
    when(source.currentFactsAfter(PROJECTION_EPOCH, MIN_ID, 200)).thenReturn(page);
    when(factory.create(page.items().getFirst())).thenReturn(firstDocument);
    when(factory.create(page.items().getLast())).thenReturn(secondDocument);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "rebuilds", rebuilds);
    ReflectionTestUtils.setField(manager, "source", source);
    ReflectionTestUtils.setField(manager, "factory", factory);
    configureTerminalPersistence(manager, rebuilds, tasks, task);
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(secondTime.plusSeconds(1), ZoneOffset.UTC));

    manager.process(task);

    verify(index)
        .upsertBatch(new SearchIndexTarget("resume-facts-vrun-1-attempt-1", 0), List.of(firstDocument, secondDocument));
    verify(index).activateRebuild(new SearchRebuildAttempt("run-1", 1));
    verify(tasks).succeed(task, "run-1");
  }

  @Test
  void rebuildFailureLogsCorrelationAndStackWithoutPrivateExceptionMessages() {
    SearchProjectionApplicationManager manager = new SearchProjectionApplicationManager();
    SearchIndexPort index = mock(SearchIndexPort.class);
    AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    RebuildRunService rebuilds = mock(RebuildRunService.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    RebuildRun run = new RebuildRun();
    run.setId("run-1");
    run.setTargetIndex("resume-facts-v1");
    IllegalStateException failure = new IllegalStateException(
        "audit-person@example.invalid", new IllegalArgumentException("15500000000"));
    failure.addSuppressed(new RuntimeException("test-secret-marker"));
    when(task.id()).thenReturn("task-1");
    when(rebuilds.findByTaskId("task-1")).thenReturn(run);
    doThrow(failure).when(index).beginRebuild(any(SearchRebuildAttempt.class));
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "rebuilds", rebuilds);
    configureTerminalPersistence(manager, rebuilds, tasks, task);
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(Instant.parse("2026-09-02T00:00:01Z"), ZoneOffset.UTC));
    Logger logger = (Logger) LoggerFactory.getLogger(SearchProjectionApplicationManager.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      manager.process(task);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage()).contains("task-1", "run-1");
              assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
                  .contains("java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
                      "SearchProjectionApplicationManagerTest.java")
                  .doesNotContain("audit-person@example.invalid", "15500000000", "test-secret-marker");
            });
    verify(tasks).fail(task, "SEARCH_REBUILD_FAILED", "搜索索引重建失败，可重试", true);
    verify(index, never()).activateRebuild(any(SearchRebuildAttempt.class));
    assertThat(failure.getMessage()).isEqualTo("audit-person@example.invalid");
  }

  private static void configureTerminalPersistence(SearchProjectionApplicationManager manager,
      RebuildRunService rebuilds, AsyncTaskPort tasks, TaskSnapshot task) {
    ReflectionTestUtils.setField(manager, "resources", mock(SearchIndexResourceApplicationManager.class));
    ProjectionCheckpointService checkpoints = mock(ProjectionCheckpointService.class);
    configureApproval(manager, checkpoints);
    ReflectionTestUtils.setField(manager, "checkpoints", checkpoints);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactions));
    when(task.attemptCount()).thenReturn(1);
    when(tasks.renewLease(task)).thenReturn(true);
    when(rebuilds.start(anyString(), anyString(), anyString(), any())).thenReturn(true);
    when(rebuilds.finish(anyString(), any(SearchRebuildStatus.class), anyLong(), any(), anyString(), any()))
        .thenReturn(true);
    when(rebuilds.finishApproved(anyString(), anyString(), anyString(), anyLong(), anyString(), any()))
        .thenReturn(true);
    TaskSnapshot failed = mock(TaskSnapshot.class);
    when(failed.status()).thenReturn("FAILED");
    when(tasks.fail(task, "SEARCH_REBUILD_FAILED", "搜索索引重建失败，可重试", true)).thenReturn(failed);
  }

  private static ProjectionCheckpoint checkpoint(Instant cursorTime) {
    ProjectionCheckpoint checkpoint = new ProjectionCheckpoint();
    checkpoint.setIndexName("test-index");
    checkpoint.setLastCreatedAt(cursorTime);
    checkpoint.setLastSnapshotId("C".repeat(26));
    checkpoint.setVersion(7);
    return checkpoint;
  }

  private static SearchProjectionApplicationManager manager(
      ResumeFactProjectionSource source,
      SearchDocumentFactory factory,
      SearchIndexPort index,
      ProjectionCheckpointService checkpoints) {
    SearchProjectionApplicationManager manager = new SearchProjectionApplicationManager();
    configureApproval(manager, checkpoints);
    ReflectionTestUtils.setField(manager, "initialized", true);
    ReflectionTestUtils.setField(manager, "source", source);
    ReflectionTestUtils.setField(manager, "factory", factory);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "checkpoints", checkpoints);
    ReflectionTestUtils.setField(
        manager, "clock", Clock.fixed(Instant.parse("2026-09-02T00:00:01Z"), ZoneOffset.UTC));
    return manager;
  }

  private static void configureApproval(SearchProjectionApplicationManager manager,
      ProjectionCheckpointService checkpoints) {
    var authority = mock(SearchIndexAuthorityApplicationManager.class);
    var target = new SearchIndexTarget("test-index", 0);
    when(authority.currentTarget()).thenReturn(target);
    when(authority.rebuildBase()).thenReturn(target);
    when(checkpoints.lockApprovedIndex()).thenReturn(target);
    when(checkpoints.approveIndex(anyString(), anyLong(), anyString(), any())).thenReturn(true);
    when(checkpoints.resetForApprovedIndex(anyString(), any(), anyString(), any())).thenReturn(true);
    var stateCheckpoints = mock(StateReplayCheckpointService.class);
    when(stateCheckpoints.resetForApprovedIndex(anyString(), anyString(), any())).thenReturn(true);
    ReflectionTestUtils.setField(manager, "authority", authority);
    ReflectionTestUtils.setField(manager, "resources", mock(SearchIndexResourceApplicationManager.class));
    ReflectionTestUtils.setField(manager, "stateCheckpoints", stateCheckpoints);
  }
}
