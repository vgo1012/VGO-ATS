package com.ezaigc.hr.common.web.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标准在线分页大小：未传参数合法，已传参数必须在 1～100 之间。 */
@Documented
@Constraint(validatedBy = {})
@Min(StandardPageLimit.MINIMUM)
@Max(StandardPageLimit.MAXIMUM)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StandardPageLimit {

  int MINIMUM = 1;
  int MAXIMUM = 100;

  String message() default "分页大小必须在 1～100 之间";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
