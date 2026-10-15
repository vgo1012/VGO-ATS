package com.ezaigc.hr.recruiting.config;

import com.ezaigc.hr.common.core.module.ModuleDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {
      "com.ezaigc.hr.recruiting.demand",
      "com.ezaigc.hr.recruiting.activity",
      "com.ezaigc.hr.recruiting.recruitment",
      "com.ezaigc.hr.recruiting.interview",
      "com.ezaigc.hr.recruiting.submission"
    })
public class RecruitingModuleConfiguration {

  @Bean
  ModuleDescriptor recruitingModuleDescriptor() {
    return ModuleDescriptor.module("recruiting", "identity", "talent", "reporting");
  }
}
