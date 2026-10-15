package com.ezaigc.hr.common.web.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import org.junit.jupiter.api.Test;

class ResourceVersionTest {

  @Test
  void parsesAndFormatsResourceVersions() {
    assertThat(ResourceVersion.parse("0")).isZero();
    assertThat(ResourceVersion.parse(Long.toString(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
    assertThat(ResourceVersion.format(42)).isEqualTo("42");
  }

  @Test
  void validatesTheCompleteWireRangeWithoutThrowing() {
    assertThat(ResourceVersion.isValid("0")).isTrue();
    assertThat(ResourceVersion.isValid(Long.toString(Long.MAX_VALUE))).isTrue();

    for (String value :
        new String[] {
          null, "", " ", "-1", "+1", "1.0", "abc", "00000000000000000000", "9223372036854775808"
        }) {
      assertThat(ResourceVersion.isValid(value)).isFalse();
    }
  }

  @Test
  void rejectsInvalidWireVersionsWithStableError() {
    for (String value :
        new String[] {
          null, "", " ", "-1", "+1", "1.0", "abc", "00000000000000000000", "9223372036854775808"
        }) {
      assertThatThrownBy(() -> ResourceVersion.parse(value))
          .isInstanceOf(PlatformException.class)
          .satisfies(
              exception ->
                  assertThat(((PlatformException) exception).code()).isEqualTo("VERSION_INVALID"));
    }
  }

  @Test
  void rejectsNegativeInternalVersions() {
    assertThatThrownBy(() -> ResourceVersion.format(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("resource version must not be negative");
  }
}
