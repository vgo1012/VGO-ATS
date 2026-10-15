package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarPreviewResponse {
  private String previewId;
  private String expectedVersion;
  private String publishedRevisionId;
  private List<WorkCalendarDayResponse> changedDates;
  private long upgradeCount;
  private long downgradeCount;
  private long shareCount;
  private List<Integer> missingYears;
  private Instant expiresAt;
  private boolean sharedIrreversible;
}
