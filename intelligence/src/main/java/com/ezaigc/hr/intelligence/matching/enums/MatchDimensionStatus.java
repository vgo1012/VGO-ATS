package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配维度的证据可计算状态。 */
@Getter
@RequiredArgsConstructor
public enum MatchDimensionStatus implements CodeDescEnum {
  KNOWN("证据可计算"),
  UNKNOWN("信息不足");

  private final String desc;

  public static MatchDimensionStatus fromCode(String code) {
    return MatchDimensionStatus.valueOf(code);
  }
}
