package com.ezaigc.hr.talent.domain.entity;

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
@TableName("talent_resume_skill_fact")
public class SkillFact extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 事实快照 ID。 */
  @TableField("fact_snapshot_id")
  private String factSnapshotId;
  /** 任务条目业务键。 */
  @TableField("item_key")
  private String itemKey;
  /** 技能名称。 */
  @TableField("skill_name")
  private String skillName;
  /** 等级名称。 */
  @TableField("level_name")
  private String levelName;
  /** 年限值。 */
  @TableField("years_value")
  private BigDecimal yearsValue;
}
