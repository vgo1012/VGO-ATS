package com.ezaigc.hr.recruiting.demand.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.common.infrastructure.config.PlatformExecutorProperties;
import com.ezaigc.hr.recruiting.demand.application.manager.JdParsingApplicationManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class JdTaskWakeupTest {
    @Test
    void committedEnqueueWakesAnIdleWorkerButRollbackDoesNotSubmitWork() {
        List<Runnable> queue = new ArrayList<>();
        AsyncTaskBatchRunner runner = mock(AsyncTaskBatchRunner.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("transactionalEventListenerFactory", TransactionalEventListenerFactory.class);
            context.registerBean("scheduledTaskMonitor", ScheduledTaskMonitor.class);
            context.getBeanFactory().registerSingleton("taskBatchRunner", runner);
            context.getBeanFactory().registerSingleton("manager", mock(JdParsingApplicationManager.class));
            PlatformExecutorProperties executorProperties = new PlatformExecutorProperties();
            executorProperties.getAi().setCoreSize(1);
            executorProperties.getAiWorkloadSlots().setJdParse(1);
            context.getBeanFactory().registerSingleton("executorProperties", executorProperties);
            context.registerBean("aiTaskExecutor", TaskExecutor.class, () -> queue::add);
            context.register(JdParseWorker.class);
            context.refresh();
            JdParseWorker worker = context.getBean(JdParseWorker.class);
            worker.poll();
            queue.removeFirst().run();
            worker.poll();
            assertThat(queue).isEmpty();
            var transactions = new TransactionTemplate(new TestTransactions());
            transactions.executeWithoutResult(status -> {
                context.publishEvent(new AsyncTaskAvailable("JD_PARSE", "committed", null));
                assertThat(queue).isEmpty();
            });
            assertThat(queue).hasSize(1);
            queue.removeFirst().run();
            transactions.executeWithoutResult(status -> {
                context.publishEvent(new AsyncTaskAvailable("JD_PARSE", "rolled-back", null));
                status.setRollbackOnly();
            });
            assertThat(queue).isEmpty();
            context.publishEvent(new AsyncTaskAvailable("OTHER_TASK", "irrelevant", null));
            assertThat(queue).isEmpty();
            context.publishEvent(new AsyncTaskAvailable("JD_PARSE", "outside-transaction", null));
            assertThat(queue).hasSize(1);
            queue.removeFirst().run();
            verify(runner, times(3)).run(eq("JD_PARSE"), eq(1), any());
        }
    }

    private static final class TestTransactions extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
