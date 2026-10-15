package com.ezaigc.hr.talent.config;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "hr.resume-lifecycle")
public class ResumeLifecycleProperties {
  private boolean enabled;
  @Min(1) @Max(1000) private int batchSize = 200;
}
