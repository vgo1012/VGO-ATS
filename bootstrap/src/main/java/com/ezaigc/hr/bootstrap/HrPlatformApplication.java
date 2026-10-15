package com.ezaigc.hr.bootstrap;

import com.ezaigc.hr.common.infrastructure.config.RuntimeModuleConfiguration;
import com.ezaigc.hr.identity.config.IdentityModuleConfiguration;
import com.ezaigc.hr.integration.config.IntegrationModuleConfiguration;
import com.ezaigc.hr.intelligence.config.IntelligenceModuleConfiguration;
import com.ezaigc.hr.recruiting.config.RecruitingModuleConfiguration;
import com.ezaigc.hr.reporting.config.ReportingModuleConfiguration;
import com.ezaigc.hr.talent.config.TalentModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@AutoConfigurationPackage(basePackages = "com.ezaigc.hr")
@Import({
        RuntimeModuleConfiguration.class,
        IdentityModuleConfiguration.class,
        TalentModuleConfiguration.class,
        RecruitingModuleConfiguration.class,
        IntelligenceModuleConfiguration.class,
        IntegrationModuleConfiguration.class,
        ReportingModuleConfiguration.class
})
public class HrPlatformApplication {

    /**
     * 启动 HR AI 招聘赋能平台应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(HrPlatformApplication.class, args);
    }
}
