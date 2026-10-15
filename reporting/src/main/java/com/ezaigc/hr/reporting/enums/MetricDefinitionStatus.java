package com.ezaigc.hr.reporting.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 指标定义状态。 */
@Getter
@RequiredArgsConstructor
public enum MetricDefinitionStatus implements CodeDescEnum {
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
