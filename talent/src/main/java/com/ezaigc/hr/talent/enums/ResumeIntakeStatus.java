package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历收件状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeIntakeStatus implements CodeDescEnum {
  PENDING_IMPORT("待入库"),
  PARSING("解析中"),
  IDENTITY_REVIEW_REQUIRED("身份待人工确认"),
  COMPLETED("处理完成"),
  READY_FOR_CONFIRMATION("待确认"),
  CONFIRMED("已确认"),
  IGNORED("已忽略"),
  REJECTED("已拒绝"),
  QUARANTINED("已隔离"),
  FAILED_RETRYABLE("失败可重试");

  private final String desc;

  /** 供 Java 注解 SQL 使用的编译期常量。 */
  public static final class Codes {
    public static final String PENDING_IMPORT = "PENDING_IMPORT";
    public static final String PARSING = "PARSING";
    public static final String IDENTITY_REVIEW_REQUIRED = "IDENTITY_REVIEW_REQUIRED";
    public static final String COMPLETED = "COMPLETED";
    public static final String READY_FOR_CONFIRMATION = "READY_FOR_CONFIRMATION";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String IGNORED = "IGNORED";
    public static final String REJECTED = "REJECTED";
    public static final String QUARANTINED = "QUARANTINED";
    public static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";

    private Codes() {}
  }
}
