package com.ezaigc.hr.recruiting.recruitment.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.ClientRestrictionPersistenceService;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateRecruitmentStateAdapterTest {
  @Test
  void blacklistFactStaysBoundToItsApplicationAndDoesNotInventTerminationOrResumeVersion() {
    var applications = mock(ApplicationPersistenceService.class);
    var restrictions = mock(ClientRestrictionPersistenceService.class);
    var adapter = new CandidateRecruitmentStateAdapter();
    ReflectionTestUtils.setField(adapter, "applications", applications);
    ReflectionTestUtils.setField(adapter, "restrictions", restrictions);
    var rejected = application("rejected", "resume-old", "TERMINATED", "REJECT");
    rejected.setTerminalAt(Instant.parse("2026-09-08T01:00:00Z"));
    var active = application("active", "resume-current", "IN_PROCESS", null);
    var other = application("other", "resume-current", "TERMINATED", "REJECT");
    when(applications.findForCandidates(List.of("candidate"))).thenReturn(List.of(rejected, active, other));
    when(restrictions.applicationsWithBlacklistRecords(Set.of("rejected", "active", "other")))
        .thenReturn(Set.of("rejected", "active"));

    var states = adapter.states(List.of("candidate")).get("candidate");

    assertThat(states.get(0).isBlacklistRecorded()).isTrue();
    assertThat(states.get(0).isScreenedOut()).isTrue();
    assertThat(states.get(0).getResumeVersionId()).isEqualTo("resume-old");
    assertThat(states.get(0).getTerminalAt()).isEqualTo(rejected.getTerminalAt());
    assertThat(states.get(1).isBlacklistRecorded()).isTrue();
    assertThat(states.get(1).isBlocksLibrary()).isTrue();
    assertThat(states.get(1).isScreenedOut()).isFalse();
    assertThat(states.get(2).isBlacklistRecorded()).isFalse();
    verify(restrictions).applicationsWithBlacklistRecords(Set.of("rejected", "active", "other"));
  }

  private Application application(String id, String resume, String stage, String outcome) {
    var application = new Application();
    application.setId(id);
    application.setCandidateId("candidate");
    application.setResumeVersionId(resume);
    application.setCurrentStage(stage);
    application.setOutcome(outcome);
    return application;
  }
}
