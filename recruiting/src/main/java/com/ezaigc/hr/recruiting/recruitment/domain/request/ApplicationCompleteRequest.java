package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_APPLICATION_STEP_RESULT_LENGTH;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCompleteRequest {
  /** 结果。 */
  private @NotBlank
      @Size(max = MAX_APPLICATION_STEP_RESULT_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String result;
  private @Size(max = 500) String reason;
  private @Size(min = 26, max = 26) String clientSubmissionId;
  private @ValidResourceVersion String version;
  private @Size(max = 32) String channel;
  private @Size(max = 500) String nextPlan;

  public ApplicationCompleteRequest(String result, String reason, String clientSubmissionId, String version) {
    this(result, reason, clientSubmissionId, version, null, null);
  }

}
