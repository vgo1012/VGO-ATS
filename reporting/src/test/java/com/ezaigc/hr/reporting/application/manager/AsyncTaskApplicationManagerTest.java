package com.ezaigc.hr.reporting.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalHandler;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskRetryGuard;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.AsyncTaskAssembler;
import com.ezaigc.hr.reporting.config.AsyncTaskProperties;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskEntity;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskLeaseRow;
import com.ezaigc.hr.reporting.service.AsyncTaskAttemptService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;

class AsyncTaskApplicationManagerTest {

  private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");
  private static final Instant LEASE_UNTIL = NOW.plusSeconds(300);
  private static final String TASK_ID = "01KTASK0000000000000000000";
  private static final String USER_ID = "01KUSER0000000000000000000";

  private final AsyncTaskService service = mock(AsyncTaskService.class);
  private final AsyncTaskAttemptService attemptService = mock(AsyncTaskAttemptService.class);
  private final AsyncTaskTerminalHandler recoveryHandler = mock(AsyncTaskTerminalHandler.class);
  private final AsyncTaskRetryGuard retryGuard = mock(AsyncTaskRetryGuard.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private AsyncTaskApplicationManager manager;

  @BeforeEach
  void setUp() {
    var guardFactory = new DefaultListableBeanFactory();
    guardFactory.registerSingleton("retryGuard", retryGuard);
    manager = new AsyncTaskApplicationManager();
    ReflectionTestUtils.setField(manager, "events", events);
    ReflectionTestUtils.setField(manager, "retryGuards", guardFactory.getBeanProvider(AsyncTaskRetryGuard.class));
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(
        manager, "currentActorProvider", (CurrentActorProvider) Optional::empty);
    ReflectionTestUtils.setField(manager, "service", service);
    ReflectionTestUtils.setField(manager, "attemptService", attemptService);
    var handlerFactory = new DefaultListableBeanFactory();
    handlerFactory.registerSingleton("recoveryHandler", recoveryHandler);
    ReflectionTestUtils.setField(manager, "terminalHandlers",
        handlerFactory.getBeanProvider(AsyncTaskTerminalHandler.class));
    ReflectionTestUtils.setField(
        manager, "ids", (PlatformIdGenerator) () -> "01KATTEMPT000000000000000");
    ReflectionTestUtils.setField(manager, "assembler", mock(AsyncTaskAssembler.class));
    ReflectionTestUtils.setField(manager, "properties", new AsyncTaskProperties());
    ReflectionTestUtils.setField(
        manager, "cursorCodec", new SignedCursorCodec("async-task-test-signing-key-32-bytes"));
  }

  @Test
  void directRetryRejectsInvalidBusinessResourceBeforeQueueMutation() {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.taskType()).thenReturn("JD_PARSE");
    when(service.findTask(TASK_ID)).thenReturn(task);
    var rejection = PlatformExceptions.forbidden("JD_MANAGEMENT_FORBIDDEN", "解析权限已撤销");
    doThrow(rejection).when(retryGuard).requireRetryAllowed(task);

    assertThatThrownBy(() -> manager.retryFailed(TASK_ID, "重新解析"))
        .isSameAs(rejection);

    verify(service, never()).retryFailed(any(), any(), any());
  }

  @Test
  void taskListUsesAuthorizationOwnerScopeWithoutDepartmentCursorScope() {
    var actor = new CurrentActorSnapshot(USER_ID, "Manager", null, "home-department",
        Set.of("support-department"), Set.of("managed-user"),
        Set.of("explicit-department"), Set.of("RECRUITMENT_MANAGER"),
        Set.of("reporting.read-own"), false, false);
    TaskSnapshot managedTask = mock(TaskSnapshot.class);
    when(managedTask.id()).thenReturn(TASK_ID);
    when(managedTask.ownerUserId()).thenReturn("managed-user");
    when(managedTask.ownerDepartmentId()).thenReturn("other-department");
    when(managedTask.requestedByUserId()).thenReturn("managed-user");
    when(managedTask.viewPermission()).thenReturn("reporting.read-own");
    when(managedTask.createTime()).thenReturn(NOW);
    when(authorization.currentActor()).thenReturn(actor);
    when(service.findRecentTasks(null, null, null, null, null, null, null, 200))
        .thenReturn(List.of(managedTask));
    when(authorization.accessibleObjectIds(
        eq(actor), eq("reporting.read-own"), any()))
        .thenReturn(Set.of(TASK_ID));

    assertThat(manager.list(null, null, null, null, null, 20)).containsExactly(managedTask);
  }

  @Test
  void businessTaskLookupReturnsNullWhenNoOriginalTaskExists() {
    when(service.findTasksByResourceIdempotencyForUpdate("RESUME_INTAKE_ADOPT", "RESUME_VERSION",
        "resume-1", "intake-adopt:resume-1:p-1")).thenReturn(List.of());
    assertThat(manager.getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION",
        "resume-1", "intake-adopt:resume-1:p-1")).isNull();
  }

  @Test
  void businessTaskLookupReturnsOriginalTaskWithoutRequesterFiltering() {
    TaskSnapshot original = snapshot("FAILED", 1, true);
    when(service.findTasksByResourceIdempotencyForUpdate("RESUME_INTAKE_ADOPT", "RESUME_VERSION",
        "resume-1", "intake-adopt:resume-1:p-1")).thenReturn(List.of(original));
    assertThat(manager.getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION",
        "resume-1", "intake-adopt:resume-1:p-1")).isSameAs(original);
    verify(service, never()).findTaskByIdempotency(any(), any());
  }

  @Test
  void businessTaskLookupRejectsAmbiguousHistoricalTasks() {
    when(service.findTasksByResourceIdempotencyForUpdate("RESUME_INTAKE_ADOPT", "RESUME_VERSION",
        "resume-1", "intake-adopt:resume-1:p-1"))
        .thenReturn(List.of(snapshot("FAILED", 1), snapshot("QUEUED", 0)));
    assertThatThrownBy(() -> manager.getByResourceIdempotency("RESUME_INTAKE_ADOPT", "RESUME_VERSION",
        "resume-1", "intake-adopt:resume-1:p-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            ex -> assertThat(ex.code()).isEqualTo("TASK_RESOURCE_IDEMPOTENCY_AMBIGUOUS"));
  }

  @Test
  void businessTaskLookupValidatesCompleteResourceIdentityBeforeQuery() {
    assertThatThrownBy(() -> manager.getByResourceIdempotency("", "RESUME_VERSION",
        "resume-1", "intake-adopt:resume-1:p-1")).isInstanceOf(IllegalArgumentException.class);
    verify(service, never()).findTasksByResourceIdempotencyForUpdate(any(), any(), any(), any());
  }

  @Test
  void directRetryChecksBusinessAdmissionBeforeQueueing() {
    TaskSnapshot failed = mock(TaskSnapshot.class);
    when(failed.taskType()).thenReturn("JD_PARSE");
    TaskSnapshot queued = mock(TaskSnapshot.class);
    when(service.findTask(TASK_ID)).thenReturn(failed, queued);
    when(service.retryFailed(any(), any(), any())).thenReturn(1);

    assertThat(manager.retryFailed(TASK_ID, "重新解析")).isSameAs(queued);
    verify(events).publishEvent(new AsyncTaskAvailable("JD_PARSE", TASK_ID, null));

    var order = inOrder(retryGuard, service);
    order.verify(retryGuard).requireRetryAllowed(failed);
    order.verify(service).retryFailed(any(), any(), any());
  }

  @Test
  void insertsACompleteAuditedTaskForANewIdempotencyKey() {
    AtomicReference<AsyncTaskEntity> inserted = new AtomicReference<>();
    when(service.findTaskByIdempotency(USER_ID, "request-key")).thenReturn(null);
    when(service.insertIgnore(any(AsyncTaskEntity.class)))
        .thenAnswer(
            invocation -> {
              inserted.set(invocation.getArgument(0));
              return 1;
            });
    when(service.findTask(TASK_ID)).thenReturn(snapshot("QUEUED", 0));

    TaskSnapshot result = manager.enqueue(command("request-key", "fingerprint"));
    verify(events).publishEvent(new AsyncTaskAvailable("REPORT_EXPORT", TASK_ID, null));

    assertThat(result.status()).isEqualTo("QUEUED");
    assertThat(inserted.get().getTraceId()).matches("[A-Za-z0-9_-]{16,64}");
    assertThat(inserted.get())
        .extracting(
            AsyncTaskEntity::getId,
            AsyncTaskEntity::getStatus,
            AsyncTaskEntity::getCreateId,
            AsyncTaskEntity::getCreateTime,
            AsyncTaskEntity::getUpdateId,
            AsyncTaskEntity::getUpdateTime)
        .containsExactly(TASK_ID, "QUEUED", USER_ID, NOW, USER_ID, NOW);
  }

  @Test
  void replaysTheExistingTaskWhenTheRequestFingerprintMatches() {
    TaskSnapshot existing = snapshot("SUCCEEDED", 1);
    AsyncTaskEntity persisted = new AsyncTaskEntity();
    persisted.setRequestFingerprint("fingerprint");
    when(service.findTaskByIdempotency(USER_ID, "request-key")).thenReturn(existing);
    when(service.getById(TASK_ID)).thenReturn(persisted);

    assertThat(manager.enqueue(command("request-key", "fingerprint"))).isSameAs(existing);
    verifyNoInteractions(events);
  }

  @Test
  void losingInsertUsesACurrentReadToReplayTheConcurrentWinner() {
    TaskSnapshot existing = snapshot("QUEUED", 0);
    AsyncTaskEntity persisted = new AsyncTaskEntity();
    persisted.setRequestFingerprint("fingerprint");
    when(service.findTaskByIdempotency(USER_ID, "request-key")).thenReturn(null);
    when(service.insertIgnore(any(AsyncTaskEntity.class))).thenReturn(0);
    when(service.findTaskByIdempotencyForUpdate(USER_ID, "request-key")).thenReturn(existing);
    when(service.findEntityByIdForUpdate(TASK_ID)).thenReturn(persisted);

    assertThat(manager.enqueue(command("request-key", "fingerprint"))).isSameAs(existing);

    verify(service).findTaskByIdempotency(USER_ID, "request-key");
    verify(service).findTaskByIdempotencyForUpdate(USER_ID, "request-key");
    verify(service).findEntityByIdForUpdate(TASK_ID);
  }

  @Test
  void workerClaimsAreAuditedAsTheReservedSystemActor() {
    when(service.findQueuedIdsForUpdate("REPORT_EXPORT", NOW, 1)).thenReturn(List.of(TASK_ID));
    when(
            service.markRunning(
                TASK_ID, NOW, LEASE_UNTIL, PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);
    when(attemptService.insertAttempt("01KATTEMPT000000000000000", TASK_ID, NOW)).thenReturn(1);
    when(service.findTask(TASK_ID)).thenReturn(snapshot("RUNNING", 1));

    var claimed = manager.claim("REPORT_EXPORT", 1);

    assertThat(claimed).singleElement().extracting(TaskSnapshot::status).isEqualTo("RUNNING");
    verify(attemptService).insertAttempt("01KATTEMPT000000000000000", TASK_ID, NOW);
  }

  @Test
  void exhaustedAttemptIsPersistedAsFinalEvenWhenTheFailureTypeIsRetryable() {
    TaskSnapshot claimed = snapshot("RUNNING", 3);
    when(service.findTask(TASK_ID)).thenReturn(snapshot("FAILED", 3));
    when(
            service.markFailed(
                TASK_ID,
                3,
                "TEMPORARY_FAILURE",
                "暂时失败",
                false,
                NOW,
                PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);
    when(attemptService.markFailed(TASK_ID, 3, "TEMPORARY_FAILURE", "暂时失败", NOW))
        .thenReturn(1);

    TaskSnapshot failed = manager.fail(claimed, "TEMPORARY_FAILURE", "暂时失败", true);

    assertThat(failed.retryable()).isFalse();
    verify(service)
        .markFailed(
            TASK_ID,
            3,
            "TEMPORARY_FAILURE",
            "暂时失败",
            false,
            NOW,
            PlatformActorConstants.SYSTEM_ACTOR_ID);
  }

  @Test
  void retryableFailureRemainsRetryableWhileAttemptBudgetIsAvailable() {
    TaskSnapshot claimed = snapshot("RUNNING", 2);
    when(service.findTask(TASK_ID)).thenReturn(snapshot("FAILED", 2, true));
    when(
            service.markFailed(
                TASK_ID,
                2,
                "TEMPORARY_FAILURE",
                "暂时失败",
                true,
                NOW,
                PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);
    when(attemptService.markFailed(TASK_ID, 2, "TEMPORARY_FAILURE", "暂时失败", NOW))
        .thenReturn(1);

    TaskSnapshot failed = manager.fail(claimed, "TEMPORARY_FAILURE", "暂时失败", true);

    assertThat(failed.retryable()).isTrue();
    verifyNoInteractions(recoveryHandler);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unhandledFailureUsesItsActualCauseAndPropagatesBusinessFailure(boolean rejectBusinessWrite) {
    TaskSnapshot claimed = snapshot("RUNNING", 1);
    var completed = mock(TaskSnapshot.class);
    when(completed.id()).thenReturn(TASK_ID);
    when(completed.taskType()).thenReturn("REPORT_EXPORT");
    when(completed.resourceId()).thenReturn("job");
    when(completed.status()).thenReturn("FAILED");
    when(completed.errorCode()).thenReturn("TASK_EXECUTION_FAILED");
    when(completed.finishedAt()).thenReturn(NOW);
    when(service.findTask(TASK_ID)).thenReturn(completed);
    when(service.markFailed(TASK_ID, 1, "TASK_EXECUTION_FAILED", "安全摘要", true,
        NOW, PlatformActorConstants.SYSTEM_ACTOR_ID)).thenReturn(1);
    when(attemptService.markFailed(TASK_ID, 1, "TASK_EXECUTION_FAILED", "安全摘要", NOW)).thenReturn(1);
    var failure = new IllegalStateException("Synthetic terminal handler failure");
    if (rejectBusinessWrite) {
      doThrow(failure).when(recoveryHandler).terminalWithoutWorker("REPORT_EXPORT", TASK_ID, "job",
          AsyncTaskStatus.FAILED, "TASK_EXECUTION_FAILED", NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);
      assertThatThrownBy(() -> manager.failUnhandled(claimed, "TASK_EXECUTION_FAILED", "安全摘要", true))
          .isSameAs(failure);
    } else {
      assertThat(manager.failUnhandled(claimed, "TASK_EXECUTION_FAILED", "安全摘要", true)).isSameAs(completed);
    }
    verify(recoveryHandler).terminalWithoutWorker("REPORT_EXPORT", TASK_ID, "job", AsyncTaskStatus.FAILED,
        "TASK_EXECUTION_FAILED", NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);
  }

  @Test
  void staleUnhandledFailureCannotInvokeBusinessCompletion() {
    when(service.findTask(TASK_ID)).thenReturn(snapshot("SUCCEEDED", 2));
    assertThatThrownBy(() -> manager.failUnhandled(snapshot("RUNNING", 1), "TASK_EXECUTION_FAILED", "安全摘要", true))
        .isInstanceOf(PlatformException.class).extracting("code").isEqualTo("TASK_STATE_CONFLICT");
    verifyNoInteractions(recoveryHandler, attemptService);
  }

  @Test
  void cancellationWinsWhenFailureArrivesAfterCancelRequest() {
    TaskSnapshot claimed = snapshot("RUNNING", 1);
    when(service.findTask(TASK_ID))
        .thenReturn(snapshot("CANCELLING", 1), snapshot("CANCELLED", 1));
    when(
            service.markFailed(
                TASK_ID,
                1,
                "TEMPORARY_FAILURE",
                "暂时失败",
                true,
                NOW,
                PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(0);
    when(service.markCancelled(TASK_ID, 1, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);
    when(attemptService.markCancelled(TASK_ID, 1, NOW)).thenReturn(1);

    TaskSnapshot result = manager.fail(claimed, "TEMPORARY_FAILURE", "暂时失败", true);

    assertThat(result.status()).isEqualTo("CANCELLED");
    verify(attemptService).markCancelled(TASK_ID, 1, NOW);
    verify(attemptService, never())
        .markFailed(TASK_ID, 1, "TEMPORARY_FAILURE", "暂时失败", NOW);
  }

  @Test
  void lateFailureIsIdempotentWhenCancellationAlreadyFinished() {
    TaskSnapshot claimed = snapshot("RUNNING", 1);
    when(service.findTask(TASK_ID)).thenReturn(snapshot("CANCELLED", 1));
    when(
            service.markFailed(
                TASK_ID,
                1,
                "TEMPORARY_FAILURE",
                "暂时失败",
                true,
                NOW,
                PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(0);

    TaskSnapshot result = manager.fail(claimed, "TEMPORARY_FAILURE", "暂时失败", true);

    assertThat(result.status()).isEqualTo("CANCELLED");
    verifyNoInteractions(attemptService);
  }

  @Test
  void expiredRunningLeaseIsAuditedAndRequeuedBeforeClaiming() {
    AsyncTaskLeaseRow expired = expiredTask("RUNNING", 1, 3);
    when(service.findExpiredLeasesForUpdate("REPORT_EXPORT", NOW, 50))
        .thenReturn(List.of(expired));
    when(
            attemptService.markFailed(
                TASK_ID,
                1,
                "TASK_LEASE_EXPIRED",
                "任务处理租约已过期，系统将按重试策略恢复",
                NOW))
        .thenReturn(1);
    when(service.requeueExpiredLease(TASK_ID, 1, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);

    assertThat(manager.claim("REPORT_EXPORT", 1)).isEmpty();

    verify(service)
        .requeueExpiredLease(TASK_ID, 1, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);
    verifyNoInteractions(recoveryHandler);
  }

  @ParameterizedTest
  @ValueSource(strings = {"REPORT_EXPORT", "RESUME_PARSE"})
  void expiredRunningLeaseBecomesFinalFailureAfterAttemptBudgetIsExhausted(String taskType) {
    AsyncTaskLeaseRow expired = expiredTask("RUNNING", 3, 3);
    expired.setTaskType(taskType);
    when(service.findExpiredLeasesForUpdate(taskType, NOW, 50))
        .thenReturn(List.of(expired));
    when(
            attemptService.markFailed(
                TASK_ID,
                3,
                "TASK_LEASE_EXPIRED",
                "任务处理租约已过期且已达到最大尝试次数，请重新发起任务",
                NOW))
        .thenReturn(1);
    when(
            service.failExpiredLease(
                TASK_ID,
                3,
                "TASK_LEASE_EXPIRED",
                "任务处理租约已过期且已达到最大尝试次数，请重新发起任务",
                NOW,
                PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);

    assertThat(manager.claim(taskType, 1)).isEmpty();
    verify(recoveryHandler).terminalWithoutWorker(taskType, TASK_ID, "export-job", AsyncTaskStatus.FAILED,
        "TASK_LEASE_EXPIRED", NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);

    verify(service)
        .failExpiredLease(
            TASK_ID,
            3,
            "TASK_LEASE_EXPIRED",
            "任务处理租约已过期且已达到最大尝试次数，请重新发起任务",
            NOW,
            PlatformActorConstants.SYSTEM_ACTOR_ID);
  }

  @Test
  void expiredCancellationConvergesToCancelledInsteadOfBeingRequeued() {
    AsyncTaskLeaseRow expired = expiredTask("CANCELLING", 2, 3);
    when(service.findExpiredLeasesForUpdate("REPORT_EXPORT", NOW, 50))
        .thenReturn(List.of(expired));
    when(attemptService.markCancelled(TASK_ID, 2, NOW)).thenReturn(1);
    when(service.cancelExpiredLease(TASK_ID, 2, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);

    assertThat(manager.claim("REPORT_EXPORT", 1)).isEmpty();

    verify(recoveryHandler).terminalWithoutWorker("REPORT_EXPORT", TASK_ID, "export-job",
        AsyncTaskStatus.CANCELLED, null, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);

    verify(service)
        .cancelExpiredLease(TASK_ID, 2, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);
    verify(service, never())
        .requeueExpiredLease(TASK_ID, 2, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);
  }

  @Test
  void progressUpdateRenewsOnlyTheClaimedAttemptLease() {
    TaskSnapshot claimed = snapshot("RUNNING", 2);
    when(
            service.updateProgress(
                TASK_ID,
                2,
                40,
                NOW,
                LEASE_UNTIL,
                PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);
    when(service.findTask(TASK_ID)).thenReturn(snapshot("RUNNING", 2));

    TaskSnapshot updated = manager.updateProgress(claimed, 40);

    assertThat(updated.attemptCount()).isEqualTo(2);
    verify(service)
        .updateProgress(
            TASK_ID,
            2,
            40,
            NOW,
            LEASE_UNTIL,
            PlatformActorConstants.SYSTEM_ACTOR_ID);
  }

  @Test
  void workerStopsWhenItsAttemptNoLongerOwnsAnActiveLease() {
    TaskSnapshot claimed = snapshot("RUNNING", 2);
    when(service.hasActiveLease(TASK_ID, 2, NOW)).thenReturn(false);

    assertThat(manager.shouldStopProcessing(claimed)).isTrue();
  }

  @Test
  void heartbeatRenewsOnlyTheClaimedAttemptLease() {
    TaskSnapshot claimed = snapshot("RUNNING", 2);
    when(
            service.renewLease(
                TASK_ID,
                2,
                NOW,
                LEASE_UNTIL,
                PlatformActorConstants.SYSTEM_ACTOR_ID))
        .thenReturn(1);

    assertThat(manager.renewLease(claimed)).isTrue();
  }

  @Test
  void directQueuedCancellationNotifiesBusinessOwnersUsingTheUpdatedTask() {
    TaskSnapshot cancelled = snapshot("CANCELLED", 0);
    when(service.findTaskForUpdate(TASK_ID)).thenReturn(snapshot("QUEUED", 0));
    when(service.findTask(TASK_ID)).thenReturn(cancelled);
    when(service.requestCancel(TASK_ID, "取消", NOW, PlatformActorConstants.SYSTEM_ACTOR_ID)).thenReturn(1);

    assertThat(manager.cancel(TASK_ID, "取消")).isSameAs(cancelled);

    verify(recoveryHandler).terminalWithoutWorker("REPORT_EXPORT", TASK_ID, "01KRESOURCE000000000000000",
        AsyncTaskStatus.CANCELLED, null, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);
  }

  @Test
  void previouslyClaimedTaskKeepsCancellationWithTheActiveWorker() {
    TaskSnapshot cancelling = snapshot("CANCELLING", 1);
    when(service.findTaskForUpdate(TASK_ID)).thenReturn(snapshot("RUNNING", 1));
    when(service.findTask(TASK_ID)).thenReturn(cancelling);
    when(service.requestCancel(TASK_ID, "取消", NOW, PlatformActorConstants.SYSTEM_ACTOR_ID)).thenReturn(1);

    assertThat(manager.cancel(TASK_ID, "取消")).isSameAs(cancelling);

    verifyNoInteractions(recoveryHandler);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUCCEEDED", "FAILED", "CANCELLED", "CANCELLING"})
  void repeatedCancellationDoesNotReopenOrNotifyAlreadyAcceptedStates(String status) {
    TaskSnapshot terminal = snapshot(status, 1);
    when(service.findTaskForUpdate(TASK_ID)).thenReturn(terminal);

    assertThat(manager.cancel(TASK_ID, "重复取消")).isSameAs(terminal);

    verify(service, never()).requestCancel(any(), any(), any(), any());
    verifyNoInteractions(recoveryHandler);
  }

  @Test
  void retiredExportTaskCannotBeRetried() {
    when(service.findTask(TASK_ID)).thenReturn(snapshot("FAILED", 1, true));

    assertThatThrownBy(() -> manager.retryFailed(TASK_ID, "重新导出"))
        .isInstanceOf(PlatformException.class)
        .extracting("code")
        .isEqualTo("TASK_TYPE_RETIRED");
    verify(service, never()).retryFailed(any(), any(), any());
  }

  @Test
  void cancellationUsesLockedCurrentStateInsteadOfEarlierQueuedSnapshot() {
    when(service.findTask(TASK_ID)).thenReturn(snapshot("QUEUED", 0));
    TaskSnapshot cancelled = snapshot("CANCELLED", 1);
    when(service.findTaskForUpdate(TASK_ID)).thenReturn(cancelled);

    manager.cancelResponse(TASK_ID, "重复取消");

    verify(service).findTaskForUpdate(TASK_ID);
    verify(service, never()).requestCancel(any(), any(), any(), any());
    verifyNoInteractions(recoveryHandler);
  }

  @Test
  void missingTaskInLockedReadRemainsNotFound() {
    assertThatThrownBy(() -> manager.cancel(TASK_ID, "取消"))
        .isInstanceOfSatisfying(PlatformException.class,
            ex -> assertThat(ex.code()).isEqualTo("TASK_NOT_FOUND"));
    verify(service, never()).requestCancel(any(), any(), any(), any());
    verifyNoInteractions(recoveryHandler);
  }

  @Test
  void failedCancellationMutationStillReportsStateConflict() {
    when(service.findTaskForUpdate(TASK_ID)).thenReturn(snapshot("RUNNING", 1));
    assertThatThrownBy(() -> manager.cancel(TASK_ID, "取消"))
        .isInstanceOfSatisfying(PlatformException.class,
            ex -> assertThat(ex.code()).isEqualTo("TASK_STATE_CONFLICT"));
    verifyNoInteractions(recoveryHandler);
  }

  @Test
  void businessTerminalFailureEscapesSoTheCallingTransactionCanRollback() {
    when(service.findTaskForUpdate(TASK_ID)).thenReturn(snapshot("QUEUED", 0));
    when(service.findTask(TASK_ID)).thenReturn(snapshot("CANCELLED", 0));
    when(service.requestCancel(TASK_ID, "取消", NOW, PlatformActorConstants.SYSTEM_ACTOR_ID)).thenReturn(1);
    var failure = new IllegalStateException("Synthetic business terminal failure");
    doThrow(failure).when(recoveryHandler).terminalWithoutWorker("REPORT_EXPORT", TASK_ID,
        "01KRESOURCE000000000000000", AsyncTaskStatus.CANCELLED, null, NOW, PlatformActorConstants.SYSTEM_ACTOR_ID);

    assertThatThrownBy(() -> manager.cancel(TASK_ID, "取消")).isSameAs(failure);
  }

  @Test
  void retryDeadlineIsValidatedBeforeAnyStateChange() {
    for (Instant deadline : List.of(NOW, NOW.minusSeconds(1), NOW.plusSeconds(86401))) {
      assertThatThrownBy(() -> manager.failAndRetryAt(snapshot("RUNNING", 1), "BROKER_UNAVAILABLE", "消息暂时不可用", deadline))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> manager.failAndRetryAt(snapshot("RUNNING", 1), "BROKER_UNAVAILABLE", "消息暂时不可用", null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(service, attemptService);
  }

  private static EnqueueTask command(String key, String fingerprint) {
    return new EnqueueTask(
        TASK_ID,
        "REPORT_EXPORT",
        "EXPORT_JOB",
        "01KRESOURCE000000000000000",
        USER_ID,
        USER_ID,
        "00000000000000000000000001",
        "talent.read",
        "talent.read",
        key,
        fingerprint,
        3,
        NOW);
  }

  private static AsyncTaskLeaseRow expiredTask(String status, int attemptNo, int maxAttempts) {
    return new AsyncTaskLeaseRow(TASK_ID, "REPORT_EXPORT", "export-job", status, attemptNo, maxAttempts);
  }

  private static TaskSnapshot snapshot(String status, int attempts) {
    return snapshot(status, attempts, false);
  }

  private static TaskSnapshot snapshot(String status, int attempts, boolean retryable) {
    return new TaskSnapshot(
        TASK_ID,
        "REPORT_EXPORT",
        "EXPORT_JOB",
        "01KRESOURCE000000000000000",
        USER_ID,
        USER_ID,
        "00000000000000000000000001",
        "talent.read",
        "talent.read",
        status,
        attempts,
        3,
        status.equals("RUNNING") ? 1 : 0,
        null,
        null,
        retryable,
        null,
        attempts,
        NOW,
        status.equals("RUNNING") ? NOW : null,
        null,
        NOW);
  }
}
