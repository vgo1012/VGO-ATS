package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 受控条件编码 枚举，限定允许使用的业务取值。 */
@Getter
@RequiredArgsConstructor
public enum ControlledCriterionCode implements CodeDescEnum {
  AGE("年龄"),
  GENDER("性别"),
  EDUCATION("学历"),
  CERTIFICATE("证书"),
  LANGUAGE("语言");

  /** 枚举值中文说明。 */
  private final String desc;

  /** 根据 业务编码 创建当前对象。 */
  public static ControlledCriterionCode fromCode(String code) {
    return ControlledCriterionCode.valueOf(code);
  }
}
