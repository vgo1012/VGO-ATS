package com.ezaigc.hr.intelligence.matching.application.provider;

import java.time.LocalDate;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/** Uses frozen JD and resume facts to obtain an evidence-grounded professional assessment. */
public interface ProfessionalScoringModelPort {

  ModelResult score(ScoreRequest request);

  /** Identity known before a call; {@code null} means the scoring capability is unavailable. */
  default ModelIdentity cacheIdentity() {
    return null;
  }

  @Value
  @Accessors(fluent = true)
  class ModelIdentity {
    String provider;
    String modelVersion;
  }

  @Value
  @Accessors(fluent = true)
  class ScoreRequest {
    @ToString.Exclude String jdFactsJson;
    @ToString.Exclude String resumeFactsJson;
    String promptVersion;
    String schemaVersion;
    LocalDate evaluatedOn;
  }

  @Value
  @Accessors(fluent = true)
  class ModelResult {
    String provider;
    String modelVersion;
    @ToString.Exclude String rawJson;
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
