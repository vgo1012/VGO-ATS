package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD 编号维度字典类型。 */
@Getter
@RequiredArgsConstructor
public enum JdCodeOptionKind implements CodeDescEnum {
  JOB_TYPE("岗位类型"),
  REGION("工作区域");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
