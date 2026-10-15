package com.ezaigc.hr.intelligence.matching.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MatchingStatusEnumTest {

  @Test
  void roundTripsPersistedMatchingStatuses() {
    assertThat(CriterionMatchStatus.fromCode("PASS")).isEqualTo(CriterionMatchStatus.PASS);
    assertThat(CriterionMatchStatus.UNKNOWN.matches("UNKNOWN")).isTrue();
    assertThat(MatchDimensionStatus.fromCode("KNOWN")).isEqualTo(MatchDimensionStatus.KNOWN);
    assertThat(MatchDimensionStatus.UNKNOWN.matches(null)).isFalse();
    assertThat(MatchRunStatus.fromCode("RUNNING")).isEqualTo(MatchRunStatus.RUNNING);
    assertThat(MatchRunStatus.fromCode("PARTIAL")).isEqualTo(MatchRunStatus.PARTIAL);
    assertThat(MatchRunAttemptStatus.fromCode("FAILED"))
        .isEqualTo(MatchRunAttemptStatus.FAILED);
    assertThat(MatchRunItemOutcomeStatus.fromCode("SUCCEEDED"))
        .isEqualTo(MatchRunItemOutcomeStatus.SUCCEEDED);
    assertThat(WeightProfileStatus.values())
        .extracting(WeightProfileStatus::getCode)
        .containsExactly("ACTIVE", "DISABLED");
  }

  @Test
  void rejectsUnknownMatchingStatuses() {
    assertThatThrownBy(() -> CriterionMatchStatus.fromCode("SKIPPED"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MatchDimensionStatus.fromCode("PARTIAL"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MatchRunAttemptStatus.fromCode("PARTIAL"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
