package com.ezaigc.hr.recruiting.recruitment.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

class OnboardingReturnReasonTest {

  @Test
  @ResourceLock(Resources.LOCALE)
  void parsesProtocolCodesIndependentlyOfTheDefaultLocale() {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      for (var reason : OnboardingReturnReason.values()) {
        String code = reason.getCode().toLowerCase(Locale.ROOT);
        assertThat(OnboardingReturnReason.fromNullableCode(code)).isSameAs(reason);
        String input = "\u2003" + code + "\u2003";
        assertThat(OnboardingReturnReason.fromNullableCode(input)).isSameAs(reason);
      }
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @Test
  void preservesMissingReasonFallbackAndRejectsUnknownCodes() {
    assertThat(OnboardingReturnReason.fromNullableCode(null)).isSameAs(OnboardingReturnReason.LIBRARY_RETURN);
    assertThat(OnboardingReturnReason.fromNullableCode("\u2003 \t"))
        .isSameAs(OnboardingReturnReason.LIBRARY_RETURN);
    assertThatThrownBy(() -> OnboardingReturnReason.fromNullableCode("invalid"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
