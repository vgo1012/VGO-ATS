package com.ezaigc.hr.intelligence.matching.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.domain.dto.ProfessionalWeights;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ScoringConfigurationValidatorTest {

  private final ScoringConfigurationValidator validator = new ScoringConfigurationValidator();

  @Test
  @ResourceLock(Resources.LOCALE)
  void validatesLowercaseInUnderTurkishLocaleWithoutMutatingTheInput() {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      var draft = criterion(ControlledCriterionCode.LANGUAGE, ControlledCriterionLevel.MUST, 0, false);
      draft.setOperator(" in ");
      assertThat(validator.validate(true, true, true, true, true, List.of(draft)).getCriteria())
          .containsExactly(draft);
      assertThat(draft.getOperator()).isEqualTo(" in ");
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "UNKNOWN", "İN"})
  void invalidOperatorsKeepTheirBusinessFieldError(String operator) {
    var draft = criterion(ControlledCriterionCode.LANGUAGE, ControlledCriterionLevel.MUST, 0, false);
    draft.setOperator(operator);
    assertThatThrownBy(() -> validator.validate(true, true, true, true, true, List.of(draft)))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("MATCH_PLAN_INVALID");
          assertThat(error.status().value()).isEqualTo(422);
          assertThat(error.fieldErrors()).singleElement()
              .satisfies(field -> assertThat(field.getField()).isEqualTo("criteria[0].operator"));
        });
  }

  @Test
  void rejectsNullCriterionWithItsFieldError() {
    var criteria = Arrays.asList(
        criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.MUST, 0, false), null);

    assertThatThrownBy(() -> validator.validate(true, true, true, true, true, criteria))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> {
              assertThat(error.code()).isEqualTo("MATCH_PLAN_INVALID");
              assertThat(error.status().value()).isEqualTo(422);
              assertThat(error.fieldErrors()).singleElement()
                  .satisfies(field -> assertThat(field.getField()).isEqualTo("criteria[1]"));
            });
  }

  @Test
  void returnsAnImmutableSnapshotAndAcceptsMissingCriteria() {
    var criteria = new ArrayList<>(List.of(
        criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.MUST, 0, false)));
    var validated = validator.validate(true, true, true, true, true, criteria);

    criteria.clear();
    assertThat(validated.getCriteria()).hasSize(1);
    assertThatThrownBy(() -> validated.getCriteria().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(validator.validate(true, true, true, true, true, null).getCriteria()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4})
  void rejectsDisablingAnyProfessionalDimension(int dimension) {
    boolean[] enabled = {true, true, true, true, true};
    enabled[dimension] = false;
    assertThatThrownBy(
            () -> validator.validate(
                enabled[0], enabled[1], enabled[2], enabled[3], enabled[4], List.of()))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MATCH_PLAN_INVALID"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4})
  void acceptsZeroWeightWhenRemainingWeightsTotalOneHundred(int dimension) {
    int[] weights = {25, 25, 25, 25, 25};
    weights[dimension] = 0;
    var saved = new ProfessionalWeights(
        weights[0], weights[1], weights[2], weights[3], weights[4]);
    assertThat(saved.validatedCopy()).isEqualTo(saved);
  }

  @Test
  void rejectsNegativeWeightEvenWhenRemainingWeightsTotalOneHundred() {
    assertThatThrownBy(() -> new ProfessionalWeights(-1, 26, 25, 25, 25))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("WEIGHT_PROFILE_INVALID"));
  }

  @Test
  void fixedDefaultWeightsAreExactlyTheConfirmedFiveDimensions() {
    ProfessionalWeights first = ProfessionalWeights.systemDefault();
    ProfessionalWeights second = ProfessionalWeights.systemDefault();

    assertThat(first).isEqualTo(new ProfessionalWeights(20, 20, 20, 20, 20));
    assertThat(first).isNotSameAs(second);
  }

  @Test
  void doesNotExposeAMutableProcessWideDefaultInstance() {
    assertThat(ProfessionalWeights.class.getFields())
        .noneMatch(
            field ->
                Modifier.isStatic(field.getModifiers())
                    && field.getType().equals(ProfessionalWeights.class));
  }

  @Test
  void rejectsPersonalWeightsThatDoNotTotalOneHundred() {
    assertThatThrownBy(() -> new ProfessionalWeights(21, 20, 20, 20, 20))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("WEIGHT_PROFILE_INVALID"));
  }

  @Test
  void acceptsAllFiveControlledFactsAndCapsBonusAtThirty() {
    var validated =
        validator.validate(
            true,
            true,
            true,
            true,
            true,
            List.of(
                criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.MUST, 0, false),
                criterion(
                    ControlledCriterionCode.GENDER,
                    ControlledCriterionLevel.DISPLAY_ONLY,
                    0,
                    false),
                criterion(
                    ControlledCriterionCode.EDUCATION, ControlledCriterionLevel.PREFER, 5, true),
                criterion(
                    ControlledCriterionCode.CERTIFICATE, ControlledCriterionLevel.PREFER, 5, true),
                criterion(
                    ControlledCriterionCode.LANGUAGE, ControlledCriterionLevel.PREFER, 20, true)));

    assertThat(validated.getPlanMaxBonusPoints()).isEqualTo(30);
  }

  @Test
  void rejectsDuplicateFactInvalidBonusAndClosableMust() {
    assertInvalid(
        List.of(
            criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER, 5, true),
            criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER, 5, true)));
    assertInvalid(
        List.of(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER, 15, true)));
    assertInvalid(
        List.of(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.MUST, 0, true)));
  }

  @Test
  void rejectsPreferBonusAboveThirty() {
    assertInvalid(
        List.of(
            criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.PREFER, 20, true),
            criterion(ControlledCriterionCode.GENDER, ControlledCriterionLevel.PREFER, 20, true)));
  }

  @Test
  void rejectsMalformedOrNonObjectExpectedValues() {
    assertInvalid(
        List.of(
            new ControlledCriterionDraft(
                ControlledCriterionCode.AGE,
                ControlledCriterionLevel.MUST,
                "LTE",
                "not-json",
                0,
                "岗位要求",
                "EXPLICIT_FACT_ONLY",
                false)));
    assertInvalid(
        List.of(
            new ControlledCriterionDraft(
                ControlledCriterionCode.AGE,
                ControlledCriterionLevel.MUST,
                "LTE",
                "[45]",
                0,
                "岗位要求",
                "EXPLICIT_FACT_ONLY",
                false)));
  }

  private void assertInvalid(List<ControlledCriterionDraft> criteria) {
    assertThatThrownBy(() -> validator.validate(true, true, true, true, true, criteria))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("MATCH_PLAN_INVALID"));
  }

  private static ControlledCriterionDraft criterion(
      ControlledCriterionCode code, ControlledCriterionLevel level, int bonus, boolean selectable) {
    return new ControlledCriterionDraft(
        code, level, "EQ", "{\"value\":true}", bonus, "岗位明确要求", "EXPLICIT_FACT_ONLY", selectable);
  }
}
