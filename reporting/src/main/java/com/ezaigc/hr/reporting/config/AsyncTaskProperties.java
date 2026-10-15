package com.ezaigc.hr.reporting.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 持久化异步任务的领取租约配置。 */
@Data
@Validated
@Component
@ConfigurationProperties("hr.reporting.async-task")
public class AsyncTaskProperties {

  private static final Duration MINIMUM_LEASE_DURATION = Duration.ofMinutes(2);
  private static final Duration MAXIMUM_LEASE_DURATION = Duration.ofHours(1);

  /** 单次领取租约；任务在进度更新时自动续约。 */
  @NotNull private Duration leaseDuration = Duration.ofMinutes(5);

  @AssertTrue(message = "async task lease duration must be between 2 minutes and 1 hour")
  public boolean isLeaseDurationValid() {
    return leaseDuration != null
        && leaseDuration.compareTo(MINIMUM_LEASE_DURATION) >= 0
        && leaseDuration.compareTo(MAXIMUM_LEASE_DURATION) <= 0;
  }

  /** 计算领取或续约后的绝对到期时间。 */
  public Instant leaseDeadline(Instant now) {
    return Objects.requireNonNull(now, "now").plus(leaseDuration);
  }
}
