package com.ezaigc.hr.recruiting.activity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;

public enum ActivityAction implements CodeDescEnum {
  PENDING("待定"), INTERVIEW("面试"), ENDED("放弃"), ONBOARDED("入职"), BLACKLIST("黑名单"), OTHER("其他");
  private final String title;
  ActivityAction(String title) { this.title = title; }
  public String getDesc() { return title; }

  /** Java 注解 SQL 需要编译期常量，由所属枚举统一提供。 */
  public static final class Codes {
    public static final String PENDING = "PENDING";
    private Codes() {}
  }
}
