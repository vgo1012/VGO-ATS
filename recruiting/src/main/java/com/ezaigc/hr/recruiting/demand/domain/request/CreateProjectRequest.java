package com.ezaigc.hr.recruiting.demand.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProjectRequest {

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
