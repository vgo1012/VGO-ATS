package com.ezaigc.hr.reporting.monthlyreport.domain.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MonthlyReportSnapshotDTO {
    String ruleVersion;
    String parserVersion;
    String templateVersion;
    String reportMonth;
    String dataAsOf;
    String scope;
    boolean ready;
    OfferSummaryDTO offerSummary;
    DemandSummaryDTO demandSummary;
    List<DimensionMetricDTO> dimensions;
    List<MonthlyReportAnalysisDTO> analysis;
    List<MonthlyReportDiagnosticDTO> diagnostics;
    List<MonthlyReportSourceDTO> sources;
}
