package com.ezaigc.hr.integration.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("hr.wecom")
public class WecomProperties {
    private boolean directoryEnabled;
    private String corpId = "";
    private String secret = "";
    @Min(1) @Max(30)
    private int requestTimeoutSeconds = 5;
    @Min(1) @Max(1800)
    private int previewTimeoutSeconds = 900;
    @Min(10) @Max(3600)
    private int cooldownSeconds = 60;
    @Min(1) @Max(1000)
    private int maxDepartments = 1000;
    @Min(1) @Max(100000)
    private int maxMembers = 10000;
    @Valid
    private Collaboration collaboration = new Collaboration();

    @Getter
    @Setter
    public static class Collaboration {
        /** 仅控制新建企业微信日程/会议；更新、取消及失败恢复不受该开关阻断。 */
        private boolean enabled;
        private boolean workerEnabled = true;
        @Min(0)
        private long agentId;
        private String calendarId = "";
    }
}
