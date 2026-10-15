package com.ezaigc.hr.talent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeDateValueTest {
    @Test
    void preservesMonthYearAndPresentWithoutInventingADay() {
        var now = LocalDate.of(2026, 9, 8);
        assertThat(ResumeDateValue.parse("2022-03").text()).isEqualTo("2022-03");
        assertThat(ResumeDateValue.parse("2022-03").completeDate()).isNull();
        assertThat(ResumeDateValue.parse("2022").completeDate()).isNull();
        assertThat(ResumeDateValue.parse("PRESENT").latest(now)).isEqualTo(now);
        assertThat(ResumeDateValue.parse("2024-02").latest(now)).isEqualTo(LocalDate.of(2024,2,29));
        assertThat(ResumeDateValue.parse("2024-02-29").completeDate()).isEqualTo(LocalDate.of(2024,2,29));
        assertThat(ResumeDateValue.parse(null)).isNull();
    }

    @Test
    void rejectsInvalidCalendarDatesAndUnknownMarkers() {
        for (String value : new String[]{"2023-02-29", "2022-13", "2022-00", "present-ish", "2022/3"}) {
            assertThatThrownBy(() -> ResumeDateValue.parse(value)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void countsExplicitPresentAndMergesOverlapsButKeepsMissingEndsUnknown() {
        var clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        var current = new ResumeFactSnapshotData.Work("a", "A", "工程师", null, null, null,
            List.of(), List.of(), "2022-03", "PRESENT");
        assertThat(ResumeExperienceYears.calculate(List.of(current, current), clock)).isBetween(
            new BigDecimal("4.4"), new BigDecimal("4.5"));
        var unknown = new ResumeFactSnapshotData.Work("b", "B", "工程师", LocalDate.of(2022,3,1), null,
            null, List.of(), List.of());
        assertThat(ResumeExperienceYears.calculate(List.of(unknown), clock)).isNull();
    }
}
