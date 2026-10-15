package com.ezaigc.hr.talent.application.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeIntakeCompletionWorkerTest {

  @Test
  void adoptionAndMatchTerminalEventsScheduleImmediateFollowUp() {
    var scheduler = mock(TaskScheduler.class);
    var worker = new ResumeIntakeCompletionWorker();
    ReflectionTestUtils.setField(worker, "scheduler", scheduler);
    ReflectionTestUtils.setField(worker, "clock",
        Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC));

    worker.terminal(new AsyncTaskTerminalEvent("RESUME_INTAKE_ADOPT", "task", "resume", "SUCCEEDED"));
    worker.terminal(new AsyncTaskTerminalEvent("MATCH_RUN", "task", "run", "SUCCEEDED"));
    worker.terminal(new AsyncTaskTerminalEvent("OTHER", "task", "resource", "SUCCEEDED"));

    verify(scheduler, times(2)).schedule(any(Runnable.class),
        any(Instant.class));
  }
}
