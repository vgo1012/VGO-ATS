package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试评价提交状态。 */
@Getter
@RequiredArgsConstructor
public enum InterviewFeedbackStatus implements CodeDescEnum {
  DRAFT("草稿"),
  SUBMITTED("已提交");

  private final String desc;

  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";

    private Codes() {}
  }
}
