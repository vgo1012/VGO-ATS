package com.ezaigc.hr.talent.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeIntakeFactAdoptionWorkerTest {
  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

  private final AsyncTaskPort tasks = mock(AsyncTaskPort.class);
  private final ResumeParsingApplicationManager parsing = mock(ResumeParsingApplicationManager.class);
  private final ResumeIntakeProcessingPersistenceService processing =
      mock(ResumeIntakeProcessingPersistenceService.class);
  private final BackgroundActorExecutor actors = mock(BackgroundActorExecutor.class);
  private final ResumeIntakeFactAdoptionWorker worker = new ResumeIntakeFactAdoptionWorker();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(worker, "tasks", tasks);
    ReflectionTestUtils.setField(worker, "parsing", parsing);
    ReflectionTestUtils.setField(worker, "processing", processing);
    ReflectionTestUtils.setField(worker, "actors", actors);
    ReflectionTestUtils.setField(worker, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    when(actors.execute(any(), any())).thenAnswer(invocation -> {
      Supplier<?> action = invocation.getArgument(1);
      return action.get();
    });
  }

  @Test
  void recoverableFailureSchedulesAnotherAttemptWithinTheThreeAttemptBudget() {
    TaskSnapshot task = task(1);
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    when(processing.findByResumeVersion("resume-version")).thenReturn(result);
    doThrow(PlatformExceptions.unavailable("INTAKE_ADOPTION_STORAGE_UNAVAILABLE", "归档存储暂时不可用"))
        .when(parsing).processIntakeAdoption(task, result);
    ArgumentCaptor<Instant> retryAt = ArgumentCaptor.forClass(Instant.class);

    worker.process(task);

    verify(tasks).failAndRetryAt(eq(task), eq("INTAKE_ADOPTION_STORAGE_UNAVAILABLE"),
        eq("归档存储暂时不可用"), retryAt.capture());
    assertThat(retryAt.getValue()).isBetween(NOW.plusMillis(2_500), NOW.plusSeconds(5));
    verify(tasks, never()).fail(any(), any(), any(), eq(false));
  }

  @Test
  void permanentFailureStopsWithoutConsumingMoreAttempts() {
    TaskSnapshot task = task(1);
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    when(processing.findByResumeVersion("resume-version")).thenReturn(result);
    doThrow(PlatformExceptions.forbidden("INTAKE_ADOPTION_FORBIDDEN", "归档权限已失效"))
        .when(parsing).processIntakeAdoption(task, result);

    worker.process(task);

    verify(tasks).fail(task, "INTAKE_ADOPTION_FORBIDDEN", "归档权限已失效", false);
    verify(tasks, never()).failAndRetryAt(any(), any(), any(), any());
  }

  @Test
  void lostLeaseDoesNotLetTheOldWorkerOverwriteTaskState() {
    TaskSnapshot task = task(1);
    ResumeIntakeProcessing result = new ResumeIntakeProcessing();
    when(processing.findByResumeVersion("resume-version")).thenReturn(result);
    doThrow(PlatformExceptions.unavailable("INTAKE_ADOPTION_STORAGE_UNAVAILABLE", "归档存储暂时不可用"))
        .when(parsing).processIntakeAdoption(task, result);
    when(tasks.shouldStopProcessing(task)).thenReturn(true);

    worker.process(task);

    verify(tasks, never()).fail(any(), any(), any(), anyBoolean());
    verify(tasks, never()).failAndRetryAt(any(), any(), any(), any());
  }

  private static TaskSnapshot task(int attempt) {
    return new TaskSnapshot("task", "RESUME_INTAKE_ADOPT", "RESUME_VERSION", "resume-version",
        "requester", "requester", "department", "talent.read", "talent.manage", "RUNNING",
        attempt, 3, 1, null, null, false, null, attempt, NOW, NOW, null, NOW);
  }
}
