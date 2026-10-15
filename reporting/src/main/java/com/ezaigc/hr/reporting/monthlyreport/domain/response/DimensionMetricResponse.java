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
public class DimensionMetricResponse {
    String dimension;
    String key;
    String group;
    int count;
    BigDecimal score;
    BigDecimal averageScore;
    Map<String, Integer> statusCounts;
    BigDecimal countShare;
    BigDecimal scoreShare;
}
