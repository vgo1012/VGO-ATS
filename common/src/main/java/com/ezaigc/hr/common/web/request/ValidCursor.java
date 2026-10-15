package com.ezaigc.hr.common.web.request;

import static com.ezaigc.hr.common.web.pagination.OpaqueCursor.CURSOR_INPUT_PATTERN;
import static com.ezaigc.hr.common.web.pagination.OpaqueCursor.MAX_CURSOR_LENGTH;

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

/** 可选分页游标的 HTTP 边界约束；未传参数合法，已传参数必须是受限 Base64URL 标记。 */
@Documented
@Constraint(validatedBy = {})
@Size(max = MAX_CURSOR_LENGTH)
@Pattern(regexp = CURSOR_INPUT_PATTERN)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCursor {

  String message() default "分页游标格式无效";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
