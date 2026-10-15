package com.ezaigc.hr.identity.config;

import com.ezaigc.hr.common.core.module.ModuleDescriptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.ezaigc.hr.identity")
@EnableConfigurationProperties({IdentityLoginAliasProperties.class, IdentityLoginProtectionProperties.class})
public class IdentityModuleConfiguration {

  @Bean
  ModuleDescriptor identityModuleDescriptor() {
    return ModuleDescriptor.module("identity");
  }
}
