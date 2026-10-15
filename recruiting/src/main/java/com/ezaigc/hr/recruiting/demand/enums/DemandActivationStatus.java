package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 客户、项目和岗位主数据的启停状态。 */
@Getter
@RequiredArgsConstructor
public enum DemandActivationStatus implements CodeDescEnum {
  ACTIVE("启用"),
  DISABLED("停用");

  private final String desc;
}
