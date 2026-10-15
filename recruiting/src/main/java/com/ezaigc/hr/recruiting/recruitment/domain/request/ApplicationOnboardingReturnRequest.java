package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplicationOnboardingReturnRequest {
  /** 未填写时按“入职库退回”处理。 */
  private @Size(max = 32) @Pattern(regexp = INPUT_PATTERN) String reasonCategory;

  private @Size(max = 460) String reasonDetail;
}
