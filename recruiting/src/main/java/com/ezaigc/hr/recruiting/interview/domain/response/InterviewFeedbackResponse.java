package com.ezaigc.hr.recruiting.interview.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewFeedbackResponse {
  /** 反馈。 */
  private InterviewFeedbackSummaryResponse feedback;
  /** 当前版本。 */
  private InterviewFeedbackVersionResponse currentVersion;
}
