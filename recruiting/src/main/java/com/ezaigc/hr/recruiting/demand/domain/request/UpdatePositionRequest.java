package com.ezaigc.hr.recruiting.demand.domain.request;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePositionRequest {

    /**
     * 项目 ID。
     */
    @OptionalPlatformIdentifier
    private String projectId;

    /**
     * 名称。
     */
    @NotBlank
    @Size(max = 160)
    private String name;

    /**
     * 技术乐观锁版本号。
     */
    @ValidResourceVersion
    private String version;
}
