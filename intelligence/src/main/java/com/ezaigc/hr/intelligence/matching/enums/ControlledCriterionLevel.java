package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 受控条件等级 枚举，限定允许使用的业务取值。 */
@Getter
@RequiredArgsConstructor
public enum ControlledCriterionLevel implements CodeDescEnum {
  MUST("必须满足"),
  PREFER("优先条件"),
  DISPLAY_ONLY("仅展示");

  /** 枚举值中文说明。 */
  private final String desc;

  /** 根据 业务编码 创建当前对象。 */
  public static ControlledCriterionLevel fromCode(String code) {
    return ControlledCriterionLevel.valueOf(code);
  }
}
