package com.ezaigc.hr.intelligence.matching.domain.response;

import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultSummaryRow;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 当前岗位同依据全部可见结果的候选人数，不受分页或本页搜索影响。 */
@Data
@AllArgsConstructor
public class MatchJobResultSummaryResponse {
  private long total;
  private long strong;
  private long consider;
  private long reject;
  private long missing;

  public static MatchJobResultSummaryResponse from(MatchJobResultSummaryRow row) {
    return new MatchJobResultSummaryResponse(row.getTotal(), row.getStrong(), row.getConsider(),
        row.getReject(), row.getMissing());
  }
}
