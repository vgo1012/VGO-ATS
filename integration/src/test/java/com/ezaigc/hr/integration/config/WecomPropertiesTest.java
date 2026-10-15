package com.ezaigc.hr.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class WecomPropertiesTest {
    @Test
    void deploymentEnvironmentOverridesBindWithoutYamlPlaceholders() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("HR_WECOM_CORPID", "synthetic-corp", "HR_WECOM_SECRET", "synthetic-secret",
                                "HR_WECOM_DIRECTORYENABLED", "true")));
        ConfigurationPropertySources.attach(environment);
        var properties = Binder.get(environment).bind("hr.wecom", WecomProperties.class).get();
        assertThat(properties.isDirectoryEnabled()).isTrue();
        assertThat(properties.getCorpId()).isEqualTo("synthetic-corp");
        assertThat(properties.getSecret()).isEqualTo("synthetic-secret");
    }

    @Test
    void defaultsAreOfflineSafeAndMissingSecretsDoNotPreventStartup() {
        var properties = new WecomProperties();
        assertThat(properties.isDirectoryEnabled()).isFalse();
        assertThat(properties.getCorpId()).isEmpty();
        assertThat(properties.getSecret()).isEmpty();
        assertThat(properties.getCollaboration().isEnabled()).isFalse();
        assertThat(properties.getCollaboration().isWorkerEnabled()).isTrue();
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isEmpty();
            properties.setDirectoryEnabled(true);
            assertThat(factory.getValidator().validate(properties)).isEmpty();
        }
    }

    @Test
    void refusesUnboundedOrNonpositiveLimits() {
        var properties = new WecomProperties();
        properties.setRequestTimeoutSeconds(0);
        properties.setPreviewTimeoutSeconds(1801);
        properties.setCooldownSeconds(0);
        properties.setMaxDepartments(0);
        properties.setMaxMembers(100001);
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).hasSize(5);
        }
    }
}
