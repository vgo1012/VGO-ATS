package com.ezaigc.hr.intelligence.matching.domain.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/** 单个候选人的持久化匹配结果。 */
@Data
@AllArgsConstructor
public class MatchResultResponse {
  private String resultId;
  private String baseEvaluationId;
  /** 候选人 ID。 */
  private String candidateId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 专业匹配得分。 */
  private BigDecimal professionalScore;
  /** 附加项得分。 */
  private int bonusScore;
  /** 综合参考分。 */
  private BigDecimal referenceScore;
  private String mustTier;
  /** 稳定排序名次。 */
  private Integer stableRank;
  /** 详情。 */
  private JsonNode details;
}
