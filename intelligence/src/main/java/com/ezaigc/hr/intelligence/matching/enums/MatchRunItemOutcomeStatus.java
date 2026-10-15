package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 单个候选人的匹配运行结果状态。 */
@Getter
@RequiredArgsConstructor
public enum MatchRunItemOutcomeStatus implements CodeDescEnum {
  SUCCEEDED("已成功"),
  FAILED("已失败");

  private final String desc;

  public static MatchRunItemOutcomeStatus fromCode(String code) {
    return MatchRunItemOutcomeStatus.valueOf(code);
  }
}
