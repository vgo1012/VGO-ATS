package com.ezaigc.hr.intelligence.matching.domain.entity;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
/** 保留原运行和证据主键的去重结果，workspaceRank 仅用于当前汇总分页。 */
@Data
public class MatchJobResultRow {
  private String id;
  private String matchRunId;
  private String baseEvaluationId;
  private String candidateId;
  private String resumeVersionId;
  private BigDecimal professionalScore;
  private int bonusScore;
  private BigDecimal referenceScore;
  private String mustTier;
  private Integer stableRank;
  private String resultJson;
  private Instant createTime;
  private Instant updateTime;
  private long workspaceRank;
}
