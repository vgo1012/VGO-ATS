package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试评价结论。 */
@Getter
@RequiredArgsConstructor
public enum InterviewFeedbackVerdict implements CodeDescEnum {
  PASS("通过"),
  FAIL("不通过");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
