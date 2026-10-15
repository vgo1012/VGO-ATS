package com.ezaigc.hr.intelligence.matching.domain.response;
import java.time.Instant;
import lombok.Data;
import lombok.AllArgsConstructor;
/** 汇总行始终携带真实来源运行；推荐、证据与人工反馈仍使用原结果 ID。 */
@Data
@AllArgsConstructor
public class MatchJobResultResponse {
  private String runId;
  private Instant scoredAt;
  private MatchResultResponse result;
}
