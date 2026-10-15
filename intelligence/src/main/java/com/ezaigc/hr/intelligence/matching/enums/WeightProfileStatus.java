package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配权重方案状态。 */
@Getter
@RequiredArgsConstructor
public enum WeightProfileStatus implements CodeDescEnum {
  ACTIVE("启用"),
  DISABLED("停用");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
