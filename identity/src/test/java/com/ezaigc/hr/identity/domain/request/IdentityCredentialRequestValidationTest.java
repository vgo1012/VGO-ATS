package com.ezaigc.hr.identity.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IdentityCredentialRequestValidationTest {

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
  void rejectsOversizedLoginEmailBeforeAuthentication() {
    AuthLoginRequest request =
        new AuthLoginRequest("a".repeat(309) + "@example.com", "Password-123");

    assertThat(hasSizeViolation(validator.validate(request), "workEmail")).isTrue();
  }

  @Test
  void rejectsOversizedLoginPasswordBeforeAuthentication() {
    AuthLoginRequest request = new AuthLoginRequest("user@example.com", "A".repeat(321));

    assertThat(hasSizeViolation(validator.validate(request), "password")).isTrue();
  }

  @Test
  void rejectsOversizedCurrentAndNewPasswordsBeforePasswordWork() {
    AuthChangePasswordRequest request =
        new AuthChangePasswordRequest("A".repeat(321), "B".repeat(257));

    Set<ConstraintViolation<AuthChangePasswordRequest>> violations = validator.validate(request);

    assertThat(hasSizeViolation(violations, "currentPassword")).isTrue();
    assertThat(hasSizeViolation(violations, "newPassword")).isTrue();
  }

  @Test
  void acceptsCredentialInputsAtConfiguredBoundary() {
    assertThat(validator.validate(new AuthLoginRequest("user@example.com", "A".repeat(320))))
        .isEmpty();
    assertThat(validator.validate(new AuthChangePasswordRequest("A".repeat(320), "B".repeat(256))))
        .isEmpty();
  }

  @Test
  void acceptsWhitespacePaddedLoginEmailForAuthenticationNormalization() {
    assertThat(
            validator.validate(new AuthLoginRequest(" ADMIN@example.com ", "Admin-Password-123")))
        .isEmpty();
  }

  @Test
  void acceptsWhitespacePaddedAdminCreationEmailForNormalization() {
    var request = new IdentityAdminCreateUserRequest(
        "测试账号", " USER@Example.COM ", null, "D".repeat(26), null,
        Set.of(), Set.of("RECRUITER"), "创建账号");
    assertThat(validator.validate(request)).isEmpty();
  }

  private static boolean hasSizeViolation(
      Set<? extends ConstraintViolation<?>> violations, String property) {
    return violations.stream()
        .anyMatch(
            violation ->
                property.equals(violation.getPropertyPath().toString())
                    && Size.class.equals(
                        violation.getConstraintDescriptor().getAnnotation().annotationType()));
  }
}
