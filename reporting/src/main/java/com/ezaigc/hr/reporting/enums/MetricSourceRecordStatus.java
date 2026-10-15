package com.ezaigc.hr.reporting.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 报表指标跨域事实投影所依赖的源记录状态。 */
@Getter
@RequiredArgsConstructor
public enum MetricSourceRecordStatus implements CodeDescEnum {
  SUBMITTED("已提交，可纳入指标统计"),
  ACTIVE("当前在招"),
  ISSUED("Offer 已发放"),
  ACCEPTED("Offer 已接受"),
  ONBOARDED("已入职"),
  COMPLETED("面试已完成"),
  PENDING("尚无流程结果");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String SUBMITTED = "SUBMITTED";
    public static final String ACTIVE = "ACTIVE";
    public static final String ISSUED = "ISSUED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String ONBOARDED = "ONBOARDED";
    public static final String COMPLETED = "COMPLETED";
    public static final String PENDING = "PENDING";

    private Codes() {}
  }
}
