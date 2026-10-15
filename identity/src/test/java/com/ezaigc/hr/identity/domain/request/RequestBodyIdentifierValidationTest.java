package com.ezaigc.hr.identity.domain.request;

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

class RequestBodyIdentifierValidationTest {

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
  void rejectsMissingOrMalformedRequiredIdentifier() {
    assertThat(propertyPaths(validator.validate(new IdentityAdminHandoverPreviewRequest(null))))
        .contains("targetUserId");
    assertThat(propertyPaths(validator.validate(new IdentityAdminHandoverPreviewRequest("short"))))
        .contains("targetUserId");
  }

  @Test
  void acceptsMissingOptionalIdentifierButRejectsMalformedValue() {
    assertThat(
            validator.validate(
                new IdentityAdminCreateDepartmentRequest("研发部", null)))
        .isEmpty();

    assertThat(
            propertyPaths(
                validator.validate(
                    new IdentityAdminCreateDepartmentRequest("研发部", "short"))))
        .contains("parentDepartmentId");
  }

  @Test
  void validatesEveryIdentifierInsideACollection() {
    IdentityAdminCreateUserRequest request =
        new IdentityAdminCreateUserRequest(
            "测试用户",
            "person@example.com",
            null,
            VALID_IDENTIFIER,
            null,
            Set.of("short"),
            Set.of("RECRUITER"),
            "创建用户");

    assertThat(propertyPaths(validator.validate(request)))
        .contains("supportDepartmentIds[].<iterable element>");
  }

  private static Set<String> propertyPaths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
