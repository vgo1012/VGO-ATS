package com.ezaigc.hr.common.infrastructure.logging;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

public final class RepeatedFailureLogger {
    private final Logger logger;
    private final LongSupplier ticker;
    private final Map<String, Failure> failures = new LinkedHashMap<>();

    public RepeatedFailureLogger(Logger logger) { this(logger, System::nanoTime); }

    RepeatedFailureLogger(Logger logger, LongSupplier ticker) {
        this.logger = logger;
        this.ticker = ticker;
    }

    public synchronized void failed(String operation, Throwable failure) {
        String key = operation + ":" + failure.getClass().getName();
        long now = ticker.getAsLong();
        Failure previous = failures.get(key);
        if (previous == null) {
            if (failures.size() == 16) failures.remove(failures.keySet().iterator().next());
            previous = new Failure();
            failures.put(key, previous);
        } else if (now - previous.loggedAt < Duration.ofMinutes(1).toNanos()) {
            previous.suppressed++;
            return;
        }
        logger.error("background-operation-failed operation={} errorType={} suppressed={}",
                operation, failure.getClass().getName(), previous.suppressed, SafeLogException.from(failure));
        previous.loggedAt = now;
        previous.suppressed = 0;
    }

    public synchronized void recovered(String operation) {
        long suppressed = 0;
        boolean recovered = false;
        var iterator = failures.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().startsWith(operation + ":")) {
                recovered = true;
                suppressed += entry.getValue().suppressed;
                iterator.remove();
            }
        }
        if (recovered) logger.info("background-operation-recovered operation={} suppressed={}", operation, suppressed);
    }

    private static final class Failure {
        private long loggedAt;
        private long suppressed;
    }
}
