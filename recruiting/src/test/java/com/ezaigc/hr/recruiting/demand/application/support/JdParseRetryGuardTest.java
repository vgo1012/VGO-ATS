package com.ezaigc.hr.recruiting.demand.application.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

class JdParseRetryGuardTest {
  private final JdParsingAdmissionPolicy admission = mock(JdParsingAdmissionPolicy.class);
  private final JdParseRetryGuard guard = new JdParseRetryGuard();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(guard, "admission", admission);
  }

  @Test
  void unrelatedTaskDoesNotTriggerJdAdmission() {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.taskType()).thenReturn("RESUME_PARSE");
    guard.requireRetryAllowed(task);
    verifyNoInteractions(admission);
  }

  @Test
  void jdTaskUsesItsExactVersionForCurrentAdmission() {
    TaskSnapshot task = task("JD_VERSION");
    when(task.resourceId()).thenReturn("version-1");
    guard.requireRetryAllowed(task);
    verify(admission).requireUserAccess("version-1");
  }

  @Test
  void malformedJdTaskCannotPassAsAnotherResourceType() {
    assertThatThrownBy(() -> guard.requireRetryAllowed(task("JD")))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(admission);
  }

  private static TaskSnapshot task(String resourceType) {
    TaskSnapshot task = mock(TaskSnapshot.class);
    when(task.taskType()).thenReturn("JD_PARSE");
    when(task.resourceType()).thenReturn(resourceType);
    return task;
  }
}
