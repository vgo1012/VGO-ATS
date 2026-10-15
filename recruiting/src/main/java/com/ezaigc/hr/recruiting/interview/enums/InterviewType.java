package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;

/** 面试类型。 */
@Getter
@RequiredArgsConstructor
public enum InterviewType implements CodeDescEnum {
  INTERNAL("内部面试"),
  EXTERNAL("外部面试"),
  EXTERNAL_CLIENT("甲方面试"),
  EXTERNAL_CLIENT_AI("甲方 AI 面试");

  private final String desc;
  public static boolean supports(String code) { return Arrays.stream(values()).anyMatch(value -> value.matches(code)); }

  /** 列表筛选额外接受 EXTERNAL，统一匹配外部标签及历史甲方面试事实。 */
  public static boolean supportsFilter(String code) {
    return "EXTERNAL".equals(code) || supports(code);
  }

}
