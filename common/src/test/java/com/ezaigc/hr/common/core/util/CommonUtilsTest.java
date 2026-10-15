package com.ezaigc.hr.common.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommonUtilsTest {

  @Test
  void normalizesNullableTextWithoutLocaleDrift() {
    assertThat(CommonUtils.isBlank(" \t")).isTrue();
    assertThat(CommonUtils.stripToNull("  text  ")).isEqualTo("text");
    assertThat(CommonUtils.stripToNull("  ")).isNull();
    assertThat(CommonUtils.upperOrEmpty(null)).isEmpty();
    assertThat(CommonUtils.upperOrEmpty("  active ")).isEqualTo("ACTIVE");
    assertThat(CommonUtils.upperToNull("  ")).isNull();
    assertThat(CommonUtils.stripOrElse("  owner  ", "fallback")).isEqualTo("owner");
    assertThat(CommonUtils.stripOrElse(" ", "fallback")).isEqualTo("fallback");
    assertThat(CommonUtils.upperOrElse(" active ", "DEFAULT")).isEqualTo("ACTIVE");
    assertThat(CommonUtils.upperOrElse(null, "DEFAULT")).isEqualTo("DEFAULT");
  }
}
