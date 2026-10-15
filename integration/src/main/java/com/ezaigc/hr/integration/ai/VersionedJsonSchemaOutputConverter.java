package com.ezaigc.hr.integration.ai;

import java.util.Objects;
import org.springframework.ai.converter.StructuredOutputConverter;

/** Supplies one exact, versioned JSON Schema while preserving the model's raw JSON response. */
public final class VersionedJsonSchemaOutputConverter implements StructuredOutputConverter<String> {

  private final String version;
  private final String jsonSchema;

  public VersionedJsonSchemaOutputConverter(String version, String jsonSchema) {
    this.version = requireText(version, "version");
    this.jsonSchema = requireText(jsonSchema, "jsonSchema");
  }

  public String version() {
    return version;
  }

  @Override
  public String getJsonSchema() {
    return jsonSchema;
  }

  @Override
  public String convert(String source) {
    return Objects.requireNonNull(source, "model output must not be null");
  }

  @Override
  public String getFormat() {
    return "Return only JSON matching this schema:\n" + getJsonSchema();
  }

  public void requireVersion(String requestedVersion) {
    if (!version().equals(requestedVersion)) {
      throw new IllegalArgumentException("unsupported AI schema version: " + requestedVersion);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
