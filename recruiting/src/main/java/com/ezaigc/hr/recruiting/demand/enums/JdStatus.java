package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD 主记录状态。 */
@Getter
@RequiredArgsConstructor
public enum JdStatus implements CodeDescEnum {
  DRAFT("草稿"),
  ACTIVE("启用"),
  CLOSED("关闭");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
