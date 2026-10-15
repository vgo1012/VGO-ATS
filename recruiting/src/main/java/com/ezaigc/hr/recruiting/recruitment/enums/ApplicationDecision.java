package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 人工招聘决定。 */
@Getter
@RequiredArgsConstructor
public enum ApplicationDecision implements CodeDescEnum {
  PASS("通过"),
  REJECT("拒绝"),
  WITHDRAW("撤回");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }

  public static boolean isNegative(String code) {
    return REJECT.matches(code) || WITHDRAW.matches(code);
  }
}
