package com.ezaigc.hr.recruiting.interview.domain.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewFeedbackDTO {
  /** 评估结论。 */
  private String verdict;
  /** 简短说明。 */
  private String shortComment;
  /** 外部反馈来源。 */
  private String externalFeedbackSource;
  /** 反馈发生时间。 */
  private Instant feedbackOccurredAt;
  /** 当前状态。 */
  private String status;
  /** 变更原因。 */
  private String changeReason;
}
