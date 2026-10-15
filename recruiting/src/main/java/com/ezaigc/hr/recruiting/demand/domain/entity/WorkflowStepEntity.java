package com.ezaigc.hr.recruiting.demand.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_workflow_step_definition")
public class WorkflowStepEntity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 模板版本 ID。 */
  @TableField("template_version_id")
  private String templateVersionId;
  /** 步骤编码。 */
  @TableField("step_code")
  private String stepCode;
  /** 顺序号。 */
  @TableField("sequence_no")
  private Integer sequenceNo;
  /** 候选人是否可以跳过该步骤。 */
  @TableField("candidate_optional")
  private Boolean candidateOptional;
  /** 默认跟进后续渠道。 */
  @TableField("default_follow_up_channel")
  private String defaultFollowUpChannel;

}
