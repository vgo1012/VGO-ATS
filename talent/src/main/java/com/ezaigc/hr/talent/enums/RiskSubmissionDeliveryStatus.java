package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 风险影响面投影所依赖的客户推荐交付状态。 */
@Getter
@RequiredArgsConstructor
public enum RiskSubmissionDeliveryStatus implements CodeDescEnum {
  DRAFT("草稿，仍处于活动影响面"),
  SENT("已发送，仍处于活动影响面");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String DRAFT = "DRAFT";
    public static final String SENT = "SENT";

    private Codes() {}
  }
}
