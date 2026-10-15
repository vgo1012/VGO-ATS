package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 招聘流程步骤的业务结果。 */
@Getter
@RequiredArgsConstructor
public enum ApplicationStepResult implements CodeDescEnum {
  PASSED("通过"),
  FAILED("未通过"),
  WITHDRAWN("已撤回");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
