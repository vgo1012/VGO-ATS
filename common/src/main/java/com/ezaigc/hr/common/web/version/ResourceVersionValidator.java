package com.ezaigc.hr.common.web.version;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** 校验版本字符串的数值范围，补足单纯正则无法识别 long 溢出的边界。 */
public final class ResourceVersionValidator
    implements ConstraintValidator<ValidResourceVersion, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return ResourceVersion.isValid(value);
  }
}
