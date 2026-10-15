package com.ezaigc.hr.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.ezaigc.hr.integration.messaging.RabbitTaskNotificationListener;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class RabbitTaskMessagingConfigurationTest {
    private final RabbitTaskMessagingConfiguration configuration = new RabbitTaskMessagingConfiguration();

    @Test
    void searchAndWorkerQueuesAreDurableBoundedAndDeadLetteredPerRoute() {
        var properties = new MessagingProperties();
        var topology = configuration.taskNotificationTopology(properties);
        var queues = topology.getDeclarablesByType(Queue.class);
        assertThat(queues).hasSize(4).allSatisfy(queue -> {
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.isExclusive()).isFalse();
            assertThat(queue.isAutoDelete()).isFalse();
            assertThat(queue.getArguments())
                    .containsEntry("x-queue-type", "quorum")
                    .containsEntry("x-max-length", 10000L)
                    .containsEntry("x-max-length-bytes", 67108864)
                    .containsEntry("x-overflow", "reject-publish");
        });
        assertThat(queues.stream()
                .filter(queue -> Set.of(properties.getSearchQueue(), properties.getWorkerQueue())
                        .contains(queue.getName())))
                .allSatisfy(queue -> assertThat(queue.getArguments())
                        .containsEntry("x-delivery-limit", 5)
                        .containsEntry("x-dead-letter-exchange", properties.getExchange() + ".dead"));
        var routingKeys = topology.getDeclarablesByType(Binding.class).stream()
                .map(Binding::getRoutingKey)
                .collect(Collectors.toSet());
        assertThat(routingKeys).contains("RESUME_SEARCH_PROJECT").containsAll(properties.getWorkerRoutingKeys());
        assertThat(topology.getDeclarablesByType(Binding.class))
                .hasSize(2 + properties.getWorkerRoutingKeys().size() * 2);
    }

    @Test
    void consumerUsesBothConfiguredQueuesAndBoundedDedicatedExecutor() {
        var connection = mock(CachingConnectionFactory.class);
        var listener = mock(RabbitTaskNotificationListener.class);
        var executor = mock(TaskExecutor.class);
        var properties = new MessagingProperties();
        properties.setSearchQueue("synthetic.search.queue");
        properties.setWorkerQueue("synthetic.worker.queue");
        var container = configuration.taskNotificationListenerContainer(
                connection, listener, executor, properties);
        assertThat(container.getQueueNames())
                .containsExactly(properties.getSearchQueue(), properties.getWorkerQueue());
        assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
        assertThat(container.getMessageListener()).isSameAs(listener);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(container, "getPrefetchCount")).isEqualTo(1);
        assertThat(ReflectionTestUtils.<TaskExecutor>invokeMethod(container, "getTaskExecutor"))
                .isSameAs(executor);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(container, "isDefaultRequeueRejected"))
                .isFalse();
        verifyNoInteractions(connection, executor);
        verify(listener).isAsyncReplies();
        verifyNoMoreInteractions(listener);
    }
}
