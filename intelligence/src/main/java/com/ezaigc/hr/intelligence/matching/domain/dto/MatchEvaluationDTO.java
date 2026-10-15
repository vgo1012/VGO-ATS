package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MatchEvaluationDTO {
  /** 新评分为 AI 原始判断；历史 JSON 缺失此字段时保留旧算法语义。 */
  private String algorithmVersion;
  private AiAssessmentMetadataDTO aiAssessment;
  /** JD 事实快照 ID。 */
  private String jdFactSnapshotId;
  /** 简历事实快照 ID。 */
  private String resumeFactSnapshotId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  private LocalDate evaluatedOn;
  /** 专业匹配得分。 */
  private BigDecimal professionalScore;
  /** 附加项得分。 */
  private int bonusScore;
  /** 综合参考分。 */
  private BigDecimal referenceScore;
  private String mustTier;
  /** 本次选择的附加分上限。 */
  private int selectedBonusCap;
  /** 维度列表。 */
  private List<MatchDimensionResultDTO> dimensions;
  /** 条件。 */
  private List<MatchCriterionResultDTO> criteria;

  public MatchEvaluationDTO(
      String jdFactSnapshotId,
      String resumeFactSnapshotId,
      String candidateId,
      String resumeVersionId,
      LocalDate evaluatedOn,
      BigDecimal professionalScore,
      int bonusScore,
      BigDecimal referenceScore,
      String mustTier,
      int selectedBonusCap,
      List<MatchDimensionResultDTO> dimensions,
      List<MatchCriterionResultDTO> criteria) {
    this.jdFactSnapshotId = jdFactSnapshotId;
    this.resumeFactSnapshotId = resumeFactSnapshotId;
    this.candidateId = candidateId;
    this.resumeVersionId = resumeVersionId;
    this.evaluatedOn = evaluatedOn;
    this.professionalScore = professionalScore;
    this.bonusScore = bonusScore;
    this.referenceScore = referenceScore;
    this.mustTier = mustTier;
    this.selectedBonusCap = selectedBonusCap;
    this.dimensions = List.copyOf(dimensions);
    this.criteria = List.copyOf(criteria);
  }
}
