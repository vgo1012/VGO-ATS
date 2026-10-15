package com.ezaigc.hr.reporting.monthlyreport.domain.dto;

import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportDiagnosticSeverity;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MonthlyReportDiagnosticDTO {
    MonthlyReportDiagnosticSeverity severity;
    String code;
    String source;
    String sheet;
    Integer row;
    String column;
    String message;
    int count;
}
