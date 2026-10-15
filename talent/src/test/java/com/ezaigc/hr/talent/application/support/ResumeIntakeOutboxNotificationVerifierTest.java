package com.ezaigc.hr.talent.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeIntakeOutboxNotificationVerifierTest {
  private final ResumeIntakeOutboxService outbox = mock(ResumeIntakeOutboxService.class);
  private final ResumeIntakeProcessingPersistenceService processing =
      mock(ResumeIntakeProcessingPersistenceService.class);
  private final ResumeIntakeOutboxNotificationVerifier verifier =
      new ResumeIntakeOutboxNotificationVerifier();
  private final ResumeIntakeOutboxEvent event = event();

  @BeforeEach
  void setUp() {
    var beans = new StaticListableBeanFactory();
    beans.addBean("dataSource", mock(DataSource.class));
    beans.addBean("outbox", outbox);
    ReflectionTestUtils.setField(
        verifier, "dataSourceProvider", beans.getBeanProvider(DataSource.class));
    ReflectionTestUtils.setField(
        verifier, "outboxProvider", beans.getBeanProvider(ResumeIntakeOutboxService.class));
    ReflectionTestUtils.setField(verifier, "processing", processing);
    when(outbox.get(event.getId())).thenReturn(event);
    when(processing.findProcessing(event.getProcessingId())).thenReturn(new ResumeIntakeProcessing());
  }

  @Test
  void exactPersistedEnvelopeOnlyWakesTheBoundProcessingType() {
    var notification = verifier.verifyNotification(ResumeIntakeOutboxEnvelopeFactory.from(event));
    assertThat(notification.getTaskType()).isEqualTo("RESUME_INTAKE_COMPLETE");
    assertThat(notification.getTaskId()).isEqualTo(event.getProcessingId());
    assertThat(notification.getTraceId()).isEqualTo(event.getTraceId());
  }

  @Test
  void changedEnvelopeIsRejectedEvenWhenTheEventIdExists() {
    var changed = ResumeIntakeOutboxEnvelopeFactory.from(event);
    event.setTraceId("another-trace-0123456789");
    assertThatThrownBy(() -> verifier.verifyNotification(changed))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ResumeIntakeOutboxEvent event() {
    var event = new ResumeIntakeOutboxEvent();
    event.setId("E".repeat(26));
    event.setProcessingId("P".repeat(26));
    event.setProcessingVersion(2);
    event.setEventType("RESUME_INTAKE_PROCESSING_AVAILABLE");
    event.setEventVersion(1);
    event.setTaskType("RESUME_INTAKE_COMPLETE");
    event.setOccurredAt(Instant.parse("2026-09-18T00:00:00Z"));
    event.setTraceId("trace-0123456789012345");
    event.setActorUserId("U".repeat(26));
    return event;
  }
}
