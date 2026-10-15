package com.ezaigc.hr.reporting.domain.dto;

import java.time.LocalDate;
import lombok.Data;

/** 按北京时间自然日汇总的真实业务事件。 */
@Data
public class MetricDayDTO {
  private LocalDate date;
  private String eventType;
  private long eventCount;
}
