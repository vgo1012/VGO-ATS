package com.ezaigc.hr.common.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JsonNodeValuesTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void readsScalarAndArrayValues() {
    var node =
        objectMapper.readTree(
            """
            {
              "name": "Java",
              "years": 5,
              "score": 4.25,
              "aliases": ["JDK", "JVM"]
            }
            """);

    assertThat(JsonNodeValues.text(node, "name")).isEqualTo("Java");
    assertThat(JsonNodeValues.integer(node, "years")).isEqualTo(5);
    assertThat(JsonNodeValues.decimal(node, "score")).isEqualByComparingTo(new BigDecimal("4.25"));
    assertThat(JsonNodeValues.strings(node, "aliases")).containsExactly("JDK", "JVM");
  }

  @Test
  void mapsObjectArraysAndUsesSafeDefaultsForMissingValues() {
    var node =
        objectMapper.readTree(
            """
            {"skills": [{"name": "Java"}]}
            """);

    assertThat(JsonNodeValues.objects(objectMapper, node, "skills", Skill.class))
        .extracting(Skill::getName)
        .containsExactly("Java");
    assertThat(JsonNodeValues.text(node, "missing")).isNull();
    assertThat(JsonNodeValues.strings(node, "missing")).isEmpty();
    assertThat(JsonNodeValues.objects(objectMapper, null, "skills", Skill.class)).isEmpty();
  }

  public static class Skill {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
