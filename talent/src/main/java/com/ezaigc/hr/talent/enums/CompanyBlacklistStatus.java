package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 公司黑名单事实记录状态。 */
@Getter
@RequiredArgsConstructor
public enum CompanyBlacklistStatus implements CodeDescEnum {
  ACTIVE("生效"),
  CORRECTED("已被更正记录取代"),
  DISABLED("停用");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
