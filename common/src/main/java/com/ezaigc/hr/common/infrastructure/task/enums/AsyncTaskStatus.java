package com.ezaigc.hr.common.infrastructure.task.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可恢复异步任务的运行状态。 */
@Getter
@RequiredArgsConstructor
public enum AsyncTaskStatus implements CodeDescEnum {
  QUEUED("排队中"),
  RUNNING("执行中"),
  CANCELLING("取消中"),
  SUCCEEDED("已成功"),
  FAILED("已失败"),
  CANCELLED("已取消");

  private final String desc;

  /** 供 Java 注解 SQL 使用的编译期常量；普通业务代码应使用枚举值。 */
  public static final class Codes {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String CANCELLING = "CANCELLING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    private Codes() {}
  }

  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED;
  }

  public boolean canCompleteCancellation(boolean allowRunningSource) {
    return this == CANCELLING || allowRunningSource && this == RUNNING;
  }

  public static AsyncTaskStatus fromCode(String code) {
    return AsyncTaskStatus.valueOf(code);
  }
}
