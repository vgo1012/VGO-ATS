package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_COMMUNICATION_CHANNEL_LENGTH;

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
public class ApplicationFollowUpRequest {

  /** 通知或沟通渠道。 */
  private @NotBlank
      @Size(max = MAX_COMMUNICATION_CHANNEL_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String channel;
  private @NotBlank @Size(max = 1000) String summary;
  private @NotBlank @Size(max = 500) String nextPlan;
  /** 联系时间。 */
  private Instant contactedAt;

}
