package com.ezaigc.hr.integration.config;

import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.integration.messaging.RabbitTaskNotificationListener;
import com.ezaigc.hr.talent.api.ResumeFactProjectionSource;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

/** 显式启用后声明持久有界通知队列；消费错误死信留存，工作恢复仍以 SQL 为准。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "hr.messaging.mode", havingValue = "rabbitmq")
public class RabbitTaskMessagingConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitTaskMessagingConfiguration.class);
    private static final long MAX_MESSAGES = 10000;
    private static final int MAX_BYTES = 67108864;
    private static final int DELIVERY_LIMIT = 5;

    @Bean
    RabbitAdmin outboxRabbitAdmin(CachingConnectionFactory outboxRabbitConnectionFactory) {
        return new RabbitAdmin(outboxRabbitConnectionFactory);
    }

    @Bean
    Declarables taskNotificationTopology(MessagingProperties properties) {
        var exchange = new DirectExchange(properties.getExchange(), true, false);
        var deadExchange = new DirectExchange(properties.getExchange() + ".dead", true, false);
        Queue searchDead = boundedQueue(properties.getSearchQueue() + ".dead").build();
        Queue search = boundedQueue(properties.getSearchQueue())
                .deliveryLimit(DELIVERY_LIMIT)
                .deadLetterExchange(deadExchange.getName())
                .deadLetterRoutingKey(ResumeFactProjectionSource.TASK_TYPE)
                .build();
        Queue workerDead = boundedQueue(properties.getWorkerQueue() + ".dead").build();
        Queue worker = boundedQueue(properties.getWorkerQueue())
                .deliveryLimit(DELIVERY_LIMIT)
                .deadLetterExchange(deadExchange.getName())
                .build();
        var declarations = new ArrayList<Declarable>();
        declarations.add(exchange);
        declarations.add(deadExchange);
        declarations.add(searchDead);
        declarations.add(search);
        declarations.add(workerDead);
        declarations.add(worker);
        declarations.add(BindingBuilder.bind(searchDead).to(deadExchange)
                .with(ResumeFactProjectionSource.TASK_TYPE));
        declarations.add(BindingBuilder.bind(search).to(exchange)
                .with(ResumeFactProjectionSource.TASK_TYPE));
        properties.getWorkerRoutingKeys().stream().distinct().forEach(routingKey -> {
            declarations.add(BindingBuilder.bind(workerDead).to(deadExchange).with(routingKey));
            declarations.add(BindingBuilder.bind(worker).to(exchange).with(routingKey));
        });
        return new Declarables(declarations);
    }

    @Bean
    @ConditionalOnProperty(name = "hr.messaging.consumer-enabled", havingValue = "true")
    SimpleMessageListenerContainer taskNotificationListenerContainer(
            CachingConnectionFactory outboxRabbitConnectionFactory,
            RabbitTaskNotificationListener listener,
            @Qualifier(ExecutorNames.BROKER_CONSUMERS) TaskExecutor executor,
            MessagingProperties properties) {
        var container = new SimpleMessageListenerContainer(outboxRabbitConnectionFactory);
        container.setQueueNames(properties.getSearchQueue(), properties.getWorkerQueue());
        container.setTaskExecutor(executor);
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(1);
        container.setPrefetchCount(1);
        container.setBatchSize(1);
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        container.setDefaultRequeueRejected(false);
        container.setErrorHandler(failure -> LOGGER.warn(
                "task-notification-consumer-failed errorType={}",
                failure.getClass().getName(),
                SafeLogException.from(failure)));
        container.setMessageListener(listener);
        container.setReceiveTimeout(1000);
        container.setConsumerStartTimeout(5000);
        container.setShutdownTimeout(5000);
        container.setForceStop(true);
        return container;
    }

    private static QueueBuilder boundedQueue(String name) {
        return QueueBuilder.durable(name)
                .quorum()
                .maxLength(MAX_MESSAGES)
                .maxLengthBytes(MAX_BYTES)
                .overflow(QueueBuilder.Overflow.rejectPublish);
    }
}
