package com.ezaigc.hr.recruiting.recruitment.domain.entity;

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
@TableName("recruitment_workflow_path_change_record")
public class PathChange extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 变更类型。 */
  @TableField("change_type")
  private String changeType;
  @TableField("before_step_ids_json")
  private String beforeStepIdsJson;
  @TableField("after_step_ids_json")
  private String afterStepIdsJson;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 应聘流程版本。 */
  @TableField("application_version")
  private long applicationVersion;
  /** 操作人用户 ID。 */
  @TableField("operated_by_user_id")
  private String operatedByUserId;
}
