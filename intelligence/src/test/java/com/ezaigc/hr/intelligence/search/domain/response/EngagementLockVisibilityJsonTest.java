package com.ezaigc.hr.intelligence.search.domain.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService.LockSummary;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EngagementLockVisibilityJsonTest {

  @Test
  void engagementLockSummaryExposesItsFieldsToHttpJson() {
    LockSummary summary =
        new LockSummary(
            "L".repeat(26),
            "C".repeat(26),
            "U".repeat(26),
            "D".repeat(26),
            "招聘顾问",
            Instant.parse("2026-09-02T00:00:00Z"),
            Instant.parse("2026-09-09T00:00:00Z"),
            true,
            "ACCEPTED");

    var tree = new ObjectMapper().valueToTree(summary);

    assertThat(tree.path("lockId").asString()).isEqualTo(summary.getLockId());
    assertThat(tree.path("contactName").asString()).isEqualTo("招聘顾问");
    assertThat(tree.path("canContact").asBoolean()).isTrue();
  }
}
