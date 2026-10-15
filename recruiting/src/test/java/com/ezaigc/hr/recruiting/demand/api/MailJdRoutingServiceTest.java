package com.ezaigc.hr.recruiting.demand.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MailJdRoutingServiceTest {

  @Test
  void normalizesOnlyUnicodeAndWhitespace() {
    assertThat(MailJdRoutingService.normalize("  Ｊａｖａ　开发  ")).isEqualTo("Java 开发");
    assertThat(MailJdRoutingService.normalize("东莞市")).isNotEqualTo(MailJdRoutingService.normalize("东莞"));
  }
}
