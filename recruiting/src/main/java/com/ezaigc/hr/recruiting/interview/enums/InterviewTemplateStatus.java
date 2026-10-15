package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试通知模板启停状态。 */
@Getter
@RequiredArgsConstructor
public enum InterviewTemplateStatus implements CodeDescEnum {
  ACTIVE("启用"),
  DISABLED("停用");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String ACTIVE = "ACTIVE";
    public static final String DISABLED = "DISABLED";

    private Codes() {}
  }
}
