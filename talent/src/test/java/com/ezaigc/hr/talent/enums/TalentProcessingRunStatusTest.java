package com.ezaigc.hr.talent.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TalentProcessingRunStatusTest {

  @Test
  void roundTripsCompleteProcessingLifecycle() {
    assertThat(TalentProcessingRunStatus.values())
        .extracting(TalentProcessingRunStatus::getCode)
        .containsExactly("RUNNING", "SUCCEEDED", "FAILED");
    assertThat(TalentProcessingRunStatus.fromCode("RUNNING"))
        .isEqualTo(TalentProcessingRunStatus.RUNNING);
    assertThatThrownBy(() -> TalentProcessingRunStatus.fromCode("CANCELLED"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
