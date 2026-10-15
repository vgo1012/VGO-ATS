package com.ezaigc.hr.recruiting.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.application.manager.EngagementLockApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.EngagementLockAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.submission.application.manager.SubmissionApplicationManager;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.service.RiskAcknowledgementPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.SendSnapshotPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.SubmissionFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.submission.service.SubmissionPersistenceService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

class SharedCandidateRecruitmentReadTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CandidateLibraryQueryService candidateLibrary = mock(CandidateLibraryQueryService.class);
  private final ApplicationPersistenceService applications = mock(ApplicationPersistenceService.class);
  private final ApplicationCollaboratorPersistenceService collaborators = mock(ApplicationCollaboratorPersistenceService.class);
  private final RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final ParticipantPersistenceService participants = mock(ParticipantPersistenceService.class);
  private final SubmissionPersistenceService submissions = mock(SubmissionPersistenceService.class);
  private final SendSnapshotPersistenceService snapshots = mock(SendSnapshotPersistenceService.class);
  private final SubmissionFeedbackPersistenceService feedback = mock(SubmissionFeedbackPersistenceService.class);
  private final RiskAcknowledgementPersistenceService acknowledgements = mock(RiskAcknowledgementPersistenceService.class);
  private final RecruitmentApplicationManager recruitment = new RecruitmentApplicationManager();
  private final InterviewApplicationManager interviewManager = new InterviewApplicationManager();
  private final SubmissionApplicationManager submissionManager = new SubmissionApplicationManager();
  private final EngagementLockApplicationManager lockManager = new EngagementLockApplicationManager();
  private final EngagementLockPersistenceService locks = mock(EngagementLockPersistenceService.class);
  private final LockHistoryPersistenceService lockHistory = mock(LockHistoryPersistenceService.class);

  @BeforeEach
  void setup() {
    for (var manager : List.of(recruitment, interviewManager, submissionManager, lockManager)) {
      ReflectionTestUtils.setField(manager, "authorization", authorization);
      ReflectionTestUtils.setField(manager, "candidateLibrary", candidateLibrary);
    }
    ReflectionTestUtils.setField(recruitment, "persistence", applications);
    ReflectionTestUtils.setField(recruitment, "applicationCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(recruitment, "recruitmentWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(interviewManager, "persistence", interviews);
    ReflectionTestUtils.setField(interviewManager, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(submissionManager, "persistence", submissions);
    ReflectionTestUtils.setField(submissionManager, "sendSnapshotPersistenceService", snapshots);
    ReflectionTestUtils.setField(submissionManager, "submissionFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(submissionManager, "riskAcknowledgementPersistenceService", acknowledgements);
    ReflectionTestUtils.setField(lockManager, "persistence", locks);
    ReflectionTestUtils.setField(lockManager, "lockHistoryPersistenceService", lockHistory);
    ReflectionTestUtils.setField(lockManager, "assembler", new EngagementLockAssembler());
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot("reader", "reader", "reader@example.test",
        "other-dept", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false));
    doThrow(PlatformExceptions.forbidden("DENIED", "无权限")).when(authorization).requireAccess(anyString(), any());
    doThrow(PlatformExceptions.forbidden("DENIED", "无权限")).when(authorization).requirePermission(anyString());
    when(candidateLibrary.eligibleCandidateIds(any())).thenReturn(Set.of("candidate"));
  }

  @Test
  void sharedApplicationStepsAreReadableWithoutRecruitmentPermissionAndRevokedAfterLeaving() {
    var app = new Application(); app.setId("application"); app.setCandidateId("candidate");
    when(applications.findApplication("application")).thenReturn(app);
    assertThat(recruitment.steps("application")).isEmpty();
    when(candidateLibrary.eligibleCandidateIds(any())).thenReturn(Set.of());
    assertThatThrownBy(() -> recruitment.steps("application")).isInstanceOf(PlatformException.class);
  }

  @Test
  void sharingDoesNotAuthorizeRecruitmentWrites() {
    var app = new Application(); app.setId("application"); app.setCandidateId("candidate");
    when(applications.lockApplication("application")).thenReturn(app);
    assertThatThrownBy(() -> recruitment.requireLockedFollowUpAccess("application"))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void sharingDoesNotAuthorizeSubmissionWrites() {
    var submission = new ClientSubmission(); submission.setId("submission"); submission.setCandidateId("candidate");
    when(submissions.find("submission")).thenReturn(submission);
    assertThatThrownBy(() -> submissionManager.acknowledgeRisk("submission", "RISK", "reason"))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void sharedInterviewIsReadableWithoutRoleOrInvitationAndRevokedAfterLeaving() {
    var interview = new Interview(); interview.setId("interview"); interview.setCandidateId("candidate");
    when(interviews.findInterview("interview")).thenReturn(interview);
    assertThat(interviewManager.get("interview")).isNotNull();
    when(candidateLibrary.eligibleCandidateIds(any())).thenReturn(Set.of());
    assertThatThrownBy(() -> interviewManager.get("interview")).isInstanceOf(PlatformException.class);
  }

  @Test
  void sharedSubmissionIsReadableWithoutPermissionAndRevokedAfterLeaving() {
    var submission = new ClientSubmission(); submission.setId("submission"); submission.setCandidateId("candidate");
    when(submissions.find("submission")).thenReturn(submission);
    assertThat(submissionManager.get("submission")).isNotNull();
    when(candidateLibrary.eligibleCandidateIds(any())).thenReturn(Set.of());
    assertThatThrownBy(() -> submissionManager.get("submission")).isInstanceOf(PlatformException.class);
  }

  @Test
  void sharedOwnershipLockHistoryIsReadableAndRevokedAfterLeaving() {
    when(locks.findByCandidate("candidate")).thenReturn(new EngagementLock());
    assertThat(lockManager.history("candidate", 20)).isEmpty();
    when(candidateLibrary.eligibleCandidateIds(any())).thenReturn(Set.of());
    assertThatThrownBy(() -> lockManager.history("candidate", 20)).isInstanceOf(PlatformException.class);
  }
}
