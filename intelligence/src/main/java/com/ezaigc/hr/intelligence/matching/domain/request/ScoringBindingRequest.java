package com.ezaigc.hr.intelligence.matching.domain.request;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import lombok.Data;

@Data
public class ScoringBindingRequest {
  /** 留空表示解除关联，不删除方案和历史结果。 */
  @OptionalPlatformIdentifier private String planVersionId;
  @ValidResourceVersion private String version;
}
