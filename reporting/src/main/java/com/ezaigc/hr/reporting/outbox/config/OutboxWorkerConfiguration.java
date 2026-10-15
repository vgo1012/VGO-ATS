package com.ezaigc.hr.reporting.outbox.config;

import com.ezaigc.hr.reporting.outbox.application.support.OutboxPublicationWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 只有明确启用 RabbitMQ 和发布 Worker 时才领取事件，避免禁用模式消耗任务尝试。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "hr.messaging.mode", havingValue = "rabbitmq")
public class OutboxWorkerConfiguration {
    @Bean
    @ConditionalOnProperty(name = "hr.messaging.worker-enabled", havingValue = "true")
    OutboxPublicationWorker outboxPublicationWorker() {
        return new OutboxPublicationWorker();
    }
}
