package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;

/** 用运行时 Logback 和实际复用线程验证任务日志归属及上下文清理。 */
class AsyncTaskLoggingContextTest {
  private static final Map<String, String> OUTER = Map.of(
      "traceId", "synthetic_trace_123456", "taskId", "outer-task", "attemptNo", "0");
  private final Logger logger = (Logger) LoggerFactory.getLogger(AsyncTaskBatchRunner.class);
  private final ListAppender<ILoggingEvent> events = new ListAppender<>() {
    @Override
    protected void append(ILoggingEvent event) {
      event.prepareForDeferredProcessing();
      super.append(event);
    }
  };
  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final List<Runnable> heartbeats = new ArrayList<>();
  private AsyncTaskBatchRunner runner;

  @BeforeEach
  void setUp() {
    events.start();
    logger.addAppender(events);
    when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
        .thenAnswer(invocation -> {
          heartbeats.add(invocation.getArgument(0));
          return mock(ScheduledFuture.class);
        });
    runner = new AsyncTaskBatchRunner(tasks, scheduler);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(events);
    events.stop();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void innerLogsBelongToTheCurrentAttemptAndDoNotLeakIntoTheNextTask(boolean failFirst)
      throws Exception {
    TaskSnapshot first = task("first-task", 1);
    TaskSnapshot second = task("second-task", 2);
    when(tasks.claim("TEST", 2)).thenReturn(List.of(first, second));
    when(tasks.failUnhandled(eq(first), any(), any(), eq(true))).thenAnswer(invocation -> {
      logger.info("synthetic-finalization");
      return first;
    });
    try (var worker = Executors.newSingleThreadExecutor()) {
      worker.submit(() -> {
        MDC.setContextMap(OUTER);
        runner.run("TEST", 2, task -> {
          logger.info("synthetic-processor");
          if (task == first) {
            MDC.put("task-local", "first-only");
            if (failFirst) throw new IllegalStateException("synthetic failure");
          }
        });
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(OUTER);
      }).get(20, TimeUnit.SECONDS);
      worker.submit(() -> assertThat(MDC.getCopyOfContextMap()).isEqualTo(OUTER))
          .get(20, TimeUnit.SECONDS);
    }
    var processing = events.list.stream()
        .filter(event -> event.getMessage().equals("synthetic-processor")).toList();
    assertThat(processing).hasSize(2);
    assertContext(processing.get(0), "first-task", 1);
    assertContext(processing.get(1), "second-task", 2);
    assertThat(processing.get(1).getMDCPropertyMap()).doesNotContainKey("task-local");
    if (failFirst) {
      var finalization = events.list.stream()
          .filter(event -> event.getMessage().equals("synthetic-finalization")).findFirst().orElseThrow();
      assertContext(finalization, "first-task", 1);
    }
  }

  @Test
  void fatalProcessorErrorStillRestoresTheReusedWorkerContext() throws Exception {
    TaskSnapshot fatal = task("fatal-task", 3);
    when(tasks.claim("TEST", 1)).thenReturn(List.of(fatal));
    try (var worker = Executors.newSingleThreadExecutor()) {
      var result = worker.submit(() -> {
        MDC.setContextMap(OUTER);
        runner.run("TEST", 1, task -> {
          MDC.put("task-local", "fatal-only");
          throw new AssertionError("synthetic fatal error");
        });
      });
      assertThatThrownBy(() -> result.get(20, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AssertionError.class);
      worker.submit(() -> assertThat(MDC.getCopyOfContextMap()).isEqualTo(OUTER))
          .get(20, TimeUnit.SECONDS);
    }
  }

  @Test
  void heartbeatUsesItsOwnTaskIdentityAndRestoresTheSchedulerThread() throws Exception {
    TaskSnapshot task = task("heartbeat-task", 4);
    when(tasks.claim("TEST", 1)).thenReturn(List.of(task));
    when(tasks.renewLease(task)).thenAnswer(invocation -> {
      logger.info("synthetic-heartbeat");
      MDC.put("task-local", "heartbeat-only");
      return false;
    });
    runner.run("TEST", 1, ignored -> {});
    assertThat(heartbeats).hasSize(1);
    try (var schedulerThread = Executors.newSingleThreadExecutor()) {
      schedulerThread.submit(() -> {
        MDC.setContextMap(OUTER);
        heartbeats.getFirst().run();
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(OUTER);
      }).get(20, TimeUnit.SECONDS);
    }
    var renewal = events.list.stream()
        .filter(event -> event.getMessage().equals("synthetic-heartbeat")).findFirst().orElseThrow();
    assertContext(renewal, "heartbeat-task", 4);
  }

  @Test
  void freshWorkerLogsTaskIdentityAndReturnsToAnEmptyContext() throws Exception {
    TaskSnapshot task = task("fresh-task", 1);
    when(tasks.claim("TEST", 1)).thenReturn(List.of(task));
    try (var worker = Executors.newSingleThreadExecutor()) {
      worker.submit(() -> {
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        runner.run("TEST", 1, ignored -> logger.info("synthetic-fresh"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
      }).get(20, TimeUnit.SECONDS);
    }
    var processing = events.list.stream()
        .filter(event -> event.getMessage().equals("synthetic-fresh")).findFirst().orElseThrow();
    assertThat(processing.getMDCPropertyMap()).containsEntry("taskId", "fresh-task")
        .containsEntry("taskType", "TEST").containsEntry("attemptNo", "1")
        .containsEntry("traceId", "task-trace-fresh-task");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "short", "unsafe-trace-123456\nforged"})
  void unknownOrInvalidPersistedTraceNeverAdoptsTheReusedThreadsTrace(String trace) throws Exception {
    TaskSnapshot task = task("legacy-task", 1);
    when(task.traceId()).thenReturn(trace);
    when(tasks.claim("TEST", 1)).thenReturn(List.of(task));
    try (var worker = Executors.newSingleThreadExecutor()) {
      worker.submit(() -> {
        MDC.setContextMap(OUTER);
        runner.run("TEST", 1, ignored -> {
          assertThat(MDC.get("traceId")).isNull();
          assertThat(MDC.get("taskId")).isEqualTo("legacy-task");
        });
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(OUTER);
      }).get(20, TimeUnit.SECONDS);
    }
  }

  @Test
  void configuredLogOutputIncludesTraceTaskAndAttemptWithoutDumpingOtherContext() {
    var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yml"));
    String pattern = yaml.getObject().getProperty("logging.pattern.level", "%5p");
    var layout = new PatternLayout();
    layout.setContext(logger.getLoggerContext());
    layout.setPattern(pattern);
    layout.start();
    Map<String, String> previous = MDC.getCopyOfContextMap();
    try {
      MDC.setContextMap(OUTER);
      MDC.put("private-value", "synthetic-private-marker");
      logger.info("synthetic-layout");
      assertThat(layout.doLayout(events.list.getLast()))
          .contains("synthetic_trace_123456", "outer-task", "0")
          .doesNotContain("synthetic-private-marker");
    } finally {
      if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
      layout.stop();
    }
  }

  private static void assertContext(ILoggingEvent event, String taskId, int attempt) {
    assertThat(event.getMDCPropertyMap()).containsEntry("taskId", taskId)
        .containsEntry("taskType", "TEST").containsEntry("attemptNo", Integer.toString(attempt))
        .containsEntry("traceId", "task-trace-" + taskId);
  }

  private static TaskSnapshot task(String id, int attempt) {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.id()).thenReturn(id);
    when(task.attemptCount()).thenReturn(attempt);
    when(task.traceId()).thenReturn("task-trace-" + id);
    return task;
  }
}
