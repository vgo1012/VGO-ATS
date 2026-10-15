package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleTiming;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResumeLifecycleTimingTest {
  @ParameterizedTest
  @CsvSource({"0,PENDING_SCREENING,NORMAL", "1,PENDING_DISPOSAL,NORMAL",
      "2,PENDING_DISPOSAL,YELLOW", "3,PENDING_DISPOSAL,RED", "4,SHARED,RED",
      "8,SHARED,RED"})
  void thresholdsFollowCompletedWorkdays(int days, String phase, String warning) {
    assertThat(ResumeLifecycleTiming.phase(days)).isEqualTo(phase);
    assertThat(ResumeLifecycleTiming.warning(days)).isEqualTo(warning);
  }

  @Test
  void midnightCatchupDoesNotSettleBeforeTheOneAmBoundary() {
    assertThat(ResumeLifecycleTiming.settlementDate(Instant.parse("2026-09-14T16:59:59Z")))
        .isEqualTo(LocalDate.of(2026, 9, 13));
    assertThat(ResumeLifecycleTiming.settlementDate(Instant.parse("2026-09-14T17:00:00Z")))
        .isEqualTo(LocalDate.of(2026, 9, 14));
  }

  @Test
  void fridayCompletionIsSettledOnSaturdayMorning() {
    assertThat(ResumeLifecycleTiming.transitionAt(LocalDate.of(2026, 9, 18)))
        .isEqualTo(Instant.parse("2026-09-18T17:00:00Z"));
  }
}
