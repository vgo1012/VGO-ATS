package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 收件关联岗位的评分状态投影。 */
@Getter
@RequiredArgsConstructor
public enum ResumeIntakeScoreStatus implements CodeDescEnum {
  NOT_APPLICABLE("未关联岗位"),
  QUEUED("等待评分"),
  WAITING_JD("等待岗位事实"),
  WAITING_CONFIG("等待评分配置"),
  RUNNING("评分中"),
  SUCCEEDED("评分成功"),
  FAILED_RETRYABLE("评分失败可重试"),
  FAILED_FINAL("评分最终失败");

  private final String desc;

  /** 后台同步与人工恢复使用相同投影，取消中仍需轮询，取消完成不可重试。 */
  public static ResumeIntakeScoreStatus fromTask(String status, boolean retryable) {
    return switch (AsyncTaskStatus.fromCode(status)) {
      case QUEUED -> QUEUED;
      case RUNNING, CANCELLING -> RUNNING;
      case SUCCEEDED -> SUCCEEDED;
      case FAILED -> retryable ? FAILED_RETRYABLE : FAILED_FINAL;
      case CANCELLED -> FAILED_FINAL;
    };
  }

  /** 将评分端口的稳定前置条件错误映射为可恢复的等待状态。 */
  public static ResumeIntakeScoreStatus forFailure(String errorCode, boolean retryable) {
    if (errorCode == null) return retryable ? FAILED_RETRYABLE : FAILED_FINAL;
    return switch (errorCode) {
      case "JD_FACTS_REQUIRED" -> WAITING_JD;
      case "MATCH_PLAN_NOT_ACTIVE" -> WAITING_CONFIG;
      default -> retryable ? FAILED_RETRYABLE : FAILED_FINAL;
    };
  }

  /** 供 Java 注解 SQL 使用的编译期常量。 */
  public static final class Codes {
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String QUEUED = "QUEUED";
    public static final String WAITING_JD = "WAITING_JD";
    public static final String WAITING_CONFIG = "WAITING_CONFIG";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String FAILED_FINAL = "FAILED_FINAL";

    private Codes() {}
  }
}
