package com.ezaigc.hr.intelligence.search.domain.request;

import jakarta.validation.constraints.AssertTrue;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新纳管使用 run 版本，已有资源核对使用资源版本；确认不等于系统能证明任意旧请求已结束。 */
@Data
public class SearchIndexReconciliationRequest {
  @ValidResourceVersion private String version;
  @NotBlank @Size(max = 500) private String reason;
  @NotNull @AssertTrue private Boolean ownershipConfirmed;
  @NotNull @AssertTrue private Boolean originalRequestTerminated;
}
