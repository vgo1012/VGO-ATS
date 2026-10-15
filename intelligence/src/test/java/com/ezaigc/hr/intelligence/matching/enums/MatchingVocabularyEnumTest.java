package com.ezaigc.hr.intelligence.matching.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MatchingVocabularyEnumTest {

  @ParameterizedTest
  @ValueSource(strings = {"EQ", "IN", "BETWEEN", "GTE", "LTE", "CONTAINS"})
  @ResourceLock(Resources.LOCALE)
  void operatorsAcceptCodeAliasesIndependentlyOfDefaultLocale(String code) {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertThat(CriterionOperator.fromCode(" " + code.toLowerCase(Locale.ROOT) + " "))
          .isEqualTo(CriterionOperator.valueOf(code));
      assertThat(CriterionOperator.fromCode(code)).isEqualTo(CriterionOperator.valueOf(code));
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "UNKNOWN", "İN"})
  void unknownOperatorAliasesRemainInvalid(String code) {
    assertThatThrownBy(() -> CriterionOperator.fromCode(code)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sortModesAndOperatorsParseToTypedValues() {
    assertThat(MatchResultSortMode.fromCode("PROFESSIONAL_SCORE"))
        .isEqualTo(MatchResultSortMode.PROFESSIONAL_SCORE);
    assertThat(CriterionOperator.fromCode("BETWEEN").isRangeOperator()).isTrue();
    assertThat(CriterionOperator.fromCode("EQ").isRangeOperator()).isFalse();
    assertThatThrownBy(() -> MatchResultSortMode.fromCode("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
