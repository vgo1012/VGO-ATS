package com.ezaigc.hr.intelligence.matching.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.intelligence.matching.enums.MatchRunItemOutcomeStatus;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunItemOutcomeMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchRunOutcomeTerminalReadTest {
  @Test
  void acceptsTheFullAllowedRun() {
    var mapper = mock(MatchRunItemOutcomeMapper.class);
    var service = new MatchRunItemOutcomePersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.lockOutcomeStatuses("run", 1001)).thenReturn(Collections.nCopies(1000, "SUCCEEDED"));
    assertThat(service.lockOutcomeStatuses("run")).hasSize(1000).containsOnly(MatchRunItemOutcomeStatus.SUCCEEDED);
  }

  @Test
  void refusesToPresentTruncatedCountsAsACompleteRun() {
    var mapper = mock(MatchRunItemOutcomeMapper.class);
    var service = new MatchRunItemOutcomePersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.lockOutcomeStatuses("run", 1001)).thenReturn(Collections.nCopies(1001, "SUCCEEDED"));
    assertThatThrownBy(() -> service.lockOutcomeStatuses("run")).isInstanceOf(IllegalStateException.class);
  }
}
