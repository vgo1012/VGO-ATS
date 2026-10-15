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
public class ApplicationCommunicationRequest {
  /** 通知或沟通渠道。 */
  private @Size(max = MAX_COMMUNICATION_CHANNEL_LENGTH)
      @Pattern(regexp = INPUT_PATTERN) String channel;
  private @Size(max = 1000) String summary;
  private @NotBlank @Size(max = 32) String result;
  private @Size(max = 500) String nextPlan;
  /** 沟通时间。 */
  private Instant communicatedAt;

}
