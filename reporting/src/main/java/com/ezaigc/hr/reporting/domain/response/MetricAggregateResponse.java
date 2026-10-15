package com.ezaigc.hr.reporting.domain.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetricAggregateResponse {
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
