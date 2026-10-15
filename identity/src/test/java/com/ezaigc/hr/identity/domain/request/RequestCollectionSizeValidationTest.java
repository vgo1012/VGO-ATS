package com.ezaigc.hr.identity.domain.request;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ASSIGNABLE_ROLES_PER_USER;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RequestCollectionSizeValidationTest {

  private static final String DEPARTMENT_ID = "00000000000000000000000001";

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
  void rejectsCollectionsAboveTheDeclaredRequestLimit() {
    Set<String> roles = new LinkedHashSet<>();
    for (int index = 0; index <= MAX_ASSIGNABLE_ROLES_PER_USER; index++) {
      roles.add("ROLE_" + index);
    }

    Set<ConstraintViolation<IdentityAdminCreateUserRequest>> violations =
        validator.validate(request(roles));

    assertThat(violations)
        .anyMatch(violation -> "roles".equals(violation.getPropertyPath().toString()));
  }

  @Test
  void acceptsCollectionsAtTheDeclaredRequestLimit() {
    Set<String> roles = new LinkedHashSet<>();
    for (int index = 0; index < MAX_ASSIGNABLE_ROLES_PER_USER; index++) {
      roles.add("ROLE_" + index);
    }

    assertThat(validator.validate(request(roles))).isEmpty();
  }

  private static IdentityAdminCreateUserRequest request(Set<String> roles) {
    return new IdentityAdminCreateUserRequest(
        "测试用户", "person@example.com", null, DEPARTMENT_ID, null, Set.of(), roles, "创建用户");
  }
}
