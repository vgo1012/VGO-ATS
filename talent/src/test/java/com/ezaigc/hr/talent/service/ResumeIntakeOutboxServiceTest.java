package com.ezaigc.hr.talent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;
import com.ezaigc.hr.talent.mapper.ResumeIntakeOutboxMapper;
import com.ezaigc.hr.talent.service.impl.ResumeIntakeOutboxServiceImpl;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeIntakeOutboxServiceTest {
  private final ResumeIntakeOutboxMapper mapper = mock(ResumeIntakeOutboxMapper.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ResumeIntakeOutboxService service = new ResumeIntakeOutboxServiceImpl();
  private final Instant now = Instant.parse("2026-09-18T00:00:00Z");

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ReflectionTestUtils.setField(service, "ids", ids);
    ReflectionTestUtils.setField(service, "events", events);
  }

  @Test
  void enqueuePersistsImmutableBindingBeforePublishingLocalWakeup() {
    when(ids.newId()).thenReturn("E".repeat(26));
    when(mapper.insertIgnore(any())).thenReturn(1);

    ResumeIntakeOutboxEvent saved = service.enqueue(
        "P".repeat(26), 3, "RESUME_INTAKE_PARSE", "U".repeat(26), now);

    var event = ArgumentCaptor.forClass(ResumeIntakeOutboxEvent.class);
    verify(mapper).insertIgnore(event.capture());
    assertThat(saved).isSameAs(event.getValue());
    assertThat(saved.getEventType()).isEqualTo("RESUME_INTAKE_PROCESSING_AVAILABLE");
    assertThat(saved.getProcessingVersion()).isEqualTo(3);
    assertThat(saved.getStatus()).isEqualTo("PENDING");
    verify(events).publishEvent(new AsyncTaskAvailable(
        "OUTBOX_PUBLISH", saved.getId(), saved.getTraceId()));
  }

  @Test
  void claimUsesUniqueLeaseAndReturnsTheClaimedVersion() {
    var candidate = new ResumeIntakeOutboxEvent();
    candidate.setId("E".repeat(26));
    candidate.setVersion(2);
    var claimed = new ResumeIntakeOutboxEvent();
    claimed.setId(candidate.getId());
    claimed.setLeaseOwner("L".repeat(26));
    when(mapper.findClaimable(now)).thenReturn(candidate);
    when(ids.newId()).thenReturn("L".repeat(26));
    when(mapper.claim(candidate.getId(), 2, "L".repeat(26), now.plusSeconds(60), now))
        .thenReturn(1);
    when(mapper.selectById(candidate.getId())).thenReturn(claimed);

    assertThat(service.claimOne(now)).isSameAs(claimed);
  }
}
