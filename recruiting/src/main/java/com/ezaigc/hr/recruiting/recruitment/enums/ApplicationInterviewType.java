package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 招聘流程当前步骤的面试筛选类型。 */
@Getter
@RequiredArgsConstructor
public enum ApplicationInterviewType implements CodeDescEnum {
  INTERNAL("内部面试"),
  CLIENT("甲方面试"),
  CLIENT_AI("甲方 AI 面试");

  private final String desc;

  public static boolean isValid(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
