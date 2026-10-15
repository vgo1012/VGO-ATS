package com.ezaigc.hr.common.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JsonTextTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void safelySerializesControlCharactersAndQuotes() throws Exception {
    String json = JsonText.object("ke\"y", "line1\nline2\\value");

    JsonNode root = mapper.readTree(json);
    assertThat(root.get("ke\"y").asString()).isEqualTo("line1\nline2\\value");
  }

  @Test
  void preservesCollectionValuesAsJsonArray() throws Exception {
    JsonNode root = mapper.readTree(JsonText.write(List.of("a\"b", "c")));

    assertThat(root.isArray()).isTrue();
    assertThat(root.get(0).asString()).isEqualTo("a\"b");
  }
}
