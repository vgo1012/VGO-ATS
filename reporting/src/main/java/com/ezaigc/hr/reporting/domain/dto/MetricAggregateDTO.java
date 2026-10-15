package com.ezaigc.hr.reporting.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

/** 指标聚合查询结果。 */
@Data
public class MetricAggregateDTO {
  /** 事件类型。 */
  private String eventType;
  /** 事件数量。 */
  private long eventCount;
  /** 事件值。 */
  private BigDecimal eventValue;
  /** 首次发生时间。 */
  private Instant firstOccurredAt;
  /** 最近发生时间。 */
  private Instant lastOccurredAt;
}
