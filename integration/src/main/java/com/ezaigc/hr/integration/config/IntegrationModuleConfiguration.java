package com.ezaigc.hr.integration.config;

import com.ezaigc.hr.common.core.module.ModuleDescriptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.ezaigc.hr.integration")
@EnableConfigurationProperties({IntegrationProperties.class, MailResumeProperties.class, MultimodalProperties.class, MessagingProperties.class, WecomProperties.class})
public class IntegrationModuleConfiguration {

    @Bean
    ModuleDescriptor integrationModuleDescriptor() {
        return ModuleDescriptor.module("integration", "identity", "talent", "recruiting", "intelligence");
    }
}
