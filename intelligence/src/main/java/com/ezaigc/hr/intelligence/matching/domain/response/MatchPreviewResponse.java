package com.ezaigc.hr.intelligence.matching.domain.response;

import com.ezaigc.hr.intelligence.matching.domain.dto.MatchCriterionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchDimensionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.AiAssessmentMetadataDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchPreviewResponse {
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
  private String algorithmVersion;
  private AiAssessmentMetadataDTO aiAssessment;
}
