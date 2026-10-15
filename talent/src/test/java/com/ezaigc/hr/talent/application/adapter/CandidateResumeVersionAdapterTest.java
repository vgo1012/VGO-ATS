package com.ezaigc.hr.talent.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.ResumeJdScoreBinding;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.ScoreRunCompletionQuery;
import com.ezaigc.hr.talent.application.manager.SharedReclaimScoreApplicationManager;
import com.ezaigc.hr.talent.service.ResumeJdScoreBindingPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

class CandidateResumeVersionAdapterTest {
  private AutoCloseable mocks;
  @Mock private TalentIntakePersistenceService persistence;
  @Mock private ResumeVersionPersistenceService versions;
  @Mock private CandidatePersistenceService candidates;
  @Mock private CandidateRecruitmentStateQuery recruitment;
  @Mock private ResumeJdScoreBindingPersistenceService scoreBindings;
  @Mock private ScoreRunCompletionQuery scoreRuns;
  @Spy private SharedReclaimScoreApplicationManager reclaimedScores = new SharedReclaimScoreApplicationManager();
  @InjectMocks private CandidateResumeVersionAdapter adapter;

  @BeforeEach
  void setUp() { mocks = MockitoAnnotations.openMocks(this); }

  @AfterEach
  void tearDown() throws Exception { mocks.close(); }

  @Test
  void everyLogicalResumeUsesTheCandidateLockBeforeItsVersionLockAndAuthorizationRead() {
    for (String id : new String[] {"first-current", "second-current"}) {
      var version = new ResumeVersion();
      version.setId(id); version.setStatus("AVAILABLE");
      when(persistence.findCandidateResumeVersion("candidate", id)).thenReturn(version);
    }
    adapter.lockForRouting("candidate", "first-current");
    adapter.lockForRouting("candidate", "second-current");
    var order = inOrder(candidates, versions, persistence);
    order.verify(candidates).lockCandidate("candidate");
    order.verify(versions).lockForRouting("first-current");
    order.verify(persistence).findCandidateResumeVersion("candidate", "first-current");
    order.verify(candidates).lockCandidate("candidate");
    order.verify(versions).lockForRouting("second-current");
    order.verify(persistence).findCandidateResumeVersion("candidate", "second-current");
    order.verifyNoMoreInteractions();
  }

  @Test
  void rechecksResumeOwnershipAfterAcquiringBothRoutingLocks() {
    when(persistence.findCandidateResumeVersion("candidate", "foreign-resume")).thenReturn(null);
    assertThatThrownBy(() -> adapter.lockForRouting("candidate", "foreign-resume"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RESUME_VERSION_MISMATCH"));
    var order = inOrder(candidates, versions, persistence);
    order.verify(candidates).lockCandidate("candidate");
    order.verify(versions).lockForRouting("foreign-resume");
    order.verify(persistence).findCandidateResumeVersion("candidate", "foreign-resume");
  }

  @Test
  void oldRecruitmentCannotRestartWithoutSharedSelectionAndSuccessfulNewScore() {
    when(recruitment.hasRecruitmentHistory("resume")).thenReturn(true);
    assertScoreRequired("jd", "old-run");
  }

  @Test
  void reclaimedScoreIsSpecificToCandidateResumeOwnerJobAndRun() {
    var binding = claimedScore();
    assertThatCode(() -> adapter.requireCurrentScore("candidate", "resume", "jd", "new-run")).doesNotThrowAnyException();
    assertScoreRequired("old-jd", "old-run");
    assertScoreRequired("jd", "old-run");
    binding.setCandidateId("another"); assertScoreRequired("jd", "new-run");
    binding.setCandidateId("candidate"); binding.setResumeVersionId("old-resume"); assertScoreRequired("jd", "new-run");
    binding.setResumeVersionId("resume"); binding.setSelectedByUserId("other-owner"); assertScoreRequired("jd", "new-run");
    binding.setSelectedByUserId("owner");
    when(scoreRuns.successfulRunIds(List.of("new-run"))).thenReturn(Set.of());
    assertScoreRequired("jd", "new-run");
  }

  @Test
  void cancellationAfterReclaimInvalidatesThatCycleScore() {
    claimedScore();
    when(recruitment.states(List.of("candidate"))).thenReturn(Map.of("candidate", List.of(
        new CandidateRecruitmentStateQuery.State("resume", "owner", "TERMINATED", false, false,
            "new-app", "jd", false, Instant.parse("2026-09-08T04:00:00Z")))));
    assertScoreRequired("jd", "new-run");
  }

  @Test
  void availableReferencesAndSubmissionSnapshotsPreserveFieldsAndReadOncePerPublicCall() {
    var version = new ResumeVersion();
    version.setId("resume");
    version.setStatus("AVAILABLE");
    version.setOriginalFileName("候选人履历.pdf");
    version.setContentSha256("0123456789abcdef".repeat(4));
    when(persistence.findCandidateResumeVersion("candidate", "resume")).thenReturn(version);

    var reference = adapter.requireAvailable("candidate", "resume");
    verifySingleReadPerPublicCallAndNoRoutingOrScoring(1);
    var snapshot = adapter.requireSubmissionSnapshot("candidate", "resume");

    assertThat(reference.resumeVersionId()).isEqualTo("resume");
    assertThat(reference.candidateId()).isEqualTo("candidate");
    assertThat(snapshot.resumeVersionId()).isEqualTo("resume");
    assertThat(snapshot.candidateId()).isEqualTo("candidate");
    assertThat(snapshot.originalFileName()).isEqualTo("候选人履历.pdf");
    assertThat(snapshot.contentSha256()).isEqualTo("0123456789abcdef".repeat(4));
    verifySingleReadPerPublicCallAndNoRoutingOrScoring(2);
  }

  @Test
  void missingOrForeignResumeIsRejectedByBothPublicReadsBeforeOtherResources() {
    when(persistence.findCandidateResumeVersion("candidate", "resume")).thenReturn(null);

    assertResumeMismatch(() -> adapter.requireAvailable("candidate", "resume"));
    verifySingleReadPerPublicCallAndNoRoutingOrScoring(1);
    assertResumeMismatch(() -> adapter.requireSubmissionSnapshot("candidate", "resume"));

    verifySingleReadPerPublicCallAndNoRoutingOrScoring(2);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"UNKNOWN", "available", " AVAILABLE "})
  void onlyExactAvailableStatusIsAcceptedByEitherPublicRead(String status) {
    var version = new ResumeVersion();
    version.setId("resume");
    version.setStatus(status);
    when(persistence.findCandidateResumeVersion("candidate", "resume")).thenReturn(version);

    assertResumeMismatch(() -> adapter.requireAvailable("candidate", "resume"));
    verifySingleReadPerPublicCallAndNoRoutingOrScoring(1);
    assertResumeMismatch(() -> adapter.requireSubmissionSnapshot("candidate", "resume"));

    verifySingleReadPerPublicCallAndNoRoutingOrScoring(2);
  }

  private void verifySingleReadPerPublicCallAndNoRoutingOrScoring(int readCount) {
    verify(persistence, times(readCount)).findCandidateResumeVersion("candidate", "resume");
    verifyNoMoreInteractions(persistence);
    verifyNoInteractions(versions, candidates, recruitment, scoreBindings, scoreRuns, reclaimedScores);
  }

  private static void assertResumeMismatch(Runnable read) {
    assertThatThrownBy(read::run).isInstanceOfSatisfying(PlatformException.class, error -> {
      assertThat(error.status().value()).isEqualTo(422);
      assertThat(error.code()).isEqualTo("RESUME_VERSION_MISMATCH");
      assertThat(error.getMessage()).isEqualTo("简历版本不存在、不可用或不属于该候选人");
      assertThat(error.recoverable()).isFalse();
      assertThat(error.fieldErrors()).isEmpty();
      assertThat(error.getCause()).isNull();
    });
  }

  private ResumeJdScoreBinding claimedScore() {
    var candidate = new Candidate(); candidate.setId("candidate"); candidate.setOwnerUserId("owner");
    candidate.setExt("{\"reclaimedResumeVersionId\":\"resume\",\"reclaimedScoreBindingId\":\"binding\",\"reclaimedAt\":\"2026-09-08T03:00:00Z\"}");
    when(candidates.findCandidate("candidate")).thenReturn(candidate);
    var binding = new ResumeJdScoreBinding(); binding.setId("binding");
    binding.setCandidateId("candidate"); binding.setResumeVersionId("resume");
    binding.setJdVersionId("jd"); binding.setDefaultMatchRunId("new-run");
    binding.setSelectedByUserId("owner"); binding.setSelectedAt(Instant.parse("2026-09-08T02:00:00Z"));
    when(scoreBindings.getById("binding")).thenReturn(binding);
    when(scoreRuns.successfulRunIds(List.of("new-run"))).thenReturn(Set.of("new-run"));
    when(recruitment.states(List.of("candidate"))).thenReturn(Map.of("candidate", List.of(
        new CandidateRecruitmentStateQuery.State("resume", "owner", "TERMINATED", false, false,
            "old-app", "jd", false, Instant.parse("2026-09-08T01:00:00Z")))));
    return binding;
  }

  private void assertScoreRequired(String jdVersionId, String runId) {
    assertThatThrownBy(() -> adapter.requireCurrentScore("candidate", "resume", jdVersionId, runId))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURRENT_RECRUITMENT_SCORE_REQUIRED"));
  }
}
