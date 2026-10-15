package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewNotificationResponse {
  /** 主键 ID。 */
  private String id;
  /** 面试 ID。 */
  private String interviewId;
  /** 通知类型。 */
  private String notificationType;
  /** 接收方类型。 */
  private String recipientType;
  /** 接收方引用。 */
  private String recipientReference;
  /** 通知或沟通渠道。 */
  private String channel;
  /** 模板版本 ID。 */
  private String templateVersionId;
  /** 渲染后主题。 */
  private String renderedSubject;
  /** 渲染后正文。 */
  private String renderedBody;
  /** 当前状态。 */
  private String status;
  /** 外部消息 ID。 */
  private String externalMessageId;
  /** 最近错误编码。 */
  private String lastErrorCode;
  /** 最近错误详情。 */
  private String lastErrorDetail;
  /** 已重试次数。 */
  private int retryCount;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}
