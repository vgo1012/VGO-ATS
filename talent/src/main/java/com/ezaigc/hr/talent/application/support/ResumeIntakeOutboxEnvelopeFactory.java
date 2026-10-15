package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.talent.constant.ResumeIntakeOutboxConstants;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;

final class ResumeIntakeOutboxEnvelopeFactory {
  private ResumeIntakeOutboxEnvelopeFactory() {}

  static OutboxEnvelope from(ResumeIntakeOutboxEvent event) {
    return new OutboxEnvelope(
        event.getId(),
        event.getEventType(),
        event.getEventVersion(),
        ResumeIntakeOutboxConstants.AGGREGATE_TYPE,
        event.getProcessingId(),
        event.getOccurredAt(),
        event.getTraceId(),
        new OutboxEnvelope.ActorSnapshot(event.getActorUserId(), null, null),
        new OutboxEnvelope.TaskPayload(event.getProcessingId(), event.getTaskType()));
  }
}
