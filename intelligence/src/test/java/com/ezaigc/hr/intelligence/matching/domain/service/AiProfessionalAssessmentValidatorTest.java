package com.ezaigc.hr.intelligence.matching.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class AiProfessionalAssessmentValidatorTest {

  @Test
  void acceptsFiveCompactAiJudgmentsAndPreservesTheirScores() {
    var result = validator().validate(validJson(), new JdFactSnapshotData(),
        new ResumeFactSnapshotData());

    assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.70");
    assertThat(result.getDimensions()).extracting("code").containsExactly(
        "CORE_SKILLS", "ROLE_EXPERIENCE", "RELATED_YEARS", "RESPONSIBILITY_MATCH",
        "INDUSTRY_AND_PROJECTS");
    assertThat(result.getDimensions()).extracting("score")
        .containsExactly(new BigDecimal("80"), new BigDecimal("70"),
            new BigDecimal("60"), new BigDecimal("90"), new BigDecimal("50"));
  }

  @Test
  void rejectsExtraneousOrLegacyEvidenceFields() {
    String extra = validJson().replace("\"summary\":\"匹配摘要\"",
        "\"summary\":\"匹配摘要\",\"recommendation\":\"录用\"");
    assertSchemaInvalid(extra);

    String legacy = validJson().replace("\"highlights\":[]",
        "\"confidence\":0.8,\"highlights\":[]");
    assertSchemaInvalid(legacy);
  }

  @Test
  void permitsIndependentTotalButRequiresZeroWhenEveryDimensionIsUnknown() {
    String independentTotal = validJson().replace("\"professionalScore\":72.70",
        "\"professionalScore\":88");
    assertThat(validator().validate(independentTotal, new JdFactSnapshotData(),
        new ResumeFactSnapshotData()).getProfessionalScore()).isEqualByComparingTo("88");

    String invalidUnknown = allUnknownJson().replace("\"professionalScore\":0",
        "\"professionalScore\":1");
    assertThatThrownBy(() -> validator().validate(invalidUnknown, new JdFactSnapshotData(),
        new ResumeFactSnapshotData()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI_ASSESSMENT_UNKNOWN_TOTAL_INVALID");
  }

  @Test
  void rejectsInvalidScoresStatusesAndScalarTypes() {
    assertThatThrownBy(() -> validator().validate(
        validJson().replace("\"score\":80", "\"score\":-1"),
        new JdFactSnapshotData(), new ResumeFactSnapshotData()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI_ASSESSMENT_SCORE_INVALID");
    assertThatThrownBy(() -> validator().validate(
        validJson().replaceFirst("\"status\":\"KNOWN\"", "\"status\":\"REJECTED\""),
        new JdFactSnapshotData(), new ResumeFactSnapshotData()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI_ASSESSMENT_DIMENSIONS_INVALID");
    assertSchemaInvalid(validJson().replace("\"professionalScore\":72.70",
        "\"professionalScore\":\"72.70\""));
    assertSchemaInvalid(validJson().replace("\"professionalScore\":72.70",
        "\"professionalScore\":72.701"));
  }

  @Test
  void ordinaryScoringStillRejectsTwentyHighlights() {
    assertThatThrownBy(() -> validator().validate(twentyHighlightsJson(),
        new JdFactSnapshotData(), new ResumeFactSnapshotData()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI_ASSESSMENT_DIMENSIONS_INVALID");
  }

  @Test
  void precomputedHighlightCapStillRejectsInvalidDimensionsAndScores() {
    String duplicateCode = twentyHighlightsJson().replace(
        "\"code\":\"ROLE_EXPERIENCE\"", "\"code\":\"CORE_SKILLS\"");
    assertThatThrownBy(() -> validator().validatePrecomputed(duplicateCode,
        new JdFactSnapshotData(), new ResumeFactSnapshotData()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI_ASSESSMENT_DIMENSIONS_INVALID");

    String invalidScore = twentyHighlightsJson().replace("\"score\":80", "\"score\":-1");
    assertThatThrownBy(() -> validator().validatePrecomputed(invalidScore,
        new JdFactSnapshotData(), new ResumeFactSnapshotData()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI_ASSESSMENT_SCORE_INVALID");
  }

  @ParameterizedTest
  @ValueSource(strings = {"100.000000000000001", "72.700000000000001", "1e-400", "-1e-400"})
  void rejectsNumbersWhoseInvalidPrecisionWouldBeLostByDoubleParsing(String number) {
    for (String raw : new String[] {
        validJson().replace("\"professionalScore\":72.70", "\"professionalScore\":" + number),
        validJson().replace("\"score\":80", "\"score\":" + number)}) {
      assertThatThrownBy(() -> validator().validate(raw,
          new JdFactSnapshotData(), new ResumeFactSnapshotData()))
          .isInstanceOf(IllegalArgumentException.class).hasMessage("AI_ASSESSMENT_SCHEMA_INVALID");
      assertThatThrownBy(() -> validator().validatePrecomputed(raw,
          new JdFactSnapshotData(), new ResumeFactSnapshotData()))
          .isInstanceOf(IllegalArgumentException.class).hasMessage("AI_ASSESSMENT_SCHEMA_INVALID");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "100", "72.70", "1e2"})
  void acceptsExactScoresWithoutChangingTheirValue(String number) {
    String raw = validJson().replace("\"professionalScore\":72.70", "\"professionalScore\":" + number)
        .replace("\"score\":80", "\"score\":" + number);
    for (var result : List.of(
        validator().validate(raw, new JdFactSnapshotData(), new ResumeFactSnapshotData()),
        validator().validatePrecomputed(raw, new JdFactSnapshotData(), new ResumeFactSnapshotData()))) {
      assertThat(result.getProfessionalScore()).isEqualByComparingTo(new BigDecimal(number));
      assertThat(result.getDimensions().getFirst().getScore()).isEqualByComparingTo(new BigDecimal(number));
    }
  }

  private static void assertSchemaInvalid(String value) {
    assertThatThrownBy(() -> validator().validate(value, new JdFactSnapshotData(),
        new ResumeFactSnapshotData()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AI_ASSESSMENT_SCHEMA_INVALID");
  }

  private static AiProfessionalAssessmentValidator validator() {
    var validator = new AiProfessionalAssessmentValidator();
    ReflectionTestUtils.setField(validator, "json", new ObjectMapper());
    return validator;
  }

  private static String validJson() {
    return """
        {"professionalScore":72.70,"summary":"匹配摘要","dimensions":[%s,%s,%s,%s,%s]}
        """.formatted(
        dimension("CORE_SKILLS", 80), dimension("ROLE_EXPERIENCE", 70),
        dimension("RELATED_YEARS", 60), dimension("RESPONSIBILITY_MATCH", 90),
        dimension("INDUSTRY_AND_PROJECTS", 50));
  }

  private static String twentyHighlightsJson() {
    return validJson().replace("\"highlights\":[]",
        "\"highlights\":[\"亮点一\",\"亮点二\",\"亮点三\",\"亮点四\"]");
  }

  private static String dimension(String code, int score) {
    return """
        {"code":"%s","status":"KNOWN","score":%d,
         "explanation":"有事实支持的专业判断","highlights":[]}
        """.formatted(code, score);
  }

  private static String allUnknownJson() {
    return """
        {"professionalScore":0,"summary":"信息不足","dimensions":[%s,%s,%s,%s,%s]}
        """.formatted(unknown("CORE_SKILLS"), unknown("ROLE_EXPERIENCE"),
        unknown("RELATED_YEARS"), unknown("RESPONSIBILITY_MATCH"),
        unknown("INDUSTRY_AND_PROJECTS"));
  }

  private static String unknown(String code) {
    return """
        {"code":"%s","status":"UNKNOWN","score":0,
         "explanation":"信息不足","highlights":[]}
        """.formatted(code);
  }
}
