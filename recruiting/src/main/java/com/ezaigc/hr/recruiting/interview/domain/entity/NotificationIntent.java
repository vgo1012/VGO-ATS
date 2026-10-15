package com.ezaigc.hr.recruiting.interview.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("interview_notification_intent")
public class NotificationIntent extends StandardPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 面试 ID。 */
  @TableField("interview_id")
  private String interviewId;
  /** 通知类型。 */
  @TableField("notification_type")
  private String notificationType;
  /** 接收方类型。 */
  @TableField("recipient_type")
  private String recipientType;
  /** 接收方引用。 */
  @TableField("recipient_reference")
  private String recipientReference;
  /** 通知或沟通渠道。 */
  @TableField("channel")
  private String channel;
  /** 模板版本 ID。 */
  @TableField("template_version_id")
  private String templateVersionId;
  /** 渲染后主题。 */
  @TableField("rendered_subject")
  private String renderedSubject;
  /** 渲染后正文。 */
  @TableField("rendered_body")
  private String renderedBody;
  /** 当前状态。 */
  @TableField("status")
  private String status;
  /** 外部消息 ID。 */
  @TableField("external_message_id")
  private String externalMessageId;
  /** 最近错误编码。 */
  @TableField("last_error_code")
  private String lastErrorCode;
  /** 最近错误详情。 */
  @TableField("last_error_detail")
  private String lastErrorDetail;
  /** 已重试次数。 */
  @TableField("retry_count")
  private int retryCount;
  /** 技术乐观锁版本号。 */
  @TableField("version")
  @Version private long version;

}
