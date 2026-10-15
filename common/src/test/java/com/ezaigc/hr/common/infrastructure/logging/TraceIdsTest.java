package com.ezaigc.hr.common.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TraceIdsTest {
  @Test
  void preservesSafeBoundaryValues() {
    for (String value : new String[] {"a".repeat(16), "Z_9-".repeat(16)}) {
      assertThat(TraceIds.isSafe(value)).isTrue();
      assertThat(TraceIds.acceptOrCreate(value)).isEqualTo(value);
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "123456789012345", "unsafe-trace-123456\nforged", "含有个人内容的输入不能作为日志关联ID"})
  void replacesUnsafeInputWithAnIndependentSafeId(String value) {
    assertThat(TraceIds.isSafe(value)).isFalse();
    String first = TraceIds.acceptOrCreate(value);
    assertThat(first).matches("[a-f0-9]{32}").isNotEqualTo(TraceIds.acceptOrCreate(value));
  }

  @Test
  void rejectsOverlongTrace() {
    assertThat(TraceIds.isSafe("a".repeat(65))).isFalse();
    assertThat(TraceIds.acceptOrCreate("a".repeat(65))).hasSize(32);
  }
}
