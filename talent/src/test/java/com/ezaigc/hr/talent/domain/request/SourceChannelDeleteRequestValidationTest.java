package com.ezaigc.hr.talent.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class SourceChannelDeleteRequestValidationTest {

  @Test
  void requiresValidVersionAndReason() {
    try (var factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
      var violations = factory.getValidator().validate(new TalentIntakeVersionReasonRequest("-1", " "));
      assertThat(violations)
          .extracting(violation -> violation.getPropertyPath().toString())
          .contains("version", "reason");
    }
  }
}
