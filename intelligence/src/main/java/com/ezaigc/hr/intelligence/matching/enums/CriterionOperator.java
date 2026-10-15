package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import com.ezaigc.hr.common.core.util.CommonUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 受控条件比较运算符。 */
@Getter
@RequiredArgsConstructor
public enum CriterionOperator implements CodeDescEnum {
  EQ("等于"),
  IN("属于集合"),
  BETWEEN("区间"),
  GTE("大于等于"),
  LTE("小于等于"),
  CONTAINS("包含");

  private final String desc;
  public boolean isRangeOperator() {
    return this == LTE || this == GTE || this == BETWEEN;
  }

  public static CriterionOperator fromCode(String code) {
    return CriterionOperator.valueOf(CommonUtils.upperOrEmpty(code));
  }
}
