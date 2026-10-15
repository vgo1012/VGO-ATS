package com.ezaigc.hr.integration.ai;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Builds the strict compact provider schema for the five-dimension assessment. */
final class ProfessionalScoringJsonSchema {

  private static final String[] CODES = {
      "CORE_SKILLS", "ROLE_EXPERIENCE", "RELATED_YEARS", "RESPONSIBILITY_MATCH",
      "INDUSTRY_AND_PROJECTS"
  };

  private ProfessionalScoringJsonSchema() {}

  static String build(ObjectMapper json) {
    ObjectNode root = object(json);
    ObjectNode properties = root.putObject("properties");
    ObjectNode dimensions = properties.putObject("dimensions");
    dimensions.put("type", "array").put("minItems", 5).put("maxItems", 5);
    dimensions.set("items", dimension(json));
    number(properties.putObject("professionalScore"), 0, 100);
    string(properties.putObject("summary"), 1, 2_000);
    required(root, "dimensions", "professionalScore", "summary");
    return root.toString();
  }

  private static ObjectNode dimension(ObjectMapper json) {
    ObjectNode value = object(json);
    ObjectNode properties = value.putObject("properties");
    enumeration(properties.putObject("code"), CODES);
    enumeration(properties.putObject("status"), "KNOWN", "UNKNOWN");
    number(properties.putObject("score"), 0, 100);
    string(properties.putObject("explanation"), 1, 2_000);
    ObjectNode highlights = properties.putObject("highlights");
    highlights.put("type", "array").put("maxItems", 4);
    string(highlights.putObject("items"), 1, 300);
    required(value, "code", "status", "score", "explanation", "highlights");
    return value;
  }

  private static ObjectNode object(ObjectMapper json) {
    return json.createObjectNode().put("type", "object").put("additionalProperties", false);
  }

  private static void required(ObjectNode value, String... names) {
    ArrayNode required = value.putArray("required");
    for (String name : names) required.add(name);
  }

  private static void enumeration(ObjectNode value, String... values) {
    value.put("type", "string");
    ArrayNode allowed = value.putArray("enum");
    for (String item : values) allowed.add(item);
  }

  private static void number(ObjectNode value, int minimum, int maximum) {
    value.put("type", "number").put("minimum", minimum).put("maximum", maximum);
  }

  private static void string(ObjectNode value, int minimum, int maximum) {
    value.put("type", "string").put("minLength", minimum).put("maxLength", maximum);
  }
}
