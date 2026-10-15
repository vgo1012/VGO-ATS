package com.ezaigc.hr.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.outbox.OutboxEnvelope;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxNotificationVerifier;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxPublishException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.integration.messaging.RabbitOutboxMessagePublisher;
import com.ezaigc.hr.integration.messaging.RabbitTaskNotificationListener;
import com.rabbitmq.client.ConnectionFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

/** 只在北京独立 QA Broker 的虚拟主机中验证真实发布确认、消费和重投；不连接生产网络。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_RABBITMQ_IT", matches = "true")
class RabbitMqExternalIT {
    private final JsonMapper json = JsonMapper.builder().build();
    private final RabbitTaskMessagingConfiguration configuration = new RabbitTaskMessagingConfiguration();
    private final String suffix = UUID.randomUUID().toString().replace("-", "");
    private MessagingProperties properties;
    private CachingConnectionFactory connection;
    private RabbitAdmin admin;
    private RabbitTemplate template;
    private RabbitOutboxMessagePublisher publisher;
    private SimpleMessageListenerContainer container;
    private ThreadPoolTaskExecutor consumers;
    private ThreadPoolTaskExecutor network;
    private ThreadPoolTaskExecutor callbacks;
    private ThreadPoolTaskScheduler scheduler;
    private boolean ownsTopology;

    @BeforeEach
    void connectToIsolatedQaBroker() throws Exception {
        String host = required("HR_MQ_HOST");
        String vhost = required("HR_MQ_VHOST");
        String user = required("HR_MQ_USER");
        String password = required("HR_MQ_PASSWORD");
        if (!"qa-rabbitmq".equals(host) || !vhost.startsWith("qa_") || !user.startsWith("qa_")) {
            throw new IllegalStateException("RabbitMQ ExternalIT requires the isolated QA host, vhost and user");
        }
        properties = new MessagingProperties();
        properties.setMode("rabbitmq");
        properties.setHost(host);
        properties.setVirtualHost(vhost);
        properties.setUsername(user);
        properties.setPassword(password);
        properties.setExchange("qa.task.events." + suffix);
        properties.setSearchQueue("qa.search.project." + suffix);
        properties.setWorkerQueue("qa.task.notify." + suffix);
        properties.setWorkerRoutingKeys(List.of("MATCH_RUN"));
        properties.setConfirmTimeoutMillis(3000);

        network = executor("qa-rabbit-io-");
        callbacks = executor("qa-rabbit-callback-");
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        var messaging = new RabbitMessagingConfiguration();
        connection = messaging.outboxRabbitConnectionFactory(properties, network, callbacks, scheduler);
        admin = new RabbitAdmin(connection);
        template = messaging.outboxRabbitTemplate(connection);
        publisher = publisherFor(template);
        var topology = configuration.taskNotificationTopology(properties);
        for (Queue queue : topology.getDeclarablesByType(Queue.class)) {
            assertThat(admin.getQueueProperties(queue.getName()))
                    .as("本次随机 QA 队列不得预先存在: %s", queue.getName()).isNull();
        }
        ownsTopology = true;
        for (Exchange exchange : topology.getDeclarablesByType(Exchange.class)) admin.declareExchange(exchange);
        for (Queue queue : topology.getDeclarablesByType(Queue.class)) admin.declareQueue(queue);
        for (Binding binding : topology.getDeclarablesByType(Binding.class)) admin.declareBinding(binding);
    }

    @AfterEach
    void removeOnlyThisRunsQueuesAndExchanges() {
        try {
            if (container != null) container.stop();
        } finally {
            try {
                if (ownsTopology) {
                    admin.deleteQueue(properties.getSearchQueue());
                    admin.deleteQueue(properties.getSearchQueue() + ".dead");
                    admin.deleteQueue(properties.getWorkerQueue());
                    admin.deleteQueue(properties.getWorkerQueue() + ".dead");
                    admin.deleteExchange(properties.getExchange());
                    admin.deleteExchange(properties.getExchange() + ".dead");
                }
            } finally {
                if (consumers != null) consumers.shutdown();
                try {
                    if (connection != null) connection.destroy();
                } finally {
                    if (scheduler != null) scheduler.shutdown();
                    if (callbacks != null) callbacks.shutdown();
                    if (network != null) network.shutdown();
                }
            }
        }
    }

    @Test
    void confirmedPublicationIsConsumedAndAcknowledged() throws InterruptedException {
        var envelope = envelope("MATCH_RUN");
        var wakeups = startListener(envelope);
        publisher.publish(envelope);
        assertThat(wakeups.poll(8, TimeUnit.SECONDS)).isEqualTo(new AsyncTaskAvailable(
                "MATCH_RUN", envelope.getPayload().getTaskId(), envelope.getTraceId()));
        container.stop();
        container = null;
        var observer = connectionFor(properties.getPort());
        try {
            assertThat(templateFor(observer).receive(properties.getWorkerQueue(), 1000)).isNull();
        } finally {
            observer.destroy();
        }
        assertThat(wakeups).isEmpty();
    }

    @Test
    void mandatoryReturnDoesNotCountAsSuccessfulPublication() {
        assertThatThrownBy(() -> publisher.publish(envelope("UNROUTED_TASK")))
                .isInstanceOfSatisfying(OutboxPublishException.class, failure -> {
                    assertThat(failure.getCode()).isEqualTo("OUTBOX_UNROUTABLE");
                    assertThat(failure.isRetryable()).isTrue();
                });
    }

    @Test
    void unavailableEndpointCanReplayTheSameEventAfterConnectionRecovers() throws Exception {
        var envelope = envelope("MATCH_RUN");
        var unavailable = connectionFor(1);
        try {
            var offlinePublisher = publisherFor(templateFor(unavailable));
            assertThatThrownBy(() -> offlinePublisher.publish(envelope))
                    .isInstanceOfSatisfying(OutboxPublishException.class, failure -> {
                        assertThat(failure.getCode()).isEqualTo("OUTBOX_CONFIRM_UNAVAILABLE");
                        assertThat(failure.isRetryable()).isTrue();
                    });
        } finally {
            unavailable.destroy();
        }
        publisher.publish(envelope);
        var received = template.receive(properties.getWorkerQueue(), 5000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getMessageId()).isEqualTo(envelope.getEventId());
        assertThat(json.readValue(received.getBody(), OutboxEnvelope.class)).isEqualTo(envelope);
        assertThat(template.receive(properties.getWorkerQueue(), 1000)).isNull();
    }

    @Test
    void rejectedTransportMessageMovesToDeadLetterQueue() throws Exception {
        var envelope = envelope("MATCH_RUN");
        var wakeups = startListener(envelope);
        var metadata = new MessageProperties();
        metadata.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        metadata.setMessageId("not-the-persisted-event-id");
        template.send(properties.getExchange(), "MATCH_RUN",
                new Message(json.writeValueAsBytes(envelope), metadata));
        var dead = template.receive(properties.getWorkerQueue() + ".dead", 8000);
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo("not-the-persisted-event-id");
        assertThat(wakeups).isEmpty();
    }

    private LinkedBlockingQueue<Object> startListener(OutboxEnvelope expected) {
        var verifier = mock(OutboxNotificationVerifier.class);
        when(verifier.supports("TASK_AVAILABLE")).thenReturn(true);
        when(verifier.verifyNotification(expected)).thenReturn(new AsyncTaskAvailable(
                expected.getPayload().getTaskType(), expected.getPayload().getTaskId(), expected.getTraceId()));
        var beans = new StaticListableBeanFactory();
        beans.addBean("qaVerifier", verifier);
        var wakeups = new LinkedBlockingQueue<Object>();
        var notifications = mock(ApplicationEventPublisher.class);
        doAnswer(call -> {
            wakeups.add(call.getArgument(0));
            return null;
        }).when(notifications).publishEvent(any(Object.class));
        var listener = new RabbitTaskNotificationListener();
        ReflectionTestUtils.setField(listener, "verifiers", beans.getBeanProvider(OutboxNotificationVerifier.class));
        ReflectionTestUtils.setField(listener, "notifications", notifications);
        ReflectionTestUtils.setField(listener, "properties", properties);
        ReflectionTestUtils.setField(listener, "objectMapper", json);
        consumers = new ThreadPoolTaskExecutor();
        consumers.setCorePoolSize(1);
        consumers.setMaxPoolSize(1);
        consumers.setQueueCapacity(1);
        consumers.initialize();
        container = configuration.taskNotificationListenerContainer(connection, listener, consumers, properties);
        container.afterPropertiesSet();
        container.start();
        return wakeups;
    }

    private CachingConnectionFactory connectionFor(int port) {
        var client = new ConnectionFactory();
        client.setHost(properties.getHost());
        client.setPort(port);
        client.setVirtualHost(properties.getVirtualHost());
        client.setUsername(properties.getUsername());
        client.setPassword(properties.getPassword());
        client.setConnectionTimeout(1000);
        client.setHandshakeTimeout(1000);
        client.setChannelRpcTimeout(2000);
        client.setAutomaticRecoveryEnabled(false);
        var factory = new CachingConnectionFactory(client);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    private static ThreadPoolTaskExecutor executor(String prefix) {
        var result = new ThreadPoolTaskExecutor();
        result.setThreadNamePrefix(prefix);
        result.setCorePoolSize(1);
        result.setMaxPoolSize(1);
        result.setQueueCapacity(32);
        result.initialize();
        return result;
    }

    private static RabbitTemplate templateFor(CachingConnectionFactory factory) {
        var rabbit = new RabbitTemplate(factory);
        rabbit.setMandatory(true);
        return rabbit;
    }

    private RabbitOutboxMessagePublisher publisherFor(RabbitTemplate rabbit) {
        var result = new RabbitOutboxMessagePublisher();
        ReflectionTestUtils.setField(result, "template", rabbit);
        ReflectionTestUtils.setField(result, "properties", properties);
        ReflectionTestUtils.setField(result, "objectMapper", json);
        return result;
    }

    private static OutboxEnvelope envelope(String taskType) {
        return new OutboxEnvelope("E".repeat(26), "TASK_AVAILABLE", 1, "ASYNC_TASK", "W".repeat(26),
                Instant.parse("2026-09-29T00:00:00Z"), "qa-rabbitmq-external-it-trace",
                new OutboxEnvelope.ActorSnapshot("U".repeat(26), "U".repeat(26), "D".repeat(26)),
                new OutboxEnvelope.TaskPayload("W".repeat(26), taskType));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing isolated QA variable " + name);
        return value;
    }
}
