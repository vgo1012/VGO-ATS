package com.ezaigc.hr.recruiting.interview.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewFeedbackRequest {
  private @NotBlank @Size(max = 16) String verdict;
  private @Size(max = 1000) String shortComment;
  private @Size(max = 500) String externalFeedbackSource;
  /** 反馈发生时间。 */
  private Instant feedbackOccurredAt;
  private @NotBlank @Size(max = 16) String status;
  private @NotBlank @Size(max = 500) String changeReason;

}
