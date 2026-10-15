package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_APPLICATION_DECISION_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDecisionRequest {
  /** 人工决定。 */
  private @NotBlank
      @Size(max = MAX_APPLICATION_DECISION_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String decision;
  private @NotBlank @Size(max = 500) String reason;
  private @Size(min = 26, max = 26) String clientSubmissionId;

}
