package com.ezaigc.hr.intelligence.search.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties("hr.search.retention")
public class SearchIndexRetentionProperties {
  @Min(1) @Max(20) private int retainCount = 2;
  @Min(1) @Max(100) private int maxLiveResources = 8;
  @Min(1) @Max(10) private int maxUnresolvedCreates = 1;
  @Min(1) @Max(20) private int batchSize = 5;
  @NotNull private Duration minimumAge = Duration.ofMinutes(10);
  @NotNull private Duration requestBudget = Duration.ofMinutes(2);

  @AssertTrue(message = "search retention capacities and time budgets must be bounded")
  public boolean isPolicyValid() {
    return maxLiveResources > retainCount && maxUnresolvedCreates <= maxLiveResources
        && minimumAge != null && !minimumAge.isNegative() && minimumAge.compareTo(Duration.ofDays(30)) <= 0
        && requestBudget != null && !requestBudget.isZero() && !requestBudget.isNegative()
        && requestBudget.compareTo(Duration.ofMinutes(5)) <= 0;
  }
}
