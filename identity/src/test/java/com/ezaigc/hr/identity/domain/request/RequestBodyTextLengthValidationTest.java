package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_DEPARTMENT_NAME_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_DISPLAY_NAME_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_REASON_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ROLE_CODE_INPUT_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_SOURCE_TITLE_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RequestBodyTextLengthValidationTest {

  private static final String VALID_IDENTIFIER = "00000000000000000000000001";

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
  void rejectsOversizedTextAndCollectionElements() {
    IdentityAdminCreateUserRequest request =
        new IdentityAdminCreateUserRequest(
            "N".repeat(MAX_DISPLAY_NAME_LENGTH + 1),
            "person@example.com",
            "T".repeat(MAX_SOURCE_TITLE_LENGTH + 1),
            VALID_IDENTIFIER,
            null,
            Set.of(),
            Set.of("R".repeat(MAX_ROLE_CODE_INPUT_LENGTH + 1)),
            "R".repeat(MAX_REASON_LENGTH + 1));

    assertThat(paths(validator.validate(request)))
        .contains(
            "displayName", "sourceTitle", "roles[].<iterable element>", "reason");
  }

  @Test
  void rejectsControlCharactersInCodeValues() {
    IdentityAdminUpdateDepartmentRequest request =
        new IdentityAdminUpdateDepartmentRequest(
            "N".repeat(MAX_DEPARTMENT_NAME_LENGTH),
            null,
            null,
            "ACTIVE\r\nFORGED",
            "0");

    assertThat(paths(validator.validate(request))).contains("status");
  }

  private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
