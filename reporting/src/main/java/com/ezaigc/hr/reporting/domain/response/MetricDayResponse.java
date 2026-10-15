package com.ezaigc.hr.reporting.domain.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetricDayResponse {
  private LocalDate date;
  private String eventType;
  private long eventCount;
}
