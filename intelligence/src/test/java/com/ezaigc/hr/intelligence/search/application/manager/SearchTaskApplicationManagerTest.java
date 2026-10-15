package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.talent.api.ResumeFactProjectionSource.TASK_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxDeliveryPort;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource.State;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SearchTaskApplicationManagerTest {
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final OutboxDeliveryPort deliveries = mock(OutboxDeliveryPort.class);
  private final ResumeSearchStateSource source = mock(ResumeSearchStateSource.class);
  private final SearchIndexPort index = mock(SearchIndexPort.class);
  private final SearchIndexAuthorityApplicationManager authority = mock(SearchIndexAuthorityApplicationManager.class);
  private final TaskSnapshot task = mock(TaskSnapshot.class);
  private final SearchTaskApplicationManager manager = new SearchTaskApplicationManager();
  private final Instant now = Instant.parse("2026-09-08T00:00:00Z");

  SearchTaskApplicationManagerTest() {
    ReflectionTestUtils.setField(manager, "tasks", tasks);
    ReflectionTestUtils.setField(manager, "deliveries", deliveries);
    ReflectionTestUtils.setField(manager, "source", source);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "authority", authority);
    ReflectionTestUtils.setField(manager, "factory", new SearchDocumentFactory());
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    when(task.id()).thenReturn("task"); when(task.taskType()).thenReturn(TASK_TYPE);
    when(task.resourceType()).thenReturn("CANDIDATE"); when(task.resourceId()).thenReturn("candidate");
    when(task.attemptCount()).thenReturn(1);
    var envelope = mock(OutboxEnvelope.class);
    when(envelope.getTraceId()).thenReturn("original-trace");
    when(deliveries.forTask(task)).thenReturn(envelope);
    when(source.currentState("candidate")).thenReturn(
        new State("candidate", 3, "姓名", "mail@example.com", "13800138000", "new-version", 2, false, null, now, null));
    when(authority.currentTarget()).thenReturn(new SearchIndexTarget("physical-index", 3));
  }

  @Test
  void readsCurrentStateBeforeResolvingThePhysicalIndexAndRestoresOriginalTrace() {
    String previous = MDC.get("traceId");
    MDC.put("traceId", "caller");
    try {
      doAnswer(call -> { assertThat(MDC.get("traceId")).isEqualTo("original-trace"); return null; })
          .when(index).upsertBatch(eq(new SearchIndexTarget("physical-index", 3)), any());
      manager.process(task);
      var ordered = inOrder(source, authority, index, tasks);
      ordered.verify(source).currentState("candidate");
      ordered.verify(authority).currentTarget();
      ordered.verify(index).upsertBatch(eq(new SearchIndexTarget("physical-index", 3)), any());
      ordered.verify(tasks).succeed(task, "candidate");
      assertThat(MDC.get("traceId")).isEqualTo("caller");
    } finally { if (previous == null) MDC.remove("traceId"); else MDC.put("traceId", previous); }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3})
  void cancellationOrLostLeaseBeforeExternalWriteStopsTheAttempt(int stopAt) {
    when(tasks.shouldStopProcessing(task)).thenReturn(stopAt == 1, stopAt == 2, stopAt == 3);
    manager.process(task);
    verify(index, never()).upsertBatch(any(), any());
    verify(tasks, never()).succeed(any(), any());
  }

  @Test
  void transientIndexFailurePersistsABoundedFutureRetry() {
    doThrow(new IllegalStateException("synthetic secret")).when(index).upsertBatch(any(), any());
    manager.process(task);
    var retry = ArgumentCaptor.forClass(Instant.class);
    verify(tasks).failAndRetryAt(eq(task), eq("SEARCH_PROJECTION_FAILED"), any(), retry.capture());
    assertThat(retry.getValue()).isAfter(now).isBefore(now.plusSeconds(3600));
    verify(tasks, never()).succeed(any(), any());
  }

  @Test
  void invalidPersistedBindingFailsWithoutReadingCandidateData() {
    when(deliveries.forTask(task)).thenThrow(new IllegalArgumentException("invalid"));
    manager.process(task);
    verify(tasks).fail(eq(task), eq("SEARCH_TASK_BINDING_INVALID"), any(), eq(false));
    verifyNoInteractions(source, index);
  }
}
