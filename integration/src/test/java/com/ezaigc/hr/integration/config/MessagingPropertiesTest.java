package com.ezaigc.hr.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.List;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

class MessagingPropertiesTest {
    @Test
    void defaultsAreDisabledAndEnabledConnectionsRequireExplicitCredentials() {
        try (var factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
            var validator = factory.getValidator();
            var properties = new MessagingProperties();
            assertThat(validator.validate(properties)).isEmpty();
            properties.setWorkerEnabled(true);
            assertThat(validator.validate(properties)).isNotEmpty();
            properties.setMode("rabbitmq");
            assertThat(validator.validate(properties)).isNotEmpty();
            properties.setHost("synthetic-host");
            properties.setUsername("synthetic-user");
            properties.setPassword("synthetic-only-password");
            assertThat(validator.validate(properties)).isEmpty();
            assertThat(properties.getWorkerRoutingKeys())
                    .contains("RESUME_PARSE", "JD_PARSE", "MATCH_RUN");
            properties.setWorkerRoutingKeys(List.of("invalid-route"));
            assertThat(validator.validate(properties)).isNotEmpty();
            assertThat(properties.toString()).doesNotContain("synthetic-only-password");
            properties.setConfirmTimeoutMillis(0);
            assertThat(validator.validate(properties)).isNotEmpty();
        }
    }
}
