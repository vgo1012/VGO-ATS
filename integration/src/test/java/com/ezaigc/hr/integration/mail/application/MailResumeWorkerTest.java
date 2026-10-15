package com.ezaigc.hr.integration.mail.application;

import static com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames.MAINTENANCE;
import static com.ezaigc.hr.integration.mail.constant.MailResumeConstants.TASK_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.integration.mail.application.manager.MailResumeApplicationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;

class MailResumeWorkerTest {
  private final MailResumeApplicationManager manager = mock(MailResumeApplicationManager.class);
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final ScheduledTaskMonitor monitor = mock(ScheduledTaskMonitor.class);
  private final List<Runnable> queued = new ArrayList<>();
  private final List<Runnable> maintenanceQueued = new ArrayList<>();
  private final List<TaskSnapshot> claimed = new ArrayList<>();
  private final List<TaskSnapshot> processed = new ArrayList<>();
  private AnnotationConfigApplicationContext context;
  private MailResumeWorker worker;

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("mail-worker-test", Map.of("hr.mail-resume.enabled", "true")));
    var beans = context.getBeanFactory();
    beans.registerSingleton("manager", manager);
    beans.registerSingleton("runner", new AsyncTaskBatchRunner(tasks, mock(TaskScheduler.class)));
    beans.registerSingleton("monitor", monitor);
    beans.registerSingleton(MAINTENANCE, (TaskExecutor) maintenanceQueued::add);
    beans.registerSingleton("mailTaskExecutor", (TaskExecutor) queued::add);
    context.register(MailResumeWorker.class);
    context.refresh();
    worker = context.getBean(MailResumeWorker.class);
    doAnswer(invocation -> claimOne()).when(tasks).claim(TASK_TYPE, 1);
    doAnswer(invocation -> {
      processed.add(invocation.getArgument(0));
      return null;
    }).when(manager).process(any(TaskSnapshot.class));
  }

  @AfterEach
  void tearDown() {
    Thread.interrupted();
    if (context != null) context.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stoppedOrInterruptedEntryRejectsDispatch(boolean interrupt) {
    stopOrInterrupt(interrupt);
    worker.poll();
    assertThat(queued).isEmpty();
    assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
    verifyNoInteractions(manager, tasks, monitor);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stoppedOrInterruptedQueuedCallbackSkipsSchedulingAndClaims(boolean interrupt) {
    worker.poll();
    assertThat(queued).hasSize(1);
    stopOrInterrupt(interrupt);
    queued.getFirst().run();
    assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
    verifyNoInteractions(manager, tasks);
    verify(monitor).finished("mail-resume-ingest", null, null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stoppingOrInterruptingDuringScheduleLetsItFinishButSkipsClaims(boolean interrupt) {
    when(manager.schedule()).thenAnswer(invocation -> {
      stopOrInterrupt(interrupt);
      return null;
    });
    runPoll();
    assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
    verify(manager).schedule();
    verifyNoMoreInteractions(manager);
    verifyNoInteractions(tasks);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stoppingOrInterruptingDuringProcessingCompletesCurrentTaskAndStopsNextClaim(
      boolean interrupt) {
    doAnswer(invocation -> {
      stopOrInterrupt(interrupt);
      processed.add(invocation.getArgument(0));
      return null;
    }).when(manager).process(any(TaskSnapshot.class));
    runPoll();
    assertOneCompletedTask(interrupt);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stoppingOrInterruptingInsideClaimStillProcessesItsTaskBeforeStopping(boolean interrupt) {
    doAnswer(invocation -> {
      List<TaskSnapshot> current = claimOne();
      stopOrInterrupt(interrupt);
      return current;
    }).when(tasks).claim(TASK_TYPE, 1);
    runPoll();
    assertOneCompletedTask(interrupt);
  }

  @Test
  void mailboxWorkIsQueuedIndependentlyOfMaintenance() {
    worker.poll();
    assertThat(maintenanceQueued).isEmpty();
    assertThat(queued).hasSize(1);
  }

  @Test
  void healthyPollProcessesFiveTasksInOrderAndCoalescesQueuedTriggers() {
    worker.poll();
    worker.poll();
    assertThat(queued).hasSize(1);
    queued.getFirst().run();
    assertThat(claimed).hasSize(5);
    assertThat(processed).containsExactlyElementsOf(claimed);
    var order = inOrder(manager, tasks);
    order.verify(manager).schedule();
    for (TaskSnapshot task : claimed) {
      order.verify(tasks).claim(TASK_TYPE, 1);
      order.verify(manager).process(task);
    }
    verifyNoMoreInteractions(manager, tasks);
    verify(monitor).coalesced("mail-resume-ingest");
    worker.poll();
    assertThat(queued).hasSize(2);
  }

  @Test
  void emptyClaimEndsPollImmediately() {
    doReturn(List.of()).when(tasks).claim(TASK_TYPE, 1);
    runPoll();
    verify(manager).schedule();
    verify(tasks).claim(TASK_TYPE, 1);
    verifyNoMoreInteractions(manager, tasks);
    assertThat(processed).isEmpty();
  }

  @Test
  void ordinaryProcessingFailureIsFinalizedAndRemainingTasksStillRun() {
    doAnswer(invocation -> {
      processed.add(invocation.getArgument(0));
      if (processed.size() == 1) throw new IllegalStateException("synthetic processing failure");
      return null;
    }).when(manager).process(any(TaskSnapshot.class));
    runPoll();
    assertThat(claimed).hasSize(5);
    assertThat(processed).containsExactlyElementsOf(claimed);
    assertThat(Thread.currentThread().isInterrupted()).isFalse();
    verify(manager).schedule();
    for (TaskSnapshot task : claimed) verify(manager).process(task);
    verify(tasks, times(5)).claim(TASK_TYPE, 1);
    verify(tasks).failUnhandled(claimed.getFirst(), "TASK_EXECUTION_FAILED",
        "任务处理发生未预期错误，请稍后重试", true);
    verifyNoMoreInteractions(manager, tasks);
  }

  @Test
  void skippedInterruptedCallbackReleasesPermitForNextHealthyPoll() {
    worker.poll();
    Thread.currentThread().interrupt();
    queued.getFirst().run();
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    verifyNoInteractions(manager, tasks);
    Thread.interrupted();
    worker.poll();
    assertThat(queued).hasSize(2);
    queued.getLast().run();
    assertThat(claimed).hasSize(5);
    assertThat(processed).containsExactlyElementsOf(claimed);
    verify(monitor, times(2)).finished("mail-resume-ingest", null, null);
  }

  private List<TaskSnapshot> claimOne() {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn("mail-task-" + (claimed.size() + 1));
    claimed.add(task);
    return List.of(task);
  }

  private void runPoll() {
    worker.poll();
    assertThat(queued).hasSize(1);
    queued.getFirst().run();
  }

  private void assertOneCompletedTask(boolean interrupt) {
    assertThat(claimed).hasSize(1);
    assertThat(processed).containsExactly(claimed.getFirst());
    assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupt);
    verify(manager).schedule();
    verify(tasks).claim(TASK_TYPE, 1);
    verify(manager).process(claimed.getFirst());
    verifyNoMoreInteractions(manager, tasks);
  }

  private void stopOrInterrupt(boolean interrupt) {
    if (interrupt) Thread.currentThread().interrupt();
    else context.close();
  }
}
