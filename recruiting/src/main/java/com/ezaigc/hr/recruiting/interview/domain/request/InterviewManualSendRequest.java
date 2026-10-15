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
public class InterviewManualSendRequest {
  private @NotBlank @Size(max = 32) String channel;
  private @Size(max = 255) String externalMessageId;
  /** 发送时间。 */
  private Instant sentAt;

}
