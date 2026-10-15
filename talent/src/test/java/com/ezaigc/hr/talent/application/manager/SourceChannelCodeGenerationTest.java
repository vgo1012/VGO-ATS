package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceChannelCodeGenerationTest {

  @Test
  void generatesStableBusinessCodeFromPlatformId() {
    assertThat(TalentIntakeApplicationManager.sourceChannelCode("01JCHANNEL0000000000000000"))
        .isEqualTo("SC_01JCHANNEL0000000000000000");
  }
}
