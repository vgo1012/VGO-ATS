package com.ezaigc.hr.intelligence.search.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

class SearchIndexRetentionPropertiesTest {
  @Test
  void safeDefaultsHaveFiniteCapacitiesAndRequestProtection() {
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var policy = new SearchIndexRetentionProperties();
      assertThat(factory.getValidator().validate(policy)).isEmpty();
      assertThat(policy.getRequestBudget()).isEqualTo(Duration.ofMinutes(2));
      assertThat(policy.getMaxLiveResources()).isEqualTo(8);
      assertThat(policy.getMaxUnresolvedCreates()).isEqualTo(1);
    }
  }

  @Test
  void policyCannotExceedCurrentReadCapacityBoundsOrMakeRequestsAndRetentionUnbounded() {
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator();
      var policy = new SearchIndexRetentionProperties();
      policy.setMaxLiveResources(101);
      assertThat(validator.validate(policy)).isNotEmpty();
      policy.setMaxLiveResources(8); policy.setMaxUnresolvedCreates(11);
      assertThat(validator.validate(policy)).isNotEmpty();
      policy.setMaxUnresolvedCreates(1); policy.setRequestBudget(Duration.ofMinutes(6));
      assertThat(validator.validate(policy)).isNotEmpty();
      policy.setRequestBudget(Duration.ofMinutes(2)); policy.setMinimumAge(Duration.ofDays(31));
      assertThat(validator.validate(policy)).isNotEmpty();
      policy.setMinimumAge(Duration.ofMinutes(10)); policy.setRetainCount(8);
      assertThat(validator.validate(policy)).isNotEmpty();
    }
  }
}
