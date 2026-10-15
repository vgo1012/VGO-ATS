package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历收件领域通知的持久发布状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeIntakeOutboxStatus implements CodeDescEnum {
  PENDING("等待发布"),
  RUNNING("发布中"),
  PUBLISHED("发布成功"),
  FAILED_RETRYABLE("发布失败可重试"),
  FAILED_FINAL("发布最终失败");

  private final String desc;

  /** 供 Java 注解 SQL 使用的编译期常量。 */
  public static final class Codes {
    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String FAILED_FINAL = "FAILED_FINAL";

    private Codes() {}
  }
}
