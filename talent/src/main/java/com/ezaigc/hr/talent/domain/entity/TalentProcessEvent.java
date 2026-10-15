package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("talent_process_event")
public class TalentProcessEvent extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 事件类型。 */
  @TableField("event_type")
  private String eventType;
  /** 实际操作人用户 ID。 */
  @TableField("actor_user_id")
  private String actorUserId;
  /** 目标类型。 */
  @TableField("target_type")
  private String targetType;
  /** 目标记录 ID。 */
  @TableField("target_id")
  private String targetId;
  /** 操作原因。 */
  @TableField("reason")
  private String reason;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
}
