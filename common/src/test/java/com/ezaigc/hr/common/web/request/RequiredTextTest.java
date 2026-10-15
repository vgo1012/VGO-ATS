package com.ezaigc.hr.common.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RequiredTextTest {

  @Test
  void trimsValidText() {
    assertThat(RequiredText.require("  招聘需求  ", "title", 8)).isEqualTo("招聘需求");
    assertThat(RequiredText.require("  原因  ", 8)).isEqualTo("原因");
  }

  @Test
  void normalizesAndBoundsOptionalText() {
    assertThat(RequiredText.optional(null, "query", 4)).isNull();
    assertThat(RequiredText.optional("   ", "query", 4)).isNull();
    assertThat(RequiredText.optional("  张三  ", "query", 4)).isEqualTo("张三");
    assertThatThrownBy(() -> RequiredText.optional("12345", "query", 4))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(exception.code()).isEqualTo("FIELD_TOO_LONG");
              assertThat(exception.getMessage()).isEqualTo("query 超过最大长度 4");
            });
  }

  @Test
  void preservesFieldAwareErrorContract() {
    assertThatThrownBy(() -> RequiredText.require(" ", "title", 8))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(exception.code()).isEqualTo("FIELD_REQUIRED");
              assertThat(exception.getMessage()).isEqualTo("title 不能为空");
            });

    assertThatThrownBy(() -> RequiredText.require("12345", "title", 4))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("FIELD_TOO_LONG");
              assertThat(exception.getMessage()).isEqualTo("title 超过最大长度 4");
            });
  }

  @Test
  void preservesGenericErrorContract() {
    assertThatThrownBy(() -> RequiredText.require(null, 8))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("FIELD_REQUIRED");
              assertThat(exception.getMessage()).isEqualTo("必填字段不能为空");
            });
  }

  @Test
  void preservesLegacyFieldInvalidContract() {
    assertThat(RequiredText.required("  备注  ", "备注", 8)).isEqualTo("备注");
    assertThatThrownBy(() -> RequiredText.required(" ", "备注", 8))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(exception.code()).isEqualTo("FIELD_INVALID");
              assertThat(exception.getMessage()).isEqualTo("备注 必须为 1～8 字");
            });
  }
}
