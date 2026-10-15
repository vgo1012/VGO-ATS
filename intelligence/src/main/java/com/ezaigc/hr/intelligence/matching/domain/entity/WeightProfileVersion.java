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
@TableName("matching_weight_profile_version")
public class WeightProfileVersion extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 权重方案 ID。 */
  @TableField("profile_id")
  private String profileId;
  /** 业务版本号。 */
  @TableField("version_no")
  private int versionNo;
  /** 技能维度权重。 */
  @TableField("skills_weight")
  private int skillsWeight;
  /** 角色维度权重。 */
  @TableField("role_weight")
  private int roleWeight;
  /** 年限维度权重。 */
  @TableField("years_weight")
  private int yearsWeight;
  /** 职责维度权重。 */
  @TableField("responsibility_weight")
  private int responsibilityWeight;
  /** 行业项目维度权重。 */
  @TableField("industry_project_weight")
  private int industryProjectWeight;
  /** 随权重版本冻结的个人筛选设置；历史版本空值表示无附加要求。 */
  @TableField("screening_settings_json")
  private String screeningSettingsJson;
  public WeightProfileVersion(String id, String profileId, int versionNo, int skillsWeight, int roleWeight,
      int yearsWeight, int responsibilityWeight, int industryProjectWeight) {
    this(id, profileId, versionNo, skillsWeight, roleWeight, yearsWeight, responsibilityWeight, industryProjectWeight, null);
  }
}
