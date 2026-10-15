package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面向招聘人员的入库状态，内部恢复步骤不增加页面状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeIntakeImportStatus implements CodeDescEnum {
  PENDING("待入库"),
  PROCESSING("解析中"),
  IMPORTED("已入库");

  private final String desc;

  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }

  /** 供注解 SQL 使用的编译期编码。 */
  public static final class Codes {
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String IMPORTED = "IMPORTED";

    private Codes() {}
  }

  public static ResumeIntakeImportStatus from(
      String status, String processingId, String scoreStatus) {
    if (ResumeIntakeStatus.COMPLETED.matches(status)
        && ResumeIntakeScoreStatus.SUCCEEDED.matches(scoreStatus)) {
      return IMPORTED;
    }
    if (ResumeIntakeStatus.PARSING.matches(status)
        || ResumeIntakeStatus.CONFIRMED.matches(status)
        || ResumeIntakeStatus.COMPLETED.matches(status)) {
      return PROCESSING;
    }
    return PENDING;
  }
}
