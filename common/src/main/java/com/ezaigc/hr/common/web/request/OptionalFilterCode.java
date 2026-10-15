package com.ezaigc.hr.common.web.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 可选筛选代码：未传参数合法，已传参数只允许受控 ASCII 代码字符。 */
@Documented
@Constraint(validatedBy = {})
@Size(max = OptionalFilterCode.MAXIMUM_LENGTH)
@Pattern(regexp = OptionalFilterCode.INPUT_PATTERN)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalFilterCode {

  int MAXIMUM_LENGTH = 80;
  String INPUT_PATTERN = RequestCodeFormat.INPUT_PATTERN;

  String message() default "筛选代码格式无效或长度超过 80";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
