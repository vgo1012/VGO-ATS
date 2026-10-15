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
@TableName("recruitment_candidate_engagement_lock_history")
public class LockHistory extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  @TableField("lock_id")
  private String lockId;
  /** 候选人 ID。 */
  @TableField("candidate_id")
  private String candidateId;
  /** 操作编码。 */
  @TableField("action_code")
  private String actionCode;
  /** 变更前负责人用户 ID。 */
  @TableField("from_owner_user_id")
  private String fromOwnerUserId;
  /** 变更后负责人用户 ID。 */
  @TableField("to_owner_user_id")
  private String toOwnerUserId;
  /** 进度类型。 */
  @TableField("progress_type")
  private String progressType;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 实际操作人用户 ID。 */
  @TableField("actor_user_id")
  private String actorUserId;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
}
