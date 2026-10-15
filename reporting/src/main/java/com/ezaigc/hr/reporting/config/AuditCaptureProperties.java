package com.ezaigc.hr.reporting.config;

import static com.ezaigc.hr.reporting.service.AuditCaptureService.MAX_BATCH_SIZE;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 审计投影后台采集配置。 */
@Data
@Validated
@Component
@ConfigurationProperties("hr.reporting.audit-capture")
public class AuditCaptureProperties {

  private boolean enabled = false;

  @NotNull private Duration initialDelay = Duration.ofSeconds(5);

  @NotNull private Duration pollDelay = Duration.ofSeconds(3);

  @Min(1)
  @Max(MAX_BATCH_SIZE)
  private int batchSize = 500;

  @NotNull private Duration reconciliationInitialDelay = Duration.ofMinutes(1);

  @NotNull private Duration reconciliationDelay = Duration.ofMinutes(1);

  @Min(1)
  @Max(MAX_BATCH_SIZE)
  private int reconciliationBatchSize = 200;

  @AssertTrue(message = "audit capture delays must be positive")
  public boolean isDelayConfigurationValid() {
    return positive(initialDelay)
        && positive(pollDelay)
        && positive(reconciliationInitialDelay)
        && positive(reconciliationDelay);
  }

  private static boolean positive(Duration value) {
    return value != null && !value.isZero() && !value.isNegative();
  }
}
