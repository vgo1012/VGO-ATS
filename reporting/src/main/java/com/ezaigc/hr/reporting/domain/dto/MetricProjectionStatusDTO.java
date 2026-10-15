package com.ezaigc.hr.reporting.domain.dto;

import java.time.Instant;
import lombok.Value;

/** 更新时间是持久采集周期水位，不能用报表请求时间代替。 */
@Value
public class MetricProjectionStatusDTO {
  Instant dataUpdatedAt;
  Instant reconciliationUpdatedAt;
}
