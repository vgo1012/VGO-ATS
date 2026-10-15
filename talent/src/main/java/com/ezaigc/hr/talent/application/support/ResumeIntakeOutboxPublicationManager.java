package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublicationSource;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.common.infrastructure.task.TaskRetryBackoff;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 领域 Outbox 自己持有发布租约，不创建通用异步影子任务。 */
@Component
@Order(20)
@Lazy
public class ResumeIntakeOutboxPublicationManager implements OutboxPublicationSource {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ResumeIntakeOutboxPublicationManager.class);
  @Resource private ObjectProvider<DataSource> dataSourceProvider;
  @Resource private ObjectProvider<ResumeIntakeOutboxService> outboxProvider;
  @Resource private OutboxMessagePublisher publisher;
  @Resource private Clock clock;

  @Override
  @Transactional(propagation = Propagation.NEVER)
  public int publishOne() {
    if (dataSourceProvider.getIfAvailable() == null) return 0;
    ResumeIntakeOutboxService outbox = outboxProvider.getObject();
    Instant now = clock.instant();
    ResumeIntakeOutboxEvent event = outbox.claimOne(now);
    if (event == null) return 0;
    String previousTrace = MDC.get("traceId");
    try {
      MDC.put("traceId", event.getTraceId());
      try {
        publisher.publish(ResumeIntakeOutboxEnvelopeFactory.from(event));
      } catch (OutboxPublishException failure) {
        LOGGER.warn("resume-intake-outbox-publication-failed eventId={} processingId={} attemptNo={} errorCode={}",
            event.getId(), event.getProcessingId(), event.getAttemptCount(), failure.getCode(),
            SafeLogException.from(failure));
        outbox.failed(event, failure.getCode(), failure.isRetryable(),
            TaskRetryBackoff.nextRetryAt(clock.instant(), event.getAttemptCount()), clock.instant());
        return 1;
      }
      outbox.published(event, clock.instant());
      return 1;
    } finally {
      if (previousTrace == null) MDC.remove("traceId"); else MDC.put("traceId", previousTrace);
    }
  }
}
