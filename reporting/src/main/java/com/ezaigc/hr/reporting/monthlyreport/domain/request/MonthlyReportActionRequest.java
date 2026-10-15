package com.ezaigc.hr.reporting.monthlyreport.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MonthlyReportActionRequest {
    @ValidResourceVersion private String version;
    @NotBlank @Size(max = 500) private String reason;
}
