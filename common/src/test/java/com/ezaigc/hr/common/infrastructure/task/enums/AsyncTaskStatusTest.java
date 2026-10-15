package com.ezaigc.hr.common.infrastructure.task.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AsyncTaskStatusTest {

  @Test
  void classifiesTerminalAndCancellationStates() {
    assertThat(AsyncTaskStatus.SUCCEEDED.isTerminal()).isTrue();
    assertThat(AsyncTaskStatus.FAILED.isTerminal()).isTrue();
    assertThat(AsyncTaskStatus.CANCELLED.isTerminal()).isTrue();
    assertThat(AsyncTaskStatus.RUNNING.isTerminal()).isFalse();
    assertThat(AsyncTaskStatus.CANCELLING.canCompleteCancellation(false)).isTrue();
    assertThat(AsyncTaskStatus.RUNNING.canCompleteCancellation(true)).isTrue();
    assertThat(AsyncTaskStatus.RUNNING.canCompleteCancellation(false)).isFalse();
  }

  @Test
  void roundTripsStableCodesAndRejectsUnknownValues() {
    assertThat(AsyncTaskStatus.fromCode("QUEUED")).isEqualTo(AsyncTaskStatus.QUEUED);
    assertThat(AsyncTaskStatus.QUEUED.matches("QUEUED")).isTrue();
    assertThat(AsyncTaskStatus.QUEUED.matches(null)).isFalse();
    assertThatThrownBy(() -> AsyncTaskStatus.fromCode("WAITING"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void annotationSqlCodesStayAlignedWithEnumCodes() {
    assertThat(AsyncTaskStatus.Codes.QUEUED).isEqualTo(AsyncTaskStatus.QUEUED.getCode());
    assertThat(AsyncTaskStatus.Codes.RUNNING).isEqualTo(AsyncTaskStatus.RUNNING.getCode());
    assertThat(AsyncTaskStatus.Codes.CANCELLING).isEqualTo(AsyncTaskStatus.CANCELLING.getCode());
    assertThat(AsyncTaskStatus.Codes.SUCCEEDED).isEqualTo(AsyncTaskStatus.SUCCEEDED.getCode());
    assertThat(AsyncTaskStatus.Codes.FAILED).isEqualTo(AsyncTaskStatus.FAILED.getCode());
    assertThat(AsyncTaskStatus.Codes.CANCELLED).isEqualTo(AsyncTaskStatus.CANCELLED.getCode());
  }
}
