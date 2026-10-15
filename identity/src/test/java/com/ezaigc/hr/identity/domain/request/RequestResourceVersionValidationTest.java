package com.ezaigc.hr.identity.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RequestResourceVersionValidationTest {

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
  void rejectsMalformedMissingAndOverflowingVersionsAtTheRequestBoundary() {
    for (String value :
        new String[] {
          null, "", " ", "-1", "+1", "1.0", "00000000000000000000", "9223372036854775808"
        }) {
      Set<ConstraintViolation<IdentityAdminDisableUserRequest>> violations =
          validator.validate(new IdentityAdminDisableUserRequest(value, "管理员停用", false));

      assertThat(violations)
          .as("version=%s", value)
          .anyMatch(violation -> "version".equals(violation.getPropertyPath().toString()));

      assertThat(validator.validate(new IdentityAdminDeleteDepartmentRequest(value, "删除部门")))
          .anyMatch(violation -> "version".equals(violation.getPropertyPath().toString()));
    }
  }

  @Test
  void acceptsTheFullNonNegativeLongVersionRange() {
    assertThat(
            validator.validate(
                new IdentityAdminDisableUserRequest(
                    Long.toString(Long.MAX_VALUE), "管理员停用", false)))
        .isEmpty();
  }

  @Test
  void departmentDeleteRequiresAReason() {
    assertThat(validator.validate(new IdentityAdminDeleteDepartmentRequest("0", " ")))
        .anyMatch(violation -> "reason".equals(violation.getPropertyPath().toString()));
  }
}
