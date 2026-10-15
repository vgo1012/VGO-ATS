package com.ezaigc.hr.talent.domain.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarDayResponse {
  private LocalDate date;
  private String dayType;
  private String description;
}
