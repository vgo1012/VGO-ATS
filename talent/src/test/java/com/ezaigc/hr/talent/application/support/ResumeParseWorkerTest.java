package com.ezaigc.hr.talent.application.support;

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
import com.ezaigc.hr.talent.application.manager.ResumeParsingApplicationManager;
import com.ezaigc.hr.talent.constant.TalentConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeParseWorkerTest {
  @Test
  void refillsAnAvailableSlotImmediatelyWithoutWaitingForAnotherPoll() {
    var worker = new ResumeParseWorker();
    var runner = mock(AsyncTaskBatchRunner.class);
    List<Runnable> submitted = new ArrayList<>();
    var properties = new PlatformExecutorProperties();
    properties.getAiWorkloadSlots().setResumeParse(4);
    ReflectionTestUtils.setField(worker, "executorProperties", properties);
    ReflectionTestUtils.setField(worker, "taskExecutor", (TaskExecutor) submitted::add);
    ReflectionTestUtils.setField(worker, "taskBatchRunner", runner);
    ReflectionTestUtils.setField(worker, "parsing", mock(ResumeParsingApplicationManager.class));
    worker.initializeConcurrency();

    worker.poll();
    worker.poll();
    assertThat(submitted).hasSize(4);
    verifyNoInteractions(runner);
    when(runner.run(eq(TalentConstants.RESUME_PARSE_TASK_TYPE), eq(1), any()))
        .thenReturn(1, 1, 0);
    submitted.getFirst().run();
    verify(runner, times(3)).run(eq(TalentConstants.RESUME_PARSE_TASK_TYPE), eq(1), any());
    worker.poll();
    assertThat(submitted).hasSize(5);

    worker.stop();
    worker.poll();
    submitted.get(1).run();
    assertThat(submitted).hasSize(5);
    verify(runner, times(3)).run(eq(TalentConstants.RESUME_PARSE_TASK_TYPE), eq(1), any());
  }
}
