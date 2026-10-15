package com.ezaigc.hr.talent.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeIntakeOutboxPublicationManagerTest {
  private final ResumeIntakeOutboxService outbox = mock(ResumeIntakeOutboxService.class);
  private final OutboxMessagePublisher publisher = mock(OutboxMessagePublisher.class);
  private final ResumeIntakeOutboxPublicationManager manager =
      new ResumeIntakeOutboxPublicationManager();
  private final Instant now = Instant.parse("2026-09-18T00:00:00Z");
  private final ResumeIntakeOutboxEvent event = event();

  @BeforeEach
  void setUp() {
    var beans = new StaticListableBeanFactory();
    beans.addBean("dataSource", mock(DataSource.class));
    beans.addBean("outbox", outbox);
    ReflectionTestUtils.setField(
        manager, "dataSourceProvider", beans.getBeanProvider(DataSource.class));
    ReflectionTestUtils.setField(
        manager, "outboxProvider", beans.getBeanProvider(ResumeIntakeOutboxService.class));
    ReflectionTestUtils.setField(manager, "publisher", publisher);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void positiveConfirmIsPersistedAfterPublishingTheExactEnvelope() {
    when(outbox.claimOne(now)).thenReturn(event);

    assertThat(manager.publishOne()).isEqualTo(1);

    var envelope = ArgumentCaptor.forClass(OutboxEnvelope.class);
    verify(publisher).publish(envelope.capture());
    assertThat(envelope.getValue().getPayload().getTaskType()).isEqualTo("RESUME_INTAKE_PARSE");
    assertThat(envelope.getValue().getPayload().getTaskId()).isEqualTo(event.getProcessingId());
    verify(outbox).published(event, now);
  }

  @Test
  void transientFailureSchedulesPersistentRetryWithoutAcknowledging() {
    when(outbox.claimOne(now)).thenReturn(event);
    doThrow(new OutboxPublishException("BROKER_TIMEOUT", "稍后重试", true, null))
        .when(publisher).publish(any());

    assertThat(manager.publishOne()).isEqualTo(1);

    var retryAt = ArgumentCaptor.forClass(Instant.class);
    verify(outbox).failed(eq(event), eq("BROKER_TIMEOUT"), eq(true), retryAt.capture(), eq(now));
    assertThat(retryAt.getValue()).isBetween(now.plusMillis(2500), now.plusSeconds(5));
    verify(outbox, never()).published(any(), any());
  }

  private static ResumeIntakeOutboxEvent event() {
    var event = new ResumeIntakeOutboxEvent();
    event.setId("E".repeat(26));
    event.setProcessingId("P".repeat(26));
    event.setProcessingVersion(1);
    event.setEventType("RESUME_INTAKE_PROCESSING_AVAILABLE");
    event.setEventVersion(1);
    event.setTaskType("RESUME_INTAKE_PARSE");
    event.setOccurredAt(Instant.parse("2026-09-18T00:00:00Z"));
    event.setTraceId("trace-0123456789012345");
    event.setActorUserId("U".repeat(26));
    event.setAttemptCount(1);
    event.setLeaseOwner("L".repeat(26));
    return event;
  }
}
