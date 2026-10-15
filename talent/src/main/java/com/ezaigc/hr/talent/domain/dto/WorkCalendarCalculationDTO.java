package com.ezaigc.hr.talent.domain.dto;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarCalculationDTO {
  private Integer elapsedWorkdays;
  private Map<Integer, String> revisionVector;
  private List<Integer> missingYears;
  private List<LocalDate> thresholdDates;
  private boolean settlementComplete;
  /** 参与已结算区间的年度版本中最晚的发布时间；在年度指针锁内读取。 */
  private Instant latestPublishedAt;

  public WorkCalendarCalculationDTO(Integer elapsedWorkdays, Map<Integer, String> revisionVector,
      List<Integer> missingYears, List<LocalDate> thresholdDates, boolean settlementComplete) {
    this(elapsedWorkdays, revisionVector, missingYears, thresholdDates, settlementComplete, null);
  }
}
