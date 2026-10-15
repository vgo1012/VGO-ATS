package com.ezaigc.hr.recruiting.demand.service.validator;

import com.ezaigc.hr.common.infrastructure.ai.ModelOutputGuard;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.recruiting.demand.enums.JdSexRequirement;
import com.ezaigc.hr.recruiting.demand.enums.SalaryPeriod;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class JdParseContractValidator {

  @Resource private ObjectMapper json;

  private static final Set<String> ROOT =
      Set.of("title", "summary", "professional", "controlledConditions", "fulfillmentConditions");
  private static final Set<String> PROFESSIONAL =
      Set.of(
          "coreSkills",
          "roleExperiences",
          "minimumYears",
          "preferredYears",
          "responsibilities",
          "industryTags",
          "projectTags");
  private static final Set<String> CONTROLLED =
      Set.of("ageMin", "ageMax", "sex", "educations", "credentials", "languages");
  private static final Set<String> FULFILLMENT =
      Set.of("locations", "availability", "salaryMin", "salaryMax", "salaryPeriod");

  public JsonNode validate(String rawJson, String sourceText) {
    return validate(rawJson);
  }

  public JsonNode validateModel(String rawJson, String sourceText) {
    return validate(rawJson);
  }

  private JsonNode validate(String rawJson) {
    JsonNode root;
    try {
      root = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(rawJson);
    } catch (RuntimeException ex) {
      throw schema("MODEL_JSON_INVALID");
    }
    if (!(root instanceof ObjectNode)) throw schema("MODEL_SCHEMA_INVALID");
    if (ModelOutputGuard.containsScoreProperty(root)) {
      throw schema("MODEL_SCORE_FIELD_FORBIDDEN");
    }
    exact(root, ROOT);
    JsonNode professional = root.get("professional");
    JsonNode controlled = root.get("controlledConditions");
    JsonNode fulfillment = root.get("fulfillmentConditions");
    exact(professional, PROFESSIONAL);
    exact(controlled, CONTROLLED);
    exact(fulfillment, FULFILLMENT);
    text(root.get("title"), 160, true);
    text(root.get("summary"), 2000, true);
    array(professional, "responsibilities", 200, 500);
    array(professional, "industryTags", 100, 120);
    array(professional, "projectTags", 100, 160);
    boundedNumber(professional.get("minimumYears"), 0, 80, true);
    boundedNumber(professional.get("preferredYears"), 0, 80, true);
    ordered(
        professional.get("minimumYears"),
        professional.get("preferredYears"),
        "MODEL_YEAR_RANGE_INVALID");
    validateSkills(professional.get("coreSkills"));
    validateRoles(professional.get("roleExperiences"));
    boundedNumber(controlled.get("ageMin"), 16, 100, true);
    boundedNumber(controlled.get("ageMax"), 16, 100, true);
    ordered(controlled.get("ageMin"), controlled.get("ageMax"), "MODEL_AGE_RANGE_INVALID");
    enumValue(controlled.get("sex"), JdSexRequirement::supports, true);
    array(controlled, "educations", 20, 120);
    array(controlled, "credentials", 100, 200);
    validateLanguages(controlled.get("languages"));
    array(fulfillment, "locations", 50, 120);
    text(fulfillment.get("availability"), 200, true);
    boundedNumber(fulfillment.get("salaryMin"), 0, 1_000_000_000, true);
    boundedNumber(fulfillment.get("salaryMax"), 0, 1_000_000_000, true);
    ordered(
        fulfillment.get("salaryMin"), fulfillment.get("salaryMax"), "MODEL_SALARY_RANGE_INVALID");
    enumValue(fulfillment.get("salaryPeriod"), SalaryPeriod::supports, true);
    return root;
  }

  private static void validateSkills(JsonNode node) {
    list(node, 200);
    Set<String> ids = new HashSet<>();
    for (JsonNode item : node) {
      exact(item, Set.of("id", "name", "required", "aliases"));
      id(item.get("id"), ids);
      text(item.get("name"), 120, false);
      if (!item.get("required").isBoolean()) throw schema("MODEL_SCHEMA_INVALID");
      array(item, "aliases", 30, 120);
    }
  }

  private static void validateRoles(JsonNode node) {
    list(node, 100);
    Set<String> ids = new HashSet<>();
    for (JsonNode item : node) {
      exact(item, Set.of("id", "role", "level"));
      id(item.get("id"), ids);
      text(item.get("role"), 160, false);
      text(item.get("level"), 80, true);
    }
  }

  private static void validateLanguages(JsonNode node) {
    list(node, 50);
    Set<String> ids = new HashSet<>();
    for (JsonNode item : node) {
      exact(item, Set.of("id", "name", "level"));
      id(item.get("id"), ids);
      text(item.get("name"), 100, false);
      text(item.get("level"), 100, true);
    }
  }

  private static void exact(JsonNode node, Set<String> names) {
    if (node == null || !node.isObject() || !new HashSet<>(node.propertyNames()).equals(names)) {
      throw schema("MODEL_SCHEMA_INVALID");
    }
  }

  private static void list(JsonNode node, int max) {
    if (node == null || !node.isArray() || node.size() > max) {
      throw schema("MODEL_SCHEMA_INVALID");
    }
  }

  private static void array(JsonNode parent, String name, int maxItems, int maxLength) {
    JsonNode node = parent.get(name);
    list(node, maxItems);
    for (JsonNode item : node) text(item, maxLength, false);
  }

  private static void text(JsonNode node, int max, boolean nullable) {
    if (node == null || (node.isNull() && nullable)) return;
    if (!node.isString()
        || (!nullable && node.asString().isBlank())
        || node.asString().length() > max) {
      throw schema("MODEL_SCHEMA_INVALID");
    }
  }

  private static String requiredText(JsonNode node, int max) {
    text(node, max, false);
    return node.asString();
  }

  private static void id(JsonNode node, Set<String> ids) {
    String value = requiredText(node, 64);
    if (!value.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(value)) {
      throw schema("MODEL_ITEM_ID_INVALID");
    }
  }

  private static void boundedNumber(JsonNode node, int min, int max, boolean nullable) {
    if (node == null || (node.isNull() && nullable)) return;
    if (!node.isNumber()
        || !Double.isFinite(node.asDouble())
        || node.decimalValue().compareTo(BigDecimal.valueOf(min)) < 0
        || node.decimalValue().compareTo(BigDecimal.valueOf(max)) > 0) {
      throw schema("MODEL_SCHEMA_INVALID");
    }
  }

  private static void ordered(JsonNode min, JsonNode max, String code) {
    if (min != null
        && max != null
        && !min.isNull()
        && !max.isNull()
        && max.decimalValue().compareTo(min.decimalValue()) < 0) {
      throw schema(code);
    }
  }

  private static void enumValue(JsonNode node, Predicate<String> supports, boolean nullable) {
    if (node == null || (node.isNull() && nullable)) return;
    if (!node.isString() || !supports.test(node.asString())) {
      throw schema("MODEL_SCHEMA_INVALID");
    }
  }

  private static ContractViolation schema(String code) {
    return new ContractViolation(
        AiOutputValidationStatus.SCHEMA_INVALID, code, "模型输出不符合 JD 事实契约");
  }

  @Getter
  @Accessors(fluent = true)
  public static final class ContractViolation extends RuntimeException {

    private final AiOutputValidationStatus validationStatus;
    private final String errorCode;
    private final String safeDetail;

    ContractViolation(
        AiOutputValidationStatus validationStatus, String errorCode, String safeDetail) {
      super(safeDetail);
      this.validationStatus = validationStatus;
      this.errorCode = errorCode;
      this.safeDetail = safeDetail;
    }
  }
}
