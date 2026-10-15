package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OnboardingProcessResponse {
  private String applicationId;
  private String status;
  private Instant startedAt;
  private Instant actualOnboardedAt;
  private Instant closedAt;
  private long elapsedSeconds;
}
