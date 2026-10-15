package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历版本可用状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeVersionStatus implements CodeDescEnum {
  AVAILABLE("可用");

  private final String desc;

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String AVAILABLE = "AVAILABLE";

    private Codes() {}
  }
}
