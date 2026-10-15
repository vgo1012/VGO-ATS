package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ScheduledTaskRegistrationContractTest {
    @Test
    void everyScheduledEntryHasUniqueDiscoverableTaskMetadata() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ezaigc.hr");
        Set<String> channels = new HashSet<>();
        for (var type : classes) {
            for (var method : type.getMethods()) {
                if (!method.isAnnotatedWith(Scheduled.class)) continue;
                assertTrue(type.isAnnotatedWith(ManagedScheduledWorker.class), type.getName());
                assertTrue(type.getSimpleName().endsWith("Worker"), type.getName());
                ScheduledTaskInfo info = method.reflect().getAnnotation(ScheduledTaskInfo.class);
                assertNotNull(info, method.getFullName());
                assertFalse(info.name().isBlank());
                assertFalse(info.module().isBlank());
                assertTrue(info.channels().length > 0);
                for (String channel : info.channels()) {
                    assertTrue(channel.matches("[A-Za-z0-9_-]{1,100}"));
                    assertTrue(channels.add(channel), "调度运行通道不可重复: " + channel);
                }
                Scheduled schedule = method.reflect().getAnnotation(Scheduled.class);
                assertTrue(!schedule.cron().isBlank() || !schedule.fixedDelayString().isBlank()
                        || schedule.fixedDelay() > 0, "新增调度类型须同步完善任务中心时间解释");
            }
        }
        assertFalse(channels.isEmpty());
    }
}
