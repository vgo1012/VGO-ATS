package com.ezaigc.hr.talent.controller.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验 HTTP 简历上传文件的必填性、大小、文件名和扩展名。 */
@Documented
@Constraint(validatedBy = ResumeFileValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidResumeFile {

  String message() default "简历文件不能为空，且必须是不超过 10 MB 的 PDF、DOCX 或 DOC 文件";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
