package com.ezaigc.hr.bootstrap.config;

import com.ezaigc.hr.identity.application.manager.IdentityAdministrationApplicationManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityBootstrapAdminProperties.class)
public class IdentityBootstrapAdminConfiguration {

    @Bean
    @ConditionalOnProperty(name = "hr.bootstrap-admin.enabled", havingValue = "true")
    ApplicationRunner initialSuperAdminProvisioner(
            IdentityAdministrationApplicationManager service,
            IdentityBootstrapAdminProperties properties) {
        return arguments ->
                service.provisionInitialSuperAdmin(
                        properties.getDepartmentCode(),
                        properties.getDepartmentName(),
                        properties.getDisplayName(),
                        properties.getWorkEmail(),
                        properties.getPassword());
    }
}
