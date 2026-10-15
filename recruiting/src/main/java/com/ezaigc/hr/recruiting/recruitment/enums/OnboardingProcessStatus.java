package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Locale;

/** 入职流程状态；真实入职事件仅在 ONBOARDED 时存在。 */
public enum OnboardingProcessStatus implements CodeDescEnum {
  IN_PROGRESS("入职中"),
  ONBOARDED("已入职"),
  RETURNED("已退回共享简历库");

  private final String desc;

  OnboardingProcessStatus(String desc) {
    this.desc = desc;
  }

  @Override
  public String getDesc() {
    return desc;
  }

  @Override
  public boolean matches(String value) {
    return value != null && name().equals(value.toUpperCase(Locale.ROOT));
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String ONBOARDED = "ONBOARDED";
    public static final String RETURNED = "RETURNED";

    private Codes() {}
  }
}
