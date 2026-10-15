package com.ezaigc.hr.reporting.monthlyreport.domain.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class OfferSummaryDTO {
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
