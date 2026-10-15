package com.ezaigc.hr.reporting.outbox.application.assembler;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import org.springframework.stereotype.Component;

/** 发布与消费校验使用同一份持久事件信封转换，保留原始任务及归属快照。 */
@Component
public class OutboxEnvelopeAssembler {
    public OutboxEnvelope toEnvelope(OutboxEventEntity event) {
        return new OutboxEnvelope(event.getId(), event.getEventType(), event.getEventVersion(),
                event.getAggregateType(), event.getAggregateId(), event.getOccurredAt(), event.getTraceId(),
                new OutboxEnvelope.ActorSnapshot(event.getActorUserId(), event.getOwnerUserId(), event.getOwnerDepartmentId()),
                new OutboxEnvelope.TaskPayload(event.getWorkTaskId(), event.getWorkTaskType()));
    }
}
