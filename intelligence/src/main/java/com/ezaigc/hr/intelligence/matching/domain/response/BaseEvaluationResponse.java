package com.ezaigc.hr.intelligence.matching.domain.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/** 可审计且不可变的基础匹配评估响应。 */
@Data
@AllArgsConstructor
public class BaseEvaluationResponse {
  private String baseEvaluationId;
  /** JD 事实快照 ID。 */
  private String jdFactSnapshotId;
  /** 简历事实快照 ID。 */
  private String resumeFactSnapshotId;
  /** 专业匹配得分。 */
  private BigDecimal professionalScore;
  /** 附加项得分。 */
  private int bonusScore;
  /** 综合参考分。 */
  private BigDecimal referenceScore;
  private String mustTier;
  /** 维度列表。 */
  private JsonNode dimensions;
  /** 条件。 */
  private JsonNode criteria;
  /** 匹配算法版本号。 */
  private String algorithmVersion;
  /** 创建时间。 */
  private Instant createTime;
}
