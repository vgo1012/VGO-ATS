package com.ezaigc.hr.integration.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 扫描页识别复用受控模型连接，只单独配置用途模型和输出上限。 */
@Data
@Validated
@ConfigurationProperties("hr.ai.multimodal")
public class MultimodalProperties {
  private boolean enabled;
  @NotBlank private String model = "qwen3-omni-flash";
  @Min(256) @Max(32768) private int maxTokens = 8192;
}
