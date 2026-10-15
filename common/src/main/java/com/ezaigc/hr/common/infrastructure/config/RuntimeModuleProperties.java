package com.ezaigc.hr.common.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 运行时模块配置属性对象，负责承载并校验相关配置。
 */
@Validated
@ConfigurationProperties("hr.runtime")
@Data
@NoArgsConstructor
public class RuntimeModuleProperties {

    private @NotEmpty Set<@NotBlank String> requiredModules;

}
