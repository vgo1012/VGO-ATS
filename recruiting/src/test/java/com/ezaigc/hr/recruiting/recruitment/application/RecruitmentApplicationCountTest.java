package com.ezaigc.hr.recruiting.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationCandidateFilterDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecruitmentApplicationCountTest {
  @Test
  void countReusesTheExactListFiltersInBoundedBatches() {
    var manager = spy(new RecruitmentApplicationManager());
    var candidateFilter = new ApplicationCandidateFilterDTO(
        "Java", List.of("杭州"), List.of("本科"), null, null, null, null, null);
    var from = Instant.parse("2026-09-01T00:00:00Z");
    var to = Instant.parse("2026-10-01T00:00:00Z");
    var page = new CursorPage<>(List.of(new Application()), "next-count", true);
    doReturn(page).when(manager).list(
        "jd", "jd-version", "ONBOARDED", "OFFER", "owner", "count-cursor", 100,
        "INTERNAL", "client", "ACTIVE", candidateFilter, from, to, "TECHNICAL", true);

    var count = manager.countApplications(
        "jd", "jd-version", "ONBOARDED", "OFFER", "owner", "count-cursor",
        "INTERNAL", "client", "ACTIVE", candidateFilter, from, to, "TECHNICAL", true);

    assertThat(count.getCount()).isEqualTo(1);
    assertThat(count.getNextCursor()).isEqualTo("next-count");
    assertThat(count.isHasMore()).isTrue();
    verify(manager).list(
        "jd", "jd-version", "ONBOARDED", "OFFER", "owner", "count-cursor", 100,
        "INTERNAL", "client", "ACTIVE", candidateFilter, from, to, "TECHNICAL", true);
  }
}
