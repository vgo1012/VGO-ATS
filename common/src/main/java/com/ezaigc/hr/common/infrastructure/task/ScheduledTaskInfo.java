package com.ezaigc.hr.common.infrastructure.task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScheduledTaskInfo {
    String name();
    String module();
    String[] channels();
    String[] businessTypes() default {};
    String enabledProperty() default "";
    boolean enabledByDefault() default true;
    long idleBackoffMaxMillis() default 0;
}
