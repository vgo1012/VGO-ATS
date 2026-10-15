package com.ezaigc.hr.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WecomSyncProperties.class)
public class WecomSyncConfiguration {}
