package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Application 流程步骤状态。 */
@Getter
@RequiredArgsConstructor
public enum ApplicationStepStatus implements CodeDescEnum {
  AVAILABLE("可选择"),
  NOT_SELECTED("未选择"),
  PENDING("待处理"),
  IN_PROGRESS("进行中"),
  PASSED("已通过"),
  FAILED("未通过"),
  WITHDRAWN("已撤回"),
  SKIPPED("已纠错跳过");

  private final String desc;

  public static boolean isActive(String code) {
    return PENDING.matches(code) || IN_PROGRESS.matches(code);
  }

  public static boolean isFinished(String code) {
    return PASSED.matches(code)
        || FAILED.matches(code)
        || WITHDRAWN.matches(code)
        || SKIPPED.matches(code);
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String NOT_SELECTED = "NOT_SELECTED";
    public static final String PENDING = "PENDING";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String SKIPPED = "SKIPPED";

    private Codes() {}
  }
}
