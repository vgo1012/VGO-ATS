package com.ezaigc.hr.reporting.outbox.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.outbox.application.assembler.OutboxEnvelopeAssembler;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxPublicationApplicationManagerTest {
    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    private final OutboxEventService events = mock(OutboxEventService.class);
    private final OutboxMessagePublisher publisher = mock(OutboxMessagePublisher.class);
    private final TaskSnapshot task = mock(TaskSnapshot.class);
    private final OutboxPublicationApplicationManager manager = new OutboxPublicationApplicationManager();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(manager, "tasks", tasks);
        ReflectionTestUtils.setField(manager, "events", events);
        ReflectionTestUtils.setField(manager, "publisher", publisher);
        ReflectionTestUtils.setField(manager, "assembler", new OutboxEnvelopeAssembler());
        ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
        when(task.id()).thenReturn("event-id");
        when(task.taskType()).thenReturn("OUTBOX_PUBLISH");
        when(task.resourceId()).thenReturn("work-id");
        when(task.attemptCount()).thenReturn(1);
        var event = new OutboxEventEntity();
        event.setId("event-id"); event.setWorkTaskId("work-id"); event.setWorkTaskType("RESUME_SEARCH_PROJECT");
        event.setEventType("TASK_AVAILABLE"); event.setEventVersion(1); event.setAggregateType("CANDIDATE");
        event.setAggregateId("candidate-id"); event.setOccurredAt(NOW); event.setTraceId("trace-0123456789012345");
        when(events.getById("event-id")).thenReturn(event);
    }

    @Test
    void recoveryPublicationUsesTheOriginalEventAndOnlyCompletesTheNewTask() {
        var originalEvent = events.getById("event-id");
        when(task.id()).thenReturn("recovery-id");
        when(events.findForRecoveryTask("recovery-id")).thenReturn(originalEvent);
        manager.process(task);
        var envelope = ArgumentCaptor.forClass(OutboxEnvelope.class);
        verify(publisher).publish(envelope.capture());
        assertThat(envelope.getValue().getEventId()).isEqualTo("event-id");
        assertThat(envelope.getValue().getPayload().getTaskId()).isEqualTo("work-id");
        verify(tasks).succeed(task, "event-id");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publicationPreservesEveryPersistedEnvelopeFieldIncludingNullableOwnership(boolean owned) {
        var event = events.getById("event-id");
        event.setActorUserId("actor");
        event.setOwnerUserId(owned ? "owner" : null);
        event.setOwnerDepartmentId(owned ? "department" : null);
        manager.process(task);
        var envelope = ArgumentCaptor.forClass(OutboxEnvelope.class);
        verify(publisher).publish(envelope.capture());
        assertThat(envelope.getValue()).isEqualTo(new OutboxEnvelope(
                "event-id", "TASK_AVAILABLE", 1, "CANDIDATE", "candidate-id", NOW,
                "trace-0123456789012345",
                new OutboxEnvelope.ActorSnapshot("actor", owned ? "owner" : null, owned ? "department" : null),
                new OutboxEnvelope.TaskPayload("work-id", "RESUME_SEARCH_PROJECT")));
        verify(tasks).succeed(task, "event-id");
    }

    @Test
    void onlyAcknowledgesAfterThePublisherReturns() {
        manager.process(task);
        var ordered = inOrder(publisher, tasks);
        var envelope = ArgumentCaptor.forClass(OutboxEnvelope.class);
        ordered.verify(publisher).publish(envelope.capture());
        ordered.verify(tasks).succeed(task, "event-id");
        assertThat(envelope.getValue().getPayload().getTaskId()).isEqualTo("work-id");
        assertThat(envelope.getValue().getTraceId()).isEqualTo("trace-0123456789012345");
    }

    @Test
    void transientPublishFailurePersistsADelayedRetryWithoutAcknowledging() {
        doThrow(new OutboxPublishException("BROKER_TIMEOUT", "确认暂时超时", true, null)).when(publisher).publish(any());
        manager.process(task);
        var retryAt = ArgumentCaptor.forClass(Instant.class);
        verify(tasks).failAndRetryAt(eq(task), eq("BROKER_TIMEOUT"), eq("确认暂时超时"), retryAt.capture());
        assertThat(retryAt.getValue()).isBetween(NOW.plusMillis(2500), NOW.plusSeconds(5));
        verify(tasks, never()).succeed(any(), any());
    }

    @Test
    void acknowledgementDatabaseRollbackSchedulesTheSameEventForReplay() {
        when(tasks.succeed(task, "event-id")).thenThrow(new TransientDataAccessResourceException("synthetic database failure"));
        manager.process(task);
        verify(publisher).publish(any());
        verify(tasks).failAndRetryAt(eq(task), eq("OUTBOX_PERSISTENCE_UNAVAILABLE"), any(), any());
    }

    @Test
    void lostLeaseDoesNotPublish() {
        when(tasks.shouldStopProcessing(task)).thenReturn(true);
        manager.process(task);
        verifyNoInteractions(publisher, events);
    }

    @Test
    void corruptEventBindingFailsWithoutNetworkTraffic() {
        when(task.resourceId()).thenReturn("different-work");
        manager.process(task);
        verify(tasks).fail(eq(task), eq("OUTBOX_BINDING_INVALID"), any(), eq(false));
        verifyNoInteractions(publisher);
    }
}
