package com.ezaigc.hr.identity.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityInputRulesTest {

  @ParameterizedTest
  @ValueSource(strings = {"1", "123456", "abcdef", "ABCDEF", "abc123", "仅中文", "  abc  "})
  void acceptsNonBlankPasswordsWithoutLengthOrCharacterCompositionRules(String password) {
    assertThatCode(() -> IdentityInputRules.validateNewPassword(password)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n"})
  void stillRejectsBlankPasswords(String password) {
    assertThatThrownBy(() -> IdentityInputRules.validateNewPassword(password))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("PASSWORD_POLICY_VIOLATION"));
  }

  @Test
  void normalizesIdentityValues() {
    assertThat(IdentityInputRules.normalizeEmail("  USER@Example.COM "))
        .isEqualTo("user@example.com");
    assertThat(IdentityInputRules.normalizeCode(" hr-team ")).isEqualTo("HR-TEAM");
    assertThat(IdentityInputRules.normalizeRole(" recruiter ")).isEqualTo("RECRUITER");
  }

  @Test
  void preservesMissingAndInvalidErrorCodes() {
    assertThatThrownBy(() -> IdentityInputRules.requireText(" ", "姓名不能为空"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("REQUIRED_FIELD_MISSING"));
    assertThatThrownBy(() -> IdentityInputRules.normalizeRole("R".repeat(65)))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("ROLE_CODE_INVALID"));
  }
}
