package com.ezaigc.hr.common.infrastructure.config;

import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(PlatformExecutorProperties.class)
public class PlatformExecutorConfiguration {

    @Bean(name = ExecutorNames.AI)
    TaskExecutor aiTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-ai-", properties.getAi(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.MATCHING)
    TaskExecutor matchingTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-matching-", properties.getMatching(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.SEARCH)
    TaskExecutor searchTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-search-", properties.getSearch(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.MAINTENANCE)
    TaskExecutor maintenanceTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-maintenance-", properties.getMaintenance(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.REPORTING)
    TaskExecutor reportingTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-reporting-", properties.getReporting(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.DIRECTORY)
    TaskExecutor directoryTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-directory-", properties.getDirectory(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.MAIL)
    TaskExecutor mailTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-mail-", properties.getMail(), properties.getShutdownAwait());
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler(PlatformExecutorProperties properties) {
        int awaitSeconds = awaitSeconds(properties.getShutdownAwait());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getSchedulerSize());
        scheduler.setThreadNamePrefix("hr-scheduler-");
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setAwaitTerminationSeconds(awaitSeconds);
        return scheduler;
    }

    @Bean(name = ExecutorNames.OUTBOX)
    TaskExecutor outboxTaskExecutor(PlatformExecutorProperties properties) {
        return executor("hr-outbox-", properties.getOutbox(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.BROKER_IO)
    ThreadPoolTaskExecutor brokerIoExecutor(PlatformExecutorProperties properties) {
        return executor("hr-broker-io-", properties.getBrokerIo(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.BROKER_CALLBACKS)
    ThreadPoolTaskExecutor brokerCallbackExecutor(PlatformExecutorProperties properties) {
        return executor("hr-broker-callback-", properties.getBrokerCallbacks(), properties.getShutdownAwait());
    }

    @Bean(name = ExecutorNames.BROKER_CONSUMERS)
    TaskExecutor brokerConsumerExecutor(PlatformExecutorProperties properties) {
        return executor("hr-broker-consumer-", properties.getBrokerConsumers(), properties.getShutdownAwait());
    }

    private static ThreadPoolTaskExecutor executor(
            String threadNamePrefix,
            PlatformExecutorProperties.Pool properties,
            Duration shutdownAwait) {
        if (properties.getMaxSize() < properties.getCoreSize()) {
            throw new IllegalArgumentException("executor maxSize must be at least coreSize");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCoreSize());
        executor.setMaxPoolSize(properties.getMaxSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitSeconds(shutdownAwait));
        executor.setAcceptTasksAfterContextClose(false);
        executor.setStrictEarlyShutdown(true);
        return executor;
    }

    private static int awaitSeconds(Duration value) {
        if (value == null || value.toSeconds() < 1) {
            throw new IllegalArgumentException("executor shutdownAwait must be positive");
        }
        return Math.toIntExact(value.toSeconds());
    }
}
