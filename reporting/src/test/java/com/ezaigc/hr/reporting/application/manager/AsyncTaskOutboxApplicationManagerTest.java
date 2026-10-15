package com.ezaigc.hr.reporting.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.domain.entity.AsyncTaskEntity;
import com.ezaigc.hr.reporting.outbox.constant.OutboxConstants;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class AsyncTaskOutboxApplicationManagerTest {
  private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
  private static final String WORK_ID = "W".repeat(26);
  private static final String PUBLICATION_ID = "P".repeat(26);
  private static final String USER_ID = "U".repeat(26);
  private static final String DEPARTMENT_ID = "D".repeat(26);

  private final AsyncTaskService tasks = mock(AsyncTaskService.class);
  private final OutboxEventService outboxEvents = mock(OutboxEventService.class);
  private final ApplicationEventPublisher notifications = mock(ApplicationEventPublisher.class);
  private final AsyncTaskApplicationManager manager = new AsyncTaskApplicationManager();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "service", tasks);
    ReflectionTestUtils.setField(manager, "outboxEvents", outboxEvents);
    ReflectionTestUtils.setField(manager, "events", notifications);
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> PUBLICATION_ID);
  }

  @Test
  void durableTaskAndItsPublicationArePersistedWithOneImmutableOutboxBinding() {
    when(tasks.findTaskByIdempotency(anyString(), anyString())).thenReturn(null);
    when(tasks.insertIgnore(any())).thenReturn(1);
    when(tasks.findTask(WORK_ID)).thenReturn(snapshot(WORK_ID, "RESUME_PARSE", "RESUME_VERSION", "R".repeat(26)));
    when(tasks.findTask(PUBLICATION_ID)).thenReturn(
        snapshot(PUBLICATION_ID, OutboxConstants.PUBLISH_TASK_TYPE, "ASYNC_TASK", WORK_ID));
    AtomicReference<OutboxEventEntity> persisted = new AtomicReference<>();
    when(outboxEvents.saveIfAbsent(any())).thenAnswer(invocation -> {
      persisted.compareAndSet(null, invocation.getArgument(0));
      return persisted.get();
    });

    TaskSnapshot created = manager.enqueue(command(WORK_ID, "RESUME_PARSE", "RESUME_VERSION", "R".repeat(26)));

    assertThat(created.id()).isEqualTo(WORK_ID);
    var inserted = ArgumentCaptor.forClass(AsyncTaskEntity.class);
    verify(tasks, times(2)).insertIgnore(inserted.capture());
    assertThat(inserted.getAllValues()).extracting(AsyncTaskEntity::getTaskType)
        .containsExactly("RESUME_PARSE", OutboxConstants.PUBLISH_TASK_TYPE);
    assertThat(persisted.get()).satisfies(event -> {
      assertThat(event.getId()).isEqualTo(PUBLICATION_ID);
      assertThat(event.getWorkTaskId()).isEqualTo(WORK_ID);
      assertThat(event.getWorkTaskType()).isEqualTo("RESUME_PARSE");
      assertThat(event.getAggregateType()).isEqualTo("RESUME_VERSION");
      assertThat(event.getAggregateId()).isEqualTo("R".repeat(26));
      assertThat(event.getEventVersion()).isOne();
      assertThat(event.getTraceId()).matches("[A-Za-z0-9_-]{16,64}");
    });
  }

  @Test
  void publicationTaskDoesNotRecursivelyCreateAnotherOutboxEvent() {
    when(tasks.findTaskByIdempotency(USER_ID, "publication-key")).thenReturn(null);
    when(tasks.insertIgnore(any())).thenReturn(1);
    when(tasks.findTask(PUBLICATION_ID)).thenReturn(
        snapshot(PUBLICATION_ID, OutboxConstants.PUBLISH_TASK_TYPE, "ASYNC_TASK", WORK_ID));

    TaskSnapshot created = manager.enqueue(command(
        PUBLICATION_ID, OutboxConstants.PUBLISH_TASK_TYPE, "ASYNC_TASK", WORK_ID));

    assertThat(created.taskType()).isEqualTo(OutboxConstants.PUBLISH_TASK_TYPE);
    verifyNoInteractions(outboxEvents);
  }

  @ParameterizedTest
  @MethodSource("outboxTraceInputs")
  void outboxEnqueuePreservesSafeTraceAndReplacesUnsafeTrace(String trace, boolean safe) {
    when(tasks.findTaskByIdempotency(anyString(), anyString())).thenReturn(null);
    when(tasks.insertIgnore(any())).thenReturn(1);
    when(tasks.findTask(WORK_ID)).thenReturn(snapshot(WORK_ID, "RESUME_PARSE", "RESUME_VERSION", "R".repeat(26)));
    when(tasks.findTask(PUBLICATION_ID)).thenReturn(
        snapshot(PUBLICATION_ID, OutboxConstants.PUBLISH_TASK_TYPE, "ASYNC_TASK", WORK_ID));
    when(outboxEvents.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
    try (var context = mockStatic(MDC.class)) {
      context.when(() -> MDC.get("traceId")).thenReturn(trace);
      manager.enqueue(command(WORK_ID, "RESUME_PARSE", "RESUME_VERSION", "R".repeat(26)));
      var event = ArgumentCaptor.forClass(OutboxEventEntity.class);
      verify(outboxEvents).saveIfAbsent(event.capture());
      var inserted = ArgumentCaptor.forClass(AsyncTaskEntity.class);
      verify(tasks, times(2)).insertIgnore(inserted.capture());
      if (safe) {
        assertThat(event.getValue().getTraceId()).isEqualTo(trace);
        assertThat(inserted.getAllValues()).extracting(AsyncTaskEntity::getTraceId).containsOnly(trace);
      } else {
        assertThat(event.getValue().getTraceId()).matches("[a-f0-9]{32}").isNotEqualTo(trace);
        assertThat(inserted.getAllValues()).allSatisfy(task ->
            assertThat(task.getTraceId()).matches("[a-f0-9]{32}").isNotEqualTo(trace));
      }
    }
  }

  private static Stream<Arguments> outboxTraceInputs() {
    return Stream.of(
        Arguments.of(null, false), Arguments.of("", false),
        Arguments.of("a".repeat(15), false), Arguments.of("a".repeat(16), true),
        Arguments.of("Z_9-".repeat(16), true), Arguments.of("a".repeat(65), false),
        Arguments.of("unsafe-trace-123456\nforged", false),
        Arguments.of("含有个人内容的输入不能作为日志关联ID", false),
        Arguments.of("01234567-89ab-cdef-0123-456789abcdef", true));
  }

  private static EnqueueTask command(String id, String type, String resourceType, String resourceId) {
    String key = OutboxConstants.PUBLISH_TASK_TYPE.equals(type) ? "publication-key" : "resume-parse-key";
    return new EnqueueTask(id, type, resourceType, resourceId, USER_ID, USER_ID, DEPARTMENT_ID,
        "talent.read", "talent.manage", key, "a".repeat(64), 5, NOW);
  }

  private static TaskSnapshot snapshot(String id, String type, String resourceType, String resourceId) {
    return new TaskSnapshot(id, type, resourceType, resourceId, USER_ID, USER_ID, DEPARTMENT_ID,
        "talent.read", "talent.manage", "QUEUED", 0, 5, 0, null, null, false, null, 0,
        NOW, null, null, NOW, "trace-0123456789012345");
  }
}
