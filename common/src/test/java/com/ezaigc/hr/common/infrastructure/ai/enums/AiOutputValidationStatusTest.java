package com.ezaigc.hr.common.infrastructure.ai.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiOutputValidationStatusTest {

  @Test
  void roundTripsEveryPersistedValidationStatus() {
    for (AiOutputValidationStatus status : AiOutputValidationStatus.values()) {
      assertThat(AiOutputValidationStatus.fromCode(status.getCode())).isEqualTo(status);
    }
    assertThatThrownBy(() -> AiOutputValidationStatus.fromCode("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
