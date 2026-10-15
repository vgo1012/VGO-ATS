package com.ezaigc.hr.recruiting.submission.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 客户禁推事实记录状态。 */
@Getter
@RequiredArgsConstructor
public enum ClientRestrictionStatus implements CodeDescEnum {
  ACTIVE("生效"),
  CORRECTED("已被更正记录取代"),
  DISABLED("停用");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
