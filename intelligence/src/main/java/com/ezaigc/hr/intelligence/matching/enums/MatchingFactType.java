package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配引擎消费的简历事实类型。 */
@Getter
@RequiredArgsConstructor
public enum MatchingFactType implements CodeDescEnum {
  RESPONSIBILITY_TAG("职责标签"),
  INDUSTRY_TAG("行业标签"),
  CREDENTIAL("证书资质"),
  LANGUAGE("语言能力");

  private final String desc;
}
