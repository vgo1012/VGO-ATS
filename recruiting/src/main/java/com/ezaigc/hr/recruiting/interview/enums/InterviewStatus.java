package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试生命周期状态。 */
@Getter
@RequiredArgsConstructor
public enum InterviewStatus implements CodeDescEnum {
  SCHEDULED("已安排"),
  COMPLETED("已完成"),
  CANCELLED("已取消");

  private final String desc;

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String SCHEDULED = "SCHEDULED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private Codes() {}
  }
}
