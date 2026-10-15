package com.ezaigc.hr.common.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sha256UtilsTest {

  @Test
  void hashesUtf8TextAndStreamsIdentically() throws Exception {
    String expected = Sha256Utils.hashUtf8("招聘");

    assertThat(
            Sha256Utils.hash(
                new ByteArrayInputStream("招聘".getBytes(StandardCharsets.UTF_8))))
        .isEqualTo(expected);
    assertThat(Sha256Utils.hashBytes("招聘".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo(expected);
  }

  @Test
  void fingerprintFramingKeepsNullPoliciesExplicit() {
    assertThat(Sha256Utils.fingerprintStrings("", 0, "a", null))
        .isNotEqualTo(Sha256Utils.fingerprintStrings("<null>", 0, "a", null));
    assertThat(Sha256Utils.fingerprintObjects(31, "a", null))
        .isNotEqualTo(Sha256Utils.fingerprintObjects(0, "a", null));
  }

  @Test
  void objectFingerprintDistinguishesNullFromLiteralText() {
    assertThat(Sha256Utils.fingerprintObjects(31, "application", null, "ISSUED"))
        .isNotEqualTo(Sha256Utils.fingerprintObjects(31, "application", "null", "ISSUED"));
  }

  @Test
  void objectFingerprintPreservesFieldBoundaries() {
    assertThat(Sha256Utils.fingerprintObjects(31, "first\u001fsecond", "third"))
        .isNotEqualTo(Sha256Utils.fingerprintObjects(31, "first", "second\u001fthird"));
    assertThat(Sha256Utils.fingerprintObjects(31, "first", "second"))
        .isEqualTo(Sha256Utils.fingerprintObjects(31, "first", "second"));
  }

  @Test
  void rejectsMultiByteDelimiterValues() {
    assertThatThrownBy(() -> Sha256Utils.fingerprintStrings("", 256, "value"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
