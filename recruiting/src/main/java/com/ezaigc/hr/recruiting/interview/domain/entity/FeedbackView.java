package com.ezaigc.hr.recruiting.interview.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackView {
  /** 反馈。 */
  private Feedback feedback;
  /** 当前版本。 */
  private FeedbackVersion currentVersion;
}
