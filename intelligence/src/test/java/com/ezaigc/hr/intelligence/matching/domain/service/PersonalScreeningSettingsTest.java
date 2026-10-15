package com.ezaigc.hr.intelligence.matching.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PersonalScreeningSettingsTest {
  private final ScoringConfigurationValidator validator = new ScoringConfigurationValidator();
  @Test
  void delegatesNullCriterionValidationToTheSharedValidator() {
    var criteria = Arrays.asList(
        criterion(ControlledCriterionCode.CERTIFICATE, ControlledCriterionLevel.MUST), null);

    assertThatThrownBy(() -> PersonalScreeningSettings.validateAndSerialize(criteria, null, validator))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> {
              assertThat(error.code()).isEqualTo("MATCH_PLAN_INVALID");
              assertThat(error.status().value()).isEqualTo(422);
              assertThat(error.fieldErrors()).singleElement()
                  .satisfies(field -> assertThat(field.getField()).isEqualTo("criteria[1]"));
            });
  }
  @Test void rejectsProtectedAttributesAndInvalidSort() {
    assertThatThrownBy(() -> PersonalScreeningSettings.validateAndSerialize(List.of(criterion(ControlledCriterionCode.AGE, ControlledCriterionLevel.MUST)), null, validator)).hasMessageContaining("岗位相关");
    assertThatThrownBy(() -> PersonalScreeningSettings.validateAndSerialize(List.of(), "arbitrary", validator)).hasMessageContaining("排序");
  }
  @Test void keepsCompanyMustAndDoesNotCountSameFactBonusTwice() {
    var official = new ControlledCriterion(); official.setCriterionCode("EDUCATION"); official.setLevel("MUST");
    var weights = weights(List.of(criterion(ControlledCriterionCode.EDUCATION, ControlledCriterionLevel.PREFER)));
    assertThat(PersonalScreeningSettings.merge(List.of(official), weights)).containsExactly(official);
    weights = weights(List.of(criterion(ControlledCriterionCode.EDUCATION, ControlledCriterionLevel.MUST)));
    assertThat(PersonalScreeningSettings.merge(List.of(official), weights)).hasSize(2).first().isSameAs(official);
  }
  @Test void keepsCertificateMustIndependentAndDoesNotCountCertificateBonusTwice() {
    var official = new ControlledCriterion(); official.setCriterionCode("CERTIFICATE"); official.setLevel("MUST");
    var prefer = weights(List.of(criterion(ControlledCriterionCode.CERTIFICATE, ControlledCriterionLevel.PREFER)));
    assertThat(PersonalScreeningSettings.merge(List.of(official), prefer)).containsExactly(official);

    var must = weights(List.of(criterion(ControlledCriterionCode.CERTIFICATE, ControlledCriterionLevel.MUST)));
    assertThat(PersonalScreeningSettings.merge(List.of(official), must))
        .hasSize(2)
        .first()
        .isSameAs(official);
    assertThat(PersonalScreeningSettings.merge(List.of(official), must).get(1).getBonusPoints()).isZero();
  }
  @Test void freezesStablePersonalDefinitionsAndDefaultSortWithoutMutatingLegacyWeights() {
    var weights = weights(List.of(criterion(ControlledCriterionCode.CERTIFICATE, ControlledCriterionLevel.PREFER)));
    var first = PersonalScreeningSettings.merge(List.of(), weights);
    assertThat(first.getFirst().getId()).hasSize(26);
    assertThat(first.getFirst().isSelectableByHr()).isFalse();
    assertThat(PersonalScreeningSettings.merge(List.of(), weights)).usingRecursiveComparison().isEqualTo(first);
    assertThat(PersonalScreeningSettings.defaultSort(weights)).isEqualTo("BONUS_SCORE");
    assertThat(PersonalScreeningSettings.merge(List.of(), new WeightProfileVersion())).isEmpty();
  }
  private WeightProfileVersion weights(List<ControlledCriterionDraft> criteria) {
    var result = new WeightProfileVersion(); result.setId("00000000000000000000000001");
    result.setScreeningSettingsJson(PersonalScreeningSettings.validateAndSerialize(criteria, "BONUS_SCORE", validator)); return result;
  }
  private ControlledCriterionDraft criterion(ControlledCriterionCode code, ControlledCriterionLevel level) {
    String expected = code == ControlledCriterionCode.CERTIFICATE ? "PMP" : "本科";
    return new ControlledCriterionDraft(code, level, "IN", "{\"values\":[\"" + expected + "\"]}", level == ControlledCriterionLevel.PREFER ? 10 : 0, "岗位要求", "EXPLICIT_FACT_ONLY", false);
  }
}
