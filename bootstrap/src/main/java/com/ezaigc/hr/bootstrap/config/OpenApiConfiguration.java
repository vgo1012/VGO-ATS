package com.ezaigc.hr.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI hrPlatformOpenApi() {
        return new OpenAPI().info(new Info()
                .title("VGO ATS API")
                .version("v1")
                .description("AI 辅助招聘平台的同源 REST API；AI 只提供事实与证据，最终决定由 HR 完成。"));
    }
}
