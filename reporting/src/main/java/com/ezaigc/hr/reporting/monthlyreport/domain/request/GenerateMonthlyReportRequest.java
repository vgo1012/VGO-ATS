package com.ezaigc.hr.reporting.monthlyreport.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GenerateMonthlyReportRequest {
    @ValidResourceVersion private String version;
    @Size(max = 2000) private String remarks;
    private boolean acknowledgeWarnings;
}
