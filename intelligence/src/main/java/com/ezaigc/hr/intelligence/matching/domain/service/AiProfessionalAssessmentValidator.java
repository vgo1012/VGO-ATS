package com.ezaigc.hr.intelligence.matching.domain.service;

import com.ezaigc.hr.intelligence.matching.domain.dto.AiProfessionalAssessmentDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.AiProfessionalDimensionDTO;
import com.ezaigc.hr.intelligence.matching.enums.MatchDimensionStatus;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fail-closed validation for the compact AI professional assessment. */
@Component
public class AiProfessionalAssessmentValidator {

  private static final Set<String> ROOT_FIELDS =
      Set.of("professionalScore", "summary", "dimensions");
  private static final Set<String> DIMENSION_FIELDS = Set.of(
      "code", "status", "score", "explanation", "highlights");
  private static final List<String> DIMENSION_CODES = List.of(
      "CORE_SKILLS", "ROLE_EXPERIENCE", "RELATED_YEARS", "RESPONSIBILITY_MATCH",
      "INDUSTRY_AND_PROJECTS");
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int MAX_SUMMARY_LENGTH = 2_000;
  private static final int MAX_EXPLANATION_LENGTH = 2_000;
  private static final int MAX_HIGHLIGHTS_PER_DIMENSION = 4;
  private static final int MAX_TOTAL_HIGHLIGHTS = 4;

  @Resource private ObjectMapper json;

  public AiProfessionalAssessmentDTO validate(
      String rawJson, JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
    return validate(rawJson, jd, resume, false);
  }

  /** 联合解析预计算评分只裁掉多余的展示亮点；其他结构、维度和评分规则仍须先通过严格校验。 */
  public AiProfessionalAssessmentDTO validatePrecomputed(
      String rawJson, JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
    return validate(rawJson, jd, resume, true);
  }

  private AiProfessionalAssessmentDTO validate(
      String rawJson, JdFactSnapshotData jd, ResumeFactSnapshotData resume,
      boolean capPrecomputedHighlights) {
    if (rawJson == null || rawJson.length() > 80_000 || jd == null || resume == null) {
      throw invalid("AI_ASSESSMENT_SCHEMA_INVALID");
    }
    JsonNode root;
    try {
      root = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(rawJson);
    } catch (RuntimeException failure) {
      throw invalid("AI_ASSESSMENT_JSON_INVALID");
    }
    requireExactFields(root, ROOT_FIELDS);
    JsonNode dimensions = root.get("dimensions");
    if (dimensions == null || !dimensions.isArray() || dimensions.size() != DIMENSION_CODES.size()) {
      throw invalid("AI_ASSESSMENT_DIMENSIONS_INVALID");
    }
    requireTextNode(root.get("summary"));
    requireDecimalNode(root.get("professionalScore"));
    for (JsonNode dimension : dimensions) {
      requireExactFields(dimension, DIMENSION_FIELDS);
      requireTextNode(dimension.get("code"));
      requireTextNode(dimension.get("status"));
      requireDecimalNode(dimension.get("score"));
      requireTextNode(dimension.get("explanation"));
      requireStringArrayNode(dimension.get("highlights"));
    }

    AiProfessionalAssessmentDTO assessment;
    try {
      assessment = json.treeToValue(root, AiProfessionalAssessmentDTO.class);
    } catch (RuntimeException failure) {
      throw invalid("AI_ASSESSMENT_SCHEMA_INVALID");
    }
    validateText(assessment.getSummary(), MAX_SUMMARY_LENGTH);
    validateScore(assessment.getProfessionalScore());

    var byCode = new LinkedHashMap<String, AiProfessionalDimensionDTO>();
    int highlightCount = 0;
    for (AiProfessionalDimensionDTO dimension : assessment.getDimensions()) {
      validateDimension(dimension);
      if (byCode.put(dimension.getCode(), dimension) != null) {
        throw invalid("AI_ASSESSMENT_DIMENSIONS_INVALID");
      }
      highlightCount += dimension.getHighlights().size();
    }
    if (!byCode.keySet().equals(new LinkedHashSet<>(DIMENSION_CODES))
        || (!capPrecomputedHighlights && highlightCount > MAX_TOTAL_HIGHLIGHTS)) {
      throw invalid("AI_ASSESSMENT_DIMENSIONS_INVALID");
    }
    boolean allUnknown = byCode.values().stream()
        .allMatch(dimension -> MatchDimensionStatus.UNKNOWN.matches(dimension.getStatus()));
    if (allUnknown && assessment.getProfessionalScore().compareTo(ZERO) != 0) {
      throw invalid("AI_ASSESSMENT_UNKNOWN_TOTAL_INVALID");
    }
    assessment.setDimensions(DIMENSION_CODES.stream().map(byCode::get).toList());
    if (capPrecomputedHighlights && highlightCount > MAX_TOTAL_HIGHLIGHTS) {
      int remaining = MAX_TOTAL_HIGHLIGHTS;
      for (AiProfessionalDimensionDTO dimension : assessment.getDimensions()) {
        List<String> highlights = dimension.getHighlights();
        int retained = Math.min(remaining, highlights.size());
        dimension.setHighlights(List.copyOf(highlights.subList(0, retained)));
        remaining -= retained;
      }
    }
    return assessment;
  }

  private static void validateDimension(AiProfessionalDimensionDTO dimension) {
    if (dimension == null || !DIMENSION_CODES.contains(dimension.getCode())) {
      throw invalid("AI_ASSESSMENT_DIMENSIONS_INVALID");
    }
    MatchDimensionStatus status;
    try {
      status = MatchDimensionStatus.fromCode(dimension.getStatus());
    } catch (RuntimeException failure) {
      throw invalid("AI_ASSESSMENT_DIMENSIONS_INVALID");
    }
    validateScore(dimension.getScore());
    validateText(dimension.getExplanation(), MAX_EXPLANATION_LENGTH);
    validateStringList(dimension.getHighlights(), MAX_HIGHLIGHTS_PER_DIMENSION, true);
    if (status == MatchDimensionStatus.UNKNOWN && dimension.getScore().compareTo(ZERO) != 0) {
      throw invalid("AI_ASSESSMENT_UNKNOWN_SCORE_INVALID");
    }
  }

  private static void requireExactFields(JsonNode node, Set<String> fields) {
    if (node == null || !node.isObject()
        || !new HashSet<>(node.propertyNames()).equals(fields)) {
      throw invalid("AI_ASSESSMENT_SCHEMA_INVALID");
    }
  }

  private static void requireTextNode(JsonNode node) {
    if (node == null || !node.isString()) throw invalid("AI_ASSESSMENT_SCHEMA_INVALID");
  }

  private static void requireDecimalNode(JsonNode node) {
    if (node == null || !node.isNumber() || node.decimalValue().scale() > 2) {
      throw invalid("AI_ASSESSMENT_SCHEMA_INVALID");
    }
  }

  private static void requireStringArrayNode(JsonNode node) {
    if (node == null || !node.isArray()) throw invalid("AI_ASSESSMENT_SCHEMA_INVALID");
    for (JsonNode value : node) requireTextNode(value);
  }

  private static void validateScore(BigDecimal score) {
    if (score == null || score.compareTo(ZERO) < 0 || score.compareTo(HUNDRED) > 0
        || score.scale() > 2) {
      throw invalid("AI_ASSESSMENT_SCORE_INVALID");
    }
  }

  private static void validateText(String value, int maximumLength) {
    if (value == null || value.isBlank() || value.length() > maximumLength) {
      throw invalid("AI_ASSESSMENT_TEXT_INVALID");
    }
  }

  private static void validateStringList(List<String> values, int maximumSize, boolean text) {
    if (values == null || values.size() > maximumSize || new HashSet<>(values).size() != values.size()) {
      throw invalid("AI_ASSESSMENT_LIST_INVALID");
    }
    for (String value : values) {
      if (value == null || value.isBlank() || value.length() > (text ? 300 : 500)) {
        throw invalid("AI_ASSESSMENT_LIST_INVALID");
      }
    }
  }

  private static IllegalArgumentException invalid(String code) {
    return new IllegalArgumentException(code);
  }
}
