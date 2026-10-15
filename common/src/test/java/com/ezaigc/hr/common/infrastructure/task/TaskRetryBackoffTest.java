package com.ezaigc.hr.common.infrastructure.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TaskRetryBackoffTest {
    @Test
    void jitterStaysInsideTheExponentialWindowAndNeverExceedsTheCap() {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        long[] caps = {5_000, 10_000, 20_000, 40_000, 80_000, 160_000, 300_000};
        for (int attempt = 1; attempt <= 20; attempt++) {
            long cap = caps[Math.min(attempt - 1, caps.length - 1)];
            var samples = new HashSet<Long>();
            for (int sample = 0; sample < 100; sample++) {
                long delay = Duration.between(now, TaskRetryBackoff.nextRetryAt(now, attempt)).toMillis();
                assertThat(delay).isBetween(cap / 2, cap);
                samples.add(delay);
            }
            assertThat(samples.size()).isGreaterThan(1);
        }
        assertThatThrownBy(() -> TaskRetryBackoff.nextRetryAt(now, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaskRetryBackoff.nextRetryAt(now, 21)).isInstanceOf(IllegalArgumentException.class);
    }
}
