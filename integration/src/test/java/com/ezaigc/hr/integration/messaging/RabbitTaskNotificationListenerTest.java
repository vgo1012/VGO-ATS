package com.ezaigc.hr.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxNotificationVerifier;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.integration.config.MessagingProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class RabbitTaskNotificationListenerTest {
    private final OutboxNotificationVerifier verifier = mock(OutboxNotificationVerifier.class);
    private final ApplicationEventPublisher notifications = mock(ApplicationEventPublisher.class);
    private final MessagingProperties properties = new MessagingProperties();
    private final RabbitTaskNotificationListener listener = new RabbitTaskNotificationListener();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("verifier", verifier);
        ReflectionTestUtils.setField(listener, "verifiers", beans.getBeanProvider(OutboxNotificationVerifier.class));
        ReflectionTestUtils.setField(listener, "notifications", notifications);
        ReflectionTestUtils.setField(listener, "properties", properties);
        ReflectionTestUtils.setField(listener, "objectMapper", json);
    }

    @Test
    void persistedResumeParseNotificationOnlyWakesTheMatchingWorker() {
        var envelope = envelope("RESUME_PARSE");
        var wakeup = new AsyncTaskAvailable("RESUME_PARSE", "W".repeat(26), envelope.getTraceId());
        when(verifier.supports("TASK_AVAILABLE")).thenReturn(true);
        when(verifier.verifyNotification(envelope)).thenReturn(wakeup);
        listener.onMessage(message(json.writeValueAsBytes(envelope), "RESUME_PARSE"));
        verify(verifier).verifyNotification(envelope);
        var event = ArgumentCaptor.forClass(AsyncTaskAvailable.class);
        verify(notifications).publishEvent(event.capture());
        assertThat(event.getValue())
                .isEqualTo(wakeup);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "oversize", "invalid-json", "wrong-id", "wrong-content-type", "wrong-route", "empty"
    })
    void badTransportInputIsRejectedWithoutReadingTheDatabase(String scenario) {
        var message = message(json.writeValueAsBytes(envelope("RESUME_PARSE")), "RESUME_PARSE");
        switch (scenario) {
            case "oversize" -> properties.setMaximumMessageBytes(10);
            case "invalid-json" -> message = message(
                    "{not-json".getBytes(StandardCharsets.UTF_8), "RESUME_PARSE");
            case "wrong-id" -> message.getMessageProperties().setMessageId("another-id");
            case "wrong-content-type" ->
                    message.getMessageProperties().setContentType("application/octet-stream");
            case "wrong-route" -> message.getMessageProperties().setReceivedRoutingKey("JD_PARSE");
            default -> message = message(new byte[0], "RESUME_PARSE");
        }
        Message rejected = message;
        assertThatThrownBy(() -> listener.onMessage(rejected))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("TASK_NOTIFICATION_REJECTED")
                .hasNoCause();
        verifyNoInteractions(verifier, notifications);
    }

    @Test
    void unavailableDatabaseNeverAcknowledgesOrPublishesUnverifiedWork() {
        when(verifier.supports("TASK_AVAILABLE")).thenReturn(true);
        when(verifier.verifyNotification(any()))
                .thenThrow(new TransientDataAccessResourceException("synthetic private endpoint"));
        assertThatThrownBy(() -> listener.onMessage(
                message(json.writeValueAsBytes(envelope("MATCH_RUN")), "MATCH_RUN")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasNoCause();
        verifyNoInteractions(notifications);
    }

    private static Message message(byte[] body, String routingKey) {
        var metadata = new MessageProperties();
        metadata.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        metadata.setMessageId("E".repeat(26));
        metadata.setReceivedRoutingKey(routingKey);
        return new Message(body, metadata);
    }

    private static OutboxEnvelope envelope(String taskType) {
        return new OutboxEnvelope(
                "E".repeat(26),
                "TASK_AVAILABLE",
                1,
                "ASYNC_TASK",
                "W".repeat(26),
                Instant.parse("2026-09-08T00:00:00Z"),
                "trace-0123456789012345",
                new OutboxEnvelope.ActorSnapshot(
                        "U".repeat(26), "U".repeat(26), "D".repeat(26)),
                new OutboxEnvelope.TaskPayload("W".repeat(26), taskType));
    }
}
