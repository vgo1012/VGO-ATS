package com.ezaigc.hr.recruiting.submission.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SendSnapshotResponse {
  /** 主键 ID。 */
  private String id;
  /** 客户推荐记录 ID。 */
  private String submissionId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 接收方地址。 */
  private String recipient;
  /** 通知或沟通渠道。 */
  private String channel;
  /** 文件名。 */
  private String fileName;
  /** 内容 SHA-256 摘要。 */
  private String contentSha256;
  /** 发送操作人用户 ID。 */
  private String sentByUserId;
  /** 发送时间。 */
  private Instant sentAt;
}
