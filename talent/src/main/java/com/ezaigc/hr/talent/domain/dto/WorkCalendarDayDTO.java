package com.ezaigc.hr.talent.domain.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 保存日历所需的日期事实。 */
@Data
@AllArgsConstructor
public class WorkCalendarDayDTO {
  private LocalDate date;
  private String dayType;
  private String description;
}
