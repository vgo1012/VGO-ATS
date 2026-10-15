package com.ezaigc.hr.reporting.monthlyreport.domain.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Value;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MonthlyReportSnapshotResponse {
    String ruleVersion;
    String parserVersion;
    String templateVersion;
    String reportMonth;
    String dataAsOf;
    String scope;
    boolean ready;
    OfferSummaryResponse offerSummary;
    DemandSummaryResponse demandSummary;
    List<DimensionMetricResponse> dimensions;
    List<MonthlyReportAnalysisResponse> analysis;
    List<MonthlyReportDiagnosticResponse> diagnostics;
    List<MonthlyReportSourceResponse> sources;
}
