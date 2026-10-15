package com.ezaigc.hr.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MailResumePropertiesTest {
  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  @Test
  void disposableDevelopmentDefaultsRemainValid() {
    assertThat(validator.validate(new MailResumeProperties())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"connect", "read", "write"})
  void rejectsTimeoutsThatBecomeZeroOrExceedIntegerMilliseconds(String phase) {
    for (Duration value : new Duration[] {null, Duration.ZERO, Duration.ofMillis(-1),
        Duration.ofNanos(1), Duration.ofNanos(999_999),
        Duration.ofMillis((long) Integer.MAX_VALUE + 1)}) {
      assertThat(validator.validate(properties(phase, value))).as("%s timeout %s", phase, value)
          .extracting(violation -> violation.getPropertyPath().toString()).contains("validTimeouts");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"connect", "read", "write"})
  void rejectsHugeDurationsWithoutOverflowingValidation(String phase) {
    assertThat(validator.validate(properties(phase, Duration.ofSeconds(Long.MAX_VALUE))))
        .extracting(violation -> violation.getPropertyPath().toString()).contains("validTimeouts");
  }

  @ParameterizedTest
  @ValueSource(strings = {"connect", "read", "write"})
  void acceptsExistingFiniteMillisecondBoundaries(String phase) {
    for (Duration value : new Duration[] {Duration.ofMillis(1), Duration.ofSeconds(30),
        Duration.ofMillis(Integer.MAX_VALUE),
        Duration.ofMillis(Integer.MAX_VALUE).plusNanos(999_999)}) {
      assertThat(validator.validate(properties(phase, value))).as("%s timeout %s", phase, value)
          .isEmpty();
    }
  }

  private static MailResumeProperties properties(String phase, Duration value) {
    var properties = new MailResumeProperties();
    switch (phase) {
      case "connect" -> properties.setConnectTimeout(value);
      case "read" -> properties.setReadTimeout(value);
      case "write" -> properties.setWriteTimeout(value);
      default -> throw new AssertionError(phase);
    }
    return properties;
  }
}
