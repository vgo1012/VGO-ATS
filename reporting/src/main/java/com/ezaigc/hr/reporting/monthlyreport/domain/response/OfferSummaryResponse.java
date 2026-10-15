package com.ezaigc.hr.reporting.monthlyreport.domain.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Value;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Map;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class OfferSummaryResponse {
    int totalCount;
    int scoredCount;
    BigDecimal totalScore;
    Map<String, Integer> statusCounts;
    BigDecimal rejectedRate;
    BigDecimal nonRejectedRate;
    BigDecimal acceptedOrOnboardedRate;
    BigDecimal onboardedRate;
    String dateFrom;
    String dateTo;
    Map<String, Integer> excludedCounts;
}
