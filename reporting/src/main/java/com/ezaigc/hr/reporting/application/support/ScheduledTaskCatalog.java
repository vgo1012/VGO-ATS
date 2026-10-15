package com.ezaigc.hr.reporting.application.support;

import com.ezaigc.hr.common.infrastructure.task.ManagedScheduledWorker;
import com.ezaigc.hr.common.infrastructure.task.ScheduledTaskInfo;
import com.ezaigc.hr.reporting.domain.dto.ScheduledTaskDefinitionDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTaskCatalog {
    @Resource private ApplicationContext context;
    @Resource private Environment environment;
    private List<Registration> registrations = List.of();

    @PostConstruct
    public void initialize() throws IOException, ClassNotFoundException {
        var resolver = new PathMatchingResourcePatternResolver(context.getClassLoader());
        var readers = new CachingMetadataReaderFactory(resolver);
        List<Registration> discovered = new ArrayList<>();
        Set<String> channels = new HashSet<>();
        for (var resource : resolver.getResources("classpath*:com/ezaigc/hr/**/*Worker.class")) {
            var metadata = readers.getMetadataReader(resource);
            if (!metadata.getAnnotationMetadata().hasAnnotation(ManagedScheduledWorker.class.getName())) continue;
            Class<?> type = Class.forName(metadata.getClassMetadata().getClassName(), false, context.getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                Scheduled schedule = method.getAnnotation(Scheduled.class);
                if (schedule == null) continue;
                ScheduledTaskInfo info = method.getAnnotation(ScheduledTaskInfo.class);
                if (info == null || info.channels().length == 0) throw new IllegalStateException("定时任务缺少运行目录定义");
                for (String channel : info.channels()) {
                    if (!channels.add(channel)) throw new IllegalStateException("定时任务运行通道重复");
                }
                discovered.add(new Registration(type, schedule, info));
            }
        }
        registrations = List.copyOf(discovered);
    }

    public List<ScheduledTaskDefinitionDTO> definitions() {
        return registrations.stream().map(this::definition)
                .sorted(Comparator.comparing(ScheduledTaskDefinitionDTO::getModule)
                        .thenComparing(ScheduledTaskDefinitionDTO::getCode)).toList();
    }

    private ScheduledTaskDefinitionDTO definition(Registration registration) {
        Scheduled schedule = registration.getSchedule();
        ScheduledTaskInfo info = registration.getInfo();
        boolean enabled = context.getBeanNamesForType(registration.getWorkerType(), true, false).length > 0;
        if (!info.enabledProperty().isBlank()) {
            enabled &= environment.getProperty(info.enabledProperty(), Boolean.class, info.enabledByDefault());
        }
        String cron = resolve(schedule.cron());
        String zoneText = resolve(schedule.zone());
        ZoneId zone = zoneText.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zoneText);
        Long interval = null;
        Instant next = null;
        String type;
        String expression;
        if (!cron.isBlank()) {
            type = "CRON";
            expression = cron;
            enabled &= !Scheduled.CRON_DISABLED.equals(cron);
            if (enabled) {
                ZonedDateTime nextDate = CronExpression.parse(cron).next(ZonedDateTime.now(zone));
                next = nextDate == null ? null : nextDate.toInstant();
            }
        } else {
            type = "FIXED_DELAY";
            expression = resolve(schedule.fixedDelayString());
            interval = expression.isBlank() ? schedule.timeUnit().toMillis(schedule.fixedDelay())
                    : DurationStyle.detectAndParse(expression, schedule.timeUnit().toChronoUnit()).toMillis();
        }
        return new ScheduledTaskDefinitionDTO(info.channels()[0], info.name(), info.module(), enabled,
                type, expression, zone.getId(), interval, next, List.of(info.channels()), List.of(info.businessTypes()), info.idleBackoffMaxMillis());
    }

    private String resolve(String value) { return environment.resolveRequiredPlaceholders(value); }

    @Value
    private static class Registration {
        Class<?> workerType;
        Scheduled schedule;
        ScheduledTaskInfo info;
    }
}
