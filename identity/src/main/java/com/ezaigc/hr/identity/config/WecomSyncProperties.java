package com.ezaigc.hr.identity.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("hr.wecom.sync")
public class WecomSyncProperties {
    private boolean enabled;
    @NotBlank @Size(max = 64)
    private String scopeVersion = "initial";
    @Min(1900) @Max(3600)
    private int leaseSeconds = 2100;
    @Min(10) @Max(3600)
    private int cooldownSeconds = 60;
    @Min(1) @Max(100)
    private int maxMemberDropPercent = 20;
}
