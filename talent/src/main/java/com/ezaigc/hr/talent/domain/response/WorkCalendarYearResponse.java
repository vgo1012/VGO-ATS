package com.ezaigc.hr.talent.domain.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarYearResponse {
  private int year;
  private WorkCalendarRevisionResponse published;
  private WorkCalendarRevisionResponse draft;
  private boolean nextYearMissing;
}
