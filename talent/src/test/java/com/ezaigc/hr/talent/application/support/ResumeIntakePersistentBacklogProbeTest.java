package com.ezaigc.hr.talent.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.constant.ResumeIntakeWorkerConstants;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeIntakePersistentBacklogProbeTest {
  private final ResumeIntakeProcessingPersistenceService processing =
      mock(ResumeIntakeProcessingPersistenceService.class);
  private final ResumeIntakeOutboxService outbox = mock(ResumeIntakeOutboxService.class);
  private final ResumeIntakePersistentBacklogProbe probe =
      new ResumeIntakePersistentBacklogProbe();
  private final Instant now = Instant.parse("2026-09-18T00:00:00Z");

  @BeforeEach
  void setUp() {
    var beans = new StaticListableBeanFactory();
    beans.addBean("dataSource", mock(DataSource.class));
    beans.addBean("processing", processing);
    beans.addBean("outbox", outbox);
    ReflectionTestUtils.setField(
        probe, "dataSourceProvider", beans.getBeanProvider(DataSource.class));
    ReflectionTestUtils.setField(
        probe,
        "processingProvider",
        beans.getBeanProvider(ResumeIntakeProcessingPersistenceService.class));
    ReflectionTestUtils.setField(
        probe, "outboxProvider", beans.getBeanProvider(ResumeIntakeOutboxService.class));
  }

  @Test
  void reportsTheIndependentResumeIntakeProcessingQueue() {
    var expected = new PersistentTaskBacklogSnapshot(
        2, Instant.parse("2026-09-17T23:00:00Z"));
    when(processing.findReadyBacklog(now)).thenReturn(expected);

    assertThat(probe.supports(ResumeIntakeWorkerConstants.PARSE, List.of())).isTrue();
    assertThat(probe.snapshot(ResumeIntakeWorkerConstants.PARSE, List.of(), now))
        .isSameAs(expected);
    verify(processing).findReadyBacklog(now);
  }

  @Test
  void contributesTheResumeIntakeDomainOutboxToTheSharedPublisherQueue() {
    var expected = new PersistentTaskBacklogSnapshot(
        3, Instant.parse("2026-09-17T22:00:00Z"));
    when(outbox.findReadyBacklog(now)).thenReturn(expected);

    assertThat(probe.supports("OUTBOX_PUBLISH", List.of("OUTBOX_PUBLISH"))).isTrue();
    assertThat(probe.snapshot("OUTBOX_PUBLISH", List.of("OUTBOX_PUBLISH"), now))
        .isSameAs(expected);
    verify(outbox).findReadyBacklog(now);
  }
}
