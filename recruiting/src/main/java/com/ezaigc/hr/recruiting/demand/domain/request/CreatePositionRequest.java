package com.ezaigc.hr.recruiting.demand.domain.request;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePositionRequest {

    /**
     * 客户 ID。
     */
    @PlatformIdentifier
    private String clientId;
    /**
     * 项目 ID。
     */
    @OptionalPlatformIdentifier
    private String projectId;

    /**
     * 业务编码。
     */
    @Size(max = 64)
    private String code;

    /**
     * 名称。
     */
    @NotBlank
    @Size(max = 160)
    private String name;
}
