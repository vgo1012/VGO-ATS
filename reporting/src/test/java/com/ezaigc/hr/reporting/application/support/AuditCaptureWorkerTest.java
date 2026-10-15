package com.ezaigc.hr.reporting.application.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.reporting.application.manager.AuditApplicationManager;
import com.ezaigc.hr.reporting.config.AuditCaptureProperties;
import com.ezaigc.hr.reporting.enums.AuditCaptureLane;
import com.ezaigc.hr.reporting.enums.AuditCaptureSource;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;

class AuditCaptureWorkerTest {

  @ParameterizedTest
  @CsvSource({"INCREMENTAL,false", "INCREMENTAL,true", "RECONCILIATION,false", "RECONCILIATION,true"})
  void stoppingOrInterruptingSkipsQueuedCallbacksAndRejectsNewTriggers(
      AuditCaptureLane lane, boolean interrupt) {
    var manager = mock(AuditApplicationManager.class);
    var queued = new ArrayList<Runnable>();
    var worker = worker(manager, queued::add);
    trigger(worker, lane);
    assertThat(queued).hasSize(1);
    try {
      stopOrInterrupt(worker, interrupt);
      queued.getFirst().run();
      worker.pollIncremental();
      worker.reconcile();
      assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
      assertThat(queued).hasSize(1);
      verifyNoInteractions(manager);
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @EnumSource(AuditCaptureLane.class)
  void interruptedEntryRejectsNewWorkAndKeepsInterruptFlag(AuditCaptureLane lane) {
    var manager = mock(AuditApplicationManager.class);
    var queued = new ArrayList<Runnable>();
    var worker = worker(manager, queued::add);
    try {
      Thread.currentThread().interrupt();
      trigger(worker, lane);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(queued).isEmpty();
      verifyNoInteractions(manager);
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @CsvSource({"INCREMENTAL,false", "INCREMENTAL,true", "RECONCILIATION,false", "RECONCILIATION,true"})
  void stoppingOrInterruptingDuringTransactionAllowsItToFinishButSkipsNextSource(
      AuditCaptureLane lane, boolean interrupt) {
    var manager = mock(AuditApplicationManager.class);
    var worker = worker(manager, Runnable::run);
    var completed = new ArrayList<AuditCaptureSource>();
    when(manager.captureNext(AuditCaptureSource.IDENTITY, lane, limit(lane)))
        .thenAnswer(invocation -> {
          stopOrInterrupt(worker, interrupt);
          completed.add(AuditCaptureSource.IDENTITY);
          return 1;
        });
    try {
      trigger(worker, lane);
      assertThat(completed).containsExactly(AuditCaptureSource.IDENTITY);
      assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
      verify(manager).captureNext(AuditCaptureSource.IDENTITY, lane, limit(lane));
      verifyNoMoreInteractions(manager);
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @CsvSource({"INCREMENTAL,false", "INCREMENTAL,true", "RECONCILIATION,false", "RECONCILIATION,true"})
  void stoppingOrInterruptingOnTransientFailurePreventsRetryAndNextSource(
      AuditCaptureLane lane, boolean interrupt) {
    var manager = mock(AuditApplicationManager.class);
    var worker = worker(manager, Runnable::run);
    when(manager.captureNext(AuditCaptureSource.IDENTITY, lane, limit(lane)))
        .thenAnswer(invocation -> {
          stopOrInterrupt(worker, interrupt);
          throw new CannotAcquireLockException("stop during transient failure");
        });
    try {
      trigger(worker, lane);
      assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
      verify(manager).captureNext(AuditCaptureSource.IDENTITY, lane, limit(lane));
      verifyNoMoreInteractions(manager);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void lanesCoalesceIndependentlyAndCaptureEverySource() {
    var manager = mock(AuditApplicationManager.class);
    var queued = new ArrayList<Runnable>();
    var worker = worker(manager, queued::add);
    worker.pollIncremental();
    worker.pollIncremental();
    worker.reconcile();
    worker.reconcile();
    assertThat(queued).hasSize(2);
    queued.forEach(Runnable::run);
    for (var lane : AuditCaptureLane.values()) {
      for (var source : AuditCaptureSource.values()) {
        verify(manager).captureNext(source, lane, limit(lane));
      }
    }
    verifyNoMoreInteractions(manager);
  }

  @ParameterizedTest
  @EnumSource(AuditCaptureLane.class)
  void retryExhaustionAndPermanentFailureStillVisitOtherSourcesAndAggregateErrors(
      AuditCaptureLane lane) {
    var manager = mock(AuditApplicationManager.class);
    var monitor = mock(ScheduledTaskMonitor.class);
    var worker = worker(manager, Runnable::run);
    ReflectionTestUtils.setField(worker, "taskMonitor", monitor);
    worker.observeScheduledTasks();
    var transientFailure = new CannotAcquireLockException("deadlock");
    var permanentFailure = new IllegalStateException("invalid source");
    when(manager.captureNext(AuditCaptureSource.IDENTITY, lane, limit(lane)))
        .thenThrow(transientFailure);
    when(manager.captureNext(AuditCaptureSource.DEMAND, lane, limit(lane)))
        .thenThrow(permanentFailure);

    trigger(worker, lane);

    verify(manager, times(3)).captureNext(AuditCaptureSource.IDENTITY, lane, limit(lane));
    for (var source : AuditCaptureSource.values()) {
      if (source != AuditCaptureSource.IDENTITY) {
        verify(manager).captureNext(source, lane, limit(lane));
      }
    }
    verifyNoMoreInteractions(manager);
    assertThat(transientFailure.getSuppressed()).containsExactly(permanentFailure);
    assertThat(permanentFailure.getSuppressed()).isEmpty();
    verify(monitor).finished("AUDIT_CAPTURE_" + lane.name(), null, "SCAN_FAILED");
  }

  private static AuditCaptureWorker worker(AuditApplicationManager manager, TaskExecutor executor) {
    var worker = new AuditCaptureWorker();
    ReflectionTestUtils.setField(worker, "manager", manager);
    ReflectionTestUtils.setField(worker, "properties", new AuditCaptureProperties());
    ReflectionTestUtils.setField(worker, "taskExecutor", executor);
    return worker;
  }

  private static void trigger(AuditCaptureWorker worker, AuditCaptureLane lane) {
    if (lane == AuditCaptureLane.INCREMENTAL) worker.pollIncremental();
    else worker.reconcile();
  }

  private static int limit(AuditCaptureLane lane) {
    var properties = new AuditCaptureProperties();
    return lane == AuditCaptureLane.INCREMENTAL
        ? properties.getBatchSize() : properties.getReconciliationBatchSize();
  }

  private static void stopOrInterrupt(AuditCaptureWorker worker, boolean interrupt) {
    if (interrupt) {
      Thread.currentThread().interrupt();
    } else {
      // 通过实际生命周期回调停止；原实现没有销毁回调时仍可编译并验证行为。
      var lifecycle = new InitDestroyAnnotationBeanPostProcessor();
      lifecycle.setDestroyAnnotationType(PreDestroy.class);
      lifecycle.postProcessBeforeInitialization(worker, "auditCaptureWorker");
      lifecycle.postProcessBeforeDestruction(worker, "auditCaptureWorker");
    }
  }

  @Test
  void incrementalCaptureRetriesTransientDatabaseConflict() {
    AuditApplicationManager manager = mock(AuditApplicationManager.class);
    AuditCaptureProperties properties = new AuditCaptureProperties();
    AuditCaptureWorker worker = new AuditCaptureWorker();
    ReflectionTestUtils.setField(worker, "manager", manager);
    ReflectionTestUtils.setField(worker, "properties", properties);
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) Runnable::run);
    when(manager.captureNext(
            AuditCaptureSource.IDENTITY,
            AuditCaptureLane.INCREMENTAL,
            properties.getBatchSize()))
        .thenThrow(new CannotAcquireLockException("deadlock"))
        .thenReturn(1);

    worker.pollIncremental();

    verify(manager, times(2))
        .captureNext(
            AuditCaptureSource.IDENTITY,
            AuditCaptureLane.INCREMENTAL,
            properties.getBatchSize());
    for (AuditCaptureSource source : AuditCaptureSource.values()) {
      if (source != AuditCaptureSource.IDENTITY) {
        verify(manager)
            .captureNext(source, AuditCaptureLane.INCREMENTAL, properties.getBatchSize());
      }
    }
  }
}
