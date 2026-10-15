package com.ezaigc.hr.recruiting.recruitment.domain.request;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationOnboardingRequest {
  /** 入职时间。 */
  private Instant onboardedAt;
  private @Size(max = 120) String sourceReference;
  private @Size(max = 500) String reason;

}
