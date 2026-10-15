package com.ezaigc.hr.recruiting.demand.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
/** 来源记录仅用于追溯，不构成正式招聘申请或评价。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demand_mail_source_message")
public class MailSourceMessageEntity extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;
  /** 职位描述版本 ID。 */
  @TableField("jd_version_id")
  @ToString.Exclude
  private String jdVersionId;
  /** 原始邮件 Message-ID。 */
  @TableField("source_message_id")
  @ToString.Exclude
  private String sourceMessageId;
  /** 收件人范围的邮件标识哈希。 */
  @TableField("message_hash")
  @ToString.Exclude
  private String messageHash;
  /** 邮件正文和来源指纹。 */
  @TableField("payload_hash")
  @ToString.Exclude
  private String payloadHash;
  /** 来源发件邮箱。 */
  @TableField("sender_email")
  @ToString.Exclude
  private String senderEmail;
  /** 当前操作者收件邮箱。 */
  @TableField("recipient_email")
  @ToString.Exclude
  private String recipientEmail;
  /** 邮件主题。 */
  @TableField("subject")
  @ToString.Exclude
  private String subject;
  /** 邮件正文，仅作为来源材料。 */
  @TableField("plain_body")
  @ToString.Exclude
  private String plainBody;
  /** 传输方式，模拟来件固定为 MAIL_SIMULATION。 */
  @TableField("delivery_type")
  @ToString.Exclude
  private String deliveryType;
}
