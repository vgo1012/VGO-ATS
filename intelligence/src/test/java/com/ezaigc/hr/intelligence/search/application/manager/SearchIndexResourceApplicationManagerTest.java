package com.ezaigc.hr.intelligence.search.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.version.ResourceVersion;
import tools.jackson.databind.ObjectMapper;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.config.SearchIndexRetentionProperties;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.domain.entity.SearchIndexResource;
import com.ezaigc.hr.intelligence.search.domain.request.SearchIndexReconciliationRequest;
import com.ezaigc.hr.intelligence.search.enums.SearchIndexResourceKind;
import com.ezaigc.hr.intelligence.search.enums.SearchIndexResourceStatus;
import com.ezaigc.hr.intelligence.search.service.ProjectionCheckpointService;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import com.ezaigc.hr.intelligence.search.service.SearchIndexResourceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SearchIndexResourceApplicationManagerTest {
  private final SearchIndexResourceApplicationManager manager = new SearchIndexResourceApplicationManager();
  private final SearchIndexResourceService resources = mock(SearchIndexResourceService.class);
  private final ProjectionCheckpointService checkpoints = mock(ProjectionCheckpointService.class);
  private final RebuildRunService rebuilds = mock(RebuildRunService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final SearchIndexPort index = mock(SearchIndexPort.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
  private final SearchIndexRetentionProperties policy = new SearchIndexRetentionProperties();
  private final Map<String, SearchIndexResource> stored = new LinkedHashMap<>();
  private final AtomicBoolean inTransaction = new AtomicBoolean();
  private final AtomicReference<SearchIndexTarget> approved = new AtomicReference<>(new SearchIndexTarget("approved", 3));
  private final SearchRebuildAttempt attempt = new SearchRebuildAttempt("run-1", 1);
  private TaskSnapshot claimed;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "resources", resources);
    ReflectionTestUtils.setField(manager, "checkpoints", checkpoints);
    ReflectionTestUtils.setField(manager, "rebuilds", rebuilds);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "policy", policy);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var ids = mock(PlatformIdGenerator.class);
    var sequence = new AtomicInteger();
    when(ids.newId()).thenAnswer(call -> "R" + String.format("%025d", sequence.incrementAndGet()));
    ReflectionTestUtils.setField(manager, "ids", ids);
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenAnswer(call -> {
      assertThat(inTransaction.compareAndSet(false, true)).isTrue();
      return new SimpleTransactionStatus();
    });
    doAnswer(call -> { inTransaction.set(false); return null; }).when(transactionManager).commit(any());
    doAnswer(call -> { inTransaction.set(false); return null; }).when(transactionManager).rollback(any());
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(transactionManager));
    when(index.resourceScope()).thenReturn("scope");
    when(checkpoints.lockApprovedIndex()).thenAnswer(call -> approved.get());
    when(resources.lock(anyString())).thenAnswer(call -> stored.get(call.getArgument(0, String.class)));
    when(resources.getById(anyString())).thenAnswer(call -> stored.values().stream()
        .filter(row -> row.getId().equals(call.getArgument(0))).findFirst().orElse(null));
    when(resources.save(any(SearchIndexResource.class))).thenAnswer(call -> {
      SearchIndexResource row = call.getArgument(0);
      assertThat(inTransaction).isTrue();
      stored.put(row.getIndexName(), row);
      return true;
    });
    when(resources.updateById(any(SearchIndexResource.class))).thenAnswer(call -> {
      assertThat(inTransaction).isTrue();
      SearchIndexResource row = call.getArgument(0);
      row.setVersion(row.getVersion() + 1);
      return true;
    });
    when(resources.liveCount()).thenAnswer(call -> stored.values().stream()
        .filter(row -> !SearchIndexResourceStatus.DELETED.matches(row.getStatus())).count());
    when(resources.unresolvedCount()).thenAnswer(call -> stored.values().stream()
        .filter(row -> SearchIndexResourceStatus.CREATING.matches(row.getStatus())
            || SearchIndexResourceStatus.CREATE_UNKNOWN.matches(row.getStatus())).count());
    when(resources.candidates(anyLong(), any(Integer.class))).thenAnswer(call -> List.copyOf(stored.values()));
    when(resources.newest(any(Integer.class))).thenReturn(List.of());
    when(resources.retiring(anyString(), anyLong(), anyString())).thenAnswer(call -> {
      assertThat(inTransaction).isTrue();
      var row = stored.values().stream().filter(value -> value.getId().equals(call.getArgument(0))).findFirst().orElseThrow();
      row.setStatus(SearchIndexResourceStatus.RETIRING.getCode());
      row.setVersion(row.getVersion() + 1);
      return true;
    });
    when(resources.pin(anyString(), anyLong())).thenReturn(true);
    claimed = task("RUNNING", false, 1);
    when(tasks.renewLease(any(TaskSnapshot.class))).thenReturn(true);
    RebuildRun run = run();
    when(rebuilds.getById("run-1")).thenReturn(run);
    when(rebuilds.currentForUpdate("run-1")).thenReturn(run);
    when(authorization.currentActor()).thenReturn(actor);
    when(actor.isSuperAdmin()).thenReturn(true);
    when(actor.getUserId()).thenReturn("admin");
  }

  @Test
  void registersCreatingBeforeRemoteIoAndAcknowledgesOnlyAfterItEndsOutsideTheTransaction() {
    doAnswer(call -> {
      assertThat(inTransaction).isFalse();
      assertThat(stored.get(attempt.indexName()).getStatus()).isEqualTo("CREATING");
      return null;
    }).when(index).createRebuildTarget(any(), anyString());
    manager.createRebuild(claimed, attempt);
    assertThat(stored.get(attempt.indexName()).getStatus()).isEqualTo("READY");
    assertThat(stored.get(attempt.indexName()).isManaged()).isTrue();
  }

  @Test
  void ambiguousCreateProtectsItsTargetAndBlocksFurtherCreationWithoutTrustingAHeadOrExpiredLease() {
    doThrow(new IllegalStateException("synthetic response lost")).when(index).createRebuildTarget(any(), anyString());
    assertThatThrownBy(() -> manager.createRebuild(claimed, attempt)).isInstanceOf(IllegalStateException.class);
    assertThat(stored.get(attempt.indexName()).getStatus()).isEqualTo("CREATE_UNKNOWN");
    var secondAttempt = new SearchRebuildAttempt("run-1", 2);
    RebuildRun run = run(); run.setTargetIndex(secondAttempt.indexName());
    when(rebuilds.getById("run-1")).thenReturn(run);
    TaskSnapshot second = task("RUNNING", false, 2);
    assertThatThrownBy(() -> manager.createRebuild(second, secondAttempt)).isInstanceOf(PlatformException.class)
        .extracting("code").isEqualTo("SEARCH_INDEX_CAPACITY_BLOCKED");
    verify(index, times(1)).createRebuildTarget(any(), anyString());
  }

  @Test
  void repeatingAKnownReadyAttemptNeverIssuesAnotherExplicitCreate() {
    manager.createRebuild(claimed, attempt);
    manager.createRebuild(claimed, attempt);
    verify(index, times(1)).createRebuildTarget(any(), anyString());
  }

  @Test
  void aStoppedAttemptCannotReserveOrCreateAnyTarget() {
    when(tasks.renewLease(claimed)).thenReturn(false);
    assertThatThrownBy(() -> manager.createRebuild(claimed, attempt)).isInstanceOf(PlatformException.class);
    assertThat(stored).isEmpty();
    verify(index, never()).createRebuildTarget(any(), anyString());
  }

  @Test
  void deleteFailuresRemainRetiringAndBlockCreationWhenLiveCapacityIsReached() {
    var old = ready("old");
    policy.setMaxLiveResources(3);
    ready("recent-one"); ready("recent-two");
    when(resources.newest(2)).thenReturn(List.of("recent-one", "recent-two"));
    doThrow(new IllegalStateException("synthetic delete failure")).when(index).retireIndex("old", old.getId());
    assertThat(manager.collect()).isZero();
    assertThat(old.getStatus()).isEqualTo("RETIRING");
    assertThatThrownBy(() -> manager.createRebuild(claimed, attempt)).isInstanceOf(PlatformException.class)
        .extracting("code").isEqualTo("SEARCH_INDEX_CAPACITY_BLOCKED");
    verify(index, never()).createRebuildTarget(any(), anyString());
  }

  @Test
  void retiringIntentSurvivesFailureAndRepeatsDeletionOutsideTheTransaction() {
    var old = ready("old");
    var calls = new AtomicInteger();
    doAnswer(call -> {
      assertThat(inTransaction).isFalse();
      assertThat(old.getStatus()).isEqualTo("RETIRING");
      assertThatThrownBy(() -> manager.pin(new SearchIndexTarget("old", 1), System.nanoTime() + 1000))
          .isInstanceOf(PlatformException.class);
      if (calls.getAndIncrement() == 0) throw new IllegalStateException("synthetic response lost");
      return null;
    }).when(index).retireIndex("old", old.getId());
    assertThat(manager.collect()).isZero();
    assertThat(manager.collect()).isEqualTo(1);
    assertThat(old.getStatus()).isEqualTo("DELETED");
  }

  @Test
  void oneFailedDeletionDoesNotStarveLaterTargetsAndAttemptsRemainBounded() {
    var failed = ready("failed"); ready("second"); ready("third");
    policy.setBatchSize(2);
    doThrow(new IllegalStateException("synthetic permanent failure")).when(index).retireIndex("failed", failed.getId());
    assertThat(manager.collect()).isEqualTo(1);
    assertThat(failed.getStatus()).isEqualTo("RETIRING");
    assertThat(stored.get("second").getStatus()).isEqualTo("DELETED");
    assertThat(stored.get("third").getStatus()).isEqualTo("READY");
    verify(index, times(2)).retireIndex(anyString(), anyString());
  }

  @Test
  void approvedAndRecentTargetsRemainProtectedEvenWhenSelectedByAStaleCandidateScan() {
    ready("approved"); ready("recent");
    when(resources.newest(2)).thenReturn(List.of("recent"));
    assertThat(manager.collect()).isZero();
    verify(index, never()).retireIndex(anyString(), anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"RUNNING", "QUEUED", "FAILED"})
  void unfinishedOrRetryableTasksProtectAllTheirHistoricalAttempts(String status) {
    var old = ready("old"); old.setTaskId("task-1"); old.setRunId("run-1");
    TaskSnapshot protectedTask = task(status, true, 3);
    when(tasks.get("task-1")).thenReturn(protectedTask);
    assertThat(manager.collect()).isZero();
    verify(index, never()).retireIndex(anyString(), anyString());
  }

  @Test
  void expiredPinCompareAndSetRejectsRetirementAndLimitsOneCollectionBatch() {
    ready("pinned");
    doReturn(false).when(resources).retiring(anyString(), anyLong(), anyString());
    assertThat(manager.collect()).isZero();
    verify(index, never()).retireIndex(anyString(), anyString());
  }

  @Test
  void manualResolutionRequiresConfirmationAndCurrentAdministratorPermission() {
    var unknown = ready("old"); unknown.setStatus("CREATE_UNKNOWN"); unknown.setManaged(false);
    var request = confirmation(unknown.getVersion()); request.setOriginalRequestTerminated(false);
    assertThatThrownBy(() -> manager.reconcile(unknown.getId(), request)).isInstanceOf(PlatformException.class);
    when(actor.isSuperAdmin()).thenReturn(false);
    assertThatThrownBy(() -> manager.getResource(unknown.getId())).isInstanceOf(PlatformException.class);
    verify(index, never()).adoptIndex(anyString(), anyString());
    assertThat(unknown.getReconciledAt()).isNull();
  }

  @Test
  void manualUnknownResolutionIsAuditedOnceAndNeverReopensTheCreationIntent() {
    var unknown = ready("old"); unknown.setStatus("CREATE_UNKNOWN"); unknown.setManaged(false);
    var request = confirmation(unknown.getVersion());
    when(index.adoptIndex("old", unknown.getId())).thenReturn(false);
    manager.reconcile(unknown.getId(), request);
    assertThat(unknown.getStatus()).isEqualTo("DELETED");
    assertThat(unknown.getReconciliationReason()).isEqualTo("旧发送实例及在途请求已明确停止");
    manager.reconcile(unknown.getId(), request);
    verify(index, times(1)).adoptIndex("old", unknown.getId());
    verify(index, never()).createRebuildTarget(any(), anyString());
  }

  @Test
  void legacyInventoryIncludesEveryExactHistoricalAttemptRatherThanOnlyTheLastRunTarget() {
    TaskSnapshot terminal = task("SUCCEEDED", false, 3);
    when(tasks.get("task-1")).thenReturn(terminal);
    var result = manager.legacyTargets("run-1");
    assertThat(result).extracting(value -> value.getIndexName()).containsExactly(
        "resume-facts-vrun-1-attempt-1", "resume-facts-vrun-1-attempt-2", "resume-facts-vrun-1-attempt-3");
    assertThat(result).extracting(value -> value.getRunVersion()).containsOnly("0");
    verify(authorization).requireAccess(anyString(), any());
  }

  @Test
  void originalTaskCurrentPermissionIsCheckedEvenForASuperAdministrator() {
    var unknown = ready("old"); unknown.setStatus("CREATE_UNKNOWN"); unknown.setManaged(false);
    unknown.setTaskId("task-1"); unknown.setRunId("run-1");
    TaskSnapshot terminal = task("SUCCEEDED", false, 1);
    when(tasks.get("task-1")).thenReturn(terminal);
    doThrow(new IllegalStateException("synthetic current permission revoked"))
        .when(authorization).requireAccess(anyString(), any());
    assertThatThrownBy(() -> manager.reconcile(unknown.getId(), confirmation(0)))
        .hasMessageContaining("permission revoked");
    assertThat(unknown.getReconciledAt()).isNull();
    verify(index, never()).adoptIndex(anyString(), anyString());
  }

  @Test
  void expiredOriginalAttemptCannotReserveAgainEvenAfterItsResourceWasDeleted() {
    var deleted = ready(attempt.indexName()); deleted.setStatus("DELETED");
    deleted.setRunId("run-1"); deleted.setTaskId("task-1"); deleted.setAttemptCount(1);
    when(tasks.renewLease(claimed)).thenReturn(false);
    assertThatThrownBy(() -> manager.createRebuild(claimed, attempt)).isInstanceOf(PlatformException.class);
    verify(index, never()).createRebuildTarget(any(), anyString());
    assertThat(deleted.getStatus()).isEqualTo("DELETED");
  }

  @Test
  void unresolvedCreationIsNeverSelectedForAutomaticDeletionOrSilentlyResolvedByHead() {
    var unknown = ready("old"); unknown.setStatus("CREATE_UNKNOWN"); unknown.setManaged(false);
    assertThat(manager.collect()).isZero();
    verify(index, never()).retireIndex(anyString(), anyString());
    verify(index, never()).adoptIndex(anyString(), anyString());
    assertThat(unknown.getStatus()).isEqualTo("CREATE_UNKNOWN");
  }

  @Test
  void anImportedApprovalKeepsItsKnownOriginalTaskForCurrentManualPermissionChecks() {
    RebuildRun origin = run(); origin.setTargetIndex("approved"); origin.setStatus("SUCCEEDED");
    TaskSnapshot terminal = task("SUCCEEDED", false, 1);
    when(rebuilds.findSucceededTarget("approved")).thenReturn(origin);
    when(tasks.get("task-1")).thenReturn(terminal);
    inTransaction.set(true);
    manager.pin(new SearchIndexTarget("approved", 3), System.nanoTime() + 1_000_000L);
    inTransaction.set(false);
    assertThat(stored.get("approved").getTaskId()).isEqualTo("task-1");
    assertThat(stored.get("approved").getRunId()).isEqualTo("run-1");
    assertThat(stored.get("approved").isManaged()).isFalse();
  }

  @Test
  void importingAnUnregisteredApprovalAlsoRespectsFiniteLiveResourceCapacity() {
    policy.setMaxLiveResources(3); ready("one"); ready("two"); ready("three");
    assertThatThrownBy(() -> manager.pin(new SearchIndexTarget("approved", 3), System.nanoTime() + 1000))
        .isInstanceOf(PlatformException.class).extracting("code").isEqualTo("SEARCH_INDEX_CAPACITY_BLOCKED");
    assertThat(stored).doesNotContainKey("approved");
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "1.0", "9223372036854775808"})
  void malformedWireVersionCannotReachExternalReconciliation(String version) {
    var request = confirmation(0); request.setVersion(version);
    assertThatThrownBy(() -> manager.reconcile("resource", request)).isInstanceOf(PlatformException.class)
        .extracting("code").isEqualTo("VERSION_INVALID");
    verify(index, never()).adoptIndex(anyString(), anyString());
    verify(index, never()).createRebuildTarget(any(), anyString());
  }

  @Test
  void maximumPersistedResourceVersionIsReturnedAsJsonText() throws Exception {
    var resource = ready("large-version"); resource.setVersion(Integer.MAX_VALUE);
    var response = manager.getResource(resource.getId());
    var mapper = new ObjectMapper();
    var json = mapper.readTree(mapper.writeValueAsString(response));
    assertThat(response.getVersion()).isEqualTo(Integer.toString(Integer.MAX_VALUE));
    assertThat(json.path("version").isString()).isTrue();
    assertThat(json.path("version").asString()).isEqualTo(Integer.toString(Integer.MAX_VALUE));
  }

  private SearchIndexResource ready(String name) {
    var row = new SearchIndexResource(); row.setId("resource-" + name); row.setIndexName(name);
    row.setScopeName("scope"); row.setResourceKind(SearchIndexResourceKind.BOOTSTRAP.getCode());
    row.setStatus(SearchIndexResourceStatus.READY.getCode()); row.setManaged(true); row.setReadyAt(Instant.EPOCH);
    stored.put(name, row); return row;
  }

  private RebuildRun run() {
    var run = new RebuildRun(); run.setId("run-1"); run.setTaskId("task-1");
    run.setTargetIndex(attempt.indexName()); run.setStatus("RUNNING"); return run;
  }

  private static TaskSnapshot task(String status, boolean retryable, int attempt) {
    var task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn("task-1"); when(task.resourceId()).thenReturn("run-1");
    when(task.taskType()).thenReturn("SEARCH_INDEX_REBUILD"); when(task.resourceType()).thenReturn("SEARCH_INDEX_REBUILD");
    when(task.status()).thenReturn(status); when(task.retryable()).thenReturn(retryable);
    when(task.attemptCount()).thenReturn(attempt); when(task.maxAttempts()).thenReturn(3);
    when(task.managePermission()).thenReturn("recruitment.manage"); when(task.requestedByUserId()).thenReturn("requester");
    return task;
  }

  private static SearchIndexReconciliationRequest confirmation(long version) {
    var request = new SearchIndexReconciliationRequest(); request.setVersion(ResourceVersion.format(version));
    request.setReason("旧发送实例及在途请求已明确停止"); request.setOwnershipConfirmed(true);
    request.setOriginalRequestTerminated(true); return request;
  }
}
