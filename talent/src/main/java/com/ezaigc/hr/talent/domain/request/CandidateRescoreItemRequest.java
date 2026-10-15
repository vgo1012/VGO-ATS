package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import lombok.Data;

@Data
public class CandidateRescoreItemRequest {
  @PlatformIdentifier private String candidateId;
  @PlatformIdentifier private String resumeVersionId;
}
