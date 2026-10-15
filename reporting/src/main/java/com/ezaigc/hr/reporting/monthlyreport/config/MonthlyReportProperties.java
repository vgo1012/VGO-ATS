package com.ezaigc.hr.reporting.monthlyreport.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties("hr.reporting.monthly-report")
public class MonthlyReportProperties {
    private boolean enabled = true;
    /** 与现有 HTTP 单文件上限一致，不能静默截断。 */
    @Min(1024) @Max(10485760) private long maxFileBytes = 10485760;
    @Min(1) @Max(100000) private int maxRows = 100000;
    @Min(1) @Max(300) private int maxGroups = 300;
    /** 原件与成品同批过期，元数据仍留在历史中。 */
    @Min(1) @Max(3650) private int retentionDays = 90;
    @Min(1) @Max(20) private int maxAttempts = 3;
    @Min(1) @Max(100) private int maxActiveReports = 10;
}
