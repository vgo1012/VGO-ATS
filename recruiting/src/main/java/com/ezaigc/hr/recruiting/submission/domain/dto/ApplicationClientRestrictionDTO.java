package com.ezaigc.hr.recruiting.submission.domain.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class ApplicationClientRestrictionDTO {
  private String clientId;
  private String reasonDetail;
  private String sourceChannel;
  private String evidenceReference;
  private Instant expiresAt;
  private Instant sourceOccurredAt;
}
