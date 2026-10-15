package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarRevisionResponse {
  private String id;
  private int year;
  private int revision;
  private String status;
  private String version;
  private String sourceNote;
  private String publishedBy;
  private Instant publishedAt;
  private String recalculationId;
  private String publishReason;
  private String publishedName;
  private List<WorkCalendarDayResponse> days;
}
