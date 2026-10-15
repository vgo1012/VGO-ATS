package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_workflow_follow_up_record")
public class FollowUp extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 应聘流程 ID。 */
  @TableField("application_id")
  private String applicationId;
  /** 招聘流程步骤 ID。 */
  @TableField("workflow_step_id")
  private String workflowStepId;
  /** 通知或沟通渠道。 */
  @TableField("channel")
  private String channel;
  /** 摘要。 */
  @TableField("summary")
  private String summary;
  /** 下一步方案。 */
  @TableField("next_plan")
  private String nextPlan;
  /** 联系时间。 */
  @TableField("contacted_at")
  private Instant contactedAt;
  /** 记录人用户 ID。 */
  @TableField("recorded_by_user_id")
  private String recordedByUserId;
}
