package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_APPLICATION_OUTCOME_LENGTH;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_OFFER_STATUS_LENGTH;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_OFFER_TERMINATION_REASON_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationOfferRequest {
  /** 当前状态。 */
  private @NotBlank
      @Size(max = MAX_OFFER_STATUS_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String status;
  /** 终止原因。 */
  private @Size(max = MAX_OFFER_TERMINATION_REASON_LENGTH) @Pattern(regexp = INPUT_PATTERN)
      String terminationReason;
  /** 应聘流程结果。 */
  private @Size(max = MAX_APPLICATION_OUTCOME_LENGTH) @Pattern(regexp = INPUT_PATTERN)
      String applicationOutcome;
  private @NotBlank @Size(max = 500) String reason;
  /** 事件发生时间。 */
  private Instant occurredAt;
  private @Size(max = 120) String sourceReference;
  private @Size(max = 120) String sourceStatus;

}
