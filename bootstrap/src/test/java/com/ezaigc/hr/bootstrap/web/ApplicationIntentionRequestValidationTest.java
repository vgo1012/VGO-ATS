package com.ezaigc.hr.bootstrap.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationIntentionRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApplicationIntentionRequestValidationTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void rejectsNullAndBlankLocationElements() {
    ApplicationIntentionRequest request =
        new ApplicationIntentionRequest(
            null, null, null, null, Arrays.asList("上海", null, "  "), "更新候选人意向");

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .allMatch(path -> path.startsWith("locations["))
        .hasSize(2);
  }

  @Test
  void enforcesTheLocationElementLengthBoundary() {
    ApplicationIntentionRequest accepted =
        new ApplicationIntentionRequest(
            null, null, null, null, List.of("地".repeat(100)), "更新候选人意向");
    ApplicationIntentionRequest rejected =
        new ApplicationIntentionRequest(
            null, null, null, null, List.of("地".repeat(101)), "更新候选人意向");

    assertThat(validator.validate(accepted)).isEmpty();
    assertThat(validator.validate(rejected))
        .singleElement()
        .satisfies(
            violation ->
                assertThat(violation.getPropertyPath().toString()).startsWith("locations[0]"));
  }
}
