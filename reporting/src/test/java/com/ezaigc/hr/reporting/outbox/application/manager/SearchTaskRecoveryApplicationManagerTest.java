package com.ezaigc.hr.reporting.outbox.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxDeliveryPort;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.assembler.AsyncTaskAssembler;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import com.ezaigc.hr.reporting.outbox.application.assembler.SearchTaskRecoveryAssembler;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.domain.entity.SearchTaskRecoveryEntity;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import com.ezaigc.hr.reporting.outbox.service.SearchTaskRecoveryService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import com.ezaigc.hr.reporting.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class SearchTaskRecoveryApplicationManagerTest {
  private final SearchTaskRecoveryApplicationManager manager = new SearchTaskRecoveryApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
  private final AsyncTaskService service = mock(AsyncTaskService.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final OutboxTaskPort outbox = mock(OutboxTaskPort.class);
  private final OutboxDeliveryPort deliveries = mock(OutboxDeliveryPort.class);
  private final OutboxEventService events = mock(OutboxEventService.class);
  private final SearchTaskRecoveryService recoveries = mock(SearchTaskRecoveryService.class);
  private final AuditService audits = mock(AuditService.class);
  private final TaskSnapshot original = mock(TaskSnapshot.class);
  private final TaskSnapshot work = mock(TaskSnapshot.class);
  private final TaskSnapshot created = mock(TaskSnapshot.class);
  private final OutboxEventEntity event = new OutboxEventEntity();

  SearchTaskRecoveryApplicationManagerTest() {
    actor.setUserId("admin"); actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    when(service.lockTask("failed")).thenReturn("failed");
    when(tasks.get("failed")).thenReturn(original);
    when(original.id()).thenReturn("failed"); when(original.status()).thenReturn("FAILED");
    when(original.attemptCount()).thenReturn(5); when(original.maxAttempts()).thenReturn(5);
    when(original.taskType()).thenReturn("OUTBOX_PUBLISH"); when(original.resourceType()).thenReturn("ASYNC_TASK");
    when(original.resourceId()).thenReturn("work"); when(original.ownerUserId()).thenReturn("owner");
    when(original.ownerDepartmentId()).thenReturn("department");
    when(original.viewPermission()).thenReturn("talent.read"); when(original.managePermission()).thenReturn("talent.manage");
    when(work.id()).thenReturn("work"); when(tasks.get("work")).thenReturn(work);
    event.setId("failed"); event.setWorkTaskId("work"); event.setWorkTaskType("RESUME_SEARCH_PROJECT");
    event.setAggregateType("CANDIDATE"); event.setAggregateId("candidate");
    when(events.getById("failed")).thenReturn(event);
    var envelope = mock(OutboxEnvelope.class);
    when(envelope.getEventId()).thenReturn("failed"); when(deliveries.forTask(work)).thenReturn(envelope);
    when(created.id()).thenReturn("recovery"); when(created.maxAttempts()).thenReturn(5);
    when(tasks.enqueue(any())).thenReturn(created); when(outbox.enqueue(any())).thenReturn(created);
    when(recoveries.save(any())).thenReturn(true); when(audits.save(any())).thenReturn(true);
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("recovery", "audit");
    var assembler = new SearchTaskRecoveryAssembler();
    ReflectionTestUtils.setField(assembler, "tasks", new AsyncTaskAssembler());
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "taskService", service);
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "outbox", outbox);
    ReflectionTestUtils.setField(manager, "deliveries", deliveries);
    ReflectionTestUtils.setField(manager, "events", events);
    ReflectionTestUtils.setField(manager, "recoveries", recoveries);
    ReflectionTestUtils.setField(manager, "audits", audits);
    ReflectionTestUtils.setField(manager, "assembler", assembler);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
  }

  @Test
  void publicationRecoveryKeepsOriginalEventAndOwnershipAndAuditsAdministratorReason() {
    var response = manager.create("failed", "  repaired broker  ", "request-key");
    assertThat(response.getOriginalTaskId()).isEqualTo("failed");
    assertThat(response.getRecoveryTask().getTaskId()).isEqualTo("recovery");
    assertThat(response.getOutboxEventId()).isEqualTo("failed");
    var command = ArgumentCaptor.forClass(EnqueueTask.class);
    verify(tasks).enqueue(command.capture());
    assertThat(command.getValue().maxAttempts()).isEqualTo(5);
    assertThat(command.getValue().requestedByUserId()).isEqualTo("admin");
    assertThat(command.getValue().ownerUserId()).isEqualTo("owner");
    assertThat(command.getValue().resourceId()).isEqualTo("work");
    var audit = ArgumentCaptor.forClass(AuditEventEntity.class);
    verify(audits).save(audit.capture());
    assertThat(audit.getValue().getReason()).isEqualTo("repaired broker");
    assertThat(audit.getValue().getTargetId()).isEqualTo("failed");
    assertThat(audit.getValue().getAfterSnapshot()).contains("recovery", "maxAttempts");
    verifyNoInteractions(outbox);
    verify(tasks, never()).retryFailed(any(), any());
  }

  @Test
  void indexRecoveryCreatesNewWorkAndItsOwnOutboxEvent() {
    when(original.taskType()).thenReturn("RESUME_SEARCH_PROJECT");
    when(original.resourceType()).thenReturn("CANDIDATE"); when(original.resourceId()).thenReturn("candidate");
    event.setWorkTaskId("failed"); when(events.findByWorkTaskId("failed")).thenReturn(event);
    var envelope = deliveries.forTask(work);
    when(deliveries.forTask(original)).thenReturn(envelope);
    var newEvent = new OutboxEventEntity(); newEvent.setId("new-event");
    when(events.findByWorkTaskId("recovery")).thenReturn(newEvent);
    assertThat(manager.create("failed", "fixed index", "key").getOutboxEventId()).isEqualTo("new-event");
    var command = ArgumentCaptor.forClass(EnqueueTask.class);
    verify(outbox).enqueue(command.capture());
    assertThat(command.getValue().maxAttempts()).isEqualTo(5);
    assertThat(command.getValue().taskType()).isEqualTo("RESUME_SEARCH_PROJECT");
    verify(tasks, never()).enqueue(any());
    verify(tasks).get("failed");
  }

  @Test
  void nonAdministratorCannotReadOrCreateEvenWithAManagePermission() {
    actor.setSuperAdmin(false);
    assertCode(() -> manager.create("failed", "reason", "key"), "SEARCH_RECOVERY_ADMIN_REQUIRED");
    assertCode(() -> manager.get("failed"), "SEARCH_RECOVERY_ADMIN_REQUIRED");
    verifyNoInteractions(service, tasks, recoveries, outbox, audits);
  }

  @ParameterizedTest
  @CsvSource({"QUEUED,0,5", "RUNNING,5,5", "SUCCEEDED,5,5", "CANCELLED,5,5", "FAILED,4,5", "FAILED,3,3"})
  void onlyAnExhaustedFiveAttemptFailedTaskCanStartRecovery(String status, int attempts, int maximum) {
    when(original.status()).thenReturn(status); when(original.attemptCount()).thenReturn(attempts);
    when(original.maxAttempts()).thenReturn(maximum);
    assertCode(() -> manager.create("failed", "reason", "key"), "SEARCH_TASK_NOT_EXHAUSTED");
    verify(tasks, never()).enqueue(any()); verifyNoInteractions(outbox, audits);
  }

  @Test
  void invalidReasonOrHeaderIsRejectedBeforeLocking() {
    assertCode(() -> manager.create("failed", " ", "key"), "SEARCH_RECOVERY_REASON_INVALID");
    assertCode(() -> manager.create("failed", "r".repeat(501), "key"), "SEARCH_RECOVERY_REASON_INVALID");
    assertCode(() -> manager.create("failed", "reason", "unsafe key"), "IDEMPOTENCY_KEY_INVALID");
    verifyNoInteractions(service, tasks);
  }

  @Test
  void replayReturnsSameRecoveryButDifferentPayloadAndDifferentKeyCannotCreateAnotherChild() {
    var record = new SearchTaskRecoveryEntity(); record.setId("recovery"); record.setOriginalTaskId("failed");
    record.setReason("reason"); record.setRequestedByUserId("admin");
    when(tasks.getByIdempotency("admin", "key")).thenReturn(created);
    when(recoveries.getById("recovery")).thenReturn(record);
    assertThat(manager.create("failed", "reason", "key").getRecoveryTask().getTaskId()).isEqualTo("recovery");
    assertCode(() -> manager.create("failed", "changed", "key"), "IDEMPOTENCY_KEY_REUSED");
    when(recoveries.findByOriginalTaskId("failed")).thenReturn(record);
    assertCode(() -> manager.create("failed", "reason", "other-key"), "SEARCH_TASK_ALREADY_RECOVERED");
    verify(tasks, never()).enqueue(any()); verifyNoInteractions(outbox, audits);
  }

  @Test
  void unsupportedTaskOrUnverifiedEventCannotCreateRecovery() {
    when(original.taskType()).thenReturn("EXPORT");
    assertCode(() -> manager.create("failed", "reason", "key"), "SEARCH_TASK_TYPE_UNSUPPORTED");
    when(original.taskType()).thenReturn("OUTBOX_PUBLISH");
    when(deliveries.forTask(work)).thenThrow(new IllegalArgumentException("synthetic corrupt binding"));
    assertCode(() -> manager.create("failed", "reason", "key"), "SEARCH_TASK_BINDING_INVALID");
    verify(tasks, never()).enqueue(any()); verifyNoInteractions(outbox, audits);
  }

  private void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(PlatformException.class,
        failure -> assertThat(failure.code()).isEqualTo(code));
  }
}
