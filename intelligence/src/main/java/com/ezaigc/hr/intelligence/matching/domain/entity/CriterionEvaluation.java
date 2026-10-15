package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 一次基础评价中的受控条件判定快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("matching_criterion_evaluation")
public class CriterionEvaluation extends StandardVersionedPersistenceFields {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("base_evaluation_id")
  private String baseEvaluationId;

  @TableField("definition_id")
  private String definitionId;

  @TableField("criterion_code")
  private String criterionCode;

  @TableField("level")
  private String level;

  @TableField("evaluation_status")
  private String evaluationStatus;

  @TableField("selected")
  private boolean selected;

  @TableField("configured_bonus")
  private int configuredBonus;

  @TableField("awarded_bonus")
  private int awardedBonus;

  /** 实际事实值 JSON；排除自动字符串输出，避免候选人事实进入诊断日志。 */
  @TableField("actual_json")
  @ToString.Exclude
  private String actualJson;

  /** 期望配置值 JSON。 */
  @TableField("expected_json")
  @ToString.Exclude
  private String expectedJson;
}
