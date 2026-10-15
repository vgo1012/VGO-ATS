package com.ezaigc.hr.integration.messaging;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxMessagePublisher;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.integration.config.MessagingProperties;
import jakarta.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 持久消息必须既可路由又得到肯定确认；关联 ID 按每次发送区分，事件 ID 在重放时保持不变。 */
@Component
@ConditionalOnProperty(name = "hr.messaging.mode", havingValue = "rabbitmq")
public class RabbitOutboxMessagePublisher implements OutboxMessagePublisher {
    @Resource(name = "outboxRabbitTemplate") private RabbitTemplate template;
    @Resource private MessagingProperties properties;
    @Resource private ObjectMapper objectMapper;

    @Override
    public void publish(OutboxEnvelope envelope) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(envelope);
        } catch (RuntimeException failure) {
            throw failure("OUTBOX_ENCODING_FAILED", "消息信封编码失败，请人工核对", false, failure);
        }
        if (envelope == null || envelope.getPayload() == null || envelope.getEventId() == null
                || envelope.getPayload().getTaskType() == null || body.length > properties.getMaximumMessageBytes()) {
            throw failure("OUTBOX_ENVELOPE_INVALID", "消息信封不完整或超过大小上限", false, null);
        }
        var metadata = new MessageProperties();
        metadata.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        metadata.setContentEncoding("UTF-8");
        metadata.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        metadata.setMessageId(envelope.getEventId());
        metadata.setType(envelope.getEventType());
        metadata.setHeader("traceId", envelope.getTraceId());
        var correlation = new CorrelationData(envelope.getEventId() + ":" + UUID.randomUUID());
        try {
            template.send(properties.getExchange(), envelope.getPayload().getTaskType(), new Message(body, metadata), correlation);
            var confirmation = correlation.getFuture().get(properties.getConfirmTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                throw failure("OUTBOX_UNROUTABLE", "消息暂时无法路由到工作队列，请检查绑定", true, null);
            }
            if (!confirmation.ack()) {
                throw failure("OUTBOX_NEGATIVE_CONFIRM", "消息服务未确认保存，稍后重试", true, null);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure("OUTBOX_INTERRUPTED", "消息确认等待已中断，稍后重试", true, failure);
        } catch (TimeoutException | ExecutionException | AmqpException failure) {
            throw failure("OUTBOX_CONFIRM_UNAVAILABLE", "消息发送或确认暂时不可用，稍后重试", true, failure);
        }
    }

    private static OutboxPublishException failure(String code, String detail, boolean retryable, Throwable cause) {
        return new OutboxPublishException(code, detail, retryable, cause);
    }
}
