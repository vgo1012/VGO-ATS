package com.ezaigc.hr.intelligence.config;

import com.ezaigc.hr.common.core.module.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"com.ezaigc.hr.intelligence.matching", "com.ezaigc.hr.intelligence.search"})
public class IntelligenceModuleConfiguration {

  @Bean
  ModuleDescriptor intelligenceModuleDescriptor() {
    return ModuleDescriptor.module("intelligence", "identity", "talent", "recruiting");
  }
}
