package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewFeedbackVersionResponse {
  /** 主键 ID。 */
  private String id;
  /** 反馈记录 ID。 */
  private String feedbackId;
  /** 业务版本号。 */
  private int versionNo;
  /** 评估结论。 */
  private String verdict;
  /** 简短说明。 */
  private String shortComment;
  /** 外部反馈来源。 */
  private String externalFeedbackSource;
  /** 反馈发生时间。 */
  private Instant feedbackOccurredAt;
  /** 变更原因。 */
  private String changeReason;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
}
