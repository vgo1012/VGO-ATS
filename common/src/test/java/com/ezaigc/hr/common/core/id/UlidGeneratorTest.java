package com.ezaigc.hr.common.core.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UlidGeneratorTest {

    @Test
    void createsCrockfordEncodedTimeSortableIdentifiers() {
        SecureRandom random = new SecureRandom(new byte[] {1, 2, 3, 4});
        String earlier = new UlidGenerator(
                Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC), random).newId();
        String later = new UlidGenerator(
                Clock.fixed(Instant.parse("2026-09-01T10:00:01Z"), ZoneOffset.UTC), random).newId();

        assertThat(earlier).matches("[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(later).isGreaterThan(earlier);
    }
}
