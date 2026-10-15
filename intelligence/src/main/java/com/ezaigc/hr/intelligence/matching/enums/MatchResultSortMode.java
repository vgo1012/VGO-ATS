package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配结果排序方式。 */
@Getter
@RequiredArgsConstructor
public enum MatchResultSortMode implements CodeDescEnum {
  REFERENCE_SCORE("综合参考分"),
  PROFESSIONAL_SCORE("专业基础分"),
  BONUS_SCORE("附加分");

  private final String desc;
  public static MatchResultSortMode fromCode(String code) {
    return MatchResultSortMode.valueOf(code);
  }
}
