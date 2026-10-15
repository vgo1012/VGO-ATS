package com.ezaigc.hr.recruiting.demand.service.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.recruiting.demand.service.validator.JdParseContractValidator.ContractViolation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class JdParseContractValidatorTest {

  private final JdParseContractValidator validator = validator();

  @Test
  void acceptsStructuredFactsWithoutEvidence() {
    var facts = validator.validateModel(valid(), "原始 JD 不再用于生成路径级证据");

    assertThat(facts.at("/professional/coreSkills/0/name").asString()).isEqualTo("Java");
    assertThat(facts.has("evidence")).isFalse();
  }

  @Test
  void rejectsLegacyEvidenceAndReliabilityFields() {
    String withEvidence = valid().replace(
        "\"fulfillmentConditions\":",
        "\"evidence\":[],\"fulfillmentConditions\":");
    String withReliability = valid().replace(
        "\"fulfillmentConditions\":",
        "\"reliability\":\"HIGH\",\"fulfillmentConditions\":");

    for (String value : new String[] {withEvidence, withReliability}) {
      assertThatThrownBy(() -> validator.validateModel(value, "Java"))
          .isInstanceOf(ContractViolation.class)
          .extracting(error -> ((ContractViolation) error).validationStatus())
          .isEqualTo(AiOutputValidationStatus.SCHEMA_INVALID);
    }
  }

  @Test
  void rejectsScoresAndInvalidRanges() {
    String withScore = valid().replace(
        "\"summary\":\"负责后端开发\"",
        "\"summary\":\"负责后端开发\",\"score\":90");
    String reversedYears = valid().replace(
        "\"minimumYears\":3,\"preferredYears\":5",
        "\"minimumYears\":6,\"preferredYears\":5");

    assertThatThrownBy(() -> validator.validateModel(withScore, "Java"))
        .isInstanceOf(ContractViolation.class)
        .extracting(error -> ((ContractViolation) error).errorCode())
        .isEqualTo("MODEL_SCORE_FIELD_FORBIDDEN");
    assertThatThrownBy(() -> validator.validateModel(reversedYears, "Java"))
        .isInstanceOf(ContractViolation.class)
        .extracting(error -> ((ContractViolation) error).errorCode())
        .isEqualTo("MODEL_YEAR_RANGE_INVALID");
  }

  @Test
  void rejectsDuplicateItemIds() {
    String duplicate = valid().replace(
        "\"coreSkills\":[{\"id\":\"skill_1\",\"name\":\"Java\",\"required\":true,\"aliases\":[]}]",
        "\"coreSkills\":[{\"id\":\"skill_1\",\"name\":\"Java\",\"required\":true,\"aliases\":[]},{\"id\":\"skill_1\",\"name\":\"Spring\",\"required\":true,\"aliases\":[]}]");

    assertThatThrownBy(() -> validator.validateModel(duplicate, "Java Spring"))
        .isInstanceOf(ContractViolation.class)
        .extracting(error -> ((ContractViolation) error).errorCode())
        .isEqualTo("MODEL_ITEM_ID_INVALID");
  }

  @ParameterizedTest
  @CsvSource({
      "minimumYears,-1e-400", "minimumYears,80.000000000000001",
      "preferredYears,-1e-400", "preferredYears,80.000000000000001",
      "ageMin,15.9999999999999999", "ageMin,100.000000000000001",
      "ageMax,15.9999999999999999", "ageMax,100.000000000000001",
      "salaryMin,-1e-400", "salaryMin,1000000000.000000001",
      "salaryMax,-1e-400", "salaryMax,1000000000.000000001"})
  void rejectsOriginalNumbersOutsideBoundsEvenWhenDoubleRoundsThemInside(String field, String number) {
    assertError(numericJson(field, number), "MODEL_SCHEMA_INVALID");
  }

  @ParameterizedTest
  @CsvSource({
      "minimumYears,3.000000000000000001,preferredYears,3,MODEL_YEAR_RANGE_INVALID",
      "ageMin,25.000000000000001,ageMax,25,MODEL_AGE_RANGE_INVALID",
      "salaryMin,1000.00000000000001,salaryMax,1000,MODEL_SALARY_RANGE_INVALID"})
  void rejectsReversedOriginalRangesEvenWhenDoubleRoundsEndpointsEqual(
      String minField, String min, String maxField, String max, String errorCode) {
    String raw = numericJson(minField, min)
        .replace("\"" + maxField + "\":null", "\"" + maxField + "\":" + max);
    assertError(raw, errorCode);
  }

  @ParameterizedTest
  @CsvSource({
      "professional,minimumYears,0", "professional,preferredYears,80",
      "controlledConditions,ageMin,16", "controlledConditions,ageMax,100",
      "fulfillmentConditions,salaryMin,0", "fulfillmentConditions,salaryMax,1000000000",
      "professional,minimumYears,3.000000000000000001",
      "controlledConditions,ageMin,25.5", "fulfillmentConditions,salaryMin,1e-400"})
  void preservesValidNumericFactsWithoutAddingPrecisionRestrictions(
      String section, String field, String number) {
    String raw = numericJson(field, number);
    for (var facts : List.of(validator.validate(raw, "JD"), validator.validateModel(raw, "JD"))) {
      assertThat(facts.at("/" + section + "/" + field).decimalValue())
          .isEqualByComparingTo(new BigDecimal(number));
    }
  }

  @ParameterizedTest
  @CsvSource({
      "professional,minimumYears,preferredYears,3.000000000000000001",
      "controlledConditions,ageMin,ageMax,25.5",
      "fulfillmentConditions,salaryMin,salaryMax,72.70"})
  void keepsEqualRangeEndpointsInclusive(String section, String minField, String maxField, String number) {
    String raw = numericJson(minField, number)
        .replace("\"" + maxField + "\":null", "\"" + maxField + "\":" + number);
    for (var facts : List.of(validator.validate(raw, "JD"), validator.validateModel(raw, "JD"))) {
      assertThat(facts.at("/" + section + "/" + minField).decimalValue())
          .isEqualByComparingTo(new BigDecimal(number));
      assertThat(facts.at("/" + section + "/" + maxField).decimalValue())
          .isEqualByComparingTo(new BigDecimal(number));
    }
  }

  private void assertError(String raw, String code) {
    for (boolean modelOutput : new boolean[] {false, true}) {
      assertThatThrownBy(() -> {
        if (modelOutput) validator.validateModel(raw, "JD");
        else validator.validate(raw, "JD");
      }).isInstanceOf(ContractViolation.class)
          .extracting(error -> ((ContractViolation) error).errorCode()).isEqualTo(code);
    }
  }

  private static String numericJson(String field, String number) {
    return valid().replace("\"minimumYears\":3,\"preferredYears\":5",
            "\"minimumYears\":null,\"preferredYears\":null")
        .replace("\"" + field + "\":null", "\"" + field + "\":" + number);
  }

  private static JdParseContractValidator validator() {
    var validator = new JdParseContractValidator();
    ReflectionTestUtils.setField(validator, "json", JsonMapper.builder().build());
    return validator;
  }

  private static String valid() {
    return """
        {"title":"Java工程师","summary":"负责后端开发","professional":{"coreSkills":[{"id":"skill_1","name":"Java","required":true,"aliases":[]}],"roleExperiences":[],"minimumYears":3,"preferredYears":5,"responsibilities":["后端开发"],"industryTags":[],"projectTags":[]},
         "controlledConditions":{"ageMin":null,"ageMax":null,"sex":"ANY","educations":["本科"],"credentials":[],"languages":[]},
         "fulfillmentConditions":{"locations":["上海"],"availability":null,"salaryMin":null,"salaryMax":null,"salaryPeriod":null}}
        """;
  }
}
