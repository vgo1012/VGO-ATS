package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 收件校验的问题聚合筛选，不改变收件自身生命周期状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeIntakeProblemStatus implements CodeDescEnum {
  PROBLEM("全部问题"),
  IDENTITY_REVIEW("待核对身份"),
  FAILED("导入或评分失败"),
  MISSING_JD("待关联 JD");

  private final String desc;

  /** 供 Java 注解 SQL 使用的编译期常量。 */
  public static final class Codes {
    public static final String PROBLEM = "PROBLEM";
    public static final String IDENTITY_REVIEW = "IDENTITY_REVIEW";
    public static final String FAILED = "FAILED";
    public static final String MISSING_JD = "MISSING_JD";

    private Codes() {}
  }
}
