package com.ezaigc.hr.talent.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarImpactDTO {
  private String fingerprint;
  private long upgradeCount;
  private long downgradeCount;
  private long shareCount;
  private List<Integer> missingYears;
}
