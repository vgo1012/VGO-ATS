package com.ezaigc.hr.reporting.monthlyreport.controller.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MonthlyReportFileValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMonthlyReportFile {
    String message() default "请选择不超过10MiB、文件名不超过200字的非空xlsx文件";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
