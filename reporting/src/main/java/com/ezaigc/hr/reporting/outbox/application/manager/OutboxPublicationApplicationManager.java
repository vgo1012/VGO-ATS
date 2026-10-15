package com.ezaigc.hr.reporting.outbox.application.manager;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublicationSource;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.infrastructure.task.TaskRetryBackoff;
import com.ezaigc.hr.reporting.outbox.application.assembler.OutboxEnvelopeAssembler;
import com.ezaigc.hr.reporting.outbox.constant.OutboxConstants;
import com.ezaigc.hr.reporting.outbox.domain.entity.OutboxEventEntity;
import com.ezaigc.hr.reporting.outbox.service.OutboxEventService;
import jakarta.annotation.Resource;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在数据库事务外发布不可变信封，确认后的任务写入仍受当前尝试租约保护。 */
@Component
@Order(10)
public class OutboxPublicationApplicationManager implements OutboxPublicationSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublicationApplicationManager.class);
    @Resource private AsyncTaskPort tasks;
    @Resource private OutboxEventService events;
    @Resource private OutboxMessagePublisher publisher;
    @Resource private Clock clock;
    @Resource private AsyncTaskBatchRunner runner;
    @Resource private OutboxEnvelopeAssembler assembler;

    @Override
    public int publishOne() {
        return runner.run(OutboxConstants.PUBLISH_TASK_TYPE, 1, this::process);
    }

    @Transactional(propagation = Propagation.NEVER)
    public void process(TaskSnapshot task) {
        try {
            publishCurrent(task);
        } catch (DataAccessException failure) {
            LOGGER.error("outbox-persistence-failed taskId={}, attemptNo={}", task.id(), task.attemptCount(), SafeLogException.from(failure));
            if (!tasks.shouldStopProcessing(task)) {
                tasks.failAndRetryAt(task, "OUTBOX_PERSISTENCE_UNAVAILABLE", "消息投递状态暂时无法保存，稍后重试",
                        TaskRetryBackoff.nextRetryAt(clock.instant(), task.attemptCount()));
            }
        }
    }

    private void publishCurrent(TaskSnapshot task) {
        if (!OutboxConstants.PUBLISH_TASK_TYPE.equals(task.taskType())) {
            throw new IllegalArgumentException("an outbox publication task is required");
        }
        if (tasks.shouldStopProcessing(task)) return;
        OutboxEventEntity event = events.getById(task.id());
        if (event == null) event = events.findForRecoveryTask(task.id());
        if (event == null || !task.resourceId().equals(event.getWorkTaskId())
                || !OutboxConstants.EVENT_TYPE.equals(event.getEventType())
                || OutboxConstants.EVENT_VERSION != event.getEventVersion()) {
            tasks.fail(task, "OUTBOX_BINDING_INVALID", "发布任务的持久事件绑定不完整，请人工核对", false);
            return;
        }
        String previousTrace = MDC.get("traceId");
        try {
            MDC.put("traceId", event.getTraceId());
            try {
                publisher.publish(assembler.toEnvelope(event));
            } catch (OutboxPublishException failure) {
                LOGGER.warn("outbox-publication-failed eventId={}, taskId={}, attemptNo={}, errorCode={}",
                        event.getId(), task.id(), task.attemptCount(), failure.getCode(), SafeLogException.from(failure));
                if (failure.isRetryable()) {
                    tasks.failAndRetryAt(task, failure.getCode(), failure.getSafeDetail(),
                            TaskRetryBackoff.nextRetryAt(clock.instant(), task.attemptCount()));
                } else {
                    tasks.fail(task, failure.getCode(), failure.getSafeDetail(), false);
                }
                return;
            }
            // 确认后进程中断或此处数据库失败时，租约恢复会重发同一 eventId；消费者必须幂等。
            tasks.succeed(task, event.getId());
        } finally {
            if (previousTrace == null) MDC.remove("traceId"); else MDC.put("traceId", previousTrace);
        }
    }

}
