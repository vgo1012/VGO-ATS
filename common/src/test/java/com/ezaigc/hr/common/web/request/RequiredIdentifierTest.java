package com.ezaigc.hr.common.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import org.junit.jupiter.api.Test;

class RequiredIdentifierTest {

  @Test
  void trimsAndAcceptsTwentySixCharacterIdentifier() {
    assertThat(RequiredIdentifier.require(" 01H00000000000000000000001 ", "candidateId"))
        .isEqualTo("01H00000000000000000000001");
  }

  @Test
  void preservesIdentifierErrorContract() {
    assertThatThrownBy(() -> RequiredIdentifier.require("short", "candidateId"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("IDENTIFIER_INVALID");
              assertThat(exception.getMessage()).isEqualTo("candidateId 必须是 26 位标识");
            });
  }
}
