package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewFeedbackSummaryResponse {
  /** 主键 ID。 */
  private String id;
  /** 面试 ID。 */
  private String interviewId;
  /** 填写人用户 ID。 */
  private String authorUserId;
  /** 反馈类型。 */
  private String feedbackKind;
  /** 当前状态。 */
  private String status;
  /** 当前业务版本号。 */
  private int currentVersionNo;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}
