package com.ezaigc.hr.bootstrap.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.integration.config.MailResumeProperties;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.support.GenericApplicationContext;

class SourceChannelCatalogConfigurationTest {
  @Test
  void mailResumeDefaultsToInitializedBossChannel() {
    var defaults = new MailResumeProperties();
    var channels = mock(SourceChannelPersistenceService.class);
    var now = Instant.parse("2026-09-09T00:00:00Z");
    SourceChannelCatalogConfiguration.initialize(channels, now);

    try (var context = new GenericApplicationContext()) {
      TestPropertyValues.of("spring.config.location=classpath:application.yml",
          "spring.config.import=", "spring.profiles.active=mail-source-test").applyTo(context);
      new ConfigDataApplicationContextInitializer().initialize(context);
      var configured = Binder.get(context.getEnvironment())
          .bindOrCreate("hr.mail-resume", MailResumeProperties.class);
      assertThat(configured.getSourceChannelId()).isEqualTo(defaults.getSourceChannelId());
      verify(channels).insertChannelIfAbsent(eq(configured.getSourceChannelId()),
          eq("DEFAULT_BOSS"), eq("BOSS"), eq("BOSS"), eq("SYSTEM"), eq(now));
      configured.setEnabled(true);
      configured.setHost("mail.example.test");
      configured.setUsername("test@example.test");
      configured.setPassword("disposable-test-password");
      assertThat(configured.hasRequiredEnabledConfiguration()).isTrue();
      configured.setSourceChannelId("");
      assertThat(configured.hasRequiredEnabledConfiguration()).isFalse();
    }
  }

  @Test
  void initializesFourStableCompanyChannelsOnEveryRun() {
    var channels = mock(SourceChannelPersistenceService.class);
    var now = Instant.parse("2026-09-09T00:00:00Z");

    SourceChannelCatalogConfiguration.initialize(channels, now);
    SourceChannelCatalogConfiguration.initialize(channels, now);

    verify(channels, times(2))
        .insertChannelIfAbsent(any(), eq("DEFAULT_BOSS"), eq("BOSS"), eq("BOSS"), eq("SYSTEM"), eq(now));
    verify(channels, times(2))
        .insertChannelIfAbsent(any(), eq("DEFAULT_ZHILIAN"), eq("智联"), eq("MANUAL"), eq("SYSTEM"), eq(now));
    verify(channels, times(2))
        .insertChannelIfAbsent(any(), eq("DEFAULT_LIEPIN"), eq("猎聘"), eq("MANUAL"), eq("SYSTEM"), eq(now));
    verify(channels, times(2))
        .insertChannelIfAbsent(any(), eq("DEFAULT_REFERRAL"), eq("内推"), eq("MANUAL"), eq("SYSTEM"), eq(now));
  }
}
