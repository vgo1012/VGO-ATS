package com.ezaigc.hr.reporting.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AsyncTaskPropertiesTest {

  @Test
  void acceptsDefaultLeaseAndRejectsUnsafeHeartbeatWindows() {
    AsyncTaskProperties properties = new AsyncTaskProperties();
    assertThat(properties.isLeaseDurationValid()).isTrue();

    properties.setLeaseDuration(Duration.ofMinutes(1));
    assertThat(properties.isLeaseDurationValid()).isFalse();

    properties.setLeaseDuration(Duration.ofHours(2));
    assertThat(properties.isLeaseDurationValid()).isFalse();
  }
}
