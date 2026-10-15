package com.ezaigc.hr.common.web.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 平台内部资源 ID 的 HTTP 边界约束。
 *
 * <p>当前兼容契约只规定 26 位长度；应用层继续使用 {@link RequiredIdentifier} 做规范化和纵深校验。
 */
@Documented
@Constraint(validatedBy = {})
@NotBlank
@Size(min = RequiredIdentifier.IDENTIFIER_LENGTH, max = RequiredIdentifier.IDENTIFIER_LENGTH)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformIdentifier {

  String message() default "必须是 26 位平台标识";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
