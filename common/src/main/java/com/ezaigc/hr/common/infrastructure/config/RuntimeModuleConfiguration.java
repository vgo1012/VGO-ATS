package com.ezaigc.hr.common.infrastructure.config;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.id.UlidGenerator;
import com.ezaigc.hr.common.core.module.ModuleDescriptor;
import com.ezaigc.hr.common.core.module.ModuleRegistry;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskBatchRunner;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskMonitor;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RuntimeModuleProperties.class, PaginationProperties.class})
@Import(PlatformExecutorConfiguration.class)
public class RuntimeModuleConfiguration {

    @Bean
    ScheduledTaskMonitor scheduledTaskMonitor() {
        return new ScheduledTaskMonitor();
    }

    @Bean
    Clock platformClock() {
        return Clock.systemUTC();
    }

    @Bean
    PlatformIdGenerator platformIdGenerator(Clock platformClock) {
        return new UlidGenerator(platformClock);
    }

    @Bean
    CursorCodec cursorCodec(PaginationProperties properties) {
        return new SignedCursorCodec(properties.getCursorSigningKey());
    }

    @Bean
    AsyncTaskBatchRunner asyncTaskBatchRunner(AsyncTaskPort tasks, TaskScheduler taskScheduler) {
        return new AsyncTaskBatchRunner(tasks, taskScheduler);
    }

    @Bean
    ModuleRegistry moduleRegistry(List<ModuleDescriptor> descriptors) {
        return new ModuleRegistry(descriptors);
    }

    @Bean
    SmartInitializingSingleton runtimeModuleVerifier(
            ModuleRegistry registry, RuntimeModuleProperties properties) {
        return () -> registry.requireModules(properties.getRequiredModules());
    }
}
