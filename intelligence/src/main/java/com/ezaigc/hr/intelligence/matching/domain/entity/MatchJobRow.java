package com.ezaigc.hr.intelligence.matching.domain.entity;
import java.time.Instant;
import lombok.Data;
/** 岗位评分台账只读投影，人数与执行次数分别统计。 */
@Data
public class MatchJobRow {
  private String jdVersionId;
  private String latestRunId;
  private Instant lastRunAt;
  private long runCount;
  private long evaluationCount;
  private long succeededCount;
  private long failedCount;
  private long candidateCount;
  private long scoredCandidateCount;
  private long activeRunCount;
}
