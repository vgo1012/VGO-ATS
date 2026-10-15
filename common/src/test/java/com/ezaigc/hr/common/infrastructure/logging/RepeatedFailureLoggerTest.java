package com.ezaigc.hr.common.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class RepeatedFailureLoggerTest {
    @Test
    void emitsFirstFailureThenMinuteSummariesAndOneRecoveryWithoutRawException() {
        List<Object[]> warnings = new ArrayList<>();
        List<Object[]> recoveries = new ArrayList<>();
        Logger logger = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("error")) warnings.add((Object[]) arguments[1]);
                    if (method.getName().equals("info")) recoveries.add(Arrays.copyOfRange(arguments, 1, arguments.length));
                    return null;
                });
        AtomicLong now = new AtomicLong();
        RepeatedFailureLogger failures = new RepeatedFailureLogger(logger, now::get);
        for (int attempt = 0; attempt < 1000; attempt++) failures.failed("database", new IllegalStateException("private-secret"));
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()[3]).isInstanceOf(SafeLogException.class);
        assertThat(((Throwable) warnings.getFirst()[3]).getMessage()).doesNotContain("private-secret");
        now.set(Duration.ofMinutes(1).toNanos());
        failures.failed("database", new IllegalStateException());
        assertThat(warnings).hasSize(2);
        assertThat(warnings.getLast()[2]).isEqualTo(999L);
        failures.failed("database", new IllegalStateException());
        failures.recovered("database");
        failures.recovered("database");
        assertThat(recoveries).hasSize(1);
        assertThat(recoveries.getFirst()[1]).isEqualTo(1L);
        failures.failed("database", new IllegalStateException());
        assertThat(warnings).hasSize(3);
    }
}
