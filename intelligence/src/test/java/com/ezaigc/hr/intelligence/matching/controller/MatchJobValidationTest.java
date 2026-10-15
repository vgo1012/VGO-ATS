package com.ezaigc.hr.intelligence.matching.controller;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class MatchJobValidationTest {
  @Test void validatesFiltersBasisAndPageBounds() throws Exception {
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var validator = factory.getValidator().forExecutables(); var controller = new MatchJobController();
      var jobs = MatchJobController.class.getDeclaredMethod("jobs", String.class, String.class, int.class);
      assertThat(validator.validateParameters(controller, jobs, new Object[] {null, null, 50})).isEmpty();
      assertThat(validator.validateParameters(controller, jobs, new Object[] {"invalid", null, 50})).isNotEmpty();
      assertThat(validator.validateParameters(controller, jobs, new Object[] {null, null, 101})).isNotEmpty();
      var summary = MatchJobController.class.getDeclaredMethod("resultsSummary", String.class, String.class);
      assertThat(validator.validateParameters(controller, summary, new Object[] {"A".repeat(26), "B".repeat(26)})).isEmpty();
      assertThat(validator.validateParameters(controller, summary, new Object[] {"invalid", "B".repeat(26)})).isNotEmpty();
      assertThat(validator.validateParameters(controller, summary, new Object[] {"A".repeat(26), "invalid"})).isNotEmpty();
      var results = MatchJobController.class.getDeclaredMethod("results", String.class,String.class,String.class,int.class);
      assertThat(validator.validateParameters(controller, results, new Object[] {"A".repeat(26),"invalid",null,50})).isNotEmpty();
    }
  }
}
