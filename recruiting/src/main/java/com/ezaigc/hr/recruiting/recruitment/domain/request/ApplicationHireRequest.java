package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_OFFER_STATUS_LENGTH;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplicationHireRequest {
  /** 确认录用时人工选择的 Offer 进展。 */
  private @NotBlank @Size(max = MAX_OFFER_STATUS_LENGTH) @Pattern(regexp = INPUT_PATTERN)
      String offerStatus;

  private @NotBlank @Size(max = 500) String reason;
}
