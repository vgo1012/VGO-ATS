package com.ezaigc.hr.intelligence.matching.domain.entity;

import lombok.Data;

/** 同依据可见候选人去重后的分级统计。 */
@Data
public class MatchJobResultSummaryRow {
  private long total;
  private long strong;
  private long consider;
  private long reject;
  private long missing;
}
