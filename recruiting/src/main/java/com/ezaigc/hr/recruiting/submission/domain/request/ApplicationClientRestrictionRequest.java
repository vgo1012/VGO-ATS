package com.ezaigc.hr.recruiting.submission.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Data;

@Data
public class ApplicationClientRestrictionRequest {
  @PlatformIdentifier private String clientId;
  @NotBlank @Size(max = 1000) private String reasonDetail;
  @NotBlank @Size(max = 40) private String sourceChannel;
  @Size(max = 1000) private String evidenceReference;
  private Instant expiresAt;
  private Instant sourceOccurredAt;
}
