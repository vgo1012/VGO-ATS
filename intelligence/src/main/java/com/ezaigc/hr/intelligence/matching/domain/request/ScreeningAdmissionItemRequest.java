package com.ezaigc.hr.intelligence.matching.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import lombok.Data;

/** 单条智能筛选准入请求，服务端会重新核对默认评分运行与三元组。 */
@Data
public class ScreeningAdmissionItemRequest {
  private @PlatformIdentifier String candidateId;
  private @PlatformIdentifier String resumeVersionId;
  private @PlatformIdentifier String jdVersionId;
  private @PlatformIdentifier String defaultMatchRunId;
}
