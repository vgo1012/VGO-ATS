package com.ezaigc.hr.common.web.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import org.junit.jupiter.api.Test;

class OptimisticUpdatesTest {

  @Test
  void acceptsExactlyOneChangedRow() {
    assertThatCode(() -> OptimisticUpdates.requireOne(1, "VERSION_CONFLICT", "已变化"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingOrUnexpectedMultipleRows() {
    assertThatThrownBy(() -> OptimisticUpdates.requireOne(0, "USER_VERSION_CONFLICT", "已变化"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("USER_VERSION_CONFLICT");
              assertThat(exception.getMessage()).isEqualTo("已变化");
            });
    assertThatThrownBy(() -> OptimisticUpdates.requireOne(2, "USER_VERSION_CONFLICT", "已变化"))
        .isInstanceOf(PlatformException.class);
  }
}
