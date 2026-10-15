package com.ezaigc.hr.common.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import org.junit.jupiter.api.Test;

class PageLimitsTest {

  @Test
  void resolvesDefaultAndAcceptedValue() {
    assertThat(PageLimits.resolve(null)).isEqualTo(20);
    assertThat(PageLimits.resolve(null, 50, 100)).isEqualTo(50);
    assertThat(PageLimits.resolve(100, 50, 100)).isEqualTo(100);
  }

  @Test
  void rejectsInvalidRequestedAndConfigurationValues() {
    assertThatThrownBy(() -> PageLimits.resolve(0, 50, 100))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("PAGE_LIMIT_INVALID"));
    assertThatThrownBy(() -> PageLimits.resolve(null, 0, 100))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
