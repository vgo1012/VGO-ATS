package com.ezaigc.hr.talent.config;

import com.ezaigc.hr.common.core.module.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.ezaigc.hr.talent")
@EnableScheduling
public class TalentModuleConfiguration {

    @Bean
    ModuleDescriptor talentModuleDescriptor() {
        return ModuleDescriptor.module("talent", "identity");
    }
}
