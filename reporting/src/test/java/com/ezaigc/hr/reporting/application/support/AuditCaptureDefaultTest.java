package com.ezaigc.hr.reporting.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ezaigc.hr.reporting.application.manager.AuditApplicationManager;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.reporting.config.AuditCaptureProperties;
import org.junit.jupiter.api.Test;
import java.util.Map;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.task.TaskExecutor;

class AuditCaptureDefaultTest {
  private AnnotationConfigApplicationContext context(boolean enable) {
    var value = new AnnotationConfigApplicationContext();
    if (enable) value.getEnvironment().getPropertySources().addFirst(
        new MapPropertySource("test", Map.of("hr.reporting.audit-capture.enabled", "true")));
    value.getBeanFactory().registerSingleton("auditApplicationManager", mock(AuditApplicationManager.class));
    value.getBeanFactory().registerSingleton("auditCaptureProperties", new AuditCaptureProperties());
    value.getBeanFactory().registerSingleton("maintenanceTaskExecutor", mock(TaskExecutor.class));
    value.getBeanFactory().registerSingleton("scheduledTaskMonitor", new ScheduledTaskMonitor());
    value.register(AuditCaptureWorker.class);
    value.refresh();
    return value;
  }

  @Test
  void dailyWorkspaceDoesNotStartAuditProjectionByDefault() {
    try (var value = context(false)) {
      assertThat(value.getBeansOfType(AuditCaptureWorker.class)).isEmpty();
    }
    assertThat(new AuditCaptureProperties().isEnabled()).isFalse();
  }

  @Test
  void explicitOptInStillSupportsDiagnosticCapture() {
    try (var value = context(true)) {
      assertThat(value.getBeansOfType(AuditCaptureWorker.class)).hasSize(1);
    }
  }
}
