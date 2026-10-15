package com.ezaigc.hr.common.infrastructure.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class ScheduledTaskMonitorTest {
    @Test
    void submissionIsQueuedNotCompletedAndCoalescingDoesNotOverwriteActiveWork() {
        var monitor = new ScheduledTaskMonitor();
        var dispatcher = new CoalescingTaskDispatcher();
        dispatcher.observe(monitor);
        List<Runnable> queue = new ArrayList<>();
        dispatcher.dispatch("scan", queue::add, () -> {
            assertThat(monitor.snapshot("scan").getRunning()).isEqualTo(1);
            assertThat(monitor.snapshot("scan").getLastCompletedAt()).isNull();
        });
        assertThat(monitor.snapshot("scan").getQueued()).isEqualTo(1);
        assertThat(monitor.snapshot("scan").getLastCompletedAt()).isNull();
        assertThat(dispatcher.dispatch("scan", queue::add, () -> {})).isFalse();
        assertThat(monitor.snapshot("scan").getCoalesced()).isEqualTo(1);
        queue.getFirst().run();
        var snapshot = monitor.snapshot("scan");
        assertThat(snapshot.getRunning()).isZero();
        assertThat(snapshot.getQueued()).isZero();
        assertThat(snapshot.getLastCompletedAt()).isNotNull();
        assertThat(snapshot.getRecentScans()).singleElement().satisfies(scan -> {
            assertThat(scan.getState().name()).isEqualTo("COMPLETED");
            assertThat(scan.getStartedAt()).isNotNull();
            assertThat(scan.getFinishedAt()).isNotNull();
            assertThat(scan.getDurationMillis()).isNotNegative();
        });
    }

    @Test
    void failuresAreSanitizedAndDoNotPreventLaterScans() {
        var monitor = new ScheduledTaskMonitor();
        var dispatcher = new CoalescingTaskDispatcher();
        dispatcher.observe(monitor);
        dispatcher.dispatch("scan", Runnable::run, () -> { throw new IllegalStateException("private secret"); });
        assertThat(monitor.snapshot("scan").getFailed()).isEqualTo(1);
        assertThat(monitor.snapshot("scan").getRecentScans().getFirst().getErrorCode()).isEqualTo("SCAN_FAILED");
        assertThat(monitor.snapshot("scan").toString()).doesNotContain("private secret");
        dispatcher.dispatch("scan", task -> { throw new TaskRejectedException("private queue detail"); }, () -> {});
        assertThat(monitor.snapshot("scan").getRejected()).isEqualTo(1);
        assertThat(monitor.snapshot("scan").getRecentScans().getFirst().getState().name()).isEqualTo("REJECTED");
        assertThat(dispatcher.dispatch("scan", Runnable::run, () -> {})).isTrue();
        assertThat(monitor.snapshot("scan").getRecentScans().getFirst().getState().name()).isEqualTo("COMPLETED");
    }

    @Test
    void terminalHistoryIsBoundedAndActiveScansAreNotEvicted() {
        var monitor = new ScheduledTaskMonitor();
        String active = monitor.queued("scan");
        monitor.started("scan", active);
        for (int index = 0; index < 100; index++) {
            String run = monitor.queued("scan");
            monitor.started("scan", run);
            monitor.finished("scan", run, null);
        }
        assertThat(monitor.snapshot("scan").getRecentScans()).hasSize(21);
        assertThat(monitor.snapshot("scan").getRunning()).isEqualTo(1);
        monitor.finished("scan", active, "SCAN_FAILED");
        assertThat(monitor.snapshot("scan").getRecentScans()).hasSize(20);
    }

    @Test
    void concurrentScansHaveIndependentRunsAndCannotClearEachOther() throws Exception {
        var monitor = new ScheduledTaskMonitor();
        var dispatcher = new CoalescingTaskDispatcher(2);
        dispatcher.observe(monitor);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Runnable work = () -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            };
            dispatcher.dispatch("scan", executor::execute, work);
            dispatcher.dispatch("scan", executor::execute, work);
            try {
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(monitor.snapshot("scan").getRunning()).isEqualTo(2);
                assertThat(monitor.snapshot("scan").getRecentScans().stream().map(ScheduledScanSnapshot::getRunId).distinct()).hasSize(2);
            } finally { release.countDown(); }
        }
        assertThat(monitor.snapshot("scan").getRunning()).isZero();
        assertThat(monitor.snapshot("scan").getRecentScans()).hasSize(2);
    }

    @Test
    void newInstanceDoesNotPretendPreviousProcessHistorySurvived() {
        var first = new ScheduledTaskMonitor();
        first.triggered("scan");
        var restarted = new ScheduledTaskMonitor();
        assertThat(restarted.instanceId()).isNotEqualTo(first.instanceId());
        assertThat(restarted.snapshot("scan").getLastTriggeredAt()).isNull();
        assertThat(restarted.snapshot("scan").getRecentScans()).isEmpty();
    }

    @Test
    void fatalErrorIsNotRecordedAsCompletedAndStillPropagates() {
        var monitor = new ScheduledTaskMonitor();
        var dispatcher = new CoalescingTaskDispatcher();
        dispatcher.observe(monitor);
        List<Runnable> queue = new ArrayList<>();
        dispatcher.dispatch("scan", queue::add, () -> { throw new AssertionError("synthetic"); });
        assertThatThrownBy(() -> queue.getFirst().run()).isInstanceOf(AssertionError.class);
        assertThat(monitor.snapshot("scan").getRecentScans().getFirst().getErrorCode()).isEqualTo("SCAN_ABORTED");
        assertThat(dispatcher.dispatch("scan", Runnable::run, () -> {})).isTrue();
    }
}
