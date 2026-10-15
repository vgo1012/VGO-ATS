package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportProcessingManager;
import com.ezaigc.hr.reporting.monthlyreport.application.manager.MonthlyReportTransactionManager;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportWorker;
import com.ezaigc.hr.reporting.monthlyreport.config.MonthlyReportProperties;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

class MonthlyReportWorkerSchedulingTest {
    @Test
    void emptyPollBacksOffButCommitNotificationWakesItAndDisabledModeStaysDisabled() {
        MonthlyReportWorker worker = new MonthlyReportWorker();
        MonthlyReportProperties properties = new MonthlyReportProperties();
        AsyncTaskBatchRunner runner = mock(AsyncTaskBatchRunner.class);
        ReflectionTestUtils.setField(worker, "properties", properties);
        ReflectionTestUtils.setField(worker, "runner", runner);
        ReflectionTestUtils.setField(worker, "manager", mock(MonthlyReportProcessingManager.class));
        ReflectionTestUtils.setField(worker, "executor", (TaskExecutor) Runnable::run);
        worker.poll();
        worker.poll();
        verify(runner, times(2)).run(any(), anyInt(), any());
        worker.available(new AsyncTaskAvailable("MONTHLY_REPORT_VALIDATE", "new", null));
        verify(runner, times(4)).run(any(), anyInt(), any());
        properties.setEnabled(false);
        worker.available(new AsyncTaskAvailable("MONTHLY_RECRUITMENT_REPORT", "disabled", null));
        verify(runner, times(4)).run(any(), anyInt(), any());
    }

    @Test
    void recoveryAndDeletionUseIndependentDispatchersAndIntervals() throws Exception {
        MonthlyReportWorker worker = new MonthlyReportWorker();
        MonthlyReportProcessingManager processing = mock(MonthlyReportProcessingManager.class);
        MonthlyReportTransactionManager transactions = mock(MonthlyReportTransactionManager.class);
        ReflectionTestUtils.setField(worker, "manager", processing);
        ReflectionTestUtils.setField(worker, "transactions", transactions);
        ReflectionTestUtils.setField(worker, "executor", (TaskExecutor) Runnable::run);
        worker.recoverUploads();
        verify(transactions).recoverAbandonedUploads();
        verifyNoInteractions(processing);
        worker.cleanup();
        verify(processing).cleanup();
        assertThat(MonthlyReportWorker.class.getMethod("recoverUploads").getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${hr.reporting.monthly-report.recovery-delay:5m}");
        assertThat(MonthlyReportWorker.class.getMethod("cleanup").getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${hr.reporting.monthly-report.cleanup-delay:1h}");
    }

    @ParameterizedTest
    @CsvSource({"POLL,false", "POLL,true", "RECOVERY,false", "RECOVERY,true", "CLEANUP,false", "CLEANUP,true"})
    void stoppedOrInterruptedEntryRejectsNewWork(Channel channel, boolean interrupt) {
        try (var fixture = new Fixture()) {
            fixture.stopOrInterrupt(interrupt);
            fixture.trigger(channel);
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            assertThat(fixture.queued).isEmpty();
            verifyNoInteractions(fixture.manager, fixture.transactions, fixture.tasks);
        }
    }

    @ParameterizedTest
    @CsvSource({"POLL,false", "POLL,true", "RECOVERY,false", "RECOVERY,true", "CLEANUP,false", "CLEANUP,true"})
    void stoppedOrInterruptedQueuedCallbackSkipsBusinessCalls(Channel channel, boolean interrupt) {
        try (var fixture = new Fixture()) {
            fixture.trigger(channel);
            assertThat(fixture.queued).hasSize(1);
            fixture.stopOrInterrupt(interrupt);
            fixture.queued.getFirst().run();
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            verifyNoInteractions(fixture.manager, fixture.transactions, fixture.tasks);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingDuringValidationFinishesCurrentTaskAndSkipsGeneration(boolean interrupt) {
        try (var fixture = new Fixture()) {
            doAnswer(invocation -> {
                fixture.stopOrInterrupt(interrupt);
                fixture.processed.add(invocation.getArgument(0));
                return null;
            }).when(fixture.manager).process(any(TaskSnapshot.class));
            fixture.runPoll();
            fixture.assertOnlyValidationCompleted(interrupt);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stoppingOrInterruptingInsideClaimStillProcessesClaimedValidation(boolean interrupt) {
        try (var fixture = new Fixture()) {
            doAnswer(invocation -> {
                List<TaskSnapshot> current = fixture.claimOne(invocation.getArgument(0));
                fixture.stopOrInterrupt(interrupt);
                return current;
            }).when(fixture.tasks).claim(anyString(), eq(1));
            fixture.runPoll();
            fixture.assertOnlyValidationCompleted(interrupt);
        }
    }

    @Test
    void healthyChannelsRemainIndependentAndBothTaskTypesActuallyProcess() {
        try (var fixture = new Fixture()) {
            fixture.worker.poll();
            fixture.worker.recoverUploads();
            fixture.worker.cleanup();
            fixture.worker.poll();
            assertThat(fixture.queued).hasSize(3);
            fixture.queued.forEach(Runnable::run);
            fixture.assertBothTaskTypesProcessed();
            verify(fixture.transactions).recoverAbandonedUploads();
            verify(fixture.manager).cleanup();
            verifyNoMoreInteractions(fixture.manager, fixture.transactions, fixture.tasks);
        }
    }

    @Test
    void ordinaryValidationFailureIsFinalizedAndGenerationStillProcesses() {
        try (var fixture = new Fixture()) {
            doAnswer(invocation -> {
                fixture.processed.add(invocation.getArgument(0));
                if (fixture.processed.size() == 1) throw new IllegalStateException("synthetic validation failure");
                return null;
            }).when(fixture.manager).process(any(TaskSnapshot.class));
            fixture.runPoll();
            fixture.assertBothTaskTypesProcessed();
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            verify(fixture.tasks).failUnhandled(fixture.claimed.getFirst(), "TASK_EXECUTION_FAILED",
                    "任务处理发生未预期错误，请稍后重试", true);
            verifyNoMoreInteractions(fixture.manager, fixture.transactions, fixture.tasks);
        }
    }

    @ParameterizedTest
    @EnumSource(Channel.class)
    void interruptedQueuedCallbackReleasesPermitAndNotificationCanWakePoll(Channel channel) {
        try (var fixture = new Fixture()) {
            fixture.trigger(channel);
            Thread.currentThread().interrupt();
            fixture.queued.getFirst().run();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(fixture.manager, fixture.transactions, fixture.tasks);
            Thread.interrupted();
            if (channel == Channel.POLL) {
                fixture.worker.available(new AsyncTaskAvailable(MonthlyReportConstants.VALIDATE_TASK, "new", null));
            } else {
                fixture.trigger(channel);
            }
            assertThat(fixture.queued).hasSize(2);
            fixture.queued.getLast().run();
            switch (channel) {
                case POLL -> fixture.assertBothTaskTypesProcessed();
                case RECOVERY -> verify(fixture.transactions).recoverAbandonedUploads();
                case CLEANUP -> verify(fixture.manager).cleanup();
            }
            verifyNoMoreInteractions(fixture.manager, fixture.transactions, fixture.tasks);
        }
    }

    private enum Channel { POLL, RECOVERY, CLEANUP }

    private static final class Fixture implements AutoCloseable {
        private final MonthlyReportProcessingManager manager = mock(MonthlyReportProcessingManager.class);
        private final MonthlyReportTransactionManager transactions = mock(MonthlyReportTransactionManager.class);
        private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
        private final List<Runnable> queued = new ArrayList<>();
        private final List<TaskSnapshot> claimed = new ArrayList<>();
        private final List<TaskSnapshot> processed = new ArrayList<>();
        private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        private final MonthlyReportWorker worker;

        private Fixture() {
            var beans = context.getBeanFactory();
            beans.registerSingleton("manager", manager);
            beans.registerSingleton("transactions", transactions);
            beans.registerSingleton("properties", new MonthlyReportProperties());
            beans.registerSingleton("runner", new AsyncTaskBatchRunner(tasks, mock(TaskScheduler.class)));
            beans.registerSingleton("taskMonitor", mock(ScheduledTaskMonitor.class));
            beans.registerSingleton(ExecutorNames.REPORTING, (TaskExecutor) queued::add);
            context.register(MonthlyReportWorker.class);
            context.refresh();
            worker = context.getBean(MonthlyReportWorker.class);
            doAnswer(invocation -> claimOne(invocation.getArgument(0))).when(tasks).claim(anyString(), eq(1));
            doAnswer(invocation -> {
                processed.add(invocation.getArgument(0));
                return null;
            }).when(manager).process(any(TaskSnapshot.class));
        }

        private List<TaskSnapshot> claimOne(String type) {
            TaskSnapshot task = mock(TaskSnapshot.class);
            when(task.id()).thenReturn(type + "-" + claimed.size());
            when(task.taskType()).thenReturn(type);
            claimed.add(task);
            return List.of(task);
        }

        private void trigger(Channel channel) {
            switch (channel) {
                case POLL -> worker.poll();
                case RECOVERY -> worker.recoverUploads();
                case CLEANUP -> worker.cleanup();
            }
        }

        private void runPoll() {
            worker.poll();
            assertThat(queued).hasSize(1);
            queued.getFirst().run();
        }

        private void stopOrInterrupt(boolean interrupt) {
            if (interrupt) Thread.currentThread().interrupt();
            else context.close();
        }

        private void assertOnlyValidationCompleted(boolean interrupt) {
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
            assertThat(claimed).hasSize(1);
            assertThat(processed).containsExactly(claimed.getFirst());
            assertThat(claimed.getFirst().taskType()).isEqualTo(MonthlyReportConstants.VALIDATE_TASK);
            verify(tasks).claim(MonthlyReportConstants.VALIDATE_TASK, 1);
            verify(manager).process(claimed.getFirst());
            verifyNoMoreInteractions(manager, transactions, tasks);
        }

        private void assertBothTaskTypesProcessed() {
            assertThat(claimed).extracting(TaskSnapshot::taskType)
                    .containsExactly(MonthlyReportConstants.VALIDATE_TASK, MonthlyReportConstants.GENERATE_TASK);
            assertThat(processed).containsExactlyElementsOf(claimed);
            verify(tasks).claim(MonthlyReportConstants.VALIDATE_TASK, 1);
            verify(tasks).claim(MonthlyReportConstants.GENERATE_TASK, 1);
            for (TaskSnapshot task : claimed) verify(manager).process(task);
        }

        @Override
        public void close() {
            Thread.interrupted();
            context.close();
        }
    }
}
