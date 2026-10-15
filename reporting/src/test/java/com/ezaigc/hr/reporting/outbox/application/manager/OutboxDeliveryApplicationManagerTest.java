package com.ezaigc.hr.reporting.outbox.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.outbox.application.assembler.OutboxEnvelopeAssembler;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxDeliveryApplicationManagerTest {
    private final OutboxEventService events = mock(OutboxEventService.class);
    private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
    private final TaskSnapshot work = mock(TaskSnapshot.class);
    private final OutboxDeliveryApplicationManager manager = new OutboxDeliveryApplicationManager();
    private final OutboxEventEntity event = new OutboxEventEntity();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(manager, "events", events);
        ReflectionTestUtils.setField(manager, "tasks", tasks);
        ReflectionTestUtils.setField(manager, "assembler", new OutboxEnvelopeAssembler());
        event.setId("event-id"); event.setEventType("TASK_AVAILABLE"); event.setEventVersion(1);
        event.setAggregateType("CANDIDATE"); event.setAggregateId("candidate-id");
        event.setWorkTaskId("work-id"); event.setWorkTaskType("RESUME_SEARCH_PROJECT");
        event.setOccurredAt(Instant.parse("2026-09-08T00:00:00Z")); event.setTraceId("trace-0123456789012345");
        event.setActorUserId("actor"); event.setOwnerUserId("owner"); event.setOwnerDepartmentId("department");
        when(events.getById("event-id")).thenReturn(event);
        when(tasks.get("work-id")).thenReturn(work);
        when(work.id()).thenReturn("work-id"); when(work.taskType()).thenReturn("RESUME_SEARCH_PROJECT");
        when(work.resourceType()).thenReturn("CANDIDATE"); when(work.resourceId()).thenReturn("candidate-id");
        when(work.requestedByUserId()).thenReturn("actor"); when(work.ownerUserId()).thenReturn("owner");
        when(work.ownerDepartmentId()).thenReturn("department");
    }

    @Test
    void repeatedNotificationOnlyReadsTheSamePersistedWork() {
        assertThat(manager.verify(envelope())).isSameAs(work);
        assertThat(manager.verify(envelope())).isSameAs(work);
        verify(tasks, times(2)).get("work-id");
        verifyNoMoreInteractions(tasks);
    }

    @Test
    void pollingRestoresThePersistedEnvelopeAndRejectsAReboundClaim() {
        when(events.findByWorkTaskId("work-id")).thenReturn(event);
        assertThat(manager.forTask(work)).isEqualTo(envelope());
        when(work.ownerDepartmentId()).thenReturn("different-department");
        assertThatThrownBy(() -> manager.forTask(work)).isInstanceOf(IllegalArgumentException.class);
        verify(tasks).get("work-id");
        verifyNoMoreInteractions(tasks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "actor", "candidate", "task", "occurredAt", "version"})
    void anExistingEventIdDoesNotAuthorizeAChangedEnvelope(String field) {
        var original = envelope();
        switch (field) {
            case "trace" -> event.setTraceId("another-trace-0123456789");
            case "actor" -> event.setActorUserId("another-actor");
            case "candidate" -> event.setAggregateId("another-candidate");
            case "task" -> event.setWorkTaskId("another-work");
            case "occurredAt" -> event.setOccurredAt(event.getOccurredAt().plusSeconds(1));
            default -> event.setEventVersion(2);
        }
        assertThatThrownBy(() -> manager.verify(original)).isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(tasks);
    }

    @Test
    void matchingEnvelopeCannotAuthorizeWorkReboundToAnotherBusinessKey() {
        when(work.resourceId()).thenReturn("another-candidate");
        assertThatThrownBy(() -> manager.verify(envelope())).isInstanceOf(IllegalArgumentException.class);
    }

    private OutboxEnvelope envelope() {
        return new OutboxEnvelope(event.getId(), event.getEventType(), event.getEventVersion(), event.getAggregateType(),
                event.getAggregateId(), event.getOccurredAt(), event.getTraceId(),
                new OutboxEnvelope.ActorSnapshot(event.getActorUserId(), event.getOwnerUserId(), event.getOwnerDepartmentId()),
                new OutboxEnvelope.TaskPayload(event.getWorkTaskId(), event.getWorkTaskType()));
    }
}
