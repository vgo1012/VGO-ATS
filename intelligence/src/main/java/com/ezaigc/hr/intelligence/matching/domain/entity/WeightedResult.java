package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("matching_weighted_result")
public class WeightedResult extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 匹配运行任务 ID。 */
  @TableField("match_run_id")
  private String matchRunId;
  @TableField("base_evaluation_id")
  private String baseEvaluationId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 专业匹配得分。 */
  @TableField("professional_score")
  private BigDecimal professionalScore;
  /** 附加项得分。 */
  @TableField("bonus_score")
  private int bonusScore;
  /** 综合参考分。 */
  @TableField("reference_score")
  private BigDecimal referenceScore;
  @TableField("must_tier")
  private String mustTier;
  /** 稳定排序名次。 */
  @TableField("stable_rank")
  private Integer stableRank;
  /** 处理结果（JSON）。 */
  @TableField("result_json")
  private String resultJson;

}
