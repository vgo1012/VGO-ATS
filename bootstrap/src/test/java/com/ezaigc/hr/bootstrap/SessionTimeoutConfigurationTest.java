package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.session.autoconfigure.SessionProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.session.MapSession;

class SessionTimeoutConfigurationTest {
  @Test
  void keepsSessionBeyondOneDayAndExpiresAfterTwoIdleDays() {
    try (var context = new GenericApplicationContext()) {
      TestPropertyValues.of("spring.config.location=classpath:application.yml",
          "spring.config.import=", "spring.profiles.active=session-timeout-test")
          .applyTo(context);
      new ConfigDataApplicationContextInitializer().initialize(context);
      SessionProperties properties = Binder.get(context.getEnvironment())
          .bindOrCreate("spring.session", SessionProperties.class);
      Duration timeout = properties.determineTimeout(() -> Duration.ofMinutes(30));
      assertThat(timeout).isEqualTo(Duration.ofHours(48));

      MapSession session = new MapSession();
      session.setMaxInactiveInterval(timeout);
      session.setLastAccessedTime(Instant.now().minus(Duration.ofHours(25)));
      assertThat(session.isExpired()).isFalse();
      session.setLastAccessedTime(Instant.now().minus(Duration.ofHours(49)));
      assertThat(session.isExpired()).isTrue();
      session.setLastAccessedTime(Instant.now());
      assertThat(session.isExpired()).isFalse();
    }
  }
}
