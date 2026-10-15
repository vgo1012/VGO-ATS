package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxNotificationVerifier;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.talent.constant.ResumeIntakeOutboxConstants;
import com.ezaigc.hr.talent.constant.ResumeIntakeWorkerConstants;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import jakarta.annotation.Resource;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@Lazy
public class ResumeIntakeOutboxNotificationVerifier implements OutboxNotificationVerifier {
  private static final Set<String> SUPPORTED_TASKS = Set.of(
      ResumeIntakeWorkerConstants.PARSE, ResumeIntakeWorkerConstants.COMPLETE);
  @Resource private ObjectProvider<DataSource> dataSourceProvider;
  @Resource private ObjectProvider<ResumeIntakeOutboxService> outboxProvider;
  @Resource private ResumeIntakeProcessingPersistenceService processing;

  @Override
  public boolean supports(String eventType) {
    return dataSourceProvider.getIfAvailable() != null
        && ResumeIntakeOutboxConstants.EVENT_TYPE.equals(eventType);
  }

  @Override
  @Transactional(readOnly = true)
  public AsyncTaskAvailable verifyNotification(OutboxEnvelope envelope) {
    if (envelope == null || envelope.getEventId() == null) throw invalid();
    var event = outboxProvider.getObject().get(envelope.getEventId());
    if (event == null
        || !SUPPORTED_TASKS.contains(event.getTaskType())
        || processing.findProcessing(event.getProcessingId()) == null
        || !ResumeIntakeOutboxEnvelopeFactory.from(event).equals(envelope)) {
      throw invalid();
    }
    return new AsyncTaskAvailable(event.getTaskType(), event.getProcessingId(), event.getTraceId());
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("RESUME_INTAKE_OUTBOX_BINDING_INVALID");
  }
}
