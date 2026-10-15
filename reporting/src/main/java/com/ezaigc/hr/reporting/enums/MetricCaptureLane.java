package com.ezaigc.hr.reporting.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 指标投影使用独立于审计的增量与循环补偿通道。 */
@Getter
@RequiredArgsConstructor
public enum MetricCaptureLane implements CodeDescEnum {
  INCREMENTAL("增量采集"), RECONCILIATION("循环补偿");
  private final String desc;
}
