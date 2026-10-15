package com.ezaigc.hr.intelligence.matching.domain.request;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import lombok.Data;

/** 精确准入记录及页面读取的技术版本，防止旧页面撤销重新准入。 */
@Data
public class ScreeningReturnItemRequest {
  private @PlatformIdentifier String admissionId;
  private @ValidResourceVersion String expectedVersion;
}
