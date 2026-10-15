package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("matching_controlled_criterion_definition")
public class ControlledCriterion extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 匹配方案版本 ID。 */
  @TableField("plan_version_id")
  private String planVersionId;
  /** 受控条件编码。 */
  @TableField("criterion_code")
  private String criterionCode;
  /** 条件满足级别。 */
  @TableField("level")
  private String level;
  /** 操作符。 */
  @TableField("operator")
  private String operator;
  /** 期望值（JSON）。 */
  @TableField("expected_value_json")
  private String expectedValueJson;
  /** 附加分值。 */
  @TableField("bonus_points")
  private int bonusPoints;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 证据使用策略。 */
  @TableField("evidence_policy")
  private String evidencePolicy;
  /** 是否允许 HR 选择。 */
  @TableField("selectable_by_hr")
  private boolean selectableByHr;
  /** 顺序号。 */
  @TableField("sequence_no")
  private int sequenceNo;
}
