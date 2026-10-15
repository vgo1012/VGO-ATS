package com.ezaigc.hr.reporting.config;

import com.ezaigc.hr.common.core.module.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.ezaigc.hr.reporting")
public class ReportingModuleConfiguration {

  @Bean
  ModuleDescriptor reportingModuleDescriptor() {
    return ModuleDescriptor.module("reporting", "identity", "talent");
  }
}
