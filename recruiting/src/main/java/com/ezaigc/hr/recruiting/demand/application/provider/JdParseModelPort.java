package com.ezaigc.hr.recruiting.demand.application.provider;

import lombok.Value;
import lombok.experimental.Accessors;

public interface JdParseModelPort {
  ModelResult parse(ParseRequest request);

  @Value
  @Accessors(fluent = true)
  class ParseRequest {
    String schemaVersion;
    String promptVersion;
    String title;
    String rawText;
  }

  @Value
  @Accessors(fluent = true)
  class ModelResult {
    String provider;
    String modelVersion;
    String rawJson;
    int inputTokens;
    int outputTokens;
    long durationMillis;
  }

  final class ModelUnavailableException extends RuntimeException {
    public ModelUnavailableException(String message) {
      super(message);
    }
  }
}
