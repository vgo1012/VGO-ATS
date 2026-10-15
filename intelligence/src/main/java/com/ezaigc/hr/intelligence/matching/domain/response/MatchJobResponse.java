package com.ezaigc.hr.intelligence.matching.domain.response;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobRow;
import java.time.Instant;
import lombok.Data;
import lombok.AllArgsConstructor;
/** 当前用户可见的岗位版本评分台账；人数去重，次数保留审计原值。 */
@Data
@AllArgsConstructor
public class MatchJobResponse {
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

  public static MatchJobResponse from(MatchJobRow v) {
    return new MatchJobResponse(v.getJdVersionId(), v.getLatestRunId(), v.getLastRunAt(),
        v.getRunCount(), v.getEvaluationCount(), v.getSucceededCount(), v.getFailedCount(),
        v.getCandidateCount(), v.getScoredCandidateCount(), v.getActiveRunCount());
  }
}
