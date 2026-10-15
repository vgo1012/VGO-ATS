package com.ezaigc.hr.recruiting.demand.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdAiModelRunStatusTest {

  @Test
  void annotationSqlCodesStayAlignedWithLifecycleEnum() {
    assertThat(JdAiModelRunStatus.values())
        .extracting(JdAiModelRunStatus::getCode)
        .containsExactly("RUNNING", "SUCCEEDED", "FAILED");
    assertThat(JdAiModelRunStatus.Codes.RUNNING)
        .isEqualTo(JdAiModelRunStatus.RUNNING.getCode());
    assertThat(JdAiModelRunStatus.Codes.SUCCEEDED)
        .isEqualTo(JdAiModelRunStatus.SUCCEEDED.getCode());
    assertThat(JdAiModelRunStatus.Codes.FAILED)
        .isEqualTo(JdAiModelRunStatus.FAILED.getCode());
  }
}
