package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 风险影响面投影排除的客户推荐处理终态。 */
@Getter
@RequiredArgsConstructor
public enum RiskSubmissionProcessStatus implements CodeDescEnum {
  ACCEPTED("已接受"),
  REJECTED("已拒绝"),
  EXPIRED("已过期");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String EXPIRED = "EXPIRED";

    private Codes() {}
  }
}
