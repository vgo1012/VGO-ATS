package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SharedCandidateReclaimItemRequest {
  @PlatformIdentifier private String candidateId;
  @PlatformIdentifier private String resumeVersionId;
  @PlatformIdentifier private String scoreBindingId;
  @NotNull private Long candidateVersion;
}
