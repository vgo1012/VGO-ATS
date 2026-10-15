package com.ezaigc.hr.reporting.monthlyreport.domain.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Value;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MonthlyReportDetailResponse {
    String id;
    String month;
    LocalDate asOfDate;
    String status;
    String version;
    Instant createTime;
    String createName;
    Integer offerCount;
    BigDecimal totalScore;
    String errorCode;
    String errorDetail;
    String taskId;
    int progressPercent;
    boolean retryable;
    String remarks;
    List<MonthlyReportFileResponse> files;
    List<MonthlyReportDiagnosticResponse> diagnostics;
    MonthlyReportSnapshotResponse snapshot;
}
