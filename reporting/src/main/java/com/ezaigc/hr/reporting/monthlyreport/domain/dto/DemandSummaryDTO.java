package com.ezaigc.hr.reporting.monthlyreport.domain.dto;

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
public class DemandSummaryDTO {
    int approvedDemandCount;
    long approvedHeadcount;
    int monthAppliedDemandCount;
    long monthAppliedHeadcount;
    int monthExpectedDemandCount;
    long monthExpectedHeadcount;
}
