package com.ezaigc.hr.recruiting.recruitment.domain.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Data;

@Data
public class ApplicationOnboardingReviewRequest {
  private @NotNull Instant onboardedAt;
  private @Size(max = 500) String reason;
}
