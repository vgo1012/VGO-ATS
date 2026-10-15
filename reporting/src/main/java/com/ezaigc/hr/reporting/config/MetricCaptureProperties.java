package com.ezaigc.hr.reporting.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 核心报表的后台投影，单批最多 1000 条来源主键。 */
@Data
@Validated
@Component
@ConfigurationProperties("hr.reporting.metric-capture")
public class MetricCaptureProperties {
  private boolean enabled = true;
  @NotNull private Duration initialDelay = Duration.ofSeconds(5);
  @NotNull private Duration pollDelay = Duration.ofSeconds(30);
  @Min(1) @Max(1000) private int batchSize = 500;
  @NotNull private Duration reconciliationInitialDelay = Duration.ofMinutes(1);
  @NotNull private Duration reconciliationDelay = Duration.ofMinutes(10);
  @Min(1) @Max(1000) private int reconciliationBatchSize = 500;
  @NotNull private Duration staleAfter = Duration.ofMinutes(5);

  @AssertTrue(message = "metric capture delays and stale threshold must be positive")
  public boolean isDelayConfigurationValid() {
    return positive(initialDelay) && positive(pollDelay) && positive(reconciliationInitialDelay)
        && positive(reconciliationDelay) && positive(staleAfter);
  }

  private static boolean positive(Duration value) {
    return value != null && !value.isNegative() && !value.isZero();
  }
}
