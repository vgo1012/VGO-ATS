package com.ezaigc.hr.reporting.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetricProjectionStatus implements CodeDescEnum {
  READY("已完成增量扫描"), LAGGING("增量水位滞后");
  private final String desc;
}
