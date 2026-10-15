package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD 内容来源。 */
@Getter
@RequiredArgsConstructor
public enum JdSourceType implements CodeDescEnum {
  MANUAL("人工录入"),
  AI_PARSED("AI 解析");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
