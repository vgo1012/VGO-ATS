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
@TableName("interview_notification_attempt")
public class NotificationAttempt extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 通知意图 ID。 */
  @TableField("notification_intent_id")
  private String notificationIntentId;
  /** 尝试类型。 */
  @TableField("attempt_type")
  private String attemptType;
  /** 通知或沟通渠道。 */
  @TableField("channel")
  private String channel;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 外部消息 ID。 */
  @TableField("external_message_id")
  private String externalMessageId;
  /** 错误编码。 */
  @TableField("error_code")
  private String errorCode;
  /** 错误详情。 */
  @TableField("error_detail")
  private String errorDetail;
  /** 操作人用户 ID。 */
  @TableField("operated_by_user_id")
  private String operatedByUserId;
  /** 事件发生时间。 */
  @TableField("occurred_at")
  private Instant occurredAt;
}
