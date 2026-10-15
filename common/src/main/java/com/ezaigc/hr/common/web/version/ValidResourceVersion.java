package com.ezaigc.hr.common.web.version;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 必填的 HTTP 乐观锁版本：仅接受可转换为非负 long 的 ASCII 十进制字符串。 */
@Documented
@Constraint(validatedBy = ResourceVersionValidator.class)
@NotBlank
@Size(max = ResourceVersion.MAXIMUM_INPUT_LENGTH)
@Pattern(regexp = ResourceVersion.INPUT_PATTERN)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidResourceVersion {

  String message() default "version 必须是非负十进制整数且不能超过 long 范围";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
