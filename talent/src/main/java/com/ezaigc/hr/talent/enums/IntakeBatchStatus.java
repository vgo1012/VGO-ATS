package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 人才收件批次状态。 */
@Getter
@RequiredArgsConstructor
public enum IntakeBatchStatus implements CodeDescEnum {
  OPEN("待处理"),
  PROCESSING("处理中"),
  COMPLETED("已完成"),
  COMPLETED_WITH_ERRORS("完成但有错误");

  private final String desc;

  /** 仅供 Java 注解 SQL 使用的编译期编码。 */
  public static final class Codes {
    public static final String OPEN = "OPEN";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS";

    private Codes() {}
  }

  public static boolean isTerminal(String code) {
    return COMPLETED.matches(code) || COMPLETED_WITH_ERRORS.matches(code);
  }
}
