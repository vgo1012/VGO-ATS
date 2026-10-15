package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配配置状态。 */
@Getter
@RequiredArgsConstructor
public enum MatchingConfigurationStatus implements CodeDescEnum {
  DRAFT("草稿"),
  ACTIVE("启用"),
  DISABLED("停用"),
  SUPERSEDED("已被新版本取代");

  private final String desc;
}
