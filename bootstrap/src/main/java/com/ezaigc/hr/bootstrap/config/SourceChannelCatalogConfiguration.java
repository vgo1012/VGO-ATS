package com.ezaigc.hr.bootstrap.config;

import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 初始化可供全公司招聘人员使用的基础来源渠道。 */
@Configuration(proxyBeanMethods = false)
public class SourceChannelCatalogConfiguration {
  private static final String SYSTEM_ACTOR = "SYSTEM";
  private static final List<DefaultChannel> DEFAULTS =
      List.of(
          new DefaultChannel("00000000000000000000000001", "DEFAULT_BOSS", "BOSS", "BOSS"),
          new DefaultChannel("00000000000000000000000002", "DEFAULT_ZHILIAN", "智联", "MANUAL"),
          new DefaultChannel("00000000000000000000000003", "DEFAULT_LIEPIN", "猎聘", "MANUAL"),
          new DefaultChannel("00000000000000000000000004", "DEFAULT_REFERRAL", "内推", "MANUAL"));

  @Bean
  ApplicationRunner defaultSourceChannelInitializer(
      SourceChannelPersistenceService channels, Clock clock) {
    return arguments -> initialize(channels, clock.instant());
  }

  static void initialize(SourceChannelPersistenceService channels, Instant now) {
    for (DefaultChannel channel : DEFAULTS) {
      channels.insertChannelIfAbsent(
          channel.id, channel.code, channel.name, channel.type, SYSTEM_ACTOR, now);
    }
  }

  private static final class DefaultChannel {
    private final String id;
    private final String code;
    private final String name;
    private final String type;

    private DefaultChannel(String id, String code, String name, String type) {
      this.id = id;
      this.code = code;
      this.name = name;
      this.type = type;
    }
  }
}
