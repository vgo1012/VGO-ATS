package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD 版本状态。 */
@Getter
@RequiredArgsConstructor
public enum JdVersionStatus implements CodeDescEnum {
  DRAFT("草稿"),
  PUBLISHED("已发布"),
  SUPERSEDED("已被新版本取代");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
