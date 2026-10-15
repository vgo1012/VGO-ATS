package com.ezaigc.hr.reporting.outbox.application.manager;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxDeliveryPort;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxNotificationVerifier;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.reporting.outbox.application.assembler.OutboxEnvelopeAssembler;
import com.ezaigc.hr.reporting.outbox.constant.OutboxConstants;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import jakarta.annotation.Resource;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 消费只核对不可变信封与持久工作，不让重投通知改变任务状态或执行网络索引。 */
@Component
public class OutboxDeliveryApplicationManager implements OutboxDeliveryPort, OutboxNotificationVerifier {
    @Resource private OutboxEventService events;
    @Resource private AsyncTaskPort tasks;
    @Resource private OutboxEnvelopeAssembler assembler;

    @Override
    @Transactional(readOnly = true)
    public OutboxEnvelope forTask(TaskSnapshot task) {
        if (task == null || task.id() == null) throw invalid();
        var event = events.findByWorkTaskId(task.id());
        if (event == null) throw invalid();
        var envelope = assembler.toEnvelope(event);
        verifyWork(task, event);
        verify(envelope);
        return envelope;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskSnapshot verify(OutboxEnvelope envelope) {
        if (envelope == null || envelope.getEventId() == null || envelope.getPayload() == null
                || !OutboxConstants.EVENT_TYPE.equals(envelope.getEventType())
                || envelope.getEventVersion() != OutboxConstants.EVENT_VERSION) {
            throw invalid();
        }
        var event = events.getById(envelope.getEventId());
        if (event == null) throw invalid();
        var expected = assembler.toEnvelope(event);
        if (!expected.equals(envelope)) throw invalid();
        var work = tasks.get(event.getWorkTaskId());
        verifyWork(work, event);
        return work;
    }

    @Override
    public boolean supports(String eventType) {
        return OutboxConstants.EVENT_TYPE.equals(eventType);
    }

    @Override
    @Transactional(readOnly = true)
    public AsyncTaskAvailable verifyNotification(OutboxEnvelope envelope) {
        TaskSnapshot task = verify(envelope);
        return new AsyncTaskAvailable(task.taskType(), task.id(), envelope.getTraceId());
    }

    private static void verifyWork(TaskSnapshot work, OutboxEventEntity event) {
        if (work == null || !Objects.equals(work.id(), event.getWorkTaskId())
                || !Objects.equals(work.taskType(), event.getWorkTaskType())
                || !Objects.equals(work.resourceType(), event.getAggregateType())
                || !Objects.equals(work.resourceId(), event.getAggregateId())
                || !Objects.equals(work.requestedByUserId(), event.getActorUserId())
                || !Objects.equals(work.ownerUserId(), event.getOwnerUserId())
                || !Objects.equals(work.ownerDepartmentId(), event.getOwnerDepartmentId())) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("OUTBOX_DELIVERY_BINDING_INVALID");
    }
}
