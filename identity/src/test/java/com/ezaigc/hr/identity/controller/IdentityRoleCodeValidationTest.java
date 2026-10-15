package com.ezaigc.hr.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.identity.domain.request.IdentityAdminReasonRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminRoleAssignmentRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Method;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IdentityRoleCodeValidationTest {

  private static final String USER_ID = "00000000000000000000000001";
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
  void rejectsUnsupportedRoleInAssignmentBody() {
    IdentityAdminRoleAssignmentRequest request =
        new IdentityAdminRoleAssignmentRequest(USER_ID, "UNKNOWN", "业务调整");

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("roleCode");
  }

  @Test
  void rejectsOversizedRoleInAssignmentBody() {
    IdentityAdminRoleAssignmentRequest request =
        new IdentityAdminRoleAssignmentRequest(USER_ID, "R".repeat(65), "业务调整");

    assertThat(validator.validate(request))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("roleCode");
  }

  @Test
  void rejectsUnsupportedRoleInRevokePath() throws NoSuchMethodException {
    IdentityAdminController controller = new IdentityAdminController();
    Method method =
        IdentityAdminController.class.getDeclaredMethod(
            "revokeRole", String.class, String.class, IdentityAdminReasonRequest.class);

    Set<ConstraintViolation<IdentityAdminController>> violations =
        validator
            .forExecutables()
            .validateParameters(
                controller,
                method,
                new Object[] {USER_ID, "UNKNOWN", new IdentityAdminReasonRequest("业务调整")});

    assertThat(violations)
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("revokeRole.roleCode");
  }

  @Test
  void acceptsSupportedRoleCaseAndEdgeWhitespaceForExistingNormalizationContract()
      throws NoSuchMethodException {
    IdentityAdminRoleAssignmentRequest request =
        new IdentityAdminRoleAssignmentRequest(USER_ID, " recruiter ", "业务调整");
    IdentityAdminController controller = new IdentityAdminController();
    Method method =
        IdentityAdminController.class.getDeclaredMethod(
            "revokeRole", String.class, String.class, IdentityAdminReasonRequest.class);

    assertThat(validator.validate(request)).isEmpty();
    assertThat(
            validator
                .forExecutables()
                .validateParameters(
                    controller,
                    method,
                    new Object[] {
                      USER_ID, " recruiter ", new IdentityAdminReasonRequest("业务调整")
                    }))
        .isEmpty();
  }
}
