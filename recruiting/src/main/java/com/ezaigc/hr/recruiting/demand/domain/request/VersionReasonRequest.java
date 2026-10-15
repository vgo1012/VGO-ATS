package com.ezaigc.hr.recruiting.demand.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VersionReasonRequest {

    /**
     * 技术乐观锁版本号。
     */
    @ValidResourceVersion
    private String version;

    /**
     * 操作原因。
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
