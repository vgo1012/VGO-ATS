package com.ezaigc.hr.intelligence.search.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchRebuildStatusTest {

  @Test
  void roundTripsCompleteSearchRebuildLifecycle() {
    assertThat(SearchRebuildStatus.values())
        .extracting(SearchRebuildStatus::getCode)
        .containsExactly("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED");
    assertThat(SearchRebuildStatus.fromCode("RUNNING")).isEqualTo(SearchRebuildStatus.RUNNING);
    assertThatThrownBy(() -> SearchRebuildStatus.fromCode("PARTIAL"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
