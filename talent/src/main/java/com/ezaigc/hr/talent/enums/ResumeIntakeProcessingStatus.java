package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 同内容简历共享的后台解析状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeIntakeProcessingStatus implements CodeDescEnum {
  QUEUED("等待处理"),
  RUNNING("处理中"),
  SUCCEEDED("处理成功"),
  FAILED_RETRYABLE("失败可重试"),
  FAILED_FINAL("最终失败");

  private final String desc;

  /** 供 Java 注解 SQL 使用的编译期常量。 */
  public static final class Codes {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String FAILED_FINAL = "FAILED_FINAL";

    private Codes() {}
  }
}
