package com.ezaigc.hr.integration.config;

import com.ezaigc.hr.common.core.util.CommonUtils;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hr.mail-resume")
public class MailResumeProperties {

  private boolean enabled;
  @NotBlank private String mailboxKey = "public-resume";
  private String host = "";
  @Min(1) @Max(65535) private int port = 993;
  private String username = "";
  @ToString.Exclude private String password = "";
  @NotBlank private String folder = "INBOX";
  // 邮件简历默认归属内置 BOSS直聘渠道，与启动时初始化的渠道 ID 保持一致。
  private String sourceChannelId = "00000000000000000000000001";
  @Min(1) @Max(200) private int batchSize = 50;
  @NotNull private Duration connectTimeout = Duration.ofSeconds(10);
  @NotNull private Duration readTimeout = Duration.ofSeconds(30);
  @NotNull private Duration writeTimeout = Duration.ofSeconds(30);

  @AssertTrue(message = "mail resume connection and source channel must be configured when enabled")
  public boolean hasRequiredEnabledConfiguration() {
    return !enabled || !CommonUtils.isBlank(host)
        && !CommonUtils.isBlank(username)
        && !CommonUtils.isBlank(password)
        && sourceChannelId != null
        && sourceChannelId.matches("[0-9A-HJKMNP-TV-Z]{26}");
  }

  @AssertTrue(message = "mail resume timeouts must be positive")
  public boolean hasValidTimeouts() {
    return positive(connectTimeout) && positive(readTimeout) && positive(writeTimeout);
  }

  private static boolean positive(Duration value) {
    return value != null
        && value.compareTo(Duration.ofMillis(1)) >= 0
        && value.compareTo(Duration.ofMillis((long) Integer.MAX_VALUE + 1)) < 0;
  }
}
