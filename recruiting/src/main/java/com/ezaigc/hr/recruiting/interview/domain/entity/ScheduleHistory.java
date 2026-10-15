package com.ezaigc.hr.recruiting.interview.domain.entity;

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
@TableName("interview_schedule_history")
public class ScheduleHistory extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 面试 ID。 */
  @TableField("interview_id")
  private String interviewId;
  /** 操作类型。 */
  @TableField("action_type")
  private String actionType;
  /** 变更前数据快照（JSON）。 */
  @TableField("before_snapshot")
  private String beforeSnapshot;
  /** 变更后数据快照（JSON）。 */
  @TableField("after_snapshot")
  private String afterSnapshot;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 操作人用户 ID。 */
  @TableField("operated_by_user_id")
  private String operatedByUserId;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
}
