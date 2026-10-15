package com.ezaigc.hr.common.web.request;

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

/**
 * 声明 HTTP 幂等键的统一边界约束。
 *
 * <p>应用层仍需通过 {@link IdempotencyKey#fromHttpHeader(String)} 做纵深校验，避免非 HTTP
 * 调用绕过输入规则。
 */
@Documented
@Constraint(validatedBy = {})
@NotBlank
@Size(max = IdempotencyKey.MAX_LENGTH)
@Pattern(regexp = IdempotencyKey.FORMAT_REGEXP)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIdempotencyKey {

    String message() default "Idempotency-Key 必须为 1～128 位字母、数字或 . _ : -";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
