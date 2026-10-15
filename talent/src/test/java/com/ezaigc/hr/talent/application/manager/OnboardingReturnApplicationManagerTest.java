package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OnboardingReturnApplicationManagerTest {
  @Test
  void marksCurrentResumeSharedAndKeepsTheApplicationResumeAsAuditContext() {
    String candidateId = "C".repeat(26);
    String applicationResumeId = "A".repeat(26);
    String currentResumeId = "R".repeat(26);
    String actorId = "U".repeat(26);
    Instant now = Instant.parse("2026-09-22T03:00:00Z");
    var candidates = mock(CandidatePersistenceService.class);
    var resumes = mock(TalentIntakePersistenceService.class);
    var routing = mock(CandidateResumeVersionService.class);
    var lifecycle = mock(ResumeLifecycleApplicationManager.class);
    var events = mock(TalentProcessEventPersistenceService.class);
    var ids = mock(PlatformIdGenerator.class);
    var manager = new OnboardingReturnApplicationManager();
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "resumes", resumes);
    ReflectionTestUtils.setField(manager, "routing", routing);
    ReflectionTestUtils.setField(manager, "lifecycle", lifecycle);
    ReflectionTestUtils.setField(manager, "events", events);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    Candidate candidate = new Candidate();
    candidate.setId(candidateId);
    candidate.setStatus("ACTIVE");
    candidate.setVersion(7);
    candidate.setExt("{\"kept\":true}");
    CandidateResumeRow resume = new CandidateResumeRow();
    resume.setCandidateId(candidateId);
    resume.setResumeVersionId(currentResumeId);
    when(candidates.findCandidate(candidateId)).thenReturn(candidate);
    when(resumes.findCurrentCandidateResumes(List.of(candidateId))).thenReturn(List.of(resume));
    when(candidates.markReturnedToShared(eq(candidateId), eq(7L), any(), eq(actorId), eq(now)))
        .thenReturn(1);
    when(ids.newId()).thenReturn("E".repeat(26));

    manager.returnToShared(candidateId, applicationResumeId, "候选人原因：未办理入职", actorId);

    ArgumentCaptor<String> ext = ArgumentCaptor.forClass(String.class);
    verify(candidates)
        .markReturnedToShared(eq(candidateId), eq(7L), ext.capture(), eq(actorId), eq(now));
    assertThat(ext.getValue())
        .contains("\"kept\":true")
        .contains("\"screenedOutResumeVersionId\":\"" + currentResumeId + "\"")
        .contains("\"onboardingReturnApplicationResumeVersionId\":\"" + applicationResumeId + "\"");
    verify(routing).lockForRouting(candidateId, currentResumeId);
    verify(lifecycle).routed(candidateId, currentResumeId, "ONBOARDING_RETURN");
    verify(events).insertEvent(
        "E".repeat(26), "ONBOARDING_RETURNED_TO_SHARED_LIBRARY", actorId,
        "CANDIDATE", candidateId, "候选人原因：未办理入职", now);
  }
}
