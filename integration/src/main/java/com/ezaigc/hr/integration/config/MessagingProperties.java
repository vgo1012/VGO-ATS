package com.ezaigc.hr.integration.config;

import com.ezaigc.hr.common.core.util.CommonUtils;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 消息连接只在显式启用时建立；不生成含密码的 toString。 */
@Getter
@Setter
@Validated
@ConfigurationProperties("hr.messaging")
public class MessagingProperties {
    @NotBlank @Pattern(regexp = "disabled|rabbitmq") private String mode = "disabled";
    private boolean workerEnabled;
    private boolean consumerEnabled;
    @Size(max = 255) private String host = "";
    @Min(1) @Max(65535) private int port = 5672;
    @NotBlank @Size(max = 128) private String virtualHost = "/";
    @Size(max = 255) private String username = "";
    @Size(max = 512) private String password = "";
    private boolean sslEnabled;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,200}") private String exchange = "hr.task.events";
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,200}") private String searchQueue = "hr.search.project";
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,200}") private String workerQueue = "hr.task.notify";
    @Size(min = 1, max = 32)
    private List<@NotBlank @Pattern(regexp = "[A-Z0-9_]{1,80}") String> workerRoutingKeys = List.of(
            "RESUME_PARSE",
            "RESUME_INTAKE_PARSE",
            "RESUME_INTAKE_COMPLETE",
            "RESUME_INTAKE_ADOPT",
            "JD_PARSE",
            "MATCH_RUN",
            "SEARCH_INDEX_REBUILD",
            "MONTHLY_REPORT_VALIDATE",
            "MONTHLY_RECRUITMENT_REPORT");
    @Min(100) @Max(10000) private int connectionTimeoutMillis = 5000;
    @Min(100) @Max(10000) private int channelRpcTimeoutMillis = 5000;
    @Min(100) @Max(15000) private int confirmTimeoutMillis = 10000;
    @Min(10) @Max(60) private int heartbeatSeconds = 30;
    @Min(2048) @Max(65536) private int maximumMessageBytes = 8192;

    @AssertTrue(message = "RabbitMQ 启用时必须配置主机和凭据；禁用模式不得启用发布或消费 Worker")
    public boolean isConnectionConfigurationComplete() {
        if ("disabled".equals(mode)) return !workerEnabled && !consumerEnabled;
        return "rabbitmq".equals(mode) && !CommonUtils.isBlank(host)
                && !CommonUtils.isBlank(username) && !CommonUtils.isBlank(password);
    }
}
