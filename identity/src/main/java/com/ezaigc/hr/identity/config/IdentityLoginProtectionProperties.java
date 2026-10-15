package com.ezaigc.hr.identity.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("hr.identity.login-protection")
public class IdentityLoginProtectionProperties {
  @Min(2)
  private int maxFailures;

  @NotNull
  private Duration failureWindow;

  @NotNull
  private Duration lockDuration;

  @AssertTrue(message = "登录失败窗口和锁定时长必须大于零")
  public boolean isPositiveDurations() {
    return failureWindow != null && !failureWindow.isNegative() && !failureWindow.isZero()
        && lockDuration != null && !lockDuration.isNegative() && !lockDuration.isZero();
  }
}
