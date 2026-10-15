package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配运行状态。 */
@Getter
@RequiredArgsConstructor
public enum MatchRunStatus implements CodeDescEnum {
  QUEUED("排队中"),
  RUNNING("执行中"),
  SUCCEEDED("已成功"),
  PARTIAL("部分成功"),
  FAILED("已失败"),
  CANCELLED("已取消");

  private final String desc;

  public static MatchRunStatus fromCode(String code) {
    return MatchRunStatus.valueOf(code);
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String PARTIAL = "PARTIAL";

    private Codes() {}
  }
}
