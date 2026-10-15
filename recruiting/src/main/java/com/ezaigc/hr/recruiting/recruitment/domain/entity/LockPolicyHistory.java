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
@TableName("recruitment_engagement_lock_policy_history")
public class LockPolicyHistory extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 策略键。 */
  @TableField("policy_key")
  private String policyKey;
  /** 上一时长天数。 */
  @TableField("previous_duration_days")
  private int previousDurationDays;
  /** 新时长天数。 */
  @TableField("new_duration_days")
  private int newDurationDays;
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
