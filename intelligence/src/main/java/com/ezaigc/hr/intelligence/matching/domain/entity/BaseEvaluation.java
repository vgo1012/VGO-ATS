package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("matching_base_evaluation")
public class BaseEvaluation extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 解析结果缓存键。 */
  @TableField("cache_key")
  private String cacheKey;
  /** JD 事实快照 ID。 */
  @TableField("jd_fact_snapshot_id")
  private String jdFactSnapshotId;
  /** 简历事实快照 ID。 */
  @TableField("resume_fact_snapshot_id")
  private String resumeFactSnapshotId;
  /** 匹配方案版本 ID。 */
  @TableField("plan_version_id")
  private String planVersionId;
  /** 权重方案版本 ID。 */
  @TableField("weight_profile_version_id")
  private String weightProfileVersionId;
  /** 受控条件选择哈希。 */
  @TableField("selected_criterion_hash")
  private String selectedCriterionHash;
  @TableField("evaluated_on")
  private LocalDate evaluatedOn;
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
  /** 本次选择的附加分上限。 */
  @TableField("selected_bonus_cap")
  private int selectedBonusCap;
  /** 各评分维度结果（JSON）。 */
  @TableField("dimensions_json")
  private String dimensionsJson;
  /** 筛选条件（JSON）。 */
  @TableField("criteria_json")
  private String criteriaJson;
  /** 匹配算法版本号。 */
  @TableField("algorithm_version")
  private String algorithmVersion;
}
