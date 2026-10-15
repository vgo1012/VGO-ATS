package com.ezaigc.hr.integration.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.integration.config.MessagingProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class RabbitOutboxMessagePublisherTest {
    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final MessagingProperties properties = new MessagingProperties();
    private final RabbitOutboxMessagePublisher publisher = new RabbitOutboxMessagePublisher();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        properties.setConfirmTimeoutMillis(100);
        ReflectionTestUtils.setField(publisher, "template", template);
        ReflectionTestUtils.setField(publisher, "properties", properties);
        ReflectionTestUtils.setField(publisher, "objectMapper", json);
    }

    @Test
    void persistentReplayKeepsMessageIdButUsesANewConfirmationCorrelation() {
        List<String> correlations = new ArrayList<>();
        doAnswer(call -> {
            Message message = call.getArgument(2);
            var metadata = message.getMessageProperties();
            assertThat(metadata.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(metadata.getContentType()).isEqualTo("application/json");
            assertThat(metadata.getMessageId()).isEqualTo("E".repeat(26));
            assertThat(call.getArgument(0, String.class)).isEqualTo(properties.getExchange());
            assertThat(call.getArgument(1, String.class)).isEqualTo("RESUME_SEARCH_PROJECT");
            assertThat(json.readTree(message.getBody()).path("payload").path("taskId").asString()).isEqualTo("W".repeat(26));
            assertThat(json.readTree(message.getBody()).path("occurredAt").asString()).isEqualTo("2026-09-08T00:00:00Z");
            CorrelationData correlation = call.getArgument(3);
            correlations.add(correlation.getId());
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
        publisher.publish(envelope());
        publisher.publish(envelope());
        assertThat(correlations).hasSize(2).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(strings = {"return", "nack", "timeout"})
    void positiveSendReturnAloneNeverCountsAsSuccessfulDelivery(String outcome) {
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            if (outcome.equals("return")) {
                correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "synthetic no route", "exchange", "route"));
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            } else if (outcome.equals("nack")) {
                correlation.getFuture().complete(new CorrelationData.Confirm(false, "synthetic refusal"));
            }
            return null;
        }).when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> publisher.publish(envelope())).isInstanceOfSatisfying(OutboxPublishException.class, failure -> {
            assertThat(failure.isRetryable()).isTrue();
            assertThat(failure.getCode()).isEqualTo(switch (outcome) {
                case "return" -> "OUTBOX_UNROUTABLE";
                case "nack" -> "OUTBOX_NEGATIVE_CONFIRM";
                default -> "OUTBOX_CONFIRM_UNAVAILABLE";
            });
        });
    }

    @Test
    void interruptedWaitPreservesTheInterruptFlag() {
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> publisher.publish(envelope())).isInstanceOfSatisfying(OutboxPublishException.class,
                    failure -> assertThat(failure.getCode()).isEqualTo("OUTBOX_INTERRUPTED"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidOrOversizedEnvelopeNeverReachesTheBroker() {
        properties.setMaximumMessageBytes(10);
        assertThatThrownBy(() -> publisher.publish(envelope())).isInstanceOf(OutboxPublishException.class);
        assertThatThrownBy(() -> publisher.publish(null)).isInstanceOf(OutboxPublishException.class);
        verifyNoInteractions(template);
    }

    private static OutboxEnvelope envelope() {
        return new OutboxEnvelope("E".repeat(26), "TASK_AVAILABLE", 1, "CANDIDATE", "C".repeat(26),
                Instant.parse("2026-09-08T00:00:00Z"), "trace-0123456789012345",
                new OutboxEnvelope.ActorSnapshot("U".repeat(26), "U".repeat(26), "D".repeat(26)),
                new OutboxEnvelope.TaskPayload("W".repeat(26), "RESUME_SEARCH_PROJECT"));
    }
}
