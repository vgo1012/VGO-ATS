package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历简单事实类型。 */
@Getter
@RequiredArgsConstructor
public enum ResumeFactType implements CodeDescEnum {
  CREDENTIAL("证书资质"),
  LANGUAGE("语言能力"),
  INDUSTRY_TAG("行业标签"),
  RESPONSIBILITY_TAG("职责标签"),
  UNKNOWN("未归类事实");

  private final String desc;
}
