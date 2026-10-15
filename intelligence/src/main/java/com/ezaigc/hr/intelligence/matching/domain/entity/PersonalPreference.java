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
@TableName("matching_personal_prefer_selection_version")
public class PersonalPreference extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 业务负责人用户 ID。 */
  @TableField("owner_user_id")
  private String ownerUserId;
  /** 匹配方案版本 ID。 */
  @TableField("plan_version_id")
  private String planVersionId;
  /** 权重方案版本 ID。 */
  @TableField("weight_profile_version_id")
  private String weightProfileVersionId;
  /** 业务版本号。 */
  @TableField("version_no")
  private int versionNo;
  /** 已选条件定义 ID 列表（JSON）。 */
  @TableField("selected_definition_ids_json")
  private String selectedDefinitionIdsJson;
}
