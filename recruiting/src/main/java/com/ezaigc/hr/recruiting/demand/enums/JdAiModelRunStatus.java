package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD AI 模型运行状态。 */
@Getter
@RequiredArgsConstructor
public enum JdAiModelRunStatus implements CodeDescEnum {
  RUNNING("执行中"),
  SUCCEEDED("已成功"),
  FAILED("已失败");

  private final String desc;

  /** 供 Java 注解 SQL 使用的编译期常量；普通业务代码应使用枚举值。 */
  public static final class Codes {
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    private Codes() {}
  }

  public static JdAiModelRunStatus fromCode(String code) {
    return JdAiModelRunStatus.valueOf(code);
  }
}
