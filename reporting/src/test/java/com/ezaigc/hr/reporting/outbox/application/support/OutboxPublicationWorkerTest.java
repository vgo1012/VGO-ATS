package com.ezaigc.hr.reporting.outbox.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublicationSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxPublicationWorkerTest {
    private final OutboxPublicationSource first = mock(OutboxPublicationSource.class);
    private final OutboxPublicationSource second = mock(OutboxPublicationSource.class);
    private final OutboxPublicationWorker worker = new OutboxPublicationWorker();
    private final List<Runnable> queued = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("first", first);
        beans.addBean("second", second);
        ReflectionTestUtils.setField(worker, "sourceProvider", beans.getBeanProvider(OutboxPublicationSource.class));
        ReflectionTestUtils.setField(worker, "executor", (TaskExecutor) queued::add);
        ReflectionTestUtils.setField(worker, "executorProperties", new PlatformExecutorProperties());
        worker.initializeConcurrency();
    }

    @Test
    void mergesTriggersAndStopsDrainingWhenNoTaskWasClaimed() {
        when(first.publishOne()).thenReturn(1, 0);
        when(second.publishOne()).thenReturn(1, 0);
        worker.poll(); worker.poll();
        assertThat(queued).hasSize(2);
        queued.forEach(Runnable::run);
        verify(first, times(3)).publishOne();
        verify(second, times(3)).publishOne();
        worker.poll();
        assertThat(queued).hasSize(2);
    }

    @Test
    void aSingleTriggerHasAFiniteDrainBudget() {
        when(first.publishOne()).thenReturn(1);
        when(second.publishOne()).thenReturn(1);
        worker.poll();
        queued.getFirst().run();
        verify(first, times(25)).publishOne();
        verify(second, times(25)).publishOne();
    }

    @Test
    void closingTheWorkerPreventsQueuedAndNewClaims() {
        worker.poll(); worker.stop(); worker.poll();
        assertThat(queued).hasSize(2);
        queued.getFirst().run();
        verifyNoInteractions(first, second);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingBetweenEmptySourcesPreventsNextClaim(boolean interrupt) {
        when(first.publishOne()).thenAnswer(invocation -> {
            stopOrInterrupt(interrupt);
            return 0;
        });
        worker.poll();
        try {
            queued.forEach(Runnable::run);
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            verify(first).publishOne();
            verifyNoMoreInteractions(first);
            verifyNoInteractions(second);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingKeepsCurrentPublicationButSkipsNewWork(boolean interrupt) {
        var completed = new ArrayList<String>();
        when(first.publishOne()).thenAnswer(invocation -> {
            stopOrInterrupt(interrupt);
            completed.add("first");
            return 1;
        });
        worker.poll();
        try {
            queued.getFirst().run();
            assertThat(completed).containsExactly("first");
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            verify(first).publishOne();
            verifyNoMoreInteractions(first);
            verifyNoInteractions(second);
            if (interrupt) {
                Thread.interrupted();
                // 已处理计数仍为正数，不应将本批误判为空闲而进入轮询退避。
                worker.poll();
                assertThat(queued).hasSize(3);
            } else {
                queued.get(1).run();
                worker.poll();
                assertThat(queued).hasSize(2);
            }
            verifyNoInteractions(second);
        } finally {
            Thread.interrupted();
        }
    }

    private void stopOrInterrupt(boolean interrupt) {
        if (interrupt) Thread.currentThread().interrupt();
        else {
            worker.stop();
            worker.stop();
        }
    }
}
