package com.ezaigc.hr.recruiting.submission.domain.entity;

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
@TableName("submission_send_snapshot")
public class SendSnapshot extends StandardVersionedPersistenceFields {
  /** 主键 ID。 */
  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  /** 客户推荐记录 ID。 */
  @TableField("submission_id")
  private String submissionId;
  /** 简历版本 ID。 */
  @TableField("resume_version_id")
  private String resumeVersionId;
  /** 接收方地址。 */
  @TableField("recipient")
  private String recipient;
  /** 通知或沟通渠道。 */
  @TableField("channel")
  private String channel;
  /** 文件名。 */
  @TableField("file_name")
  private String fileName;
  /** 内容 SHA-256 摘要。 */
  @TableField("content_sha256")
  private String contentSha256;
  /** 发送操作人用户 ID。 */
  @TableField("sent_by_user_id")
  private String sentByUserId;
  /** 发送时间。 */
  @TableField("sent_at")
  private Instant sentAt;
}
