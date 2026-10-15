package com.ezaigc.hr.recruiting.recruitment.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import lombok.Data;

@Data
public class ApplicationBatchItemRequest {
  @PlatformIdentifier private String candidateId;
  @PlatformIdentifier private String resumeVersionId;
}
