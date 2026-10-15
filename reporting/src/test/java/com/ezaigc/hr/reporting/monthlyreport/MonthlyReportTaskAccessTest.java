package com.ezaigc.hr.reporting.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.reporting.application.manager.AsyncTaskApplicationManager;
import com.ezaigc.hr.reporting.monthlyreport.constant.MonthlyReportConstants;
import com.ezaigc.hr.reporting.service.AsyncTaskService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 通用异步任务入口也必须套用月报的角色+权限边界。 */
class MonthlyReportTaskAccessTest {
  private static final String TASK_ID = "01M1KFNYEV1VMB8D7Q69S6V622";

  @Test
  void ordinaryHrCannotReadRetryOrCancelAMonthlyReportTaskThroughTheGenericTaskApi() {
    AsyncTaskService tasks = mock(AsyncTaskService.class);
    AuthorizationService authorization = mock(AuthorizationService.class);
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.taskType()).thenReturn(MonthlyReportConstants.VALIDATE_TASK);
    when(tasks.findTask(TASK_ID)).thenReturn(task);
    when(authorization.currentActor()).thenReturn(ordinaryHrWithMisgrantedPermission());
    AsyncTaskApplicationManager manager = manager(tasks, authorization);

    assertForbidden(() -> manager.getResponse(TASK_ID));
    assertForbidden(() -> manager.retryFailedResponse(TASK_ID, "重新校验"));
    assertForbidden(() -> manager.cancelResponse(TASK_ID, "取消"));

    verify(tasks, never()).retryFailed(TASK_ID, null, null);
    verify(tasks, never()).findTaskForUpdate(TASK_ID);
    verify(authorization, never()).requireAccess(any(), any());
  }

  private static AsyncTaskApplicationManager manager(AsyncTaskService tasks, AuthorizationService authorization) {
    AsyncTaskApplicationManager manager = new AsyncTaskApplicationManager();
    ReflectionTestUtils.setField(manager, "service", tasks);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    return manager;
  }

  private static void assertForbidden(ThrowingAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MONTHLY_REPORT_FORBIDDEN"));
  }

  private static CurrentActorSnapshot ordinaryHrWithMisgrantedPermission() {
    return new CurrentActorSnapshot("ordinary", "普通 HR", "ordinary@example.test", "department", Set.of(), Set.of(),
        Set.of(), Set.of("RECRUITER"), Set.of(MonthlyReportConstants.PERMISSION), false, false);
  }

  @FunctionalInterface
  private interface ThrowingAction {
    void run();
  }
}
