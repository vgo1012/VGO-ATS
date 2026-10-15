package com.ezaigc.hr.common.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 分页游标的运行时安全配置。
 */
@Validated
@ConfigurationProperties("hr.pagination")
@Data
@NoArgsConstructor
public class PaginationProperties {

    @NotBlank
    @Size(min = 32)
    private String cursorSigningKey;
}
