package com.ezaigc.hr.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorConfiguration;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 验证真实 SDK 的装配与 TLS 参数；不创建网络连接，不作为 Broker 协议证据。 */
class RabbitMessagingConfigurationTest {
    @Test
    void disabledModeDoesNotCreateAnyBrokerResources() {
        try (var context = new AnnotationConfigApplicationContext(RabbitMessagingConfiguration.class)) {
            assertThat(context.getBeansOfType(CachingConnectionFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(RabbitTemplate.class)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void enabledModeUsesBoundedIoAndCorrelatedMandatoryPublishing(boolean tls) throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("hr.messaging.mode", "rabbitmq")));
            var properties = new MessagingProperties();
            properties.setHost("synthetic.invalid");
            properties.setUsername("synthetic-user");
            properties.setPassword("synthetic-only-password");
            properties.setSslEnabled(tls);
            context.registerBean(MessagingProperties.class, () -> properties);
            context.register(PlatformExecutorConfiguration.class, RabbitMessagingConfiguration.class);
            context.refresh();
            var factory = context.getBean(CachingConnectionFactory.class);
            var client = factory.getRabbitConnectionFactory();
            assertThat(factory.isPublisherConfirms()).isTrue();
            assertThat(factory.isPublisherReturns()).isTrue();
            assertThat(factory.getChannelCacheSize()).isEqualTo(2);
            assertThat(context.getBean(RabbitTemplate.class).isMandatoryFor(new Message(new byte[0]))).isTrue();
            assertThat(client.getConnectionTimeout()).isEqualTo(properties.getConnectionTimeoutMillis());
            assertThat(client.getHandshakeTimeout()).isEqualTo(properties.getConnectionTimeoutMillis());
            assertThat(client.getChannelRpcTimeout()).isEqualTo(properties.getChannelRpcTimeoutMillis());
            assertThat(client.isAutomaticRecoveryEnabled()).isFalse();
            assertThat(client.getNioParams().getNbIoThreads()).isEqualTo(1);
            assertThat(client.getNioParams().getWriteQueueCapacity()).isEqualTo(32);
            assertThat(client.getNioParams().getWriteEnqueuingTimeoutInMs()).isEqualTo(properties.getConnectionTimeoutMillis());
            assertThat(client.getNioParams().getNioExecutor()).isSameAs(
                    context.getBean(ExecutorNames.BROKER_IO, ThreadPoolTaskExecutor.class).getThreadPoolExecutor());
            assertThat(client.getNioParams().getConnectionShutdownExecutor()).isSameAs(
                    context.getBean(ExecutorNames.BROKER_CALLBACKS, ThreadPoolTaskExecutor.class).getThreadPoolExecutor());
            assertThat(client.getNioParams().getConnectionShutdownExecutor()).isNotSameAs(client.getNioParams().getNioExecutor());
            assertThat(client.isSSL()).isEqualTo(tls);
            if (tls) {
                var engine = SSLContext.getDefault().createSSLEngine("synthetic.invalid", 5671);
                client.getNioParams().getSslEngineConfigurator().configure(engine);
                assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
            }
        }
    }

}
