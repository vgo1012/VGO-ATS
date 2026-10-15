package com.ezaigc.hr.integration.messaging;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxNotificationVerifier;
import com.ezaigc.hr.integration.config.MessagingProperties;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Broker 线程只校验持久绑定并提交唤醒提示；SQL 任务轮询承担断线和重投恢复。 */
@Component
@ConditionalOnProperty(name = "hr.messaging.mode", havingValue = "rabbitmq")
public class RabbitTaskNotificationListener implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitTaskNotificationListener.class);
    @Resource private MessagingProperties properties;
    @Resource private ObjectMapper objectMapper;
    @Resource private ObjectProvider<OutboxNotificationVerifier> verifiers;
    @Resource private ApplicationEventPublisher notifications;

    @Override
    public void onMessage(Message message) {
        String previousTrace = MDC.get("traceId");
        try {
            if (message == null
                    || message.getBody().length == 0
                    || message.getBody().length > properties.getMaximumMessageBytes()
                    || !MessageProperties.CONTENT_TYPE_JSON.equals(
                            message.getMessageProperties().getContentType())) {
                throw new IllegalArgumentException("TASK_NOTIFICATION_FORMAT_INVALID");
            }
            var envelope = objectMapper.readValue(message.getBody(), OutboxEnvelope.class);
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            if (envelope == null
                    || envelope.getPayload() == null
                    || envelope.getEventId() == null
                    || !envelope.getEventId().equals(message.getMessageProperties().getMessageId())
                    || routingKey == null
                    || !routingKey.equals(envelope.getPayload().getTaskType())) {
                throw new IllegalArgumentException("TASK_NOTIFICATION_ROUTE_INVALID");
            }
            var matches = verifiers.orderedStream()
                    .filter(verifier -> verifier.supports(envelope.getEventType()))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalArgumentException("TASK_NOTIFICATION_PROTOCOL_INVALID");
            }
            var notification = matches.getFirst().verifyNotification(envelope);
            MDC.put("traceId", envelope.getTraceId());
            notifications.publishEvent(notification);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "task-notification-rejected errorType={}",
                    failure.getClass().getName(),
                    SafeLogException.from(failure));
            throw new AmqpRejectAndDontRequeueException("TASK_NOTIFICATION_REJECTED");
        } finally {
            if (previousTrace == null) {
                MDC.remove("traceId");
            } else {
                MDC.put("traceId", previousTrace);
            }
        }
    }
}
