package com.ezaigc.hr.common.infrastructure.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks Mapper SQL whose semantics cannot be preserved by a typed MyBatis-Plus wrapper.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SpecialSql {

    String value();
}
