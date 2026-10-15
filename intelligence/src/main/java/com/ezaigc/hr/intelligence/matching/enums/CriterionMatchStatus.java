package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 受控条件与简历事实的匹配状态。 */
@Getter
@RequiredArgsConstructor
public enum CriterionMatchStatus implements CodeDescEnum {
  PASS("满足"),
  UNKNOWN("未知"),
  FAIL("不满足");

  private final String desc;

  public static CriterionMatchStatus fromCode(String code) {
    return CriterionMatchStatus.valueOf(code);
  }
}
