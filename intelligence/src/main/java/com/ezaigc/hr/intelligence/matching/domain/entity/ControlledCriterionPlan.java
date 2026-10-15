package com.ezaigc.hr.intelligence.matching.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("matching_controlled_criterion_plan_version")
public class ControlledCriterionPlan extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 职位描述（JD）版本 ID。 */
  @TableField("jd_version_id")
  private String jdVersionId;
  /** 业务版本号。 */
  @TableField("version_no")
  private int versionNo;
  /** 方案允许的最大附加分。 */
  @TableField("plan_max_bonus_points")
  private int planMaxBonusPoints;

}
