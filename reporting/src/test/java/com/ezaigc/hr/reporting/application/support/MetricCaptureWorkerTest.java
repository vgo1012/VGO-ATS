package com.ezaigc.hr.reporting.application.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.application.manager.MetricCaptureApplicationManager;
import com.ezaigc.hr.reporting.config.MetricCaptureProperties;
import com.ezaigc.hr.reporting.enums.MetricCaptureLane;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;

class MetricCaptureWorkerTest {

  @Test
  void shutdownSkipsQueuedCallbacksAndRejectsNewTriggers() {
    var manager = mock(MetricCaptureApplicationManager.class);
    var queued = new ArrayList<Runnable>();
    var worker = worker(manager, queued::add);
    worker.pollIncremental();
    worker.reconcile();
    assertThat(queued).hasSize(2);
    worker.stop();
    queued.forEach(Runnable::run);
    worker.pollIncremental();
    worker.reconcile();
    assertThat(queued).hasSize(2);
    verifyNoInteractions(manager);
  }

  @Test
  void shutdownDuringTransactionAllowsItToFinishButSkipsNextSource() {
    var manager = mock(MetricCaptureApplicationManager.class);
    var worker = worker(manager, Runnable::run);
    when(manager.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 500))
        .thenAnswer(invocation -> {
          worker.stop();
          assertThat(Thread.currentThread().isInterrupted()).isFalse();
          return 1;
        });
    worker.pollIncremental();
    verify(manager).captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 500);
    verifyNoMoreInteractions(manager);
  }

  @Test
  void interruptedEntryAndQueuedCallbackDoNotEnterDatabaseAndKeepInterruptFlag() {
    var manager = mock(MetricCaptureApplicationManager.class);
    var queued = new ArrayList<Runnable>();
    var worker = worker(manager, queued::add);
    worker.pollIncremental();
    try {
      Thread.currentThread().interrupt();
      worker.pollIncremental();
      worker.reconcile();
      queued.getFirst().run();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(queued).hasSize(1);
      verifyNoInteractions(manager);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void stoppingOrInterruptingOnTransientFailurePreventsRetryAndNextSource() {
    for (boolean interrupt : List.of(false, true)) {
      var manager = mock(MetricCaptureApplicationManager.class);
      var worker = worker(manager, Runnable::run);
      when(manager.captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 500))
          .thenAnswer(invocation -> {
            if (interrupt) Thread.currentThread().interrupt();
            else worker.stop();
            throw new CannotAcquireLockException("stop during transient failure");
          });
      try {
        worker.pollIncremental();
        assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
        verify(manager).captureNext(MetricEventType.RESUME_INGESTED, MetricCaptureLane.INCREMENTAL, 500);
        verifyNoMoreInteractions(manager);
      } finally {
        Thread.interrupted();
      }
    }
  }

  private static MetricCaptureWorker worker(MetricCaptureApplicationManager manager, TaskExecutor executor) {
    var worker = new MetricCaptureWorker();
    ReflectionTestUtils.setField(worker, "manager", manager);
    ReflectionTestUtils.setField(worker, "properties", new MetricCaptureProperties());
    ReflectionTestUtils.setField(worker, "taskExecutor", executor);
    return worker;
  }

  @Test
  void incrementalCaptureRetriesTransientDatabaseConflict() {
    MetricCaptureApplicationManager manager = mock(MetricCaptureApplicationManager.class);
    MetricCaptureProperties properties = new MetricCaptureProperties();
    MetricCaptureWorker worker = new MetricCaptureWorker();
    ReflectionTestUtils.setField(worker, "manager", manager);
    ReflectionTestUtils.setField(worker, "properties", properties);
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) Runnable::run);
    when(manager.captureNext(
            MetricEventType.RESUME_INGESTED,
            MetricCaptureLane.INCREMENTAL,
            properties.getBatchSize()))
        .thenThrow(new CannotAcquireLockException("deadlock"))
        .thenReturn(1);

    worker.pollIncremental();

    verify(manager, times(2))
        .captureNext(
            MetricEventType.RESUME_INGESTED,
            MetricCaptureLane.INCREMENTAL,
            properties.getBatchSize());
    for (MetricEventType source : MetricEventType.values()) {
      if (source != MetricEventType.RESUME_INGESTED) {
        verify(manager)
            .captureNext(source, MetricCaptureLane.INCREMENTAL, properties.getBatchSize());
      }
    }
  }
}
