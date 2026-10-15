package com.ezaigc.hr.recruiting.demand.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.recruiting.demand.application.manager.JdParsingApplicationManager;
import com.ezaigc.hr.recruiting.demand.constant.DemandConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class JdParseWorkerTest {

  @Test
  void submitsTenJdsAndClaimsEachOnlyWhenItsExecutorSlotStarts() {
    JdParseWorker worker = new JdParseWorker();
    AsyncTaskBatchRunner runner = mock(AsyncTaskBatchRunner.class);
    List<Runnable> submitted = new ArrayList<>();
    ReflectionTestUtils.setField(worker, "executorProperties", new PlatformExecutorProperties());
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) submitted::add);
    ReflectionTestUtils.setField(worker, "taskBatchRunner", runner);
    ReflectionTestUtils.setField(worker, "manager", mock(JdParsingApplicationManager.class));
    worker.initializeConcurrency();

    worker.poll();
    worker.poll();
    assertThat(submitted).hasSize(10);
    verifyNoInteractions(runner);
    when(runner.run(eq(DemandConstants.JD_PARSE_TASK_TYPE), eq(1), any())).thenReturn(1);
    submitted.subList(0, 10).forEach(Runnable::run);
    verify(runner, times(10)).run(eq(DemandConstants.JD_PARSE_TASK_TYPE), eq(1), any());
    worker.poll();
    assertThat(submitted).hasSize(20);
    submitted.subList(10, 20).forEach(Runnable::run);
    verify(runner, times(20)).run(eq(DemandConstants.JD_PARSE_TASK_TYPE), eq(1), any());
  }
}
