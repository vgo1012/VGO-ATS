package com.ezaigc.hr.recruiting.interview.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewExternalCalendarApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewExternalCalendarProjection;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewExternalCalendarWorkerTest {
    private final InterviewExternalCalendarApplicationManager manager =
            mock(InterviewExternalCalendarApplicationManager.class);
    private final ScheduledTaskMonitor monitor = mock(ScheduledTaskMonitor.class);
    private final InterviewExternalCalendarWorker worker = new InterviewExternalCalendarWorker();
    private final List<Runnable> queued = new ArrayList<>();
    private final AtomicLong ticker = new AtomicLong();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "manager", manager);
        ReflectionTestUtils.setField(worker, "monitor", monitor);
        ReflectionTestUtils.setField(worker, "executor", (TaskExecutor) queued::add);
        ReflectionTestUtils.setField(worker, "dispatcher", new CoalescingTaskDispatcher(1, ticker::get));
        worker.observe();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingRejectsNewTriggers(boolean interrupt) {
        try {
            stopOrInterrupt(interrupt);
            worker.poll();
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            assertThat(queued).isEmpty();
            verifyNoInteractions(manager);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingSkipsQueuedQuery(boolean interrupt) {
        worker.poll();
        try {
            stopOrInterrupt(interrupt);
            queued.getFirst().run();
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            verifyNoInteractions(manager);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingAfterQuerySkipsProcessingAndZeroCallsBackOff(boolean interrupt) {
        when(manager.due(10)).thenAnswer(invocation -> {
            stopOrInterrupt(interrupt);
            return List.of(projection("first"), projection("second"));
        });
        worker.poll();
        try {
            queued.getFirst().run();
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            verify(manager).due(10);
            verifyNoMoreInteractions(manager);
            if (interrupt) {
                Thread.interrupted();
                worker.poll();
                assertThat(queued).hasSize(1);
                ticker.set(Duration.ofSeconds(5).toNanos());
                worker.poll();
                assertThat(queued).hasSize(2);
            }
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingDuringFirstProcessAllowsCompletionAndKeepsWorkCount(boolean interrupt) {
        var completed = new ArrayList<String>();
        when(manager.due(10)).thenReturn(List.of(projection("first"), projection("second")));
        doAnswer(invocation -> {
            stopOrInterrupt(interrupt);
            completed.add("first");
            return null;
        }).when(manager).process("first");
        worker.poll();
        try {
            queued.getFirst().run();
            assertThat(completed).containsExactly("first");
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            verify(manager).due(10);
            verify(manager).process("first");
            verifyNoMoreInteractions(manager);
            if (interrupt) {
                Thread.interrupted();
                worker.poll();
                assertThat(queued).hasSize(2);
            }
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void normalBatchProcessesInOrderAndCoalescesTriggers() {
        when(manager.due(10)).thenReturn(List.of(projection("first"), projection("second")));
        worker.poll();
        worker.poll();
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        var order = inOrder(manager);
        order.verify(manager).due(10);
        order.verify(manager).process("first");
        order.verify(manager).process("second");
        verifyNoMoreInteractions(manager);
        verify(monitor).coalesced("wecom-interview-calendar");
        verify(monitor).finished("wecom-interview-calendar", null, null);
        worker.poll();
        assertThat(queued).hasSize(2);
    }

    @Test
    void emptyBatchBacksOffUntilControlledTimeAdvances() {
        when(manager.due(10)).thenReturn(List.of());
        worker.poll();
        queued.getFirst().run();
        worker.poll();
        assertThat(queued).hasSize(1);
        verify(manager).due(10);
        verifyNoMoreInteractions(manager);
        ticker.set(Duration.ofSeconds(5).toNanos());
        worker.poll();
        assertThat(queued).hasSize(2);
    }

    @Test
    void processingFailureKeepsExistingScanFailureAndStopsBatch() {
        when(manager.due(10)).thenReturn(List.of(projection("first"), projection("second")));
        doThrow(new IllegalStateException("processing failure")).when(manager).process("first");
        worker.poll();
        queued.getFirst().run();
        verify(manager).due(10);
        verify(manager).process("first");
        verifyNoMoreInteractions(manager);
        verify(monitor).finished("wecom-interview-calendar", null, "SCAN_FAILED");
        worker.poll();
        assertThat(queued).hasSize(1);
    }

    private static InterviewExternalCalendarProjection projection(String id) {
        var value = new InterviewExternalCalendarProjection();
        value.setId(id);
        return value;
    }

    private void stopOrInterrupt(boolean interrupt) {
        if (interrupt) {
            Thread.currentThread().interrupt();
        } else {
            var lifecycle = new InitDestroyAnnotationBeanPostProcessor();
            lifecycle.setDestroyAnnotationType(PreDestroy.class);
            lifecycle.postProcessBeforeInitialization(worker, "interviewExternalCalendarWorker");
            lifecycle.postProcessBeforeDestruction(worker, "interviewExternalCalendarWorker");
            lifecycle.postProcessBeforeDestruction(worker, "interviewExternalCalendarWorker");
        }
    }
}
