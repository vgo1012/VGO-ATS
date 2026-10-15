package com.ezaigc.hr.common.web.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 可选平台资源 ID：未传参数合法，已传参数必须是 26 位。 */
@Documented
@Constraint(validatedBy = {})
@Size(min = RequiredIdentifier.IDENTIFIER_LENGTH, max = RequiredIdentifier.IDENTIFIER_LENGTH)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalPlatformIdentifier {

  String message() default "必须是 26 位平台标识";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
