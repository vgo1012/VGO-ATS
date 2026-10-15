package com.ezaigc.hr.integration.config;

import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.nio.NioParams;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 连接、网络写入队列和确认等待均有界；不在模板内部增加不可见的重试循环。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "hr.messaging.mode", havingValue = "rabbitmq")
public class RabbitMessagingConfiguration {
    @Bean
    CachingConnectionFactory outboxRabbitConnectionFactory(MessagingProperties properties,
            @Qualifier(ExecutorNames.BROKER_IO) ThreadPoolTaskExecutor networkExecutor,
            @Qualifier(ExecutorNames.BROKER_CALLBACKS) ThreadPoolTaskExecutor callbacks,
            ThreadPoolTaskScheduler scheduler) throws NoSuchAlgorithmException {
        var client = new ConnectionFactory();
        client.setHost(properties.getHost());
        client.setPort(properties.getPort());
        client.setVirtualHost(properties.getVirtualHost());
        client.setUsername(properties.getUsername());
        client.setPassword(properties.getPassword());
        client.setConnectionTimeout(properties.getConnectionTimeoutMillis());
        client.setHandshakeTimeout(properties.getConnectionTimeoutMillis());
        client.setChannelRpcTimeout(properties.getChannelRpcTimeoutMillis());
        client.setShutdownTimeout(5000);
        client.setRequestedHeartbeat(properties.getHeartbeatSeconds());
        client.setHeartbeatExecutor(scheduler.getScheduledThreadPoolExecutor());
        client.setAutomaticRecoveryEnabled(false);
        // 应用协议上限更小，让消费者有机会拒绝超限信封；驱动仍保留绝对内存边界。
        client.setMaxInboundMessageBodySize(65536);
        client.setWorkPoolTimeout(properties.getConnectionTimeoutMillis());
        client.useNio();
        client.setNioParams(new NioParams().setNbIoThreads(1)
                .setNioExecutor(networkExecutor.getThreadPoolExecutor())
                // 关闭回调不能排入被长期 IO 循环占用的单线程池。
                .setConnectionShutdownExecutor(callbacks.getThreadPoolExecutor())
                .setWriteQueueCapacity(32)
                .setWriteEnqueuingTimeoutInMs(properties.getConnectionTimeoutMillis()));
        if (properties.isSslEnabled()) {
            // 使用 JVM 信任库校验证书及主机名，禁止使用 SDK 的 trust-all 便捷初始化。
            client.useSslProtocol(SSLContext.getDefault());
            client.enableHostnameVerification();
        }
        var factory = new CachingConnectionFactory(client);
        factory.setExecutor(callbacks);
        factory.setChannelCacheSize(2);
        factory.setChannelCheckoutTimeout(properties.getConnectionTimeoutMillis());
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    @Bean
    RabbitTemplate outboxRabbitTemplate(CachingConnectionFactory outboxRabbitConnectionFactory) {
        var template = new RabbitTemplate(outboxRabbitConnectionFactory);
        template.setMandatory(true);
        return template;
    }
}
