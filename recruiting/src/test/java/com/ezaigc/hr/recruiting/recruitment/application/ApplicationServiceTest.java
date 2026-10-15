package com.ezaigc.hr.recruiting.recruitment.application;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;
import com.ezaigc.hr.talent.application.adapter.CandidateResumeVersionAdapter;
import com.ezaigc.hr.talent.application.manager.OnboardingReturnApplicationManager;
import com.ezaigc.hr.talent.application.manager.ResumeLifecycleApplicationManager;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.domain.response.OfferStatusResponse;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.FollowUpPlanLifecycleManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.recruiting.activity.application.manager.ActivityOriginManager;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewFeedbackDTO;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.FeedbackVersionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.demand.api.ActiveWorkflowSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.ApplicationAssembler;
import com.ezaigc.hr.talent.api.CandidateAccessPolicy;
import com.ezaigc.hr.talent.api.ResumeLifecycleService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewNotificationApplicationManager;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationType;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.api.ActiveMatchPlanQueryService;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.EngagementLockApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.CreateApplicationDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.OfferCommandDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.ApplicationDetails;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Communication;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Decision;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingEvent;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OnboardingProcess;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItem;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkItemRow;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.CommunicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.DecisionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.IntentionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OfferStatusRecordPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingEventPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingProcessPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.impl.RecruitmentWorkflowStepPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;
import com.ezaigc.hr.recruiting.submission.application.adapter.ClientSubmissionValidationAdapter;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CandidateOnboardingReturnService;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningTransitionService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.BeanUtils;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.identity.application.adapter.IdentityAuthorizationAdapter;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import java.util.Optional;

import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDetailsResponse;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.transaction.support.SmartTransactionObject;

import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewExternalCalendarApplicationManager;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.dto.CreateInterviewDTO;
import com.ezaigc.hr.recruiting.interview.service.InterviewIdempotencyPersistenceService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.ezaigc.hr.recruiting.submission.application.adapter.ClientSubmissionInterviewValidationAdapter;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.service.SubmissionPersistenceService;

class ApplicationServiceTest {
  private static final String ACTOR = "A".repeat(26),
      DEPARTMENT = "D".repeat(26),
      CANDIDATE = "C".repeat(26);
  private static final String JD_VERSION = "J".repeat(26),
      JD = "K".repeat(26),
      DEMAND = "M".repeat(26),
      RESUME = "R".repeat(26);
  private final ApplicationPersistenceService persistence =
      mock(ApplicationPersistenceService.class);
  private final ApplicationCollaboratorPersistenceService collaborators =
      mock(ApplicationCollaboratorPersistenceService.class);
  private final CommunicationPersistenceService communications =
      mock(CommunicationPersistenceService.class);
  private final DecisionPersistenceService decisions = mock(DecisionPersistenceService.class);
  private final FollowUpPersistenceService followUps = mock(FollowUpPersistenceService.class);
  private final IntentionPersistenceService intentions = mock(IntentionPersistenceService.class);
  private final OfferStatusRecordPersistenceService offers =
      mock(OfferStatusRecordPersistenceService.class);
  private final OnboardingEventPersistenceService onboarding =
      mock(OnboardingEventPersistenceService.class);
  private final OnboardingProcessPersistenceService onboardingProcesses =
      mock(OnboardingProcessPersistenceService.class);
  private final RecruitmentIdempotencyPersistenceService idempotency =
      mock(RecruitmentIdempotencyPersistenceService.class);
  private final RecruitmentOwnershipHistoryPersistenceService ownership =
      mock(RecruitmentOwnershipHistoryPersistenceService.class);
  private final RecruitmentWorkflowStepPersistenceService workflowSteps =
      mock(RecruitmentWorkflowStepPersistenceService.class);
  private final StageHistoryPersistenceService stageHistory =
      mock(StageHistoryPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final IdentityDirectoryService directory = mock(IdentityDirectoryService.class);
  private final CandidateDirectoryService candidates = mock(CandidateDirectoryService.class);
  private final CandidateOnboardingReturnService candidateOnboardingReturns =
      mock(CandidateOnboardingReturnService.class);
  private final CandidateAccessPolicy candidateAccess = mock(CandidateAccessPolicy.class);
  private final CandidateResumeVersionService resumes = mock(CandidateResumeVersionService.class);
  private final CandidateScreeningTransitionService screeningTransitions = mock(CandidateScreeningTransitionService.class);
  private final DemandQueryService demands = mock(DemandQueryService.class);
  private final ActiveMatchPlanQueryService matchPlans = mock(ActiveMatchPlanQueryService.class);
  private final CompanyBlacklistQueryService blacklist = mock(CompanyBlacklistQueryService.class);
  private final ClientSubmissionValidationAdapter submissionValidator =
      mock(ClientSubmissionValidationAdapter.class);
  private final EngagementLockApplicationManager locks =
      mock(EngagementLockApplicationManager.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final InterviewNotificationApplicationManager interviewNotifications = mock(InterviewNotificationApplicationManager.class);
  private final CursorCodec cursorCodec =
      new SignedCursorCodec("application-service-test-signing-key-32-bytes");
  private final Instant now = Instant.parse("2026-09-02T00:00:00Z");
  private RecruitmentApplicationManager service;

  @BeforeEach
  void setUp() {
    when(persistence.enableProgressiveStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(demands.requireJdVersionForNewUse(anyString()))
        .thenAnswer(call -> withHeadcount(demands.requireJdVersion(call.getArgument(0))));
    when(demands.lockJdForHeadcount(anyString()))
        .thenAnswer(call -> {
          JdVersionSnapshot jd = demands.requireJdVersion(call.getArgument(0));
          if (jd == null) {
            jd = new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of());
          }
          return withHeadcount(jd);
        });
    when(offers.latestOfferForUpdate(anyString()))
        .thenAnswer(call -> offers.latestOffer(call.getArgument(0)));
    when(persistence.findJdVersionId(anyString())).thenReturn(JD_VERSION);
    when(authorization.currentActor())
        .thenReturn(
            new CurrentActorSnapshot(
                ACTOR,
                "HR",
                "hr@example.com",
                DEPARTMENT,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("RECRUITER"),
                Set.of("recruitment.manage", "demand.read"),
                false,
                false));
    when(directory.activeUser(ACTOR))
        .thenReturn(new IdentityDirectoryService.UserReference(ACTOR, "HR", DEPARTMENT));
    when(ids.newId()).thenReturn("I".repeat(26));
    when(persistence.insertIdempotency(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(communications.insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
    when(followUps.insertFollowUp(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
    when(onboardingProcesses.insertProcess(anyString(), any(), anyString(), any())).thenReturn(1);
    when(onboardingProcesses.lockProcess(anyString()))
        .thenAnswer(call -> onboardingProcess(call.getArgument(0), "IN_PROGRESS", now.minusSeconds(7200), null));
    when(onboardingProcesses.markOnboarded(anyString(), any(), anyString(), anyLong(), any()))
        .thenReturn(1);
    when(onboardingProcesses.markReturned(anyString(), anyString(), anyLong(), any())).thenReturn(1);
    service = new RecruitmentApplicationManager();
    ReflectionTestUtils.setField(service, "assembler", new ApplicationAssembler());
    ReflectionTestUtils.setField(service, "followUpPlanLifecycle", mock(FollowUpPlanLifecycleManager.class));
    ReflectionTestUtils.setField(service, "persistence", persistence);
    ReflectionTestUtils.setField(service, "interviewPersistenceService", interviews);
    ReflectionTestUtils.setField(service, "interviewNotificationManager", interviewNotifications);
    when(workflowSteps.lockSteps(any())).thenAnswer(call -> workflowSteps.findSteps(call.getArgument(0)));
    when(workflowSteps.bindClientSubmission(any(), any(), anyLong(), any(), any())).thenReturn(1);
    ReflectionTestUtils.setField(service, "applicationCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(service, "communicationPersistenceService", communications);
    ReflectionTestUtils.setField(service, "decisionPersistenceService", decisions);
    ReflectionTestUtils.setField(service, "followUpPersistenceService", followUps);
    ReflectionTestUtils.setField(service, "intentionPersistenceService", intentions);
    ReflectionTestUtils.setField(service, "offerStatusRecordPersistenceService", offers);
    ReflectionTestUtils.setField(service, "onboardingEventPersistenceService", onboarding);
    ReflectionTestUtils.setField(service, "onboardingProcessPersistenceService", onboardingProcesses);
    ReflectionTestUtils.setField(service, "recruitmentIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(service, "recruitmentOwnershipHistoryPersistenceService", ownership);
    ReflectionTestUtils.setField(service, "recruitmentWorkflowStepPersistenceService", workflowSteps);
    ReflectionTestUtils.setField(service, "stageHistoryPersistenceService", stageHistory);
    ReflectionTestUtils.setField(service, "authorization", authorization);
    ReflectionTestUtils.setField(service, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(service, "directory", directory);
    ReflectionTestUtils.setField(service, "candidates", candidates);
    ReflectionTestUtils.setField(service, "blacklist", blacklist);
    when(persistence.findCandidateId(anyString())).thenReturn("C".repeat(26));
    ReflectionTestUtils.setField(service, "candidateOnboardingReturns", candidateOnboardingReturns);
    ReflectionTestUtils.setField(service, "candidateAccess", candidateAccess);
    ReflectionTestUtils.setField(service, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(service,"lifecycle",mock(ResumeLifecycleService.class));
    ReflectionTestUtils.setField(service, "resumes", resumes);
    ReflectionTestUtils.setField(service, "screeningTransitions", screeningTransitions);
    ReflectionTestUtils.setField(service, "demands", demands);
    ReflectionTestUtils.setField(service, "matchPlans", matchPlans);
    ReflectionTestUtils.setField(service, "submissionValidator", submissionValidator);
    ReflectionTestUtils.setField(service, "engagementLocks", locks);
    ReflectionTestUtils.setField(service, "ids", ids);
    ReflectionTestUtils.setField(service, "cursorCodec", cursorCodec);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void superAdminViewAccessDoesNotGrantJdProcessingAccess() {
    String owner = "O".repeat(26);
    when(authorization.currentActor())
        .thenReturn(new CurrentActorSnapshot(ACTOR, "管理员", "admin@example.com", DEPARTMENT,
            Set.of(), Set.of(), Set.of(), Set.of("SUPER_ADMIN"), Set.of("recruitment.manage", "demand.read"),
            true, true));
    when(demands.requireJdVersion(JD_VERSION))
        .thenReturn(new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", owner, DEPARTMENT, Set.of()));

    assertThat(service.canProcessJdVersion(JD_VERSION)).isFalse();
  }

  @Test
  void explicitDemandCollaboratorCanProcessJd() {
    String owner = "O".repeat(26);
    when(demands.requireJdVersion(JD_VERSION))
        .thenReturn(new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", owner, DEPARTMENT, Set.of(ACTOR)));

    assertThat(service.canProcessJdVersion(JD_VERSION)).isTrue();
  }

  @Test
  void legacyPublicFlagDoesNotGrantApplicationCreation() {
    JdVersionSnapshot jd = new JdVersionSnapshot(JD_VERSION, JD, null, "INTERNAL",
        "O".repeat(26), DEPARTMENT, Set.of());
    jd.setPublicVisible(true);
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(jd);

    assertThat(service.canProcessJdVersion(JD_VERSION)).isFalse();
    assertThatThrownBy(() -> service.create(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "历史公共标记"), "public-jd"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
    verify(persistence, never()).insertApplication(any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void deletedPublicJdCannotCreateNewApplication() {
    JdVersionSnapshot jd = new JdVersionSnapshot(JD_VERSION, JD, null, "INTERNAL",
        ACTOR, DEPARTMENT, Set.of());
    jd.setPublicVisible(true);
    jd.setDeleted(true);
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(jd);

    assertThat(service.canProcessJdVersion(JD_VERSION)).isFalse();
    assertThatThrownBy(() -> service.create(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "已删除岗位"), "deleted-jd"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("JD_DELETED"));
    verify(persistence, never()).insertApplication(any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(matchPlans);
  }

  @Test
  void superAdminCannotCreateOrAdvanceForAnotherHrWithoutCollaboration() {
    String owner = "O".repeat(26);
    CurrentActorSnapshot admin = new CurrentActorSnapshot(ACTOR, "管理员", "admin@example.com", DEPARTMENT,
        Set.of(), Set.of(), Set.of(), Set.of("SUPER_ADMIN"), Set.of("recruitment.manage", "demand.read"),
        true, true);
    when(authorization.currentActor()).thenReturn(admin);
    when(candidates.requireActive(CANDIDATE))
        .thenReturn(new CandidateDirectoryService.CandidateRef(CANDIDATE, "候选人"));
    when(resumes.requireAvailable(CANDIDATE, RESUME))
        .thenReturn(new CandidateResumeVersionService.ResumeVersionRef(RESUME, CANDIDATE));
    when(demands.requireJdVersion(JD_VERSION))
        .thenReturn(new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", owner, DEPARTMENT, Set.of()));

    assertThatThrownBy(() -> service.create(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "管理员尝试代推进"), "admin-create"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
    verifyNoInteractions(matchPlans);

    String applicationId = "P".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId, owner, now));
    when(collaborators.findCollaborators(applicationId)).thenReturn(List.of());
    assertThatThrownBy(() -> service.startStep(applicationId, "S".repeat(26), 1L))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
    verify(workflowSteps, never()).findStep(any(), any());
    assertThatThrownBy(() -> service.requireLockedFollowUpAccess(applicationId))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
  }

  private static JdVersionSnapshot withHeadcount(JdVersionSnapshot jd) {
    if (jd != null && jd.getHeadcount() == null) {
      jd.setHeadcount(10000);
    }
    return jd;
  }

  @Test
  void followUpReplayAccessChecksCurrentOwnerWithoutRequiringOpenApplication() {
    String applicationId="P".repeat(26);
    Application app=application(applicationId,ACTOR,now);
    app.setTerminalAt(now);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(collaborators.findCollaborators(applicationId)).thenReturn(List.of());

    assertThat(service.requireLockedFollowUpAccess(applicationId)).isSameAs(app);

    verify(persistence).lockApplication(applicationId);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void terminationAccessLocksJdBeforeApplicationAndStillRequiresProcessingPermission(boolean ownsApplication) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ownsApplication ? ACTOR : "O".repeat(26), now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(collaborators.findCollaboratorsForUpdate(applicationId)).thenReturn(List.of());

    if (ownsApplication) {
      assertThat(service.requireLockedTerminationAccess(applicationId)).isSameAs(app);
    } else {
      assertThatThrownBy(() -> service.requireLockedTerminationAccess(applicationId))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
    }
    var order = Mockito.inOrder(persistence, demands, authorization);
    order.verify(persistence).findJdVersionId(applicationId);
    order.verify(demands).lockJdForHeadcount(JD_VERSION);
    order.verify(persistence).lockApplication(applicationId);
    order.verify(authorization).requireAccess(eq("recruitment.manage"), any(AuthorizationTarget.class));
    verifyNoInteractions(decisions, offers, interviews);
  }

  @ParameterizedTest
  @CsvSource({"REJECT,false", "WITHDRAW,false", "REJECT,true", "WITHDRAW,true"})
  void blacklistDoesNotPreventExplicitTerminalDecision(String value, boolean correction) {
    CompanyBlacklistQueryService blocked = blockingCompanyPolicy();
    ReflectionTestUtils.setField(service, "blacklist", blocked);
    String applicationId = "P".repeat(26), priorId = "B".repeat(26), recordId = "I".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("DECISION");
    WorkflowStep step = new WorkflowStep();
    step.setId("S".repeat(26));
    step.setStepCode("DECISION");
    step.setStatus("PENDING");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(persistence.updateOutcome(eq(applicationId), eq("TERMINATED"), eq(value), eq(now),
        eq(null), anyLong(), eq(ACTOR), eq(now))).thenReturn(1);
    when(decisions.insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(workflowSteps.finishDecisionStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    Decision prior = new Decision(priorId, applicationId, "DECISION", "PASS", "原决定",
        null, null, ACTOR, now);
    Decision expected = new Decision(recordId, applicationId, correction ? "CORRECTION" : "DECISION",
        value, "人工逐条处置", correction ? priorId : null, null, ACTOR, now);
    when(decisions.findDecision(applicationId, priorId)).thenReturn(prior);
    when(decisions.findDecision(applicationId, recordId)).thenReturn(expected);


    Decision actual = correction
        ? service.correctDecision(applicationId, priorId, value, "人工逐条处置", null, "terminal-correction")
        : service.decide(applicationId, value, "人工逐条处置", null, "terminal-decision");

    assertThat(actual).isSameAs(expected);
    verify(decisions).insertDecision(recordId, applicationId, correction ? "CORRECTION" : "DECISION",
        value, "人工逐条处置", correction ? priorId : null, null, ACTOR, now);
    verify(persistence).updateOutcome(eq(applicationId), eq("TERMINATED"), eq(value), eq(now),
        eq(null), anyLong(), eq(ACTOR), eq(now));
    verify(stageHistory).insertStageHistory(recordId, applicationId, "DECISION", "TERMINATED",
        "人工逐条处置", ACTOR, now);
    verify(blocked).lockCandidateForCommand("C".repeat(26));
    verify(blocked, never()).requireRecruitmentAllowed(any());
  }

  @ParameterizedTest
  @CsvSource({"DECIDE_REJECT,1,1,false", "DECIDE_WITHDRAW,1,1,false", "CORRECT_REJECT,1,1,false", "CORRECT_WITHDRAW,1,1,false",
      "OFFER_TERMINATE,1,1,false", "OFFER_CORRECT_TERMINATE,1,1,false", "DECIDE_WITHDRAW,0,1,false", "DECIDE_WITHDRAW,1,0,false",
      "DECIDE_WITHDRAW,1,1,true"})
  void terminalCommandsCancelAllRemainingInterviewPagesAtomically(
      String scenario, int cancelledRows, int historyRows, boolean notificationFails) {
    String applicationId = "P".repeat(26), priorId = "B".repeat(26), reason = "逐条终止当前招聘活动";
    Application app = application(applicationId, ACTOR, now);
    boolean offerCommand = scenario.startsWith("OFFER");
    app.setCurrentStage(offerCommand ? "OFFER" : "DECISION");
    WorkflowStep step = pathStep("S".repeat(26), "DECISION", 1, "PENDING", true);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(workflowSteps.finishDecisionStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(decisions.insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(decisions.findDecision(applicationId, priorId)).thenReturn(new Decision(priorId, applicationId,
        "DECISION", "PASS", "原决定", null, null, ACTOR, now.minusSeconds(60)));
    OfferStatusRecord prior = new OfferStatusRecord();
    prior.setId(priorId);
    prior.setApplicationId(applicationId);
    prior.setStatus("IN_PROGRESS");
    prior.setOccurredAt(now.minusSeconds(60));
    if (offerCommand) {
      when(offers.latestOffer(applicationId)).thenReturn(prior);
      when(offers.findOffer(applicationId, priorId)).thenReturn(prior);
    }
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    List<Interview> scheduled = IntStream.rangeClosed(1, 101).mapToObj(index -> {
      Interview interview = new Interview();
      interview.setId(String.format("%026d", index));
      interview.setApplicationId(applicationId);
      interview.setWorkflowStepId("T".repeat(26));
      interview.setStatus("SCHEDULED");
      interview.setVersion(3L);
      return interview;
    }).toList();
    when(interviews.lockScheduledApplicationInterviews(applicationId, null)).thenReturn(scheduled.subList(0, 100));
    when(interviews.lockScheduledApplicationInterviews(applicationId, scheduled.get(99).getId()))
        .thenReturn(scheduled.subList(100, 101));
    when(interviews.cancel(any(), eq(3L), eq(ACTOR), eq(now))).thenReturn(cancelledRows);
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(historyRows);
    if (notificationFails) {
      doThrow(new IllegalStateException("面试通知意图批量写入失败")).when(interviewNotifications)
          .createNotificationsBatch(anyList(), eq(InterviewNotificationType.INTERVIEW_CANCEL), eq(ACTOR));
    }
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactional = (RecruitmentApplicationManager) proxyFactory.getProxy();
    String value = scenario.endsWith("REJECT") ? "REJECT" : "WITHDRAW";
    OfferCommandDTO offer = new OfferCommandDTO("TERMINATED", "OTHER", "WITHDRAW", reason, now, null, null);
    Supplier<Object> command = () -> switch (scenario) {
      case "DECIDE_REJECT", "DECIDE_WITHDRAW" -> transactional.decide(applicationId, value, reason, null, "terminal-interviews");
      case "CORRECT_REJECT", "CORRECT_WITHDRAW" -> transactional.correctDecision(applicationId, priorId, value, reason, null, "terminal-interviews");
      case "OFFER_TERMINATE" -> transactional.addOffer(applicationId, offer, "terminal-interviews");
      case "OFFER_CORRECT_TERMINATE" -> transactional.correctOffer(applicationId, priorId, offer, "terminal-interviews");
      default -> throw new AssertionError(scenario);
    };

    if (cancelledRows != 1 || historyRows != 1 || notificationFails) {
      if (cancelledRows != 1) {
        assertThatThrownBy(command::get).isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
      } else {
        assertThatThrownBy(command::get).isInstanceOf(IllegalStateException.class)
            .hasMessage(notificationFails ? "面试通知意图批量写入失败" : "招聘活动终止时面试取消历史写入失败");
      }
      verify(transactions).rollback(transaction);
      verify(transactions, never()).commit(any());
      return;
    }
    command.get();
    verify(transactions).commit(transaction);
    var order = Mockito.inOrder(persistence, interviews);
    order.verify(persistence).lockApplication(applicationId);
    order.verify(interviews).lockScheduledApplicationInterviews(applicationId, null);
    for (Interview interview : scheduled) {
      verify(interviews).cancel(interview.getId(), 3L, ACTOR, now);
      verify(interviews).insertHistory(any(), eq(interview.getId()), eq("CANCEL"),
          ArgumentMatchers.contains("SCHEDULED"), ArgumentMatchers.contains("CANCELLED"),
          eq(reason), eq(ACTOR), eq(now));
    }
    verify(interviews).lockScheduledApplicationInterviews(applicationId, scheduled.get(99).getId());
    verify(interviewNotifications).createNotificationsBatch(scheduled.subList(0, 100), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR);
    verify(interviewNotifications).createNotificationsBatch(scheduled.subList(100, 101), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR);
    verify(blacklist, never()).requireRecruitmentAllowed(any());
  }

  @ParameterizedTest
  @CsvSource({
      "false,PASS,offer,0", "false,PASS,offer,1", "false,PASS,offer,2",
      "false,PASS,history,0", "false,PASS,history,1", "false,PASS,history,2",
      "false,REJECT,history,0", "false,REJECT,history,1", "false,REJECT,history,2",
      "true,REJECT,decision,0", "true,REJECT,decision,1", "true,REJECT,decision,2",
      "true,WITHDRAW,offer,0", "true,WITHDRAW,offer,1", "true,WITHDRAW,offer,2",
      "true,REJECT,history,0", "true,REJECT,history,1", "true,REJECT,history,2",
      "true,PASS,offer,0", "true,PASS,offer,1", "true,PASS,offer,2",
      "true,PASS,history,0", "true,PASS,history,1", "true,PASS,history,2"})
  void decisionTransitionsRequireCompleteFactsBeforeCommitting(
      boolean correction, String value, String checkedWrite, int changedRows) {
    decisionTransitionsRequireCompleteFactsBeforeCommitting(correction, value, checkedWrite, changedRows, "INTERNAL");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void externalPassingDecisionNeedsNoRetiredRecommendation(boolean correction) {
    decisionTransitionsRequireCompleteFactsBeforeCommitting(correction, "PASS", "history", 1, "EXTERNAL");
  }

  private void decisionTransitionsRequireCompleteFactsBeforeCommitting(
      boolean correction, String value, String checkedWrite, int changedRows, String recruitmentType) {
    String applicationId = "P".repeat(26), priorId = "B".repeat(26), recordId = "I".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setRecruitmentType(recruitmentType);
    app.setCurrentStage(correction && !"PASS".equals(value) ? "OFFER" : "DECISION");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    WorkflowStep step = pathStep("S".repeat(26), "DECISION", 1, "PENDING", false);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(workflowSteps.finishDecisionStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    OfferStatusRecord offer = new OfferStatusRecord();
    offer.setId("O".repeat(26));
    offer.setApplicationId(applicationId);
    offer.setStatus("NOT_STARTED");
    if ("OFFER".equals(app.getCurrentStage())) {
      when(offers.latestOffer(applicationId)).thenReturn(offer);
    }
    Decision prior = new Decision(priorId, applicationId, "DECISION", "PASS", "原决定",
        null, null, ACTOR, now.minusSeconds(60));
    Decision expected = new Decision(recordId, applicationId, correction ? "CORRECTION" : "DECISION",
        value, "人工决定及其事实", correction ? priorId : null, null, ACTOR, now);
    when(decisions.findDecision(applicationId, priorId)).thenReturn(prior);
    when(decisions.findDecision(applicationId, recordId)).thenReturn(expected);
    when(decisions.insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("decision".equals(checkedWrite) ? changedRows : 1);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn("offer".equals(checkedWrite) ? changedRows : 1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("history".equals(checkedWrite) ? changedRows : 1);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService = (RecruitmentApplicationManager) proxyFactory.getProxy();
    Supplier<Decision> command = () -> correction
        ? transactionalService.correctDecision(applicationId, priorId, value, "人工决定及其事实", null, "decision-facts")
        : transactionalService.decide(applicationId, value, "人工决定及其事实", null, "decision-facts");

    if (changedRows == 1) {
      assertThat(command.get()).isSameAs(expected);
      if (correction && "OFFER".equals(app.getCurrentStage())) {
        verify(workflowSteps, never()).finishDecisionStep(any(), any(), any(), any(), any(), any());
      }
      verify(transactions).commit(transaction);
      verify(transactions, never()).rollback(any());
      return;
    }
    String expectedMessage = switch (checkedWrite) {
      case "decision" -> "人工决定更正记录写入失败";
      case "offer" -> "Offer 状态记录写入失败";
      case "history" -> "Application 阶段历史写入失败";
      default -> throw new AssertionError(checkedWrite);
    };
    assertThatThrownBy(command::get).isInstanceOf(IllegalStateException.class).hasMessage(expectedMessage);
    verify(transactions).rollback(transaction);
    verify(transactions, never()).commit(any());
    verify(decisions, never()).findDecision(applicationId, recordId);
    if ("decision".equals(checkedWrite)) {
      verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
      verifyNoInteractions(stageHistory);
    }
  }

  @ParameterizedTest
  @CsvSource({"PASS,0", "PASS,1", "PASS,2", "REJECT,0", "REJECT,1", "REJECT,2",
      "WITHDRAW,0", "WITHDRAW,1", "WITHDRAW,2"})
  void correctionCompletesReopenedDecisionWorkItemAtomically(String value, int changedRows) {
    String applicationId = "P".repeat(26), priorId = "B".repeat(26), recordId = "I".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("DECISION");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    WorkflowStep step = pathStep("S".repeat(26), "DECISION", 1, "PENDING", false);
    if ("WITHDRAW".equals(value)) {
      step.setStatus("IN_PROGRESS");
      step.setStartedAt(now.minusSeconds(30));
    }
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(workflowSteps.finishDecisionStep(any(), any(), any(), any(), any(), any())).thenReturn(changedRows);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(decisions.findDecision(applicationId, priorId)).thenReturn(new Decision(priorId, applicationId,
        "DECISION", "REJECT", "已重新开启前的决定", null, null, ACTOR, now.minusSeconds(60)));
    Decision expected = new Decision(recordId, applicationId, "CORRECTION", value, "重新开启后人工更正",
        priorId, null, ACTOR, now);
    when(decisions.findDecision(applicationId, recordId)).thenReturn(expected);
    when(decisions.insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService = (RecruitmentApplicationManager) proxyFactory.getProxy();
    Supplier<Decision> command = () -> transactionalService.correctDecision(
        applicationId, priorId, value, "重新开启后人工更正", null, "reopened-correction");

    if (changedRows == 1) {
      assertThat(command.get()).isSameAs(expected);
      verify(workflowSteps).finishDecisionStep(step.getId(), "PASS".equals(value) ? "PASSED" : "FAILED",
          "重新开启后人工更正", null, ACTOR, now);
      verify(transactions).commit(transaction);
      verify(transactions, never()).rollback(any());
    } else {
      assertThatThrownBy(command::get).isInstanceOfSatisfying(PlatformException.class,
          error -> assertThat(error.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
      verify(transactions).rollback(transaction);
      verify(transactions, never()).commit(any());
      verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
      verify(decisions, never()).findDecision(applicationId, recordId);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"MISSING", "AVAILABLE", "PASSED", "FAILED", "WITHDRAWN", "SKIPPED"})
  void correctionCannotOverwriteAnInactiveDecisionStep(String status) {
    String applicationId = "P".repeat(26), priorId = "B".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("DECISION");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(decisions.findDecision(applicationId, priorId)).thenReturn(new Decision(priorId, applicationId,
        "DECISION", "REJECT", "原决定", null, null, ACTOR, now.minusSeconds(60)));
    when(decisions.insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(workflowSteps.findSteps(applicationId)).thenReturn("MISSING".equals(status) ? List.of()
        : List.of(pathStep("S".repeat(26), "DECISION", 1, status, false)));

    assertThatThrownBy(() -> service.correctDecision(applicationId, priorId, "REJECT", "更正决定", null, "inactive-step"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code())
            .isEqualTo("MISSING".equals(status) ? "DECISION_STEP_MISSING" : "DECISION_STEP_NOT_ACTIVE"));
    verify(workflowSteps, never()).finishDecisionStep(any(), any(), any(), any(), any(), any());
    verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
    verifyNoInteractions(stageHistory);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 60})
  void terminatedOfferAndApplicationRemainArchived(int recordedClockSkew) {
    var serverTime = new AtomicReference<>(now);
    Clock advancingClock = mock(Clock.class);
    when(advancingClock.instant()).thenAnswer(call -> serverTime.get());
    ReflectionTestUtils.setField(service, "clock", advancingClock);
    String applicationId = "P".repeat(26), decisionId = "B".repeat(26);
    Application stored = application(applicationId, ACTOR, now);
    stored.setCreateTime(now.minusSeconds(3600));
    stored.setCurrentStage("OFFER");
    stored.setOutcome("PASS");
    Supplier<Application> snapshot = () -> {
      Application copy = new Application();
      BeanUtils.copyProperties(stored, copy);
      return copy;
    };
    when(persistence.lockApplication(applicationId)).thenAnswer(call -> snapshot.get());
    when(persistence.findApplication(applicationId)).thenAnswer(call -> snapshot.get());
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenAnswer(call -> {
          assertThat((Long) call.getArgument(5)).isEqualTo(stored.getVersion());
          stored.setCurrentStage(call.getArgument(1));
          stored.setOutcome(call.getArgument(2));
          stored.setTerminalAt(call.getArgument(3));
          Integer activeSlot = call.getArgument(4);
          stored.setActiveSlot(activeSlot == null ? null : activeSlot.longValue());
          stored.setVersion(stored.getVersion() + 1);
          return 1;
        });
    var sequence = new AtomicInteger(10);
    when(ids.newId()).thenAnswer(call -> String.format("%026d", sequence.incrementAndGet()));
    WorkflowStep offerStep = pathStep("T".repeat(26), "OFFER", 2, "IN_PROGRESS", false);
    WorkflowStep decisionStep = pathStep("S".repeat(26), "DECISION", 1, "PASSED", false);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(decisionStep, offerStep));
    when(workflowSteps.finishOfferStep(any(), any(), any(), any(), any())).thenAnswer(call -> {
      offerStep.setStatus(call.getArgument(1));
      return 1;
    });
    when(persistence.markOfferInProgress(any(), any(), any(), any(), any())).thenAnswer(call -> {
      offerStep.setStatus("IN_PROGRESS");
      return 1;
    });
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    List<OfferStatusRecord> offerFacts = new ArrayList<>();
    OfferStatusRecord initial = new OfferStatusRecord("0".repeat(26), applicationId, "STATUS", "IN_PROGRESS",
        null, "PASS", "开始Offer", null, null, "MANUAL_BEISEN_REFERENCE", null, null, now.minusSeconds(120), ACTOR);
    initial.setCreateTime(now.minusSeconds(120));
    offerFacts.add(initial);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenAnswer(call -> {
          OfferStatusRecord fact = new OfferStatusRecord(call.getArgument(0), call.getArgument(1), call.getArgument(2),
              call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6), call.getArgument(7),
              call.getArgument(8), call.getArgument(9), call.getArgument(10), call.getArgument(11), call.getArgument(12),
              call.getArgument(13));
          fact.setCreateTime(call.getArgument(14));
          offerFacts.add(fact);
          return 1;
        });
    when(offers.findOffers(applicationId)).thenAnswer(call -> List.copyOf(offerFacts));
    when(offers.findOffer(eq(applicationId), any())).thenAnswer(call -> offerFacts.stream()
        .filter(fact -> fact.getId().equals(call.getArgument(1))).findFirst().orElse(null));
    // 与持久化层单独验证的查询契约一致：系统写入时间决定生效顺序。
    when(offers.latestOffer(applicationId)).thenAnswer(call -> offerFacts.stream().max(
        Comparator.comparing(OfferStatusRecord::getCreateTime).thenComparing(OfferStatusRecord::getId)).orElse(null));
    Decision priorDecision = new Decision(decisionId, applicationId, "DECISION", "PASS", "原录用决定",
        null, null, ACTOR, now.minusSeconds(180));
    List<Decision> decisionFacts = new ArrayList<>(List.of(priorDecision));
    when(decisions.findDecisions(applicationId)).thenAnswer(call -> List.copyOf(decisionFacts));
    when(decisions.findDecision(eq(applicationId), any())).thenAnswer(call -> decisionFacts.stream()
        .filter(fact -> fact.getId().equals(call.getArgument(1))).findFirst().orElse(null));
    when(decisions.insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      decisionFacts.add(new Decision(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3),
          call.getArgument(4), call.getArgument(5), call.getArgument(6), call.getArgument(7), call.getArgument(8)));
      return 1;
    });

    Instant terminalTime = now.plusSeconds(recordedClockSkew);
    OfferStatusRecord terminated = service.addOffer(applicationId,
        new OfferCommandDTO("TERMINATED", "OTHER", "WITHDRAW", "结束原Offer", terminalTime, null, null), "terminate-chain");
    assertThat(stored.getCurrentStage()).isEqualTo("TERMINATED");
    assertThat(offerStep.getStatus()).isEqualTo("FAILED");
    assertThatThrownBy(() -> service.correctOffer(applicationId, terminated.getId(),
        new OfferCommandDTO("IN_PROGRESS", null, null, "尝试恢复旧流程", now, null, null),
        "archived-offer"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_ARCHIVED"));
    assertThat(stored.getCurrentStage()).isEqualTo("TERMINATED");
    assertThat(offerStep.getStatus()).isEqualTo("FAILED");
    assertThat(terminated.getStatus()).isEqualTo("TERMINATED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"TERMINATED", "IN_PROGRESS", "ACCEPTED"})
  void archivedOfferCannotBeCorrectedFromAnyHistoricalCycle(String latestStatus) {
    String applicationId = "P".repeat(26), oldTerminationId = "B".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("TERMINATED");
    app.setTerminalAt(now.minusSeconds(30));
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    OfferStatusRecord historical = new OfferStatusRecord();
    historical.setId(oldTerminationId);
    historical.setApplicationId(applicationId);
    historical.setStatus("TERMINATED");
    OfferStatusRecord latest = new OfferStatusRecord();
    latest.setId("L".repeat(26));
    latest.setApplicationId(applicationId);
    latest.setStatus(latestStatus);
    when(offers.findOffer(applicationId, oldTerminationId)).thenReturn(historical);
    when(offers.latestOffer(applicationId)).thenReturn(latest);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    assertThatThrownBy(() -> service.correctOffer(applicationId, oldTerminationId,
        new OfferCommandDTO("IN_PROGRESS", null, null, "尝试恢复旧流程", now, null, null),
        "archived-offer-correction"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_ARCHIVED"));
    verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
    verify(offers, never()).insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any());
    assertThat(historical.getStatus()).isEqualTo("TERMINATED");
    verifyNoInteractions(stageHistory);
  }

  @ParameterizedTest
  @CsvSource({
      "ADVANCE,offer,0", "ADVANCE,offer,1", "ADVANCE,offer,2",
      "TERMINATE,offer,0", "TERMINATE,offer,1", "TERMINATE,offer,2",
      "TERMINATE,history,0", "TERMINATE,history,1", "TERMINATE,history,2",
      "CORRECT,offer,0", "CORRECT,offer,1", "CORRECT,offer,2",
      "CORRECT_TERMINATE,offer,0", "CORRECT_TERMINATE,offer,1", "CORRECT_TERMINATE,offer,2",
      "CORRECT_TERMINATE,history,0", "CORRECT_TERMINATE,history,1", "CORRECT_TERMINATE,history,2",
      "ONBOARD,event,0", "ONBOARD,event,1", "ONBOARD,event,2",
      "ONBOARD,offer,0", "ONBOARD,offer,1", "ONBOARD,offer,2",
      "ONBOARD,history,0", "ONBOARD,history,1", "ONBOARD,history,2"})
  void offerLifecycleRequiresCompleteFactsBeforeCommitting(String scenario, String checkedWrite, int changedRows) {
    String applicationId = "P".repeat(26), priorId = "B".repeat(26), recordId = "I".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    OfferStatusRecord prior = new OfferStatusRecord();
    prior.setId(priorId);
    prior.setApplicationId(applicationId);
    prior.setStatus(switch (scenario) {
      case "ADVANCE" -> "NOT_STARTED";
      case "ONBOARD" -> "offer".equals(checkedWrite) ? "ACCEPTED" : "ISSUED";
      default -> "IN_PROGRESS";
    });
    prior.setOccurredAt(now.minusSeconds(60));
    when(offers.findOffer(applicationId, priorId)).thenReturn(prior);
    when(offers.latestOffer(applicationId)).thenReturn(prior);
    OfferStatusRecord expectedOffer = new OfferStatusRecord();
    expectedOffer.setId(recordId);
    expectedOffer.setApplicationId(applicationId);
    when(offers.findOffer(applicationId, recordId)).thenReturn(expectedOffer);
    OnboardingEvent expectedEvent = new OnboardingEvent(recordId, applicationId, now,
        "MANUAL_BEISEN_REFERENCE", null, "招聘事实必须完整", ACTOR);
    when(onboarding.findOnboarding(applicationId)).thenReturn(expectedEvent);
    when(onboarding.insertOnboarding(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("event".equals(checkedWrite) ? changedRows : 1);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn("offer".equals(checkedWrite) ? changedRows : 1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn("history".equals(checkedWrite) ? changedRows : 1);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService = (RecruitmentApplicationManager) proxyFactory.getProxy();
    boolean terminal = scenario.endsWith("TERMINATE");
    String targetStatus = terminal ? "TERMINATED" : "ADVANCE".equals(scenario) ? "ISSUED" : "IN_PROGRESS";
    OfferCommandDTO input = new OfferCommandDTO(targetStatus,
        terminal ? "OTHER" : null, terminal ? "WITHDRAW" : null, "招聘事实必须完整", now, null, null);
    Supplier<Object> command = () -> switch (scenario) {
      case "ADVANCE", "TERMINATE" -> transactionalService.addOffer(applicationId, input, "offer-facts");
      case "CORRECT", "CORRECT_TERMINATE" -> transactionalService.correctOffer(applicationId, priorId, input, "offer-facts");
      case "ONBOARD" -> transactionalService.onboard(applicationId, now, null, "招聘事实必须完整", "offer-facts");
      default -> throw new AssertionError(scenario);
    };

    if (changedRows == 1) {
      assertThat(command.get()).isSameAs("ONBOARD".equals(scenario) ? expectedEvent : expectedOffer);
      verify(transactions).commit(transaction);
      verify(transactions, never()).rollback(any());
      return;
    }
    String message = switch (checkedWrite) {
      case "event" -> "入职事件写入失败";
      case "offer" -> "Offer 状态记录写入失败";
      case "history" -> "Application 阶段历史写入失败";
      default -> throw new AssertionError(checkedWrite);
    };
    assertThatThrownBy(command::get).isInstanceOf(IllegalStateException.class).hasMessage(message);
    verify(transactions).rollback(transaction);
    verify(transactions, never()).commit(any());
    verify(offers, never()).findOffer(applicationId, recordId);
    verify(onboarding, never()).findOnboarding(any());
    verifyNoInteractions(locks);
  }

  @ParameterizedTest
  @CsvSource({"ADVANCE,OFFER_TRANSITION_INVALID", "CORRECT,OFFER_STATUS_INVALID"})
  void acceptedOfferStatusIsReadOnlyHistoricalData(String scenario, String expectedCode) {
    String applicationId = "P".repeat(26), priorId = "B".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setId(priorId);
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    issued.setOccurredAt(now.minusSeconds(60));
    when(offers.latestOffer(applicationId)).thenReturn(issued);
    when(offers.findOffer(applicationId, priorId)).thenReturn(issued);
    OfferCommandDTO input =
        new OfferCommandDTO("ACCEPTED", null, null, "不再新增已接受状态", now, null, null);

    assertThatThrownBy(() -> {
      if ("ADVANCE".equals(scenario)) {
        service.addOffer(applicationId, input, "accepted-read-only-advance");
      } else {
        service.correctOffer(applicationId, priorId, input, "accepted-read-only-correct");
      }
    }).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo(expectedCode));

    verify(offers, never()).insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"WITHDRAW,0", "WITHDRAW,1", "WITHDRAW,2",
      "ADVANCE,0", "ADVANCE,1", "ADVANCE,2", "TRANSFER,0", "TRANSFER,1", "TRANSFER,2",
      "HANDOVER,0", "HANDOVER,1", "HANDOVER,2"})
  void stageAndOwnershipChangesRequireCompleteHistory(String scenario, int changedRows) {
    String applicationId = "P".repeat(26), targetId = "U".repeat(26), submissionId = "Q".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(600));
    app.setCurrentStage("IN_PROCESS");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(persistence.updateOwner(any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(directory.activeUser(targetId)).thenReturn(new IdentityDirectoryService.UserReference(targetId, "接手 HR", DEPARTMENT));
    WorkflowStep active = pathStep("S".repeat(26), "WITHDRAW".equals(scenario)
        ? "CLIENT_SUBMISSION" : "CANDIDATE_COMMUNICATION", 1, "IN_PROGRESS", true);
    active.setClientSubmissionId(submissionId);
    WorkflowStep decision = pathStep("T".repeat(26), "DECISION", 2, "AVAILABLE", false);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(active, decision));
    when(workflowSteps.findStep(applicationId, active.getId())).thenReturn(active);
    when(followUps.countFollowUps(active.getId())).thenReturn(1);
    when(workflowSteps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(workflowSteps.selectStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(workflowSteps.withdrawBoundClientStep(any(), any(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(changedRows);
    when(ownership.insertOwnership(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(changedRows);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService = (RecruitmentApplicationManager) proxyFactory.getProxy();
    Supplier<Object> command = () -> switch (scenario) {
      case "WITHDRAW" -> {
        transactionalService.withdrawBoundClientStep(applicationId, submissionId, "保留完整变更历史");
        yield Boolean.TRUE;
      }
      case "ADVANCE" -> transactionalService.completeStep(applicationId, active.getId(), "PASSED", "保留完整变更历史", null, 1L);
      case "TRANSFER" -> transactionalService.transfer(applicationId, targetId, "保留完整变更历史", 1L, "TRANSFER");
      case "HANDOVER" -> transactionalService.transfer(applicationId, ACTOR, targetId, ACTOR, "保留完整变更历史");
      default -> throw new AssertionError(scenario);
    };

    if (changedRows == 1) {
      assertThat(command.get()).isNotNull();
      verify(transactions).commit(transaction);
      verify(transactions, never()).rollback(any());
    } else {
      String message = "TRANSFER".equals(scenario) || "HANDOVER".equals(scenario)
          ? "Application 负责人历史写入失败" : "ADVANCE".equals(scenario)
              ? "逐步跟进阶段历史写入失败" : "Application 阶段历史写入失败";
      assertThatThrownBy(command::get).isInstanceOf(IllegalStateException.class).hasMessage(message);
      verify(transactions).rollback(transaction);
      verify(transactions, never()).commit(any());
      verifyNoInteractions(locks);
    }
  }

  @ParameterizedTest
  @CsvSource({"complete,DECISION,0", "complete,DECISION,1",
      "skip,DECISION,0", "skip,DECISION,1",
      "complete,INTERVIEW_ROUND_1,0", "complete,INTERVIEW_ROUND_1,1",
      "skip,INTERVIEW_ROUND_1,0", "skip,INTERVIEW_ROUND_1,1",
      "complete,NONE,0", "skip,NONE,0"})
  void advancementRequiresDurableNextActionSelection(String operation, String nextCode, int changedRows) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(600));
    WorkflowStep active = pathStep("S".repeat(26), "CANDIDATE_COMMUNICATION", 1, "IN_PROGRESS", true);
    WorkflowStep next = pathStep("T".repeat(26), nextCode, 2, "AVAILABLE", false);
    boolean hasNext = !"NONE".equals(nextCode);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, active.getId())).thenReturn(active);
    when(workflowSteps.findSteps(applicationId)).thenReturn(hasNext ? List.of(active, next) : List.of(active));
    when(followUps.countFollowUps(active.getId())).thenReturn(1);
    when(workflowSteps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(workflowSteps.skipStep(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(persistence.enableProgressiveStage(any(), any(), anyLong(), any(), any())).thenReturn(changedRows);
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    authorization.currentActor().setSuperAdmin(true);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService = (RecruitmentApplicationManager) proxyFactory.getProxy();
    Supplier<ApplicationDetails> command = () -> "complete".equals(operation)
        ? transactionalService.completeStep(applicationId, active.getId(), "PASSED", "完成沟通", null, 1L)
        : transactionalService.skipStep(applicationId, active.getId(), "纠正错录", 1L);

    if (changedRows == 0) {
      assertThatThrownBy(command::get).isInstanceOfSatisfying(PlatformException.class,
          error -> assertThat(error.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
      verify(transactions).rollback(transaction);
      verify(transactions, never()).commit(any());
      verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
      verifyNoInteractions(stageHistory, locks);
    } else {
      assertThat(command.get().getApplication().getId()).isEqualTo(applicationId);
      verify(transactions).commit(transaction);
      verify(transactions, never()).rollback(any());
      verify(workflowSteps, never()).selectStep(any(), any(), any(), any(), any(), any());
      verify(persistence).enableProgressiveStage(applicationId, "PATH_SELECTION", app.getVersion(), ACTOR, now);
      verify(stageHistory).insertStageHistory(any(), eq(applicationId), eq(app.getCurrentStage()),
          eq("PATH_SELECTION"), any(), eq(ACTOR), eq(now));
    }
  }

  @ParameterizedTest
  @CsvSource({"TERMINATED,APPLICATION_TERMINATED", "ONBOARDED,APPLICATION_TERMINATED"})
  void administratorStepCorrectionCannotBypassAdvancementGuards(String blockedState, String errorCode) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(600));
    app.setCurrentStage(blockedState);
    app.setTerminalAt(now.minusSeconds(60));
    WorkflowStep active = pathStep("S".repeat(26), "CANDIDATE_COMMUNICATION", 1, "IN_PROGRESS", true);
    WorkflowStep next = pathStep("T".repeat(26), "DECISION", 2, "AVAILABLE", false);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, active.getId())).thenReturn(active);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(active, next));
    when(workflowSteps.skipStep(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(workflowSteps.selectStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    authorization.currentActor().setSuperAdmin(true);

    assertThatThrownBy(() -> service.skipStep(applicationId, active.getId(), "纠正错录", 1L))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo(errorCode));
    verify(workflowSteps, never()).skipStep(any(), any(), anyLong(), any(), any());
    verify(workflowSteps, never()).selectStep(any(), any(), any(), any(), any(), any());
    verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
    verifyNoInteractions(stageHistory, locks);
  }

  @ParameterizedTest
  @CsvSource({"PASSED,PENDING", "PASSED,IN_PROGRESS", "FAILED,PENDING", "FAILED,IN_PROGRESS",
      "PASSED,NONE", "FAILED,NONE", "SKIPPED,NONE", "WITHDRAWN,NONE"})
  void clientWithdrawalPreservesCompletedSteps(String historicalStatus, String activeStatus) {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    WorkflowStep historical = pathStep("S".repeat(26), "CLIENT_SUBMISSION", 1, historicalStatus, true);
    historical.setClientSubmissionId(submissionId);
    historical.setCompletedAt(now.minusSeconds(120));
    historical.setResultReason("先前的实际结果");
    WorkflowStep active = pathStep("T".repeat(26), "CLIENT_AI_INTERVIEW", 2, activeStatus, true);
    active.setClientSubmissionId(submissionId);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    boolean hasActive = !"NONE".equals(activeStatus);
    when(workflowSteps.findSteps(applicationId))
        .thenReturn(hasActive ? List.of(historical, active) : List.of(historical));
    when(workflowSteps.withdrawBoundClientStep(any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    service.withdrawBoundClientStep(applicationId, submissionId, "撤回当前客户步骤");

    verify(workflowSteps, never()).withdrawBoundClientStep(eq(historical.getId()), any(), any(), any());
    if (hasActive) {
      verify(workflowSteps).withdrawBoundClientStep(active.getId(), "撤回当前客户步骤", ACTOR, now);
      verify(persistence).updateStage(applicationId, "PATH_SELECTION", app.getVersion(), ACTOR, now);
    } else {
      verify(workflowSteps, never()).withdrawBoundClientStep(any(), any(), any(), any());
      verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
      verifyNoInteractions(stageHistory);
    }
    assertThat(historical.getStatus()).isEqualTo(historicalStatus);
    assertThat(historical.getCompletedAt()).isEqualTo(now.minusSeconds(120));
    assertThat(historical.getResultReason()).isEqualTo("先前的实际结果");
    verifyNoInteractions(locks);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void clientWithdrawalEndsTheScheduledInterviewEvenWhenOnlyTheInterviewHasTheBinding(boolean stepBound) {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    WorkflowStep active = pathStep("T".repeat(26), "CLIENT_AI_INTERVIEW", 2, "IN_PROGRESS", true);
    active.setClientSubmissionId(stepBound ? submissionId : null);
    Interview interview = new Interview();
    interview.setId("U".repeat(26));
    interview.setApplicationId(applicationId);
    interview.setWorkflowStepId(active.getId());
    interview.setClientSubmissionId(submissionId);
    interview.setStatus("SCHEDULED");
    interview.setScheduledStartAt(now.plusSeconds(3600));
    interview.setScheduledEndAt(now.plusSeconds(7200));
    interview.setTimezoneId("Asia/Shanghai");
    interview.setMeetingMode("ONLINE");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(active));
    when(workflowSteps.withdrawBoundClientStep(any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(interviews.lockSubmissionInterviews(applicationId, submissionId, null))
        .thenReturn(List.of(interview));
    when(interviews.cancel(interview.getId(), 0L, ACTOR, now)).thenReturn(1);
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    service.withdrawBoundClientStep(applicationId, submissionId, "客户撤回面试安排");

    verify(interviews).cancel(interview.getId(), 0L, ACTOR, now);
    // 部署表的 ck_interview_schedule_action 只接受 CREATE、UPDATE、RESCHEDULE、CANCEL。
    verify(interviewNotifications).createNotificationsBatch(List.of(interview), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR);
    ArgumentCaptor<String> before = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
    verify(interviews).insertHistory(any(), eq(interview.getId()), eq("CANCEL"), before.capture(), after.capture(),
        eq("客户撤回面试安排"), eq(ACTOR), eq(now));
    assertThat(before.getValue()).contains("SCHEDULED", submissionId);
    assertThat(after.getValue()).contains("CANCELLED", submissionId);
    verify(workflowSteps).withdrawBoundClientStep(active.getId(), "客户撤回面试安排", ACTOR, now);
    verify(persistence).updateStage(applicationId, "PATH_SELECTION", app.getVersion(), ACTOR, now);
  }

  @Test
  void clientInterviewBindingIsPreservedThroughWithdrawalAndCannotBeReused() {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setRecruitmentType("EXTERNAL");
    WorkflowStep active = pathStep("T".repeat(26), "INTERVIEW_ROUND_1", 2, "PENDING", true);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.lockStep(applicationId, active.getId())).thenReturn(active);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(active));
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(new DemandQueryService.SubmissionDemandContext(
        DEMAND, "L".repeat(26), null, null, "EXTERNAL", ACTOR, DEPARTMENT));
    when(workflowSteps.bindClientSubmission(active.getId(), submissionId, active.getVersion(), ACTOR, now))
        .thenAnswer(call -> { active.setClientSubmissionId(submissionId); return 1; });
    when(workflowSteps.withdrawBoundClientStep(active.getId(), "甲方取消", ACTOR, now))
        .thenAnswer(call -> { active.setStatus("WITHDRAWN"); return 1; });
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    service.bindClientInterviewStep(applicationId, active.getId(), submissionId);
    assertThat(active.getClientSubmissionId()).isEqualTo(submissionId);
    verify(submissionValidator).requireSentAndMatching(submissionId, applicationId, CANDIDATE,
        "L".repeat(26), JD, JD_VERSION, RESUME);
    service.withdrawBoundClientStep(applicationId, submissionId, "甲方取消");
    assertThat(active.getStatus()).isEqualTo("WITHDRAWN");
    assertThat(active.getClientSubmissionId()).isEqualTo(submissionId);
    assertThatThrownBy(() -> service.bindClientInterviewStep(applicationId, active.getId(), submissionId))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_STEP_NOT_ACTIVE"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "IN_PROGRESS", "PASSED", "NOT_SELECTED"})
  void sendingBindsOnlyTheActiveClientSubmissionStep(String status) {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    WorkflowStep step = pathStep("T".repeat(26), "CLIENT_SUBMISSION", 2, status, true);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(new DemandQueryService.SubmissionDemandContext(
        DEMAND, "L".repeat(26), null, null, "EXTERNAL", ACTOR, DEPARTMENT));

    service.bindSentClientSubmissionStep(applicationId, submissionId);

    if (Set.of("PENDING", "IN_PROGRESS").contains(status)) {
      verify(workflowSteps).bindClientSubmission(step.getId(), submissionId, step.getVersion(), ACTOR, now);
    } else {
      verify(workflowSteps, never()).bindClientSubmission(any(), any(), anyLong(), any(), any());
      verifyNoInteractions(submissionValidator);
    }
    assertThat(step.getStatus()).isEqualTo(status);
  }

  @ParameterizedTest
  @CsvSource({"PENDING,true", "IN_PROGRESS,true", "PASSED,false", "FAILED,false", "WITHDRAWN,false"})
  void bindingNeverReplacesAnotherSubmissionOrCompletedHistory(String status, boolean bound) {
    String applicationId = "P".repeat(26);
    WorkflowStep step = pathStep("T".repeat(26), "INTERVIEW_ROUND_1", 2, status, true);
    step.setClientSubmissionId(bound ? "B".repeat(26) : null);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId, ACTOR, now));
    when(workflowSteps.lockStep(applicationId, step.getId())).thenReturn(step);
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(new DemandQueryService.SubmissionDemandContext(
        DEMAND, "L".repeat(26), null, null, "EXTERNAL", ACTOR, DEPARTMENT));

    assertThatThrownBy(() -> service.bindClientInterviewStep(applicationId, step.getId(), "Q".repeat(26)))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(
            bound ? "INTERVIEW_SUBMISSION_ALREADY_BOUND" : "INTERVIEW_STEP_NOT_ACTIVE"));
    verify(workflowSteps, never()).bindClientSubmission(any(), any(), anyLong(), any(), any());
    assertThat(step.getStatus()).isEqualTo(status);
  }

  @ParameterizedTest
  @CsvSource({"PENDING,PASSED", "PENDING,FAILED", "PENDING,WITHDRAWN",
      "IN_PROGRESS,PASSED", "IN_PROGRESS,FAILED", "IN_PROGRESS,WITHDRAWN"})
  void completingAClientStepCannotReplaceTheRecommendationBoundDuringScheduling(String status, String result) {
    String applicationId = "P".repeat(26), originalSubmission = "Q".repeat(26), replacement = "B".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(1800));
    app.setRecruitmentType("EXTERNAL");
    WorkflowStep step = pathStep("T".repeat(26), "INTERVIEW_ROUND_1", 2, status, true);
    step.setClientSubmissionId(originalSubmission);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, step.getId())).thenReturn(step);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(followUps.countFollowUps(step.getId())).thenReturn(1);
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(new DemandQueryService.SubmissionDemandContext(
        DEMAND, "L".repeat(26), null, null, "EXTERNAL", ACTOR, DEPARTMENT));
    when(workflowSteps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);

    assertThatThrownBy(() -> service.completeStep(applicationId, step.getId(), result, "面试结论", replacement, step.getVersion()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("WORKFLOW_STEP_SUBMISSION_MISMATCH"));
    verify(workflowSteps, never()).completeStep(any(), any(), any(), any(), anyLong(), any(), any());
    assertThat(step.getClientSubmissionId()).isEqualTo(originalSubmission);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "true,true"})
  void clientStepCompletionAcceptsItsExistingBindingOrAnInitiallyUnboundStep(boolean bound, boolean omitted) {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(1800));
    app.setRecruitmentType("EXTERNAL");
    WorkflowStep step = pathStep("T".repeat(26), "INTERVIEW_ROUND_1", 2, "IN_PROGRESS", true);
    step.setClientSubmissionId(bound ? submissionId : null);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, step.getId())).thenReturn(step);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(followUps.countFollowUps(step.getId())).thenReturn(1);
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(new DemandQueryService.SubmissionDemandContext(
        DEMAND, "L".repeat(26), null, null, "EXTERNAL", ACTOR, DEPARTMENT));
    when(workflowSteps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);

    assertThat(service.completeStep(applicationId, step.getId(), "PASSED", "面试结论", omitted ? null : " " + submissionId + " ", step.getVersion()))
        .isNotNull();
    verify(workflowSteps).completeStep(step.getId(), "PASSED", "面试结论", submissionId, step.getVersion(), ACTOR, now);
  }

  @ParameterizedTest
  @CsvSource({"INTERNAL,INTERVIEW_ROUND_1,PASSED", "INTERNAL,INTERVIEW_ROUND_2,FAILED",
      "INTERNAL,INTERVIEW_ROUND_3,PASSED", "EXTERNAL,PRE_SUBMISSION_INTERNAL_INTERVIEW,FAILED"})
  void internalInterviewCannotBeCompletedByHrWithoutFormalFeedback(String recruitmentType, String stepCode, String result) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setRecruitmentType(recruitmentType);
    WorkflowStep step = pathStep("T".repeat(26), stepCode, 2, "IN_PROGRESS", true);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, step.getId())).thenReturn(step);
    when(followUps.countFollowUps(step.getId())).thenReturn(1);

    assertThatThrownBy(() -> service.completeStep(applicationId, step.getId(), result, "HR 不能用跟进代替面评", null, step.getVersion()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERNAL_INTERVIEW_FEEDBACK_REQUIRED"));
    verify(workflowSteps, never()).completeStep(any(), any(), any(), any(), anyLong(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"INTERVIEW_ROUND_1,PASSED", "INTERVIEW_ROUND_1,FAILED", "INTERVIEW_ROUND_1,WITHDRAWN",
      "INTERVIEW_ROUND_2,PASSED", "INTERVIEW_ROUND_2,FAILED", "INTERVIEW_ROUND_2,WITHDRAWN",
      "INTERVIEW_ROUND_3,PASSED", "INTERVIEW_ROUND_3,FAILED", "INTERVIEW_ROUND_3,WITHDRAWN"})
  void internalInterviewCompletionDoesNotRequireAClientRecommendation(String stepCode, String result) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(1800));
    app.setRecruitmentType("INTERNAL");
    Interview completedInterview = new Interview();
    completedInterview.setStatus("COMPLETED");
    when(interviews.lockLatestByStep("T".repeat(26))).thenReturn(completedInterview);
    WorkflowStep step = pathStep("T".repeat(26), stepCode, 2, "IN_PROGRESS", true);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, step.getId())).thenReturn(step);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(followUps.countFollowUps(step.getId())).thenReturn(1);
    when(workflowSteps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);

    assertThat(service.completeStep(applicationId, step.getId(), result, "内部面试结论", null, step.getVersion()))
        .isNotNull();

    verify(workflowSteps).completeStep(step.getId(), result, "内部面试结论", null, step.getVersion(), ACTOR, now);
    verifyNoInteractions(submissionValidator);
  }

  @ParameterizedTest
  @ValueSource(strings={"PASSED","FAILED","WITHDRAWN"})
  void companyBlacklistBlocksEveryNonterminalStepCompletionIncludingNegativeResults(String result) {
    ReflectionTestUtils.setField(service, "blacklist", blockingCompanyPolicy());
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    WorkflowStep step = pathStep("T".repeat(26), "CANDIDATE_COMMUNICATION", 1, "IN_PROGRESS", true);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, step.getId())).thenReturn(step);

    assertThatThrownBy(() -> service.completeStep(applicationId, step.getId(), result,
        "记录当前沟通结果并返回选路", null, step.getVersion()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verify(workflowSteps, never()).completeStep(any(), any(), any(), any(), anyLong(), any(), any());
    verify(persistence, never()).enableProgressiveStage(any(), any(), anyLong(), any(), any());
    verifyNoInteractions(stageHistory, communications);
  }

  @Test
  void aNewInternalScheduleMustNotReuseHistoricalCompletedFeedback() {
    String applicationId = "P".repeat(26), stepId = "T".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setRecruitmentType("INTERNAL");
    WorkflowStep step = pathStep(stepId, "INTERVIEW_ROUND_1", 2, "IN_PROGRESS", true);
    Interview scheduled = new Interview();
    scheduled.setStatus("SCHEDULED");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, stepId)).thenReturn(step);
    when(followUps.countFollowUps(stepId)).thenReturn(1);
    when(interviews.lockLatestByStep(stepId)).thenReturn(scheduled);

    assertThatThrownBy(() -> service.completeStep(applicationId, stepId, "PASSED", "不能复用旧面评", null, step.getVersion()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERNAL_INTERVIEW_FEEDBACK_REQUIRED"));
    verify(workflowSteps, never()).completeStep(any(), any(), any(), any(), anyLong(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"INTERNAL,INTERVIEW_ROUND_1", "INTERNAL,INTERVIEW_ROUND_2", "INTERNAL,INTERVIEW_ROUND_3",
      "INTERNAL,CANDIDATE_COMMUNICATION", "EXTERNAL,PRE_SUBMISSION_INTERNAL_INTERVIEW", "EXTERNAL,CANDIDATE_COMMUNICATION"})
  void nonClientStepCompletionRejectsAnUnrelatedRecommendation(String recruitmentType, String stepCode) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(1800));
    app.setRecruitmentType(recruitmentType);
    WorkflowStep step = pathStep("T".repeat(26), stepCode, 2, "IN_PROGRESS", true);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, step.getId())).thenReturn(step);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(followUps.countFollowUps(step.getId())).thenReturn(1);
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(new DemandQueryService.SubmissionDemandContext(
        DEMAND, "L".repeat(26), null, null, recruitmentType, ACTOR, DEPARTMENT));
    when(workflowSteps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);

    assertThatThrownBy(() -> service.completeStep(applicationId, step.getId(), "PASSED", "内部流程结论", "Q".repeat(26), step.getVersion()))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CLIENT_SUBMISSION_NOT_ALLOWED"));
    verify(workflowSteps, never()).completeStep(any(), any(), any(), any(), anyLong(), any(), any());
    verifyNoInteractions(submissionValidator);
  }

  @ParameterizedTest
  @CsvSource({"INTERNAL,INTERVIEW_ROUND_1,false", "EXTERNAL,PRE_SUBMISSION_INTERNAL_INTERVIEW,false",
      "EXTERNAL,CLIENT_AI_INTERVIEW,true", "EXTERNAL,INTERVIEW_ROUND_1,true"})
  void companyBlacklistBlocksLockedInterviewContextBeforeStartingStep(String recruitmentType, String stepCode, boolean clientInterview) {
    ReflectionTestUtils.setField(service, "blacklist", blockingCompanyPolicy());
    String applicationId = "P".repeat(26), stepId = "T".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setRecruitmentType(recruitmentType);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.lockStep(applicationId, stepId))
        .thenReturn(pathStep(stepId, stepCode, 1, "PENDING", true));
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(new DemandQueryService.SubmissionDemandContext(
        DEMAND, "L".repeat(26), null, null, recruitmentType, ACTOR, DEPARTMENT));


    when(workflowSteps.startStep(any(), anyLong(), any(), any())).thenReturn(1);
    assertThatThrownBy(() -> service.requireLockedInterviewContext(applicationId, stepId))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verify(workflowSteps, never()).startStep(any(), anyLong(), any(), any());

  }

  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "IN_PROGRESS", "PASSED", "FAILED", "WITHDRAWN", "NOT_SELECTED"})
  void schedulingRechecksStepStateAndOwnershipAfterTheApplicationLock(String currentStatus) {
    String applicationId = "P".repeat(26), stepId = "T".repeat(26), currentOwner = "B".repeat(26);
    Application stale = application(applicationId, ACTOR, now);
    Application current = application(applicationId, currentOwner, now);
    when(persistence.findApplication(applicationId)).thenReturn(stale);
    when(persistence.lockApplication(applicationId)).thenReturn(current);
    when(collaborators.findCollaborators(applicationId)).thenReturn(List.of(ACTOR));
    when(collaborators.findCollaboratorsForUpdate(applicationId)).thenReturn(List.of(ACTOR));
    when(workflowSteps.findStep(applicationId, stepId))
        .thenReturn(pathStep(stepId, "INTERVIEW_ROUND_1", 1, "PENDING", true));
    when(workflowSteps.lockStep(applicationId, stepId))
        .thenReturn(pathStep(stepId, "INTERVIEW_ROUND_1", 1, currentStatus, true));

    assertThat(service.requireInterviewContext(applicationId, stepId).ownerUserId()).isEqualTo(ACTOR);
    when(workflowSteps.startStep(any(),anyLong(),any(),any())).thenReturn(1);
    if (Set.of("PENDING", "IN_PROGRESS").contains(currentStatus)) {
      var context = service.requireLockedInterviewContext(applicationId, stepId);
      assertThat(context.ownerUserId()).isEqualTo(currentOwner);
      assertThat(context.stepStatus()).isEqualTo("IN_PROGRESS");
    } else {
      assertThatThrownBy(() -> service.requireLockedInterviewContext(applicationId, stepId))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("INTERVIEW_STEP_NOT_ACTIVE"));
    }
    var targets = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization, Mockito.times(2)).requireAccess(eq("recruitment.manage"), targets.capture());
    assertThat(targets.getAllValues()).extracting(AuthorizationTarget::getOwnerUserId)
        .containsExactly(ACTOR, currentOwner);
    var order = Mockito.inOrder(persistence, workflowSteps);
    order.verify(persistence).lockApplication(applicationId);
    order.verify(workflowSteps).lockStep(applicationId, stepId);
  }

  @ParameterizedTest
  @CsvSource({"0,1", "1,0", "1,2"})
  void failedInterviewCancellationOrHistoryRollsBackWithdrawal(int cancelledRows, int historyRows) {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    WorkflowStep active = pathStep("T".repeat(26), "CLIENT_AI_INTERVIEW", 2, "IN_PROGRESS", true);
    active.setClientSubmissionId(submissionId);
    Interview interview = new Interview();
    interview.setId("U".repeat(26));
    interview.setWorkflowStepId(active.getId());
    interview.setStatus("SCHEDULED");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(active));
    when(interviews.lockSubmissionInterviews(applicationId, submissionId, null)).thenReturn(List.of(interview));
    when(interviews.cancel(interview.getId(), 0L, ACTOR, now)).thenReturn(cancelledRows);
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(historyRows);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxy = new ProxyFactory(service);
    proxy.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(interceptor);
    RecruitmentApplicationManager transactional = (RecruitmentApplicationManager) proxy.getProxy();

    assertThatThrownBy(() -> transactional.withdrawBoundClientStep(applicationId, submissionId, "甲方取消"))
        .isInstanceOf(RuntimeException.class);
    verify(transactions).rollback(transaction);
    verify(transactions, never()).commit(any());
    verify(workflowSteps, never()).withdrawBoundClientStep(any(), any(), any(), any());
  }

  @Test
  void withdrawalReadsAllInterviewPagesAndPreservesCompletedFeedbackFacts() {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId, ACTOR, now));
    List<Interview> first = IntStream.rangeClosed(1, 100).mapToObj(index -> {
      Interview value = new Interview();
      value.setId(String.format("%026d", index));
      value.setWorkflowStepId("T".repeat(26));
      value.setStatus("COMPLETED");
      return value;
    }).toList();
    Interview scheduled = new Interview();
    scheduled.setId("U".repeat(26));
    scheduled.setWorkflowStepId("T".repeat(26));
    scheduled.setStatus("SCHEDULED");
    when(interviews.lockSubmissionInterviews(applicationId, submissionId, null)).thenReturn(first);
    when(interviews.lockSubmissionInterviews(applicationId, submissionId, first.getLast().getId()))
        .thenReturn(List.of(scheduled));
    when(interviews.cancel(scheduled.getId(), 0L, ACTOR, now)).thenReturn(1);
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    service.withdrawBoundClientStep(applicationId, submissionId, "取消余下面试");

    verify(interviews).cancel(scheduled.getId(), 0L, ACTOR, now);
    verify(interviews, Mockito.times(1)).cancel(any(), anyLong(), any(), any());
    assertThat(first).allSatisfy(value -> assertThat(value.getStatus()).isEqualTo("COMPLETED"));
    verify(workflowSteps, never()).withdrawBoundClientStep(any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void withdrawalBatchesNotificationReadsAndRollsBackALaterPageFailure(boolean laterPageFails) {
    String applicationId = "P".repeat(26), submissionId = "Q".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId, ACTOR, now));
    List<Interview> values = IntStream.rangeClosed(1, 103).mapToObj(index -> {
      Interview value = new Interview();
      value.setId(String.format("%026d", index));
      value.setApplicationId(applicationId);
      value.setWorkflowStepId("T".repeat(26));
      value.setStepCode("CLIENT_AI_INTERVIEW");
      value.setOwnerUserId(ACTOR);
      value.setCandidateId(CANDIDATE);
      value.setStatus(index == 1 ? "COMPLETED" : index == 2 ? "CANCELLED" : "SCHEDULED");
      value.setScheduledStartAt(now.plusSeconds(index));
      value.setScheduledEndAt(now.plusSeconds(index + 3600));
      value.setMeetingMode("ONLINE");
      value.setTimezoneId("Asia/Shanghai");
      value.setMeetingUrl("https://meeting.example/" + index);
      value.setCandidateNotificationChannel("EMAIL");
      return value;
    }).toList();
    when(interviews.lockSubmissionInterviews(applicationId, submissionId, null)).thenReturn(values.subList(0, 100));
    when(interviews.lockSubmissionInterviews(applicationId, submissionId, values.get(99).getId()))
        .thenReturn(values.subList(100, 103));
    when(interviews.cancel(any(), anyLong(), eq(ACTOR), eq(now))).thenReturn(1);
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(interviews.findActiveTemplate("INTERVIEW_CANCEL")).thenReturn(new TemplateVersion(
        "V".repeat(26), "Z".repeat(26), 1, "取消面试", "{{applicationId}} {{location}}", ACTOR, now));
    ParticipantPersistenceService people = mock(ParticipantPersistenceService.class);
    NotificationIntentPersistenceService notifications = mock(NotificationIntentPersistenceService.class);
    Map<String, List<Participant>> participantsById = values.stream().collect(Collectors.toMap(Interview::getId,
        value -> List.of(new Participant(value.getId(), ACTOR, "INTERVIEWER", "PENDING"),
            new Participant(value.getId(), "U" + value.getId().substring(1), "INTERVIEWER", "PENDING"),
            new Participant(value.getId(), "U" + value.getId().substring(1), "INTERVIEWER", "PENDING"))));
    when(people.lockParticipantsByInterviewIds(any())).thenAnswer(call ->
        call.<Collection<String>>getArgument(0).stream().flatMap(id -> participantsById.get(id).stream()).toList());
    AtomicInteger written = new AtomicInteger();
    when(notifications.insertNotifications(any())).thenAnswer(call -> {
      List<NotificationIntent> batch = call.getArgument(0);
      String interviewId = batch.getFirst().getInterviewId();
      Interview interview = values.stream().filter(value -> value.getId().equals(interviewId)).findFirst().orElseThrow();
      assertThat(batch).extracting(NotificationIntent::getRecipientReference)
          .containsExactly(ACTOR, "U" + interviewId.substring(1), CANDIDATE);
      assertThat(batch).extracting(NotificationIntent::getChannel).containsExactly("IN_APP", "IN_APP", "EMAIL");
      assertThat(batch).allSatisfy(intent -> {
        assertThat(intent.getNotificationType()).isEqualTo("INTERVIEW_CANCEL");
        assertThat(intent.getRenderedBody()).contains(applicationId, interview.getMeetingUrl()).doesNotContain("{{");
        assertThat(intent.getStatus()).isEqualTo("PENDING_MANUAL");
      });
      return written.incrementAndGet() != 101 || !laterPageFails;
    });
    InterviewNotificationApplicationManager realNotifications = new InterviewNotificationApplicationManager();
    ReflectionTestUtils.setField(realNotifications, "persistence", interviews);
    ReflectionTestUtils.setField(realNotifications, "participantPersistenceService", people);
    ReflectionTestUtils.setField(realNotifications, "notificationIntentPersistenceService", notifications);
    ReflectionTestUtils.setField(realNotifications, "ids", ids);
    ReflectionTestUtils.setField(realNotifications, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ReflectionTestUtils.setField(service, "interviewNotificationManager", realNotifications);
    AtomicInteger generated = new AtomicInteger();
    when(ids.newId()).thenAnswer(call -> String.format("N%025d", generated.incrementAndGet()));
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxy = new ProxyFactory(service);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    proxy.setProxyTargetClass(true);
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(interceptor);
    RecruitmentApplicationManager transactional = (RecruitmentApplicationManager) proxy.getProxy();
    if (laterPageFails) {
      assertThatThrownBy(() -> transactional.withdrawBoundClientStep(applicationId, submissionId, "取消余下面试"))
          .isInstanceOf(IllegalStateException.class).hasMessage("面试通知意图批量写入失败");
      verify(transactions).rollback(transaction);
      verify(transactions, never()).commit(any());
    } else {
      transactional.withdrawBoundClientStep(applicationId, submissionId, "取消余下面试");
      verify(transactions).commit(transaction);
      verify(transactions, never()).rollback(any());
    }
    assertThat(written.get()).isEqualTo(101);
    verify(interviews, Mockito.times(2)).findActiveTemplate("INTERVIEW_CANCEL");
    verify(people, never()).findParticipants(any());
    verify(people, never()).findParticipantsByInterviewIds(any());
    verify(people).lockParticipantsByInterviewIds(values.subList(2, 100).stream().map(Interview::getId).collect(Collectors.toSet()));
    verify(people).lockParticipantsByInterviewIds(values.subList(100, 103).stream().map(Interview::getId).collect(Collectors.toSet()));
    verify(interviews, never()).cancel(eq(values.getFirst().getId()), anyLong(), any(), any());
    verify(interviews, never()).cancel(eq(values.get(1).getId()), anyLong(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void companyBlacklistBlocksPassingDecision(boolean correction) {
    ReflectionTestUtils.setField(service, "blacklist", blockingCompanyPolicy());
    assertThatThrownBy(() -> decisionTransitionsRequireCompleteFactsBeforeCommitting(
        correction, "PASS", "history", 1))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verify(decisions, never()).insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
  }

  @Test
  void companyBlacklistBlocksNewApplication() {
    configureBatchPathCreation();
    ReflectionTestUtils.setField(service, "blacklist", blockingCompanyPolicy());
    assertThatThrownBy(() -> service.create(creationReadViewCommand(), "blacklisted-create"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verifyNoCreationFacts();
  }

  private CompanyBlacklistQueryService blockingCompanyPolicy() {
    return mock(CompanyBlacklistQueryService.class, call -> {
      if (call.getMethod().getName().equals("isCompanyBlacklisted")) return true;
      if (call.getMethod().getName().equals("requireRecruitmentAllowed")) {
        throw PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单阻断招聘推进");
      }
      return Mockito.RETURNS_DEFAULTS.answer(call);
    });
  }

  @ParameterizedTest
  @CsvSource({"PATH_SELECTION,REJECT,0", "PATH_SELECTION,WITHDRAW,0", "IN_PROCESS,REJECT,1",
      "IN_PROCESS,WITHDRAW,1", "IN_PROCESS,REJECT,0", "IN_PROCESS,WITHDRAW,2"})
  void earlyTerminalDecisionKeepsStepsAndOutcomeAtomic(String stage, String value, int changedSteps) {
    String applicationId = "P".repeat(26), decisionId = "S".repeat(26), recordId = "I".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage(stage);
    WorkflowStep decision = new WorkflowStep();
    decision.setId(decisionId);
    decision.setStepCode("DECISION");
    decision.setStatus("AVAILABLE");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    WorkflowStep active = new WorkflowStep();
    active.setId("T".repeat(26));
    active.setStepCode("CANDIDATE_COMMUNICATION");
    active.setStatus("IN_PROGRESS");
    WorkflowStep finished = new WorkflowStep();
    finished.setId("U".repeat(26));
    finished.setStepCode("PRE_SUBMISSION_INTERNAL_INTERVIEW");
    finished.setStatus("PASSED");
    int expectedChanges = "IN_PROCESS".equals(stage) ? 1 : 0;
    when(workflowSteps.findSteps(applicationId)).thenReturn(expectedChanges == 1
        ? List.of(finished, active, decision) : List.of(decision));
    when(workflowSteps.withdrawActiveSteps(applicationId, "提前终止招聘", ACTOR, now))
        .thenReturn(changedSteps);
    when(workflowSteps.selectStep(eq(decisionId), eq("IN_PROGRESS"), eq(ACTOR), any(), eq(ACTOR), eq(now)))
        .thenReturn(1);
    when(workflowSteps.finishDecisionStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(decisions.insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateOutcome(eq(applicationId), eq("TERMINATED"), eq(value), eq(now),
        eq(null), anyLong(), eq(ACTOR), eq(now))).thenReturn(1);
    Decision expected = new Decision(recordId, applicationId, "DECISION", value, "提前终止招聘",
        null, null, ACTOR, now);
    when(decisions.findDecision(applicationId, recordId)).thenReturn(expected);


    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService = (RecruitmentApplicationManager) proxyFactory.getProxy();
    if (changedSteps != expectedChanges) {
      assertThatThrownBy(() -> transactionalService.decide(applicationId, value, "提前终止招聘", null, "early-terminal"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
      verify(transactions).rollback(transaction);
      verify(transactions, never()).commit(any());
      verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
      verify(decisions, never()).findDecision(any(), any());
      return;
    }
    assertThat(transactionalService.decide(applicationId, value, "提前终止招聘", null, "early-terminal"))
        .isSameAs(expected);
    verify(transactions).commit(transaction);
    verify(transactions, never()).rollback(any());
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertIdempotency(eq(ACTOR), eq("early-terminal"), eq("APPLICATION_DECIDE"),
        fingerprint.capture(), eq(recordId), eq(now));
    when(idempotency.findIdempotency(ACTOR, "early-terminal"))
        .thenReturn(new IdempotencyRecord(ACTOR, "early-terminal", "APPLICATION_DECIDE", fingerprint.getValue(), recordId));
    app.setTerminalAt(now);
    assertThat(service.decide(applicationId, value, "提前终止招聘", null, "early-terminal"))
        .isSameAs(expected);
    // 终止后的同键重放读取原事实，不再激活步骤或重复追加决定。
    verify(decisions).insertDecision(recordId, applicationId, "DECISION", value, "提前终止招聘",
        null, null, ACTOR, now);
    verify(workflowSteps).finishDecisionStep(decisionId, "FAILED", "提前终止招聘", null, ACTOR, now);
    verify(workflowSteps).withdrawActiveSteps(applicationId, "提前终止招聘", ACTOR, now);
    verify(persistence).updateOutcome(eq(applicationId), eq("TERMINATED"), eq(value), eq(now),
        eq(null), anyLong(), eq(ACTOR), eq(now));
    verify(blacklist, never()).requireRecruitmentAllowed(any());
    verifyNoInteractions(offers);
  }

  @ParameterizedTest
  @CsvSource({"AVAILABLE,PASS", "PASSED,REJECT", "FAILED,WITHDRAW", "NOT_SELECTED,REJECT"})
  void earlyTerminationDoesNotAllowPrematurePassOrRewriteFinishedDecision(String status, String value) {
    String applicationId = "P".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId, ACTOR, now));
    WorkflowStep step = new WorkflowStep();
    step.setId("S".repeat(26));
    step.setStepCode("DECISION");
    step.setStatus(status);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));

    assertThatThrownBy(() -> service.decide(applicationId, value, "人工决定", null, "invalid-decision"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("DECISION_STEP_NOT_ACTIVE"));
    verifyNoInteractions(decisions, offers, stageHistory);
    verify(workflowSteps, never()).selectStep(any(), any(), any(), any(), any(), any());
    verify(workflowSteps, never()).withdrawActiveSteps(any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"CANDIDATE_COMMUNICATION", "CLIENT_AI_INTERVIEW"})
  void pathAmendmentAfterWithdrawalPreservesCompletedFacts(String withdrawnCode) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now);
    app.setCurrentStage("CLIENT_AI_INTERVIEW".equals(withdrawnCode) ? "PATH_SELECTION" : "IN_PROCESS");
    if ("CLIENT_AI_INTERVIEW".equals(withdrawnCode)) {
      app.setRecruitmentType("EXTERNAL");
    }
    WorkflowStep withdrawn = pathStep("B".repeat(26), withdrawnCode, 1, "WITHDRAWN", true);
    withdrawn.setStartedAt(now.minusSeconds(120));
    withdrawn.setCompletedAt(now.minusSeconds(60));
    withdrawn.setResultReason("候选人撤回当前安排");
    withdrawn.setClientSubmissionId("Q".repeat(26));
    WorkflowStep next = pathStep("S".repeat(26), "INTERVIEW_ROUND_1", 2, "NOT_SELECTED", true);
    WorkflowStep decision = pathStep("T".repeat(26), "DECISION", 3, "AVAILABLE", false);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(withdrawn, next, decision));
    when(workflowSteps.selectStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(workflowSteps.updateUnstartedSelection(any(), anyList(), anyBoolean(), any(), any()))
        .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(persistence.insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    assertThat(service.selectPath(applicationId, List.of(next.getId()), "撤回后重新选择后续路径", 1L, true)
        .getApplication().getId()).isEqualTo(applicationId);
    verify(workflowSteps).updateUnstartedSelection(applicationId,
        List.of(next.getId(), decision.getId()), true, ACTOR, now);
    verify(workflowSteps).updateUnstartedSelection(applicationId, List.of(), false, ACTOR, now);
    verify(workflowSteps).selectStep(eq(next.getId()), eq("IN_PROGRESS"), eq(ACTOR), any(), eq(ACTOR), eq(now));
    verify(persistence).updateStage(applicationId, "IN_PROCESS", 1L, ACTOR, now);
    assertThat(withdrawn.getCompletedAt()).isEqualTo(now.minusSeconds(60));
    assertThat(withdrawn.getResultReason()).isEqualTo("候选人撤回当前安排");
    assertThat(withdrawn.getClientSubmissionId()).isEqualTo("Q".repeat(26));
  }

  @Test
  void amendmentCanDeselectPendingWorkAndEnterDecisionWithoutRewritingHistory() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now);
    WorkflowStep finished = pathStep("B".repeat(26), "CANDIDATE_COMMUNICATION", 1, "PASSED", true);
    finished.setCompletedAt(now.minusSeconds(60));
    WorkflowStep pending = pathStep("S".repeat(26), "INTERVIEW_ROUND_1", 2, "PENDING", true);
    WorkflowStep decision = pathStep("T".repeat(26), "DECISION", 3, "AVAILABLE", false);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(finished, pending, decision));
    when(workflowSteps.updateUnstartedSelection(any(), anyList(), anyBoolean(), any(), any()))
        .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());
    when(workflowSteps.selectStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);
    when(persistence.insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    service.selectPath(applicationId, List.of(), "取消尚未开始的面试", 1L, true);

    verify(workflowSteps).updateUnstartedSelection(applicationId, List.of(decision.getId()), true, ACTOR, now);
    verify(workflowSteps).updateUnstartedSelection(applicationId, List.of(pending.getId()), false, ACTOR, now);
    verify(workflowSteps).selectStep(eq(decision.getId()), eq("IN_PROGRESS"), eq(ACTOR), any(), eq(ACTOR), eq(now));
    verify(persistence).updateStage(applicationId, "DECISION", 1L, ACTOR, now);
    ArgumentCaptor<String> before = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertPathChange(any(), eq(applicationId), eq("AMENDMENT"), before.capture(),
        after.capture(), eq("取消尚未开始的面试"), eq(1L), eq(ACTOR), eq(now));
    assertThat(before.getValue()).contains(finished.getId(), pending.getId(), decision.getId());
    assertThat(after.getValue()).contains(finished.getId(), decision.getId()).doesNotContain(pending.getId());
    verify(stageHistory).insertStageHistory(any(), eq(applicationId), eq(app.getCurrentStage()),
        eq("DECISION"), any(), eq(ACTOR), eq(now));
  }

  @ParameterizedTest
  @CsvSource({"IN_PROGRESS,false,APPLICATION_PATH_ALREADY_STARTED",
      "PENDING,true,APPLICATION_PATH_ALREADY_STARTED",
      "DECISION,false,APPLICATION_PATH_DECIDED", "OFFER,false,APPLICATION_PATH_DECIDED",
      "BACKTRACK,false,APPLICATION_PATH_ORDER_INVALID", "FOREIGN,false,APPLICATION_PATH_STEP_INVALID",
      "OVERSIZED,false,APPLICATION_PATH_STEP_INVALID"})
  void rejectsUnsafePathAmendmentsBeforeWriting(String scenario, boolean started, String errorCode) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    WorkflowStep ordinary = pathStep("S".repeat(26), "CANDIDATE_COMMUNICATION", 1, "NOT_SELECTED", true);
    WorkflowStep decision = pathStep("T".repeat(26), "DECISION", 2, "AVAILABLE", false);
    List<String> requested = List.of();
    switch (scenario) {
      case "IN_PROGRESS", "PENDING" -> {
        ordinary.setStatus(scenario);
        ordinary.setStartedAt(started ? now : null);
      }
      case "DECISION" -> decision.setStatus("PASSED");
      case "OFFER" -> app.setCurrentStage("OFFER");
      case "BACKTRACK" -> {
        decision.setStepCode("INTERVIEW_ROUND_1");
        decision.setStatus("WITHDRAWN");
        requested = List.of(ordinary.getId());
      }
      case "FOREIGN" -> requested = List.of("F".repeat(26));
      case "OVERSIZED" -> requested = Collections.nCopies(10, ordinary.getId());
      default -> throw new AssertionError(scenario);
    }
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(ordinary, decision));
    List<String> selected = requested;

    assertThatThrownBy(() -> service.selectPath(applicationId, selected, "修改路径", 1L, true))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(errorCode));
    verify(workflowSteps, never()).updateUnstartedSelection(any(), anyList(), anyBoolean(), any(), any());
    verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
    verifyNoInteractions(stageHistory);
  }

  @ParameterizedTest
  @CsvSource({"selected,0", "selected,2", "unselected,0", "unselected,2", "activation,0", "activation,2"})
  void partialPathWritesRequestTransactionRollback(String failedWrite, int changedRows) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    WorkflowStep ordinary = pathStep("S".repeat(26), "CANDIDATE_COMMUNICATION", 1, "PENDING", true);
    WorkflowStep decision = pathStep("T".repeat(26), "DECISION", 2, "AVAILABLE", false);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(ordinary, decision));
    when(workflowSteps.updateUnstartedSelection(any(), anyList(), eq(true), any(), any()))
        .thenReturn("selected".equals(failedWrite) ? changedRows : 1);
    when(workflowSteps.updateUnstartedSelection(any(), anyList(), eq(false), any(), any()))
        .thenReturn("unselected".equals(failedWrite) ? changedRows : 1);
    when(workflowSteps.selectStep(any(), any(), any(), any(), any(), any())).thenReturn(changedRows);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService = (RecruitmentApplicationManager) proxyFactory.getProxy();

    assertThatThrownBy(() -> transactionalService.selectPath(applicationId, List.of(), "修改路径", 1L, true))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
    verify(transactions).rollback(transaction);
    verify(transactions, never()).commit(any());
    verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
    verify(persistence, never()).insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    verifyNoInteractions(stageHistory);
  }

  private static WorkflowStep pathStep(String id, String code, int sequence, String status, boolean optional) {
    WorkflowStep step = new WorkflowStep();
    step.setId(id);
    step.setStepCode(code);
    step.setSequenceNo(sequence);
    step.setStatus(status);
    step.setCandidateOptional(optional);
    return step;
  }

  @Test
  void createWritesNothingWhenActiveWorkflowIsMissing() {
    when(candidates.requireActive(CANDIDATE))
        .thenReturn(new CandidateDirectoryService.CandidateRef(CANDIDATE, "候选人"));
    when(resumes.requireAvailable(CANDIDATE, RESUME))
        .thenReturn(new CandidateResumeVersionService.ResumeVersionRef(RESUME, CANDIDATE));
    when(demands.requireJdVersion(JD_VERSION))
        .thenReturn(
            new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    when(demands.requireActiveWorkflow(JD_VERSION))
        .thenThrow(
            new PlatformException(
                HttpStatus.UNPROCESSABLE_CONTENT, "ACTIVE_WORKFLOW_REQUIRED", "缺少流程", false));

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "创建招聘活动"),
                    "create-application"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error ->
                Assertions.assertThat(error.code())
                    .isEqualTo("ACTIVE_WORKFLOW_REQUIRED"));
    verifyNoInteractions(matchPlans);
    verify(persistence, never())
        .insertApplication(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any());
  }

  @Test
  void createPersistsWorkflowStepsWithoutScalarWrites() {
    String applicationId = "I".repeat(26);
    when(candidates.requireActive(CANDIDATE))
        .thenReturn(new CandidateDirectoryService.CandidateRef(CANDIDATE, "候选人"));
    when(resumes.requireAvailable(CANDIDATE, RESUME))
        .thenReturn(new CandidateResumeVersionService.ResumeVersionRef(RESUME, CANDIDATE));
    when(demands.requireJdVersion(JD_VERSION))
        .thenReturn(
            new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    when(demands.requireActiveWorkflow(JD_VERSION))
        .thenReturn(
            new ActiveWorkflowSnapshot(
                "W".repeat(26),
                JD_VERSION,
                "INTERNAL",
                List.of(
                    new ActiveWorkflowSnapshot.Step(
                        "S".repeat(26), "CANDIDATE_COMMUNICATION", 1, true, "PHONE"),
                    new ActiveWorkflowSnapshot.Step(
                        "T".repeat(26), "DECISION", 2, false, null))));
    when(matchPlans.requireActive(JD_VERSION))
        .thenReturn(
            new ActiveMatchPlanQueryService.ActiveMatchPlanSnapshot(
                "Q".repeat(26), JD_VERSION, "E".repeat(26), 1));
    Application created = application(applicationId, ACTOR, now);
    created.setCreateTime(now);
    when(persistence.findApplication(applicationId)).thenReturn(created);
    when(persistence.insertApplication(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any()))
        .thenReturn(1);
    when(workflowSteps.insertSteps(anyList())).thenReturn(true);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(ownership.insertOwnership(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);

    service.create(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "创建招聘活动"),
        "create-application-steps");

    var routingOrder = Mockito.inOrder(resumes, persistence);
    routingOrder.verify(resumes).lockForRouting(CANDIDATE, RESUME);
    routingOrder.verify(persistence).insertApplication(any(), eq(CANDIDATE), any(), any(),
        eq(JD_VERSION), eq(RESUME), any(), any(), any(), any(), any(), any(), any());
    verify(screeningTransitions).complete(CANDIDATE, RESUME, ACTOR, now);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<WorkflowStep>> batch = ArgumentCaptor.forClass(List.class);
    verify(workflowSteps).insertSteps(batch.capture());
    assertThat(batch.getValue())
        .extracting(
            WorkflowStep::getApplicationId,
            WorkflowStep::getTemplateVersionId,
            WorkflowStep::getStepDefinitionId,
            WorkflowStep::getStepCode,
            WorkflowStep::getSequenceNo,
            WorkflowStep::getStatus,
            WorkflowStep::getVersion,
            WorkflowStep::getCreateTime,
            WorkflowStep::getUpdateTime)
        .containsExactly(
            Tuple.tuple(
                applicationId,
                "W".repeat(26),
                "S".repeat(26),
                "CANDIDATE_COMMUNICATION",
                1,
                "AVAILABLE",
                0L,
                now,
                now),
            Tuple.tuple(
                applicationId,
                "W".repeat(26),
                "T".repeat(26),
                "DECISION",
                2,
                "AVAILABLE",
                0L,
                now,
                now));
    assertThat(mockingDetails(workflowSteps).getInvocations())
        .noneMatch(invocation -> invocation.getMethod().getName().equals("insertStep"));
  }

  @Test
  void createFailsClosedWhenWorkflowStepBatchReportsFailure() {
    ActiveWorkflowSnapshot workflow =
        new ActiveWorkflowSnapshot(
            "W".repeat(26),
            JD_VERSION,
            "INTERNAL",
            List.of(
                new ActiveWorkflowSnapshot.Step(
                    "S".repeat(26), "DECISION", 1, false, null)));

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    service, "insertApplicationSteps", "I".repeat(26), workflow, now))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Application 流程步骤批量写入失败");
  }

  @Test
  void retiredClientSubmissionStepIsRemovedWithoutLeavingSequenceGaps() {
    ActiveWorkflowSnapshot workflow =
        new ActiveWorkflowSnapshot(
            "W".repeat(26),
            JD_VERSION,
            "EXTERNAL",
            List.of(
                new ActiveWorkflowSnapshot.Step(
                    "S".repeat(26), "CANDIDATE_COMMUNICATION", 1, true, "PHONE"),
                new ActiveWorkflowSnapshot.Step(
                    "T".repeat(26), "CLIENT_SUBMISSION", 2, false, null),
                new ActiveWorkflowSnapshot.Step(
                    "U".repeat(26), "DECISION", 3, false, null)));
    RecruitmentWorkflowStepPersistenceServiceImpl validator =
        spy(new RecruitmentWorkflowStepPersistenceServiceImpl());
    doReturn(true).when(validator).saveBatch(anyCollection(), eq(MAX_WORKFLOW_STEP_COUNT));
    when(workflowSteps.insertSteps(anyList()))
        .thenAnswer(invocation -> validator.insertSteps(invocation.getArgument(0)));
    when(ids.newId()).thenReturn("I".repeat(26), "J".repeat(26));

    ReflectionTestUtils.invokeMethod(
        service, "insertApplicationSteps", "A".repeat(26), workflow, now);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<WorkflowStep>> batch = ArgumentCaptor.forClass(List.class);
    verify(workflowSteps).insertSteps(batch.capture());
    assertThat(batch.getValue())
        .extracting(
            WorkflowStep::getStepDefinitionId,
            WorkflowStep::getStepCode,
            WorkflowStep::getSequenceNo)
        .containsExactly(
            Tuple.tuple("S".repeat(26), "CANDIDATE_COMMUNICATION", 1),
            Tuple.tuple("U".repeat(26), "DECISION", 2));
  }

  @Test
  void createFailsClosedWhenRequiredHistoryInsertAffectsNoRow() {
    when(candidates.requireActive(CANDIDATE))
        .thenReturn(new CandidateDirectoryService.CandidateRef(CANDIDATE, "候选人"));
    when(resumes.requireAvailable(CANDIDATE, RESUME))
        .thenReturn(new CandidateResumeVersionService.ResumeVersionRef(RESUME, CANDIDATE));
    when(demands.requireJdVersion(JD_VERSION))
        .thenReturn(
            new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    when(demands.requireActiveWorkflow(JD_VERSION))
        .thenReturn(
            new ActiveWorkflowSnapshot(
                "W".repeat(26),
                JD_VERSION,
                "INTERNAL",
                List.of(
                    new ActiveWorkflowSnapshot.Step(
                        "S".repeat(26), "DECISION", 1, false, null))));
    when(matchPlans.requireActive(JD_VERSION))
        .thenReturn(
            new ActiveMatchPlanQueryService.ActiveMatchPlanSnapshot(
                "Q".repeat(26), JD_VERSION, "E".repeat(26), 1));
    Application created = application("I".repeat(26), ACTOR, now);
    created.setCreateTime(now);
    when(persistence.findApplication(created.getId())).thenReturn(created);
    when(persistence.insertApplication(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any()))
        .thenReturn(1);
    when(workflowSteps.insertSteps(anyList())).thenReturn(true);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateApplicationDTO(
                        CANDIDATE, JD_VERSION, RESUME, null, "创建招聘活动"),
                    "create-history-failure"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Application 阶段历史写入失败");
    verify(ownership, never())
        .insertOwnership(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void intentionRejectsOversizedAndNullContainingLocationListsBeforePersistence() {
    String applicationId = "P".repeat(26);
    when(persistence.lockApplication(applicationId))
        .thenReturn(application(applicationId, ACTOR, now));
    List<String> oversized =
        IntStream.rangeClosed(1, 21).mapToObj(index -> "地点" + index).toList();

    assertThatThrownBy(
            () ->
                service.addIntention(
                    applicationId, null, null, null, null, oversized, "更新候选人意向"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTENTION_LOCATION_LIMIT"));
    assertThatThrownBy(
            () ->
                service.addIntention(
                    applicationId,
                    null,
                    null,
                    null,
                    null,
                    Arrays.asList("上海", null),
                    "更新候选人意向"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTENTION_LOCATION_INVALID"));
    verifyNoInteractions(intentions);
  }

  @Test
  void intentionRejectsOverlongLocationItemsAndCombinedStorageValue() {
    String applicationId = "P".repeat(26);
    when(persistence.lockApplication(applicationId))
        .thenReturn(application(applicationId, ACTOR, now));

    assertThatThrownBy(
            () ->
                service.addIntention(
                    applicationId,
                    null,
                    null,
                    null,
                    null,
                    List.of("地".repeat(101)),
                    "更新候选人意向"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTENTION_LOCATION_INVALID"));
    assertThatThrownBy(
            () ->
                service.addIntention(
                    applicationId,
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        "甲".repeat(100),
                        "乙".repeat(100),
                        "丙".repeat(100),
                        "丁".repeat(100),
                        "戊".repeat(100)),
                    "更新候选人意向"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTENTION_LOCATION_INVALID"));
    verifyNoInteractions(intentions);
  }

  @Test
  void completeWritesCommunicationAndFollowUpWithoutSeparateRequest() {
    String applicationId = "P".repeat(26), stepId = "S".repeat(26);
    Application app =
        new Application(
            applicationId,
            CANDIDATE,
            DEMAND,
            JD,
            JD_VERSION,
            RESUME,
            "W".repeat(26),
            "Q".repeat(26),
            "INTERNAL",
            "IN_PROCESS",
            null,
            ACTOR,
            DEPARTMENT,
            1L,
            null,
            1L);
    WorkflowStep step =
        new WorkflowStep(
            stepId,
            applicationId,
            "W".repeat(26),
            "F".repeat(26),
            "CANDIDATE_COMMUNICATION",
            1,
            true,
            "PHONE",
            "IN_PROGRESS",
            ACTOR,
            now.plusSeconds(3600),
            now,
            null,
            null,
            null,
            2L);
    app.setCreateTime(now);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(collaborators.findCollaborators(applicationId)).thenReturn(List.of());
    when(workflowSteps.findStep(applicationId, stepId)).thenReturn(step);
    when(followUps.countFollowUps(stepId)).thenReturn(0);

    when(workflowSteps.completeStep(any(),any(),any(),any(),anyLong(),any(),any())).thenReturn(1);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    service.completeStep(applicationId, stepId, "PASSED", "完成", null, 2);
    verify(communications).insertCommunication(any(), eq(applicationId), eq("OTHER"), eq("完成"), eq("CONTINUE"), any(), any(), eq(ACTOR), any());
    verify(followUps).insertFollowUp(any(), eq(applicationId), eq(stepId), eq("OTHER"), eq("完成"), any(), any(), eq(ACTOR), any());
  }

  @ParameterizedTest
  @CsvSource({"DECISION,complete", "OFFER,complete", "DECISION,skip", "OFFER,skip"})
  void genericStepCommandsCannotBypassDedicatedBusinessFacts(String stepCode, String operation) {
    String applicationId = "P".repeat(26), stepId = "S".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now);
    WorkflowStep step = new WorkflowStep();
    step.setId(stepId);
    step.setApplicationId(applicationId);
    step.setStepCode(stepCode);
    step.setStatus("IN_PROGRESS");
    step.setSequenceNo(1);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, stepId)).thenReturn(step);
    when(followUps.countFollowUps(stepId)).thenReturn(1);
    when(workflowSteps.completeStep(any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    when(workflowSteps.skipStep(any(), any(), anyLong(), any(), any())).thenReturn(1);
    // 管理员同样必须保留决定和 Offer 事实，不能用纠错权限绕过专用命令。
    authorization.currentActor().setSuperAdmin(true);

    assertThatThrownBy(() -> {
      if ("complete".equals(operation)) {
        service.completeStep(applicationId, stepId, "PASSED", "完成步骤", null, 1L);
      } else {
        service.skipStep(applicationId, stepId, "纠正错录", 1L);
      }
    }).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("WORKFLOW_STEP_DEDICATED_COMMAND_REQUIRED"));
    verify(workflowSteps, never()).completeStep(any(), any(), any(), any(), anyLong(), any(), any());
    verify(workflowSteps, never()).skipStep(any(), any(), anyLong(), any(), any());
    verify(workflowSteps, never()).findSteps(any());
    verifyNoInteractions(decisions, offers, stageHistory, locks);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PASSED", "FAILED", "WITHDRAWN", "SKIPPED"})
  void genericCommandsStillCompleteOrCorrectOrdinarySteps(String result) {
    String applicationId = "P".repeat(26), stepId = "S".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now);
    WorkflowStep step = new WorkflowStep();
    step.setId(stepId);
    step.setApplicationId(applicationId);
    step.setStepCode("CANDIDATE_COMMUNICATION");
    step.setStatus("IN_PROGRESS");
    step.setSequenceNo(1);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(workflowSteps.findStep(applicationId, stepId)).thenReturn(step);
    when(followUps.countFollowUps(stepId)).thenReturn(1);

    ApplicationDetails details;
    if ("SKIPPED".equals(result)) {
      authorization.currentActor().setSuperAdmin(true);
      when(workflowSteps.skipStep(stepId, "纠正错录", 1L, ACTOR, now)).thenReturn(1);
      details = service.skipStep(applicationId, stepId, "纠正错录", 1L);
      verify(workflowSteps).skipStep(stepId, "纠正错录", 1L, ACTOR, now);
    } else {
      when(workflowSteps.completeStep(stepId, result, "沟通结果", null, 1L, ACTOR, now))
          .thenReturn(1);
      details = service.completeStep(applicationId, stepId, result, "沟通结果", null, 1L);
      verify(workflowSteps).completeStep(stepId, result, "沟通结果", null, 1L, ACTOR, now);
    }
    assertThat(details.getApplication().getId()).isEqualTo(applicationId);
  }

  @Test
  void noResponseCommunicationDoesNotRenewCandidateEngagementLock() {
    String applicationId = "P".repeat(26), communicationId = "I".repeat(26);
    Application app =
        new Application(
            applicationId,
            CANDIDATE,
            DEMAND,
            JD,
            JD_VERSION,
            RESUME,
            "W".repeat(26),
            "Q".repeat(26),
            "INTERNAL",
            "IN_PROCESS",
            null,
            ACTOR,
            DEPARTMENT,
            1L,
            null,
            1L);
    Communication communication =
        new Communication(
            communicationId,
            applicationId,
            "PHONE",
            "无人接听",
            "NO_RESPONSE",
            "明日再次联系",
            now,
            ACTOR);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(collaborators.findCollaborators(applicationId)).thenReturn(List.of());
    when(communications.insertCommunication(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(communications.findByIdForUpdate(communicationId)).thenReturn(communication);
    when(locks.activeLock(CANDIDATE))
        .thenReturn(
            new EngagementLockQueryService.LockSummary(
                "L".repeat(26),
                CANDIDATE,
                ACTOR,
                DEPARTMENT,
                "候选人",
                now,
                now.plusSeconds(3600),
                true,
                null));

    service.communicate(applicationId, "PHONE", "无人接听", "NO_RESPONSE", "明日再次联系", now, "communication-key");

    verify(locks, never()).recordProgress(any(), any(), any());
  }

  @Test
  void communicationResponseLossDoesNotInsertAgain() {
    String applicationId="P".repeat(26), communicationId="I".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId,ACTOR,now));
    Communication stored=new Communication(communicationId,applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",now,ACTOR);
    when(communications.findByIdForUpdate(communicationId)).thenReturn(stored);
    var first=service.communicate(applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",null,"communication-key");
    when(idempotency.findIdempotency(ACTOR,"communication-key")).thenReturn(communicationReplay(applicationId,null));
    ReflectionTestUtils.setField(service,"clock",Clock.fixed(now.plusSeconds(60),ZoneOffset.UTC));
    var repeated=service.communicate(applicationId," phone "," 无人接听 ","no_response"," 明日再次联系 ",null,"communication-key");
    assertThat(repeated.getId()).isEqualTo(first.getId());
    assertThat(repeated.getCommunicatedAt()).isEqualTo(now);
    verify(communications).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
    verify(communications,never()).findCommunications(any());
  }

  @Test
  void createIsBlockedWhenHeadcountIsMissingOrFull() {
    when(candidates.requireActive(CANDIDATE))
        .thenReturn(new CandidateDirectoryService.CandidateRef(CANDIDATE, "候选人"));
    when(resumes.requireAvailable(CANDIDATE, RESUME))
        .thenReturn(new CandidateResumeVersionService.ResumeVersionRef(RESUME, CANDIDATE));
    JdVersionSnapshot jd = new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of());
    doReturn(jd).when(demands).requireJdVersionForNewUse(JD_VERSION);
    CreateApplicationDTO command =
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "创建招聘活动");

    assertThatThrownBy(() -> service.create(command, "missing-hc"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_HEADCOUNT_REQUIRED"));

    jd.setHeadcount(1);
    when(offers.countOccupiedByJd(JD)).thenReturn(1);
    assertThatThrownBy(() -> service.create(command, "full-hc"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_HEADCOUNT_FULL"));
    verify(persistence, never()).insertApplication(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADVANCE", "CORRECT"})
  void enteringIssuedOfferDoesNotOccupyHeadcount(String commandType) {
    String applicationId = "P".repeat(26), priorId = "B".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    JdVersionSnapshot jd = new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of());
    jd.setHeadcount(1);
    doReturn(jd).when(demands).lockJdForHeadcount(JD_VERSION);
    when(offers.countOccupiedByJd(JD)).thenReturn(1);
    OfferStatusRecord prior = new OfferStatusRecord();
    prior.setId(priorId);
    prior.setApplicationId(applicationId);
    prior.setStatus("IN_PROGRESS");
    prior.setOccurredAt(now.minusSeconds(60));
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(prior);
    when(offers.findOffer(applicationId, priorId)).thenReturn(prior);
    OfferCommandDTO command =
        new OfferCommandDTO("ISSUED", null, null, "发放 Offer", now, null, null);
    OfferStatusRecord expected = new OfferStatusRecord();
    expected.setId("I".repeat(26));
    expected.setApplicationId(applicationId);
    expected.setStatus("ISSUED");
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(offers.findOffer(applicationId, expected.getId())).thenReturn(expected);

    OfferStatusRecord actual = "ADVANCE".equals(commandType)
        ? service.addOffer(applicationId, command, "full-hc-advance")
        : service.correctOffer(applicationId, priorId, command, "full-hc-correct");

    assertThat(actual).isSameAs(expected);
    verify(offers, never()).countOccupiedByJd(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"确认入职", ""})
  void onboardingAnIssuedOfferConsumesTheAvailableHeadcount(String comment) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    JdVersionSnapshot jd = new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of());
    jd.setHeadcount(1);
    doReturn(jd).when(demands).lockJdForHeadcount(JD_VERSION);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setId("B".repeat(26));
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    issued.setOccurredAt(now.minusSeconds(60));
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(issued);
    when(onboarding.insertOnboarding(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    OnboardingEvent expected =
        new OnboardingEvent("I".repeat(26), applicationId, now, "MANUAL_BEISEN_REFERENCE",
            null, comment, ACTOR);
    when(onboarding.findOnboarding(applicationId)).thenReturn(expected);

    assertThat(service.onboard(applicationId, now, null, comment, "onboard-full-hc"))
        .isSameAs(expected);

    verify(onboarding).insertOnboarding(any(), eq(applicationId), eq(now),
        eq("MANUAL_BEISEN_REFERENCE"), isNull(), eq(comment), eq(ACTOR), any());
    verify(offers).countOccupiedByJd(JD);
    verify(offers).insertOffer(any(), eq(applicationId), eq("STATUS"), eq("ONBOARDED"), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void onboardingIsBlockedWhenActualOnboardingWouldExceedHeadcount() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    JdVersionSnapshot jd = new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of());
    jd.setHeadcount(1);
    doReturn(jd).when(demands).lockJdForHeadcount(JD_VERSION);
    when(offers.countOccupiedByJd(JD)).thenReturn(1);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    issued.setOccurredAt(now.minusSeconds(60));
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(issued);

    assertThatThrownBy(() -> service.onboard(
        applicationId, now, null, "确认实际入职", "onboard-full-hc"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_HEADCOUNT_FULL"));

    verify(onboarding, never()).insertOnboarding(any(), any(), any(), any(), any(), any(), any(), any());
    verify(offers, never()).insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any());
  }

  @Test
  void hireCombinesDecisionAndIssuedOfferBeforeStartingOnboardingProcess() {
    String applicationId = "P".repeat(26);
    Application decisionApp = application(applicationId, ACTOR, now);
    decisionApp.setCurrentStage("DECISION");
    Application offerApp = application(applicationId, ACTOR, now);
    offerApp.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(decisionApp);
    when(persistence.findApplication(applicationId)).thenReturn(decisionApp, offerApp);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setId("B".repeat(26));
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    issued.setOccurredAt(now);
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(issued);
    OnboardingProcess expected = new OnboardingProcess();
    expected.setApplicationId(applicationId);
    expected.setStatus("IN_PROGRESS");
    expected.setStartedAt(now);
    expected.setStartedByUserId(ACTOR);
    when(onboardingProcesses.insertProcess(applicationId, now, ACTOR, now)).thenReturn(1);
    when(onboardingProcesses.findProcess(applicationId)).thenReturn(expected);
    RecruitmentApplicationManager flow = spy(service);
    // Spy 决策同步更新当前读夹具，模拟事务自身的阶段写入。
    Mockito.doAnswer(call -> {
      doReturn(offerApp).when(persistence).lockApplication(applicationId);
      return new Decision();
    }).when(flow).decide(
        eq(applicationId), eq("PASS"), eq("确认录用"), isNull(), anyString());

    assertThat(flow.hire(applicationId, "ISSUED", "确认录用", "hire-once"))
        .isSameAs(expected);

    verify(flow).decide(eq(applicationId), eq("PASS"), eq("确认录用"), isNull(), anyString());
    verify(flow, never()).addOffer(eq(applicationId), any(), anyString());
    verify(flow, never()).onboard(anyString(), any(), any(), any(), anyString());
    verify(onboardingProcesses).insertProcess(applicationId, now, ACTOR, now);
  }

  @Test
  void onboardingReviewAppendsCorrectionAndUpdatesEffectiveTime() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("ONBOARDED");
    app.setTerminalAt(now.minusSeconds(10));
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setId("B".repeat(26));
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    issued.setOccurredAt(now.minusSeconds(3600));
    OfferStatusRecord onboarded = new OfferStatusRecord();
    onboarded.setId("O".repeat(26));
    onboarded.setApplicationId(applicationId);
    onboarded.setStatus("ONBOARDED");
    onboarded.setOccurredAt(now.minusSeconds(10));
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(onboarded);
    when(offers.findOffers(applicationId)).thenReturn(List.of(issued, onboarded));
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    Instant reviewedAt = now.minusSeconds(1800);
    OnboardingProcess current =
        onboardingProcess(applicationId, "ONBOARDED", now.minusSeconds(3600), now.minusSeconds(10));
    OnboardingProcess expected =
        onboardingProcess(applicationId, "ONBOARDED", now.minusSeconds(3600), reviewedAt);
    when(onboardingProcesses.lockProcess(applicationId)).thenReturn(current);
    when(onboardingProcesses.findProcess(applicationId)).thenReturn(expected);

    assertThat(service.reviewOnboarding(
        applicationId, reviewedAt, "北森信息复核", "review-onboarding"))
        .isSameAs(expected);

    verify(offers).insertOffer(
        any(), eq(applicationId), eq("CORRECTION"), eq("ONBOARDED"), isNull(), eq("PASS"),
        eq("北森信息复核"), eq(onboarded.getId()), isNull(), eq("MANUAL_ONBOARDING_REVIEW"),
        isNull(), eq("ONBOARDING_TIME_REVIEWED"), eq(reviewedAt), eq(ACTOR), eq(now));
    verify(persistence).updateOutcome(
        applicationId, "ONBOARDED", "PASS", reviewedAt, null, app.getVersion(), ACTOR, now);
    verify(onboardingProcesses).markOnboarded(
        applicationId, reviewedAt, ACTOR, current.getVersion(), now);
  }

  @Test
  void onboardingReviewLocksJdBeforeApplicationAndCompletesRealOnboarding() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    Instant startedAt = now.minusSeconds(3600);
    Instant reviewedAt = now.minusSeconds(600);
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> lockOrder = new ArrayList<>();
    JdVersionSnapshot jd = withHeadcount(new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    when(demands.lockJdForHeadcount(JD_VERSION)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      lockOrder.add("JD");
      return jd;
    });
    when(persistence.lockApplication(applicationId)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      // 首次 Application 行锁前必须已持有 JD；后续内部入职调用沿用同一事务。
      assertThat(lockOrder).contains("JD");
      lockOrder.add("APPLICATION");
      return app;
    });
    OnboardingProcess current = onboardingProcess(applicationId, "IN_PROGRESS", startedAt, null);
    OnboardingProcess expected = onboardingProcess(applicationId, "ONBOARDED", startedAt, reviewedAt);
    when(onboardingProcesses.lockProcess(applicationId)).thenReturn(current);
    when(onboardingProcesses.findProcess(applicationId)).thenReturn(expected);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setId("B".repeat(26));
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    issued.setOccurredAt(startedAt);
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(issued);
    when(onboarding.insertOnboarding(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);

    assertThat(commandProxy(transactions).reviewOnboarding(
        applicationId, reviewedAt, "确认实际入职", "review-onboarding"))
        .isSameAs(expected);

    assertThat(lockOrder).startsWith("JD", "APPLICATION");
    verify(onboarding).insertOnboarding(any(), eq(applicationId), eq(reviewedAt),
        eq("MANUAL_BEISEN_REFERENCE"), isNull(), eq("确认实际入职"), eq(ACTOR), eq(now));
    verify(offers).insertOffer(any(), eq(applicationId), eq("STATUS"), eq("ONBOARDED"),
        isNull(), eq("PASS"), eq("确认实际入职"), isNull(), isNull(),
        eq("MANUAL_BEISEN_REFERENCE"), isNull(), isNull(), eq(reviewedAt), eq(ACTOR), eq(now));
    verify(persistence).updateOutcome(applicationId, "ONBOARDED", "PASS", reviewedAt,
        null, app.getVersion(), ACTOR, now);
    verify(onboardingProcesses).markOnboarded(
        applicationId, reviewedAt, ACTOR, current.getVersion(), now);
    verify(offers).countOccupiedByJd(JD);
    assertCommandTransaction(transactions, true);
  }

  @Test
  void onboardingReturnDefaultsReasonAndMovesCandidateToSharedLibraryAtomically() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setId("O".repeat(26));
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(issued);
    OnboardingProcess process = onboardingProcess(
        applicationId, "IN_PROGRESS", now.minusSeconds(3600), null);
    when(onboardingProcesses.lockProcess(applicationId)).thenReturn(process);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    OfferStatusRecord expected = new OfferStatusRecord();
    expected.setId("I".repeat(26));
    when(offers.findOffer(applicationId, "I".repeat(26))).thenReturn(expected);

    assertThat(service.returnOnboardingToShared(
        applicationId, null, null, "return-onboarding"))
        .isSameAs(expected);

    verify(candidateOnboardingReturns).returnToShared(
        CANDIDATE, RESUME, "入职库退回", ACTOR);
    verify(offers).insertOffer(
        any(), eq(applicationId), eq("STATUS"), eq("TERMINATED"), eq("OTHER"), eq("REJECT"),
        eq("入职库退回"), isNull(), isNull(), eq("MANUAL_ONBOARDING_RETURN"), isNull(),
        eq("ONBOARDING_LIBRARY_RETURN"), eq(now), eq(ACTOR), eq(now));
    verify(persistence).updateOutcome(
        applicationId, "TERMINATED", "REJECT", now, null, app.getVersion(), ACTOR, now);
    verify(stageHistory).insertStageHistory(
        any(), eq(applicationId), eq("OFFER"), eq("TERMINATED"), eq("入职库退回"), eq(ACTOR), eq(now));
    verify(onboardingProcesses).markReturned(
        applicationId, ACTOR, process.getVersion(), now);
  }

  @Test
  void onboardingReturnsConflictWhenSourceReferenceWasUsedByAnOffer() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    OfferStatusRecord issued = new OfferStatusRecord();
    issued.setId("B".repeat(26));
    issued.setApplicationId(applicationId);
    issued.setStatus("ISSUED");
    issued.setOccurredAt(now.minusSeconds(60));
    when(offers.latestOfferForUpdate(applicationId)).thenReturn(issued);
    when(onboarding.insertOnboarding(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenThrow(new DuplicateKeyException("duplicate source"));

    assertThatThrownBy(() -> service.onboard(
        applicationId, now, "already-used-source", "确认入职", "duplicate-onboard-source"))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(error.code()).isEqualTo("ONBOARDING_SOURCE_EVENT_EXISTS");
        });

    verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void communicationRollsBackWhenInsertCardinalityIsInvalid(int affectedRows) {
    String applicationId = "P".repeat(26), communicationId = "I".repeat(26);
    when(persistence.lockApplication(applicationId))
        .thenReturn(application(applicationId, ACTOR, now));
    when(communications.insertCommunication(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(affectedRows);
    when(communications.findCommunications(applicationId))
        .thenReturn(
            List.of(
                new Communication(
                    communicationId,
                    applicationId,
                    "PHONE",
                    "沟通完成",
                    "CONTINUE",
                    "安排下一步",
                    now,
                    ACTOR)));

    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxyFactory.addAdvice(interceptor);
    RecruitmentApplicationManager transactionalService =
        (RecruitmentApplicationManager) proxyFactory.getProxy();

    assertThatThrownBy(
            () ->
                transactionalService.communicate(
                    applicationId, "PHONE", "沟通完成", "CONTINUE", "安排下一步", now, "communication-key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("候选人沟通记录写入失败");
    verifyNoInteractions(locks);
    verify(communications, never()).findCommunications(any());
    verify(transactions).rollback(transaction);
    verify(transactions, never()).commit(any());
  }

  private IdempotencyRecord communicationReplay(String applicationId, Instant inputTime) {
    return new IdempotencyRecord(ACTOR,"communication-key","COMMUNICATION_RECORD",
        Sha256Utils.fingerprintObjects(31,applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",inputTime),
        "I".repeat(26));
  }

  @ParameterizedTest
  @ValueSource(strings={"channel","summary","result","nextPlan","time","application"})
  void communicationReplayRejectsChangedRequest(String changed) {
    String original="P".repeat(26);
    String applicationId=changed.equals("application") ? "Q".repeat(26) : original;
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId,ACTOR,now));
    when(idempotency.findIdempotency(ACTOR,"communication-key")).thenReturn(communicationReplay(original,null));
    assertThatThrownBy(() -> service.communicate(applicationId,
        changed.equals("channel") ? "OTHER" : "PHONE",changed.equals("summary") ? "新说明" : "无人接听",
        changed.equals("result") ? "CONTINUE" : "NO_RESPONSE",changed.equals("nextPlan") ? "下周联系" : "明日再次联系",
        changed.equals("time") ? now : null,"communication-key"))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    verifyNoInteractions(communications,locks);
  }

  @ParameterizedTest
  @ValueSource(booleans={true})
  void communicationReplaySurvivesLaterClosureButNewCommandDoesNot(boolean closed) {
    String applicationId="P".repeat(26);
    Application app=application(applicationId,ACTOR,now);
    if(closed) app.setTerminalAt(now);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(idempotency.findIdempotency(ACTOR,"communication-key")).thenReturn(communicationReplay(applicationId,null));
    var stored=new Communication("I".repeat(26),applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",now,ACTOR);
    when(communications.findByIdForUpdate(stored.getId())).thenReturn(stored);
    assertThat(service.communicate(applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",null,"communication-key")).isSameAs(stored);
    assertThatThrownBy(() -> service.communicate(applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",null,"new-key"))
        .isInstanceOf(PlatformException.class);
    verify(communications,never()).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
    verifyNoInteractions(locks);
  }

  @Test void communicationReplayRequiresCurrentProcessingPermission() {
    String applicationId="P".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId,"O".repeat(26),now));
    assertThatThrownBy(() -> service.communicate(applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",null,"communication-key"))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
    verifyNoInteractions(idempotency,communications,locks);
  }

  @Test void communicationReplayRejectsRevokedResourceAccessBeforeLookup() {
    String applicationId = "P".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId, ACTOR, now));
    doThrow(PlatformExceptions.forbidden("ACCESS_REVOKED", "当前访问权限已撤销"))
        .when(authorization).requireAccess(eq("recruitment.manage"), any());
    assertThatThrownBy(() -> service.communicate(applicationId, "PHONE", "无人接听", "NO_RESPONSE",
        "明日再次联系", null, "communication-key"))
        .isInstanceOfSatisfying(PlatformException.class,
            ex -> assertThat(ex.code()).isEqualTo("ACCESS_REVOKED"));
    verifyNoInteractions(idempotency, communications, locks);
  }

  @Test void communicationReservationConflictReadsWinnerUsingCurrentRead() {
    String applicationId="P".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId,ACTOR,now));
    when(persistence.insertIdempotency(any(),any(),any(),any(),any(),any())).thenReturn(0);
    when(idempotency.findIdempotencyForUpdate(ACTOR,"communication-key")).thenReturn(communicationReplay(applicationId,null));
    var stored=new Communication("I".repeat(26),applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",now,ACTOR);
    when(communications.findByIdForUpdate(stored.getId())).thenReturn(stored);
    assertThat(service.communicate(applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",null,"communication-key")).isSameAs(stored);
    verify(idempotency).findIdempotency(ACTOR,"communication-key");
    verify(idempotency).findIdempotencyForUpdate(ACTOR,"communication-key");
    verify(communications,never()).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
  }

  @Test void communicationReplayDoesNotReturnAnotherApplicationsRecord() {
    String applicationId="P".repeat(26);
    when(persistence.lockApplication(applicationId)).thenReturn(application(applicationId,ACTOR,now));
    when(idempotency.findIdempotency(ACTOR,"communication-key")).thenReturn(communicationReplay(applicationId,null));
    var stored=new Communication();stored.setApplicationId("Q".repeat(26));
    when(communications.findByIdForUpdate("I".repeat(26))).thenReturn(stored);
    assertThatThrownBy(() -> service.communicate(applicationId,"PHONE","无人接听","NO_RESPONSE","明日再次联系",null,"communication-key"))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("COMMUNICATION_RESULT_MISSING"));
    verify(communications,never()).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
  }

  @Test
  void validatesCollaboratorsWithOneDirectoryBatchLookup() {
    String applicationId = "P".repeat(26);
    String firstCollaborator = "U".repeat(26);
    String secondCollaborator = "V".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCreateTime(now.minusSeconds(60));
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(collaborators.findCollaborators(applicationId)).thenReturn(List.of());
    when(persistence.updateStage(
            applicationId, "IN_PROCESS", 1L, ACTOR, now))
        .thenReturn(1);
    when(persistence.findApplication(applicationId)).thenReturn(app);
    when(persistence.insertCollaboratorChange(
            any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    when(collaborators.insertCollaborators(
            applicationId,
            List.of(firstCollaborator, secondCollaborator),
            ACTOR,
            now))
        .thenReturn(true);

    service.updateCollaborators(
        applicationId,
        List.of(firstCollaborator, secondCollaborator),
        1L,
        "调整协作人");

    verify(directory).activeUsers(Set.of(firstCollaborator, secondCollaborator));
    verify(directory, never()).activeUser(firstCollaborator);
    verify(directory, never()).activeUser(secondCollaborator);
    verify(collaborators)
        .insertCollaborators(
            applicationId,
            List.of(firstCollaborator, secondCollaborator),
            ACTOR,
            now);
    assertThat(mockingDetails(collaborators).getInvocations())
        .noneMatch(invocation -> invocation.getMethod().getName().equals("insertCollaborator"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"collaborators", "path", "stage", "followUp", "intention"})
  void requiredRecordFailureStopsTheUseCaseBeforeReadback(String failedWrite) {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    WorkflowStep step = new WorkflowStep();
    step.setId("S".repeat(26));
    step.setApplicationId(applicationId);
    step.setStepCode("DECISION");
    step.setStatus("AVAILABLE");
    step.setSequenceNo(1);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(step));
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenReturn(1);

    Runnable command;
    switch (failedWrite) {
      case "collaborators" -> {
        when(collaborators.insertCollaborators(any(), anyList(), any(), any())).thenReturn(true);
        command = () -> service.updateCollaborators(applicationId, List.of(ACTOR), 1L, "更新协作人");
      }
      case "path", "stage" -> {
        when(workflowSteps.selectStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(workflowSteps.updateUnstartedSelection(any(), anyList(), anyBoolean(), any(), any()))
            .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());
        if ("stage".equals(failedWrite)) {
          when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
          when(persistence.insertPathChange(
                  any(), any(), any(), any(), any(), any(), anyLong(), any(), any()))
              .thenReturn(1);
        }
        command = () -> service.selectPath(applicationId, List.of(), "选择实际路径", 1L, false);
      }
      case "followUp" -> {
        when(followUps.insertFollowUp(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(0);
        step.setStatus("PENDING");
        when(workflowSteps.findStep(applicationId, step.getId())).thenReturn(step);
        command = () -> service.followUp(
            applicationId, step.getId(), "PHONE", "沟通完成", "安排下一步", now);
      }
      case "intention" -> command = () -> service.addIntention(
          applicationId, null, null, null, null, List.of("上海"), "记录意向");
      default -> throw new AssertionError("Unknown test case: " + failedWrite);
    }

    String expectedMessage = switch (failedWrite) {
      case "collaborators" -> "协作人变更记录写入失败";
      case "path" -> "招聘路径变更记录写入失败";
      case "stage" -> "Application 阶段历史写入失败";
      case "followUp" -> "招聘跟进记录写入失败";
      case "intention" -> "候选人意向记录写入失败";
      default -> throw new AssertionError("Unknown test case: " + failedWrite);
    };
    assertThatThrownBy(command::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(expectedMessage);
    verify(persistence, never()).findApplication(any());
    verify(followUps, never()).findFollowUps(any());
    verify(intentions, never()).findIntentions(any());
    if ("path".equals(failedWrite)) {
      verifyNoInteractions(stageHistory);
    }
  }

  @Test
  void rejectsCollaboratorCollectionsBeyondTheApplicationLimit() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    List<String> oversized =
        IntStream.rangeClosed(0, 50)
            .mapToObj(index -> String.format("%026d", index))
            .toList();

    assertThatThrownBy(
            () -> service.updateCollaborators(applicationId, oversized, 1L, "调整协作人"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("COLLABORATOR_LIMIT"));
    verifyNoInteractions(directory);
  }

  @Test
  void workbenchUsesFixedSystemThresholdInsteadOfStoredDeadline() {
    Instant anchor = now.minus(73, ChronoUnit.HOURS);
    WorkItemRow row =
        new WorkItemRow(
            "P".repeat(26),
            CANDIDATE,
            JD,
            JD_VERSION,
            "S".repeat(26),
            "CANDIDATE_COMMUNICATION",
            "PENDING",
            ACTOR,
            now.plusSeconds(86400),
            anchor,
            "IN_PROCESS",
            null,
            now.minusSeconds(7200));
    when(persistence.findWorkItems(ACTOR, false, Set.of(), 100)).thenReturn(List.of(row));
    List<WorkItem> items = service.workbench(false, 100);

    assertThat(items)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getDueAt())
                  .isEqualTo(anchor.plus(72, ChronoUnit.HOURS));
              assertThat(item.isOverdue()).isTrue();
            });
  }

  @Test
  void cancelledRecruitmentRequiresNewSharedScoreBeforeCreatingAnotherApplication() {
    configureBatchPathCreation();
    doThrow(PlatformExceptions.conflict("CURRENT_RECRUITMENT_SCORE_REQUIRED", "请重新选择 JD 评分"))
        .when(resumes).requireCurrentScore(CANDIDATE, RESUME, JD_VERSION, null);
    assertThatThrownBy(() -> service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "再次推荐"), List.of(), "new-score-required"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURRENT_RECRUITMENT_SCORE_REQUIRED"));
    verify(persistence, never()).insertApplication(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(workflowSteps, matchPlans);
  }

  @Test
  void batchCreationMapsSelectedCodesToFrozenStepsAndKeepsMandatoryDecision() {
    configureBatchPathCreation();
    var response = service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "批量确认路径"),
        List.of(WorkflowStepCode.CANDIDATE_COMMUNICATION), "batch-path");
    assertThat(response.getApplication().getCurrentStage()).isEqualTo("IN_PROCESS");
    verify(workflowSteps).selectStep(eq("communication"), eq("IN_PROGRESS"), eq(ACTOR), any(), eq(ACTOR), eq(now));
    verify(workflowSteps).updateUnstartedSelection("I".repeat(26), List.of("communication", "decision"), true, ACTOR, now);
    verify(workflowSteps).updateUnstartedSelection("I".repeat(26), List.of(), false, ACTOR, now);
  }

  @Test
  void guessedPrivateCandidateCannotBeCreatedOnAnOwnedJd() {
    configureBatchPathCreation();
    doThrow(PlatformExceptions.forbidden("CANDIDATE_READ_FORBIDDEN", "候选人不在可访问范围"))
        .when(candidateAccess).requireResumeReadable(CANDIDATE, RESUME);
    assertThatThrownBy(() -> service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "尝试通过已知ID推荐"), List.of(), "private-candidate"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("CANDIDATE_READ_FORBIDDEN"));
    verifyNoInteractions(candidates, resumes, matchPlans);
    verify(persistence, never()).insertApplication(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void resumeOwnerCanRecommendToReadableLeadersJdButAnotherHrCannot() {
    configureBatchPathCreation();
    String leader = "L".repeat(26);
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(
        new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", leader, DEPARTMENT, Set.of()));
    when(candidates.ownedCandidateIds(List.of(CANDIDATE), ACTOR)).thenReturn(Set.of(CANDIDATE));

    var created = service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "本人简历推荐"),
        List.of(), "owner-recommendation");
    assertThat(created.getApplication().getId()).isEqualTo("I".repeat(26));
    verify(authorization).requireAccess(eq("demand.read"), any());

    when(candidates.ownedCandidateIds(List.of(CANDIDATE), ACTOR)).thenReturn(Set.of());
    assertThatThrownBy(() -> service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "他人简历推荐"),
        List.of(), "other-recommendation"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
  }

  @Test
  void authorizedApplicationReplayDoesNotRequireNewPoolVisibility() {
    configureBatchPathCreation();
    var command = new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "可读共享候选人推荐");
    service.createApplicationWithPath(command, List.of(), "authorized-replay");
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertIdempotency(eq(ACTOR), eq("authorized-replay"), eq("APPLICATION_CREATE"), fingerprint.capture(), any(), eq(now));
    when(idempotency.findIdempotency(ACTOR, "authorized-replay"))
        .thenReturn(new IdempotencyRecord(ACTOR, "authorized-replay", "APPLICATION_CREATE", fingerprint.getValue(), "I".repeat(26)));
    doThrow(PlatformExceptions.forbidden("CANDIDATE_READ_FORBIDDEN", "候选人池归属已变化"))
        .when(candidateAccess).requireResumeReadable(CANDIDATE, RESUME);
    assertThat(service.createApplicationWithPath(command, List.of(), "authorized-replay").getApplication().getId())
        .isEqualTo("I".repeat(26));
    verify(candidateAccess).requireResumeReadable(CANDIDATE, RESUME);
  }

  @Test
  void candidateVisibilityDoesNotGrantOtherPrivateResumeVersions() {
    configureBatchPathCreation();
    String otherPrivateVersion = "Z".repeat(26);
    doThrow(PlatformExceptions.forbidden("RESUME_VERSION_READ_FORBIDDEN", "只能访问招聘活动已冻结的简历版本"))
        .when(candidateAccess).requireResumeReadable(CANDIDATE, otherPrivateVersion);
    assertThatThrownBy(() -> service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, otherPrivateVersion, null, "协作候选的另一私有版本"), List.of(), "other-private-version"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("RESUME_VERSION_READ_FORBIDDEN"));
    verifyNoInteractions(candidates, resumes, matchPlans);
    verify(persistence, never()).insertApplication(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void readableFrozenResumeCanBeRecommendedWithoutOtherVersionAccess() {
    configureBatchPathCreation();
    doThrow(PlatformExceptions.forbidden("RESUME_VERSION_READ_FORBIDDEN", "其他版本不可读"))
        .when(candidateAccess).requireResumeReadable(CANDIDATE, "Z".repeat(26));
    var response = service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "推荐本人协作活动可读的冻结版本"), List.of(), "readable-frozen-version");
    assertThat(response.getApplication().getId()).isEqualTo("I".repeat(26));
    verify(candidateAccess).requireResumeReadable(CANDIDATE, RESUME);
    verify(resumes).requireAvailable(CANDIDATE, RESUME);
  }

  @Test
  void batchEmptyPathMeansExplicitlySkippingOptionalSteps() {
    configureBatchPathCreation();
    var response = service.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "明确仅人工决定"), List.of(), "batch-empty-path");
    assertThat(response.getApplication().getCurrentStage()).isEqualTo("DECISION");
    verify(workflowSteps).updateUnstartedSelection("I".repeat(26), List.of("decision"), true, ACTOR, now);
    verify(workflowSteps).updateUnstartedSelection("I".repeat(26), List.of("communication"), false, ACTOR, now);
  }

  @Test
  void batchPathFingerprintIgnoresOrderButRejectsChangedSelectionOnSameKey() {
    configureBatchPathCreation();
    var command = new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "批量路径幂等");
    service.createApplicationWithPath(command, List.of(WorkflowStepCode.CANDIDATE_COMMUNICATION, WorkflowStepCode.DECISION), "path-idempotency");
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertIdempotency(eq(ACTOR), eq("path-idempotency"), eq("APPLICATION_CREATE"), fingerprint.capture(), any(), eq(now));
    when(idempotency.findIdempotency(ACTOR, "path-idempotency"))
        .thenReturn(new IdempotencyRecord(ACTOR, "path-idempotency", "APPLICATION_CREATE", fingerprint.getValue(), "I".repeat(26)));
    service.createApplicationWithPath(command, List.of(WorkflowStepCode.DECISION, WorkflowStepCode.CANDIDATE_COMMUNICATION), "path-idempotency");
    verify(persistence).insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    assertThatThrownBy(() -> service.createApplicationWithPath(command, List.of(), "path-idempotency"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void invalidBatchPathRollsBackTheSameCreationTransaction() {
    configureBatchPathCreation();
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    ProxyFactory proxy = new ProxyFactory(service); proxy.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(interceptor);
    var transactional = (RecruitmentApplicationManager) proxy.getProxy();
    assertThatThrownBy(() -> transactional.createApplicationWithPath(
        new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "不存在的流程步骤"),
        List.of(WorkflowStepCode.CLIENT_SUBMISSION), "batch-invalid-path"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("APPLICATION_PATH_STEP_INVALID"));
    verify(transactions).rollback(transaction);
    verify(transactions, never()).commit(any());
    verify(workflowSteps, never()).updateUnstartedSelection(any(), any(), anyBoolean(), any(), any());
  }


  @ParameterizedTest
  @ValueSource(strings = {"create", "createApplication", "createApplicationWithPath"})
  void creationReplaySeesCommittedAggregateAfterLosingReservation(String entry) {
    CreationReadViewTransactions transactions = new CreationReadViewTransactions();
    configureCommittedCreationReplay(entry, transactions, "matching");

    AtomicReference<Object> replay = new AtomicReference<>();
    Assertions.assertThatCode(() -> replay.set(invokeCreation(transactionalCreation(transactions), entry)))
        .doesNotThrowAnyException();
    Object result = replay.get();

    if (result instanceof ApplicationDetails details) {
      assertThat(details.getApplication().getId()).isEqualTo("I".repeat(26));
      assertThat(details.getApplication().getCurrentStage()).isEqualTo("OFFER");
      assertThat(details.getCollaboratorUserIds()).containsExactly("B".repeat(26));
      assertThat(details.getWorkflowSteps()).hasSize(2);
      assertThat(details.getDecisions()).extracting(Decision::getId).containsExactly("D".repeat(26));
      assertThat(details.getOfferStatusRecords()).extracting(OfferStatusRecord::getId)
          .containsExactly("O".repeat(26));
    } else {
      ApplicationDetailsResponse response = (ApplicationDetailsResponse) result;
      assertThat(response.getApplication().getId()).isEqualTo("I".repeat(26));
      assertThat(response.getApplication().getCurrentStage()).isEqualTo("OFFER");
      assertThat(response.getCollaboratorUserIds()).containsExactly("B".repeat(26));
      assertThat(response.getWorkflowSteps()).hasSize(2);
      assertThat(response.getDecisions()).extracting(item -> item.getId()).containsExactly("D".repeat(26));
      assertThat(response.getOfferStatusRecords()).extracting(item -> item.getId()).containsExactly("O".repeat(26));
      assertThat(response.getApplication().getOnboardingStatus()).isEqualTo("IN_PROGRESS");
      assertThat(response.getApplication().getOnboardingElapsedSeconds()).isEqualTo(3600L);
      assertThat(response.getApplication().getLatestCompletedInterviewRoundNo()).isEqualTo(3);
      assertThat(response.getApplication().getClientId()).isEqualTo("X".repeat(26));
      assertThat(response.getApplication().getClientName()).isEqualTo("已提交客户");
      // OFFER 阶段没有正在执行的面试步骤。
      assertThat(response.getApplication().getCurrentInterviewType()).isNull();
    }
    verify(idempotency).findIdempotency(ACTOR, "creation-read-view");
    verify(idempotency).findIdempotencyForUpdate(ACTOR, "creation-read-view");
    assertCreationTransaction(transactions, true);
    verifyNoCreationFacts();
    verifyNoInteractions(candidateAccess, candidates, resumes);
  }

  @ParameterizedTest
  @CsvSource({
      "create, wrong, IDEMPOTENCY_KEY_REUSED",
      "createApplication, wrong, IDEMPOTENCY_KEY_REUSED",
      "createApplicationWithPath, wrong, IDEMPOTENCY_KEY_REUSED",
      "create, missing, IDEMPOTENCY_RESERVATION_FAILED",
      "createApplication, missing, IDEMPOTENCY_RESERVATION_FAILED",
      "createApplicationWithPath, missing, IDEMPOTENCY_RESERVATION_FAILED"})
  void creationReplayRejectsChangedPayloadAndMissingCommittedRecord(
      String entry, String winner, String expectedCode) {
    CreationReadViewTransactions transactions = new CreationReadViewTransactions();
    configureCommittedCreationReplay(entry, transactions, winner);

    assertThatThrownBy(() -> invokeCreation(transactionalCreation(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo(expectedCode));

    assertCreationTransaction(transactions, false);
    verifyNoCreationFacts();
    verifyNoInteractions(candidateAccess, candidates, resumes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"create", "createApplication", "createApplicationWithPath"})
  void creationReplayChecksCurrentResourceAuthorization(String entry) {
    CreationReadViewTransactions transactions = new CreationReadViewTransactions();
    configureCommittedCreationReplay(entry, transactions, "matching");
    doThrow(PlatformExceptions.forbidden("ACTION_FORBIDDEN", "资源授权已经撤销"))
        .when(authorization).requireAccess(eq("recruitment.manage"), any());

    assertThatThrownBy(() -> invokeCreation(transactionalCreation(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("ACTION_FORBIDDEN"));

    assertCreationTransaction(transactions, false);
    verifyNoCreationFacts();
    verifyNoInteractions(candidateAccess, candidates, resumes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"create", "createApplication", "createApplicationWithPath"})
  void normalCreationKeepsOneCommittingTransaction(String entry) {
    configureBatchPathCreation();
    CreationReadViewTransactions transactions = new CreationReadViewTransactions();

    invokeCreation(transactionalCreation(transactions), entry);

    assertCreationTransaction(transactions, true);
    verify(persistence).insertApplication(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any());
  }

  @Test
  void creationAndPathValidationKeepOneRollingBackTransaction() {
    configureBatchPathCreation();
    CreationReadViewTransactions transactions = new CreationReadViewTransactions();

    assertThatThrownBy(() -> transactionalCreation(transactions).createApplicationWithPath(
        creationReadViewCommand(), List.of(WorkflowStepCode.CLIENT_SUBMISSION), "creation-read-view"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_PATH_STEP_INVALID"));

    assertCreationTransaction(transactions, false);
    verify(persistence).insertApplication(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any());
    verify(workflowSteps, never()).updateUnstartedSelection(any(), any(), anyBoolean(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"createApplication", "createApplicationWithPath"})
  void creationAndResponseAssemblyKeepOneRollingBackTransaction(String entry) {
    configureBatchPathCreation();
    CreationReadViewTransactions transactions = new CreationReadViewTransactions();
    IllegalStateException failure = new IllegalStateException("响应装配失败");
    when(interviews.activeRoundNosByApplicationIds(any())).thenThrow(failure);

    assertThatThrownBy(() -> invokeCreation(transactionalCreation(transactions), entry)).isSameAs(failure);

    assertCreationTransaction(transactions, false);
    verify(persistence).insertApplication(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any());
  }

  private CreateApplicationDTO creationReadViewCommand() {
    return new CreateApplicationDTO(CANDIDATE, JD_VERSION, RESUME, null, "同一创建请求");
  }

  private Object invokeCreation(RecruitmentApplicationManager transactional, String entry) {
    return switch (entry) {
      case "create" -> transactional.create(creationReadViewCommand(), "creation-read-view");
      case "createApplication" -> transactional.createApplication(creationReadViewCommand(), "creation-read-view");
      case "createApplicationWithPath" -> transactional.createApplicationWithPath(
          creationReadViewCommand(), List.of(), "creation-read-view");
      default -> throw new IllegalArgumentException(entry);
    };
  }

  private RecruitmentApplicationManager transactionalCreation(CreationReadViewTransactions transactions) {
    ProxyFactory proxy = new ProxyFactory(service);
    proxy.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    // 读取生产方法的真实事务属性，测试不替换隔离级别。
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(interceptor);
    return (RecruitmentApplicationManager) proxy.getProxy();
  }

  private void configureCommittedCreationReplay(
      String entry, CreationReadViewTransactions transactions, String winnerMode) {
    configureBatchPathCreation();
    String target = "I".repeat(26);
    CreateApplicationDTO command = creationReadViewCommand();
    String fingerprint = entry.equals("createApplicationWithPath")
        ? Sha256Utils.fingerprintObjects(31, CANDIDATE, JD_VERSION, RESUME, null, command.getReason(), "")
        : Sha256Utils.fingerprintObjects(31, CANDIDATE, JD_VERSION, RESUME, null, command.getReason());
    IdempotencyRecord winner = new IdempotencyRecord(ACTOR, "creation-read-view", "APPLICATION_CREATE",
        winnerMode.equals("wrong") ? "另一载荷" : fingerprint, target);
    when(idempotency.findIdempotency(ACTOR, "creation-read-view")).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return transactions.winnerVisible() && !winnerMode.equals("missing") ? winner : null;
    });
    when(idempotency.findIdempotencyForUpdate(ACTOR, "creation-read-view")).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return transactions.winnerCommitted && !winnerMode.equals("missing") ? winner : null;
    });
    when(persistence.insertIdempotency(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      transactions.winnerCommitted = true;
      return 0;
    });
    Application application = application(target, ACTOR, now);
    application.setCreateTime(now.minusSeconds(7200));
    application.setCurrentStage("OFFER");
    application.setRecruitmentType("EXTERNAL");
    application.setProgressiveFollowUp(true);
    when(persistence.findApplication(target)).thenAnswer(call -> transactions.winnerVisible() ? application : null);
    when(collaborators.findCollaborators(target)).thenAnswer(call ->
        transactions.winnerVisible() ? List.of("B".repeat(26)) : List.of());
    WorkflowStep completed = pathStep("T".repeat(26), "DECISION", 2, "PASSED", false);
    WorkflowStep current = pathStep("S".repeat(26), "OFFER", 3, "IN_PROGRESS", true);
    completed.setApplicationId(target);
    current.setApplicationId(target);
    when(workflowSteps.findSteps(target)).thenAnswer(call ->
        transactions.winnerVisible() ? List.of(completed, current) : List.of());
    Decision decision = new Decision("D".repeat(26), target, "DECISION", "PASS", "已提交决定",
        null, null, ACTOR, now.minusSeconds(3600));
    decision.setCreateTime(now.minusSeconds(3600));
    when(decisions.findDecisions(target)).thenAnswer(call ->
        transactions.winnerVisible() ? List.of(decision) : List.of());
    OfferStatusRecord offer = new OfferStatusRecord();
    offer.setId("O".repeat(26));
    offer.setApplicationId(target);
    offer.setRecordType("STATUS");
    offer.setStatus("ISSUED");
    offer.setReason("已提交录用通知");
    offer.setOccurredAt(now.minusSeconds(3600));
    offer.setOperatedByUserId(ACTOR);
    when(offers.findOffers(target)).thenAnswer(call -> transactions.winnerVisible() ? List.of(offer) : List.of());
    OnboardingProcess process = onboardingProcess(target, "IN_PROGRESS", now.minusSeconds(3600), null);
    when(onboardingProcesses.findProcess(target)).thenAnswer(call -> transactions.winnerVisible() ? process : null);
    when(interviews.latestCompletedRoundNosByApplicationIds(Set.of(target))).thenAnswer(call ->
        transactions.winnerVisible() ? Map.of(target, 3) : Map.of());
    JdVersionSnapshot jd = mock(JdVersionSnapshot.class);
    when(jd.getClientId()).thenReturn("X".repeat(26));
    when(jd.getClientName()).thenReturn("已提交客户");
    when(demands.requireJdVersion(JD_VERSION)).thenAnswer(call -> transactions.winnerVisible() ? jd : null);
  }

  private void verifyNoCreationFacts() {
    verify(persistence, never()).insertApplication(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any());
    verify(workflowSteps, never()).insertSteps(anyList());
    verify(workflowSteps, never()).updateUnstartedSelection(any(), anyList(), anyBoolean(), any(), any());
    verify(ownership, never()).insertOwnership(any(), any(), any(), any(), any(), any(), any(), any());
    verify(stageHistory, never()).insertStageHistory(any(), any(), any(), any(), any(), any(), any());
  }

  private void assertCreationTransaction(CreationReadViewTransactions transactions, boolean committed) {
    assertThat(transactions.beginCount).isEqualTo(1);
    assertThat(transactions.commitCount).isEqualTo(committed ? 1 : 0);
    assertThat(transactions.rollbackCount).isEqualTo(committed ? 0 : 1);
  }

  // 合成数据库读视图只用于验证实际 Spring 隔离属性与业务链；不替代真实 MySQL 并发验证。
  private static final class CreationReadViewTransactions extends AbstractPlatformTransactionManager {
    private int isolation;
    private int beginCount;
    private int commitCount;
    private int rollbackCount;
    private boolean winnerCommitted;

    private boolean winnerVisible() {
      return winnerCommitted && isolation == TransactionDefinition.ISOLATION_READ_COMMITTED;
    }

    @Override
    protected Object doGetTransaction() { return new Object(); }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      isolation = definition.getIsolationLevel();
      beginCount++;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) { commitCount++; }

    @Override
    protected void doRollback(DefaultTransactionStatus status) { rollbackCount++; }
  }

  private void configureBatchPathCreation() {
    String applicationId = "I".repeat(26);
    ReflectionTestUtils.setField(service, "assembler", new ApplicationAssembler());
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    when(demands.requireActiveWorkflow(JD_VERSION)).thenReturn(new ActiveWorkflowSnapshot("W".repeat(26), JD_VERSION,
        "INTERNAL", List.of(new ActiveWorkflowSnapshot.Step("S".repeat(26), "CANDIDATE_COMMUNICATION", 1, true, "PHONE"),
        new ActiveWorkflowSnapshot.Step("T".repeat(26), "DECISION", 2, false, null))));
    when(matchPlans.requireActive(JD_VERSION)).thenReturn(new ActiveMatchPlanQueryService.ActiveMatchPlanSnapshot("Q".repeat(26), JD_VERSION, "E".repeat(26), 1));
    Application created = application(applicationId, ACTOR, now); created.setCreateTime(now); created.setCurrentStage("PATH_SELECTION");
    when(persistence.findApplication(applicationId)).thenReturn(created);
    when(persistence.lockApplication(applicationId)).thenReturn(created);
    when(persistence.insertApplication(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(workflowSteps.insertSteps(anyList())).thenReturn(true);
    when(stageHistory.insertStageHistory(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(ownership.insertOwnership(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(workflowSteps.findSteps(applicationId)).thenReturn(List.of(pathStep("communication", "CANDIDATE_COMMUNICATION", 1, "AVAILABLE", true),
        pathStep("decision", "DECISION", 2, "AVAILABLE", false)));
    when(workflowSteps.updateUnstartedSelection(any(), anyList(), anyBoolean(), any(), any())).thenAnswer(call -> ((List<?>) call.getArgument(1)).size());
    when(workflowSteps.selectStep(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateStage(any(), any(), anyLong(), any(), any())).thenAnswer(call -> { created.setCurrentStage(call.getArgument(1)); return 1; });
    when(persistence.insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void workbenchRejectsMissingPermissionBeforeReadingAssignedTasks(boolean management) {
    CurrentActorSnapshot actor = new CurrentActorSnapshot(
        ACTOR, "招聘人员", "hr@example.com", DEPARTMENT, Set.of(), Set.of(), Set.of(),
        Set.of(management ? "RECRUITMENT_MANAGER" : "RECRUITER"), Set.of(), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    doThrow(PlatformExceptions.forbidden("PERMISSION_REQUIRED", "缺少招聘权限"))
        .when(authorization).requirePermission("recruitment.manage");

    assertThatThrownBy(() -> service.workbench(management, 100))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("PERMISSION_REQUIRED"));

    verify(persistence, never()).findWorkItems(any(), any(Boolean.class), any(), any(Integer.class));
  }

  @Test
  void managementWorkbenchDelegatesVisibilityScopeBeforeLimit() {
    String managedOwner = "N".repeat(26);
    CurrentActorSnapshot manager =
        new CurrentActorSnapshot(
            ACTOR,
            "招聘经理",
            "manager@example.com",
            DEPARTMENT,
            Set.of(),
            Set.of(managedOwner),
            Set.of(),
            Set.of("RECRUITMENT_MANAGER"),
            Set.of("recruitment.manage"),
            false,
            false);
    when(authorization.currentActor()).thenReturn(manager);
    when(persistence.findWorkItems(ACTOR, false, Set.of(), 25))
        .thenReturn(List.of());

    assertThat(service.workbench(true, 25)).isEmpty();

    verify(persistence).findWorkItems(ACTOR, false, Set.of(), 25);
  }

  @Test
  void hrDirectorManagementWorkbenchUsesCompanyScope() {
    CurrentActorSnapshot director =
        new CurrentActorSnapshot(
            ACTOR,
            "人力负责人",
            "director@example.com",
            DEPARTMENT,
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("HR_DIRECTOR"),
            Set.of("recruitment.manage"),
            true,
            false);
    when(authorization.currentActor()).thenReturn(director);
    when(persistence.findWorkItems(ACTOR, true, Set.of(), 25)).thenReturn(List.of());

    assertThat(service.workbench(true, 25)).isEmpty();

    verify(persistence).findWorkItems(ACTOR, true, Set.of(), 25);
  }

  @Test
  void hrDirectorCanTransferAnApplicationOutsideManagementChain() {
    String applicationId = "P".repeat(26);
    String currentOwner = "O".repeat(26);
    String targetOwner = "T".repeat(26);
    CurrentActorSnapshot director =
        new CurrentActorSnapshot(
            ACTOR,
            "人力负责人",
            "director@example.com",
            DEPARTMENT,
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of("HR_DIRECTOR"),
            Set.of("recruitment.manage"),
            true,
            false);
    Application app = application(applicationId, currentOwner, now);
    app.setCreateTime(now);
    when(authorization.currentActor()).thenReturn(director);
    when(persistence.lockApplication(applicationId)).thenReturn(app);
    when(directory.activeUser(targetOwner))
        .thenReturn(new IdentityDirectoryService.UserReference(targetOwner, "目标招聘人", DEPARTMENT));
    when(persistence.updateOwner(
        eq(applicationId), eq(targetOwner), eq(DEPARTMENT), anyLong(), eq(ACTOR), eq(now)))
        .thenReturn(1);
    when(ownership.insertOwnership(any(), eq(applicationId), eq("TRANSFER"), eq(currentOwner),
        eq(targetOwner), eq("公司范围转交"), eq(ACTOR), eq(now))).thenReturn(1);
    when(persistence.findApplication(applicationId)).thenReturn(app);

    service.transfer(applicationId, targetOwner, "公司范围转交", app.getVersion(), "TRANSFER");

    verify(persistence).updateOwner(
        applicationId, targetOwner, DEPARTMENT, app.getVersion(), ACTOR, now);
  }

  @Test
  void operationalListReturnsOnlyApplicationsOwnedByRecruitmentManager() {
    String managedOwner = "N".repeat(26);
    CurrentActorSnapshot manager =
        new CurrentActorSnapshot(
            ACTOR,
            "招聘经理",
            "manager@example.com",
            DEPARTMENT,
            Set.of(),
            Set.of(managedOwner),
            Set.of(),
            Set.of("RECRUITMENT_MANAGER"),
            Set.of("recruitment.manage"),
            false,
            false);
    Application mine = application("V".repeat(26), ACTOR, now.minusSeconds(1));
    when(authorization.currentActor()).thenReturn(manager);
    when(persistence.findApplications(null, null, ACTOR, null, null, "ALL", null, null, null, null, null, 200))
        .thenReturn(List.of(mine));
    when(collaborators.findCollaborators(anyString())).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(CurrentActorSnapshot.class), eq("recruitment.manage"), any()))
        .thenReturn(Set.of(mine.getId()));

    var page = service.list(null, null, null, null, null, null, 50);

    assertThat(page.getItems()).extracting(Application::getId).containsExactly(mine.getId());
  }

  @Test
  void listPassesTimeAndInterviewFiltersBeforePaginationAndValidatesRange() {
    Instant from = now.minusSeconds(60);
    Instant to = now.plusSeconds(60);
    for (String type : List.of("INTERNAL", "CLIENT", "CLIENT_AI")) {
      service.list(null, null, "IN_PROCESS", "INTERVIEW_ROUND_1", null, null, 50,
          null, null, "ACTIVE", null, from, to, type);
      verify(persistence).findApplications(null, null, ACTOR, "IN_PROCESS", "INTERVIEW_ROUND_1",
          "ACTIVE", from, to, type, null, null, 200);
    }
    service.list(null, null, null, null, null, null, 50, null, null, "ALL", null, from, null, null);
    service.list(null, null, null, null, null, null, 50, null, null, "ALL", null, null, to, null);
    for (Instant invalidEnd : List.of(from, from.minusSeconds(1))) {
      assertThatThrownBy(() -> service.list(null, null, null, null, null, null, 50,
          null, null, "ALL", null, from, invalidEnd, null))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("APPLICATION_TIME_RANGE_INVALID"));
    }
    assertThatThrownBy(() -> service.list(null, null, null, null, null, null, 50,
        null, null, "ALL", null, from, to, "UNKNOWN"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_TYPE_INVALID"));
  }

  @Test
  void listCursorIsBoundToTimeAndInterviewFilters() {
    Application first = application("Y".repeat(26), ACTOR, now);
    Application second = application("Z".repeat(26), ACTOR, now.minusSeconds(1));
    first.setUpdateTime(now);
    second.setUpdateTime(now.minusSeconds(1));
    Instant from = now.minusSeconds(60);
    Instant to = now.plusSeconds(60);
    when(persistence.findApplications(null, null, ACTOR, null, null, "ALL", from, to, "CLIENT", null, null, 200))
        .thenReturn(List.of(first, second));
    when(collaborators.findCollaborators(anyString())).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(), eq("recruitment.manage"), any()))
        .thenReturn(Set.of(first.getId(), second.getId()));
    String cursor = service.list(null, null, null, null, null, null, 1,
        null, null, "ALL", null, from, to, "CLIENT").getNextCursor();
    assertThat(cursor).isNotNull();
    assertThatThrownBy(() -> service.list(null, null, null, null, null, cursor, 1,
        null, null, "ALL", null, from.plusSeconds(1), to, "CLIENT"))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> service.list(null, null, null, null, null, cursor, 1,
        null, null, "ALL", null, from, to.plusSeconds(1), "CLIENT"))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> service.list(null, null, null, null, null, cursor, 1,
        null, null, "ALL", null, from, to, "INTERNAL"))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void listValidatesAndPassesStageToThePagedDatabaseQuery() {
    when(persistence.findApplications(null, null, ACTOR, "ONBOARDED", null, "ALL", null, null, null, null, null, 200))
        .thenReturn(List.of());

    assertThat(service.list(null, null, "ONBOARDED", null, null, null, 50).getItems()).isEmpty();
    verify(persistence).findApplications(null, null, ACTOR, "ONBOARDED", null, "ALL", null, null, null, null, null, 200);

    assertThatThrownBy(() -> service.list(null, null, "UNKNOWN", null, null, null, 50))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_STAGE_INVALID"));
  }

  @Test
  void onboardingListIncludesInProgressProcessesAndExposesTheirLiveDuration() {
    String applicationId = "P".repeat(26);
    Application app = application(applicationId, ACTOR, now);
    app.setCurrentStage("OFFER");
    app.setUpdateTime(now);
    when(persistence.findApplications(
        null, null, ACTOR, "ONBOARDED", null, "ALL", null, null, null, true, null, null, 200))
        .thenReturn(List.of(app));
    when(collaborators.findCollaborators(applicationId)).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(), eq("recruitment.manage"), any()))
        .thenReturn(Set.of(applicationId));
    when(demands.clientSummariesForVersions(List.of(JD_VERSION))).thenReturn(Map.of());
    when(interviews.activeRoundNosByApplicationIds(Set.of(applicationId))).thenReturn(Map.of());
    when(interviews.latestCompletedRoundNosByApplicationIds(Set.of(applicationId))).thenReturn(Map.of());
    OnboardingProcess process =
        onboardingProcess(applicationId, "IN_PROGRESS", now.minusSeconds(5400), null);
    when(onboardingProcesses.findProcesses(List.of(applicationId))).thenReturn(List.of(process));

    var page = service.applicationResponses(
        null, null, "ONBOARDED", null, null, null, 50,
        null, null, "ALL", null, null, null, null, true);

    assertThat(page.getItems()).singleElement().satisfies(response -> {
      assertThat(response.getCurrentStage()).isEqualTo("OFFER");
      assertThat(response.getOnboardingStatus()).isEqualTo("IN_PROGRESS");
      assertThat(response.getOnboardingStartedAt()).isEqualTo(now.minusSeconds(5400));
      assertThat(response.getActualOnboardedAt()).isNull();
      assertThat(response.getOnboardingElapsedSeconds()).isEqualTo(5400);
    });
  }

  @Test
  void onboardingListRequiresTheOnboardedStageProjection() {
    assertThatThrownBy(() -> service.list(
        null, null, "OFFER", null, null, null, 50,
        null, null, "ALL", null, null, null, null, true))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("ONBOARDING_FILTER_SCOPE_INVALID"));
  }

  @Test
  void listCursorCannotBeReusedAcrossStageFilters() {
    Application first = application("Y".repeat(26), ACTOR, now);
    Application second = application("Z".repeat(26), ACTOR, now.minusSeconds(1));
    first.setUpdateTime(now);
    second.setUpdateTime(now.minusSeconds(1));
    when(persistence.findApplications(null, null, ACTOR, "ONBOARDED", null, "ALL", null, null, null, null, null, 200))
        .thenReturn(List.of(first, second));
    when(collaborators.findCollaborators(anyString())).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(), eq("recruitment.manage"), any()))
        .thenReturn(Set.of(first.getId(), second.getId()));

    String cursor = service.list(null, null, "ONBOARDED", null, null, null, 1).getNextCursor();

    assertThat(cursor).isNotNull();
    assertThatThrownBy(() -> service.list(null, null, "OFFER", null, null, cursor, 1))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void lifecycleFilterIsValidatedAndPassedToDatabaseWithoutChangingOwnerScope() {
    for (String lifecycle : List.of("ACTIVE", "ARCHIVED", "ALL")) {
      assertThat(service.list(null, null, null, null, null, null, 50, null, null, lifecycle).getItems()).isEmpty();
      verify(persistence).findApplications(null, null, ACTOR, null, null, lifecycle, null, null, null, null, null, 200);
    }
    assertThatThrownBy(() -> service.list(null, null, null, null, null, null, 50, null, null, "DELETED"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_LIFECYCLE_INVALID"));
  }

  @Test
  void historyCursorCannotBeReusedInActiveListAndStillChecksObjectAccess() {
    Application first = application("Y".repeat(26), ACTOR, now);
    Application second = application("Z".repeat(26), ACTOR, now.minusSeconds(1));
    Application hidden = application("X".repeat(26), ACTOR, now.minusSeconds(2));
    first.setUpdateTime(now);
    second.setUpdateTime(now.minusSeconds(1));
    hidden.setUpdateTime(now.minusSeconds(2));
    first.setTerminalAt(now);
    second.setTerminalAt(now);
    hidden.setTerminalAt(now);
    when(persistence.findApplications(null, null, ACTOR, null, null, "ARCHIVED", null, null, null, null, null, 200))
        .thenReturn(List.of(first, hidden, second));
    when(collaborators.findCollaborators(anyString())).thenReturn(List.of());
    when(authorization.accessibleObjectIds(any(), eq("recruitment.manage"), any()))
        .thenReturn(Set.of(first.getId(), second.getId()));

    var page = service.list(null, null, null, null, null, null, 1, null, null, "ARCHIVED");
    assertThat(page.getItems()).extracting(Application::getId).containsExactly(first.getId());
    assertThat(page.getNextCursor()).isNotNull();
    assertThatThrownBy(() -> service.list(null, null, null, null, null, page.getNextCursor(), 1,
        null, null, "ACTIVE")).isInstanceOf(PlatformException.class);
    assertThat(service.list(null, null, null, null, null, null, 50, null, null, "ARCHIVED").getItems())
        .extracting(Application::getId).containsExactly(first.getId(), second.getId());
  }

  private static Application application(String id, String ownerUserId, Instant updateTime) {
    return new Application(
        id,
        CANDIDATE,
        DEMAND,
        JD,
        JD_VERSION,
        RESUME,
        "W".repeat(26),
        "Q".repeat(26),
        "INTERNAL",
        "IN_PROCESS",
        null,
        ownerUserId,
        DEPARTMENT,
        1L,
        null,
        1L);
  }

  private static OnboardingProcess onboardingProcess(
      String applicationId, String status, Instant startedAt, Instant actualOnboardedAt) {
    OnboardingProcess process = new OnboardingProcess();
    process.setApplicationId(applicationId);
    process.setStatus(status);
    process.setStartedAt(startedAt);
    process.setActualOnboardedAt(actualOnboardedAt);
    process.setClosedAt(actualOnboardedAt);
    process.setStartedByUserId(ACTOR);
    process.setCompletedByUserId(actualOnboardedAt == null ? null : ACTOR);
    process.setVersion(0);
    return process;
  }

  // 本地待审草案：插入原 ApplicationServiceTest，不是新的测试类。
  // 合成持久化返回值验证业务错误契约，不证明真实 MySQL 并发/隔离。
  private static final String REQUEST_APP = "P".repeat(26);
  private static final String OTHER_REQUEST_APP = "Z".repeat(26);
  private static final String OTHER_REQUEST_JD = "X".repeat(26);
  private static final String OTHER_REQUEST_JD_VERSION = "Y".repeat(26);
  private static final String REQUEST_RECORD = "I".repeat(26);
  private static final String REQUEST_KEY = "application-command-boundary";
  private static final String REQUEST_REASON = "确认本次招聘命令";

  @ParameterizedTest
  @CsvSource({
      "decide, action", "decide, application",
      "correctDecision, action", "correctDecision, application",
      "addOffer, action", "addOffer, application",
      "correctOffer, action", "correctOffer, application",
      "onboard, action", "onboard, application",
      "hire, action", "hire, application",
      "reviewOnboarding, action", "reviewOnboarding, application",
      "returnOnboardingToShared, action", "returnOnboardingToShared, application"
  })
  void competingApplicationCommandRejectsReusedKey(String entry, String changed) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    AtomicReference<IdempotencyRecord> committed = new AtomicReference<>();
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      events.add("ordinary-key-miss:" + REQUEST_APP);
      return null;
    });
    when(persistence.insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), any(), any(), any(), any()))
        .thenAnswer(call -> {
          assertThat(events).contains("ordinary-key-miss:" + REQUEST_APP);
          // 另一个 Application 和 JD 不受当前 Application/JD 的锁阻塞。
          Application other = application(OTHER_REQUEST_APP, ACTOR, now);
          other.setJdId(OTHER_REQUEST_JD);
          other.setJdVersionId(OTHER_REQUEST_JD_VERSION);
          assertThat(other.getId()).isNotEqualTo(REQUEST_APP);
          assertThat(other.getJdId()).isNotEqualTo(JD);
          assertThat(other.getJdVersionId()).isNotEqualTo(JD_VERSION);
          // winner采用真实公开命令的action及其规范载荷，wrong-action仍是合法业务请求。
          String winnerEntry = changed.equals("action")
              ? (entry.equals("decide") ? "correctDecision" : "decide") : entry;
          String winnerTarget = winnerEntry.equals("hire") || winnerEntry.equals("reviewOnboarding")
              ? other.getId() : "T".repeat(26);
          committed.set(new IdempotencyRecord(ACTOR, REQUEST_KEY, commandAction(winnerEntry),
              commandFingerprint(winnerEntry, other.getId()), winnerTarget));
          assertThat(committed.get().getTargetId()).isEqualTo(
              winnerEntry.equals("hire") || winnerEntry.equals("reviewOnboarding")
                  ? OTHER_REQUEST_APP : "T".repeat(26));
          events.add("other-app-commit:" + other.getId() + ":" + other.getJdId() + ":" + other.getJdVersionId());
          events.add("reservation-conflict:" + REQUEST_APP);
          return 0;
        });
    when(idempotency.findIdempotencyForUpdate(ACTOR, REQUEST_KEY)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      events.add("current-key:" + REQUEST_APP);
      return committed.get();
    });

    assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));

    String winnerCommit = "other-app-commit:" + OTHER_REQUEST_APP + ":"
        + OTHER_REQUEST_JD + ":" + OTHER_REQUEST_JD_VERSION;
    if (entry.equals("decide") || entry.equals("correctDecision")) {
      assertThat(events).containsSubsequence("app-lock:" + REQUEST_APP,
          (entry.equals("decide") ? "current-collaborators:" : "ordinary-collaborators:") + REQUEST_APP,
          "ordinary-key-miss:" + REQUEST_APP,
          winnerCommit, "reservation-conflict:" + REQUEST_APP, "current-key:" + REQUEST_APP);
      assertThat(events).doesNotContain("ordinary-jd-id:" + REQUEST_APP, "jd-lock:" + JD_VERSION);
    } else if (entry.equals("returnOnboardingToShared")) {
      assertThat(events).containsSubsequence("ordinary-app:" + REQUEST_APP,
          "ordinary-collaborators:" + REQUEST_APP, "ordinary-key-miss:" + REQUEST_APP,
          winnerCommit, "reservation-conflict:" + REQUEST_APP, "current-key:" + REQUEST_APP);
      assertThat(events).doesNotContain("app-lock:" + REQUEST_APP, "jd-lock:" + JD_VERSION);
    } else {
      assertThat(events).containsSubsequence("ordinary-jd-id:" + REQUEST_APP, "jd-lock:" + JD_VERSION,
          "app-lock:" + REQUEST_APP, "current-collaborators:" + REQUEST_APP,
          "ordinary-key-miss:" + REQUEST_APP, winnerCommit,
          "reservation-conflict:" + REQUEST_APP, "current-key:" + REQUEST_APP);
      verify(collaborators).findCollaboratorsForUpdate(REQUEST_APP);
      verify(collaborators, never()).findCollaborators(REQUEST_APP);
    }
    verify(idempotency).findIdempotency(ACTOR, REQUEST_KEY);
    verify(idempotency).findIdempotencyForUpdate(ACTOR, REQUEST_KEY);
    assertCommandTransaction(transactions, false);
    verifyNoCommandResultReadOrWrite();
  }

  @ParameterizedTest
  @ValueSource(strings = {"decide", "correctDecision", "addOffer", "correctOffer", "onboard",
      "hire", "reviewOnboarding", "returnOnboardingToShared"})
  void applicationCommandReservationWithoutWinnerKeepsRetryError(String entry) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    when(persistence.insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), any(), any(), any(), any()))
        .thenReturn(0);

    assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_RESERVATION_FAILED"));

    assertCommandTransaction(transactions, false);
    verifyNoCommandResultReadOrWrite();
  }

  @ParameterizedTest
  @ValueSource(strings = {"decide", "correctDecision", "addOffer", "correctOffer", "onboard",
      "hire", "reviewOnboarding", "returnOnboardingToShared"})
  void existingApplicationCommandReturnsTypedResponseWithoutNewFacts(String entry) {
    CompanyBlacklistQueryService blocked = blockingCompanyPolicy();
    ReflectionTestUtils.setField(service, "blacklist", blocked);
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenReturn(commandReplay(entry));
    configureCommandResult(entry);

    Object response = invokeApplicationCommand(commandProxy(transactions), entry);

    assertCommandResponse(response, entry);
    verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
    verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
    assertCommandTransaction(transactions, true);
    verifyNoCommandWrites();
    verify(blocked, never()).requireRecruitmentAllowed(any());
  }

  @ParameterizedTest
  @ValueSource(strings={"addOffer","returnOnboardingToShared"})
  void candidateLockPrecedesSharedIdempotencyKeyAndHeadcountLocksForReplay(String entry) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    when(persistence.findCandidateId(REQUEST_APP)).thenReturn(CANDIDATE);
    Mockito.doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(events).doesNotContain("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP, "request-key");
      events.add("candidate-lock");
      return null;
    }).when(blacklist).lockCandidateForCommand(CANDIDATE);
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "回放时风险已生效"))
        .when(blacklist).requireRecruitmentAllowed(any());
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenAnswer(call -> {
      assertThat(events).contains("candidate-lock"); events.add("request-key");
      return commandReplay(entry);
    });
    configureCommandResult(entry);

    assertCommandResponse(invokeApplicationCommand(commandProxy(transactions), entry), entry);
    if (entry.equals("addOffer")) {
      assertThat(events).containsSubsequence("candidate-lock", "jd-lock:" + JD_VERSION,
          "app-lock:" + REQUEST_APP, "current-collaborators:" + REQUEST_APP, "request-key");
    } else {
      assertThat(events).containsSubsequence("candidate-lock", "request-key");
      verify(persistence, never()).lockApplication(any());
      verifyNoInteractions(candidateOnboardingReturns);
    }
    verify(blacklist, never()).requireRecruitmentAllowed(any());
    verifyNoCommandWrites();
    assertCommandTransaction(transactions, true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"decide", "correctDecision", "addOffer", "correctOffer", "onboard",
      "hire", "reviewOnboarding", "returnOnboardingToShared"})
  void matchingApplicationCommandWithoutTypedResultKeepsIncompleteError(String entry) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenReturn(commandReplay(entry));
    if (entry.equals("hire")) {
      doReturn(null).when(onboardingProcesses).lockProcess(REQUEST_APP);
    }

    assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_REPLAY_INCOMPLETE"));

    assertCommandTransaction(transactions, false);
    verifyNoCommandWrites();
  }

  @ParameterizedTest
  @ValueSource(strings = {"decide", "correctDecision", "addOffer", "correctOffer", "onboard",
      "hire", "reviewOnboarding", "returnOnboardingToShared"})
  void applicationCommandRejectsCurrentResourceAccessBeforeKeyLookup(String entry) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    doThrow(PlatformExceptions.forbidden("ACTION_FORBIDDEN", "当前无权访问活动"))
        .when(authorization).requireAccess(eq("recruitment.manage"), any());

    assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("ACTION_FORBIDDEN"));

    verifyNoInteractions(idempotency);
    assertCommandTransaction(transactions, false);
    verifyNoCommandResultReadOrWrite();
  }

  @ParameterizedTest
  @ValueSource(strings = {"decide", "correctDecision", "addOffer", "correctOffer", "onboard",
      "hire", "reviewOnboarding", "returnOnboardingToShared"})
  void applicationCommandRejectsCurrentProcessingPermissionBeforeKeyLookup(String entry) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    Application app = application(REQUEST_APP, "B".repeat(26), now);
    app.setCurrentStage("OFFER");
    when(persistence.findApplication(REQUEST_APP)).thenReturn(app);
    when(persistence.lockApplication(REQUEST_APP)).thenReturn(app);
    when(collaborators.findCollaborators(REQUEST_APP)).thenReturn(List.of());

    assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));

    verifyNoInteractions(idempotency);
    assertCommandTransaction(transactions, false);
    verifyNoCommandResultReadOrWrite();
  }

  @ParameterizedTest
  @ValueSource(strings = {"decide", "correctDecision", "addOffer", "correctOffer", "onboard",
      "hire", "reviewOnboarding", "returnOnboardingToShared"})
  void anotherActorsKeyDoesNotBlockIndependentReservation(String entry) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    IdempotencyRecord otherActorRecord = new IdempotencyRecord("B".repeat(26), REQUEST_KEY,
        "APPLICATION_CREATE", "another-request", OTHER_REQUEST_APP);
    when(idempotency.findIdempotency(any(), eq(REQUEST_KEY))).thenAnswer(call ->
        otherActorRecord.getActorUserId().equals(call.getArgument(0)) ? otherActorRecord : null);
    when(persistence.insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), any(), any(), any(), any()))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          assertThat(call.<String>getArgument(0)).isNotEqualTo(otherActorRecord.getActorUserId());
          throw new IllegalStateException("新命令已成功进入本操作者的独立预约路径");
        });

    assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("新命令已成功进入本操作者的独立预约路径");

    verify(idempotency).findIdempotency(ACTOR, REQUEST_KEY);
    verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
    assertCommandTransaction(transactions, false);
    verifyNoCommandResultReadOrWrite();
  }

  @ParameterizedTest
  @ValueSource(strings = {"normal", "write-failure", "response-failure"})
  void normalOfferAndItsResponseKeepOneTransaction(String failure) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    AtomicReference<OfferStatusRecord> written = new AtomicReference<>();
    when(offers.findOffer(REQUEST_APP, REQUEST_RECORD)).thenAnswer(call -> written.get());
    OfferStatusRecord previous = new OfferStatusRecord();
    previous.setId("B".repeat(26));
    previous.setApplicationId(REQUEST_APP);
    previous.setStatus("IN_PROGRESS");
    previous.setOccurredAt(now.minusSeconds(60));
    when(offers.latestOfferForUpdate(REQUEST_APP)).thenReturn(previous);
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          if (failure.equals("write-failure")) return 0;
          OfferStatusRecord record = new OfferStatusRecord();
          record.setId(call.getArgument(0));
          record.setApplicationId(call.getArgument(1));
          record.setRecordType(call.getArgument(2));
          record.setStatus(call.getArgument(3));
          record.setTerminationReason(call.getArgument(4));
          record.setApplicationOutcome(call.getArgument(5));
          record.setReason(call.getArgument(6));
          record.setCorrectsRecordId(call.getArgument(7));
          record.setReopensRecordId(call.getArgument(8));
          record.setSource(call.getArgument(9));
          record.setSourceReference(call.getArgument(10));
          record.setSourceStatus(call.getArgument(11));
          record.setOccurredAt(call.getArgument(12));
          record.setOperatedByUserId(call.getArgument(13));
          record.setCreateTime(call.getArgument(14));
          written.set(record);
          return 1;
        });
    if (failure.equals("response-failure")) {
      ApplicationAssembler assembler = spy(new ApplicationAssembler());
      doThrow(new IllegalStateException("响应组装失败")).when(assembler).toResponse(any(OfferStatusRecord.class));
      ReflectionTestUtils.setField(service, "assembler", assembler);
    }

    if (failure.equals("normal")) {
      Object response = invokeApplicationCommand(commandProxy(transactions), "addOffer");
      assertThat(response).hasFieldOrPropertyWithValue("id", REQUEST_RECORD)
          .hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
          .hasFieldOrPropertyWithValue("recordType", "STATUS")
          .hasFieldOrPropertyWithValue("status", "ISSUED")
          .hasFieldOrPropertyWithValue("reason", REQUEST_REASON)
          .hasFieldOrPropertyWithValue("correctsRecordId", null)
          .hasFieldOrPropertyWithValue("reopensRecordId", null)
          .hasFieldOrPropertyWithValue("source", "MANUAL_BEISEN_REFERENCE")
          .hasFieldOrPropertyWithValue("sourceReference", "SOURCE")
          .hasFieldOrPropertyWithValue("sourceStatus", "ISSUED")
          .hasFieldOrPropertyWithValue("terminationReason", null)
          .hasFieldOrPropertyWithValue("applicationOutcome", null)
          .hasFieldOrPropertyWithValue("occurredAt", now)
          .hasFieldOrPropertyWithValue("operatedByUserId", ACTOR)
          .hasFieldOrPropertyWithValue("createTime", now);
    } else {
      assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), "addOffer"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(failure.equals("write-failure") ? "Offer 状态记录写入失败" : "响应组装失败");
    }
    verify(offers).insertOffer(any(), eq(REQUEST_APP), eq("STATUS"), eq("ISSUED"), any(), any(),
        eq(REQUEST_REASON), isNull(), isNull(), eq("MANUAL_BEISEN_REFERENCE"),
        eq("SOURCE"), eq("ISSUED"), eq(now), eq(ACTOR), eq(now));
    verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
    assertCommandTransaction(transactions, failure.equals("normal"));
  }

  // 待审108：仅existing aware TM + annotation proxy；不是真实MySQL隔离测试。
  @ParameterizedTest
  @CsvSource({"revoked, false", "active, false", "added, false", "revoked, true"})
  void hcOfferUsesCurrentProcessingPermissionAfterPreLockCommit(String change, boolean owner) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    Application currentApp = application(REQUEST_APP, owner ? ACTOR : "B".repeat(26), now);
    currentApp.setCurrentStage("OFFER");
    currentApp.setVersion(8L);
    List<String> priorCollaborators = change.equals("added") ? List.of() : List.of(ACTOR);
    AtomicReference<List<String>> committedCollaborators = new AtomicReference<>(priorCollaborators);
    AtomicReference<Boolean> captured = new AtomicReference<>(false);
    // 仅本probe的固定预期API defaultAnswer，baseline无该方法也能编译；不是新增生产shadow。
    ApplicationCollaboratorPersistenceService probeCollaborators = mock(
        ApplicationCollaboratorPersistenceService.class, invocation -> {
          if (invocation.getMethod().getName().equals("findCollaboratorsForUpdate")) {
            assertThat(invocation.<String>getArgument(0)).isEqualTo(REQUEST_APP);
            assertThat(events).contains("app-current-lock");
            events.add("current-collaborator-view");
            return committedCollaborators.get();
          }
          return Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
    ReflectionTestUtils.setField(service, "applicationCollaboratorPersistenceService", probeCollaborators);
    when(persistence.findJdVersionId(REQUEST_APP)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      captured.set(true);
      events.add("scalar-old-view");
      return JD_VERSION;
    });
    when(demands.lockJdForHeadcount(JD_VERSION)).thenAnswer(call -> {
      assertThat(captured.get()).isTrue();
      assertThat(events).doesNotContain("app-current-lock");
      // T2 updateCollaborators先app锁，T1此刻尚未持有JD/app锁。
      committedCollaborators.set(change.equals("revoked") ? List.of() : List.of(ACTOR));
      events.add("collaborator-change-committed-before-jd-lock");
      events.add("jd-lock:" + JD_VERSION);
      return withHeadcount(new JdVersionSnapshot(
          JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    });
    when(persistence.lockApplication(REQUEST_APP)).thenAnswer(call -> {
      assertThat(events).contains("collaborator-change-committed-before-jd-lock");
      events.add("app-current-lock");
      return currentApp;
    });
    when(probeCollaborators.findCollaborators(REQUEST_APP)).thenAnswer(call -> {
      events.add("ordinary-collaborator-view");
      return captured.get() ? priorCollaborators : committedCollaborators.get();
    });
    // 旧view已存在合法完成的本actor命令；避免typed missing干扰权限期待。
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenReturn(commandReplay("addOffer"));
    configureCommandResult("addOffer");

    if (change.equals("revoked") && !owner) {
      // 管理查看仍被mock允许，独立验证processing，而非按测试指定access拒绝。
      assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), "addOffer"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
      verifyNoInteractions(idempotency);
      verifyNoCommandResultReadOrWrite();
      assertCommandTransaction(transactions, false);
    } else {
      AtomicReference<Object> assembled = new AtomicReference<>();
      Assertions.assertThatCode(() -> assembled.set(
          invokeApplicationCommand(commandProxy(transactions), "addOffer"))).doesNotThrowAnyException();
      Object response = assembled.get();
      assertCommandResponse(response, "addOffer");
      verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
      verifyNoCommandWrites();
      assertCommandTransaction(transactions, true);
    }
    assertThat(events).containsSubsequence("scalar-old-view",
        "collaborator-change-committed-before-jd-lock", "jd-lock:" + JD_VERSION, "app-current-lock");
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    ArgumentCaptor<AuthorizationTarget> target = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization).requireAccess(eq("recruitment.manage"), target.capture());
    assertThat(target.getValue())
        .hasFieldOrPropertyWithValue("objectType", "APPLICATION")
        .hasFieldOrPropertyWithValue("objectId", REQUEST_APP)
        .hasFieldOrPropertyWithValue("ownerUserId", currentApp.getOwnerUserId())
        .hasFieldOrPropertyWithValue("departmentId", currentApp.getOwnerDepartmentId())
        .hasFieldOrPropertyWithValue("collaboratorUserIds", Set.copyOf(committedCollaborators.get()));
    verify(probeCollaborators, never()).deleteCollaborators(any());
    verify(probeCollaborators, never()).insertCollaborators(any(), any(), any(), any());
    assertThat(mockingDetails(probeCollaborators).getInvocations()).allMatch(invocation ->
        Set.of("findCollaborators", "findCollaboratorsForUpdate")
            .contains(invocation.getMethod().getName()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void appFirstDecisionSeesCollaboratorStateWithoutAnEarlySnapshot(boolean revoked) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    Application currentApp = application(REQUEST_APP, "B".repeat(26), now);
    currentApp.setCurrentStage("OFFER");
    when(persistence.lockApplication(REQUEST_APP)).thenAnswer(call -> {
      assertThat(events).doesNotContain("ordinary-jd-id:" + REQUEST_APP);
      events.add("app-current-lock");
      return currentApp;
    });
    when(collaborators.findCollaboratorsForUpdate(REQUEST_APP)).thenAnswer(call -> {
      assertThat(events).contains("app-current-lock");
      return revoked ? List.of() : List.of(ACTOR);
    });
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenReturn(commandReplay("decide"));
    configureCommandResult("decide");
    if (revoked) {
      assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), "decide"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
      verifyNoInteractions(idempotency);
      verifyNoCommandResultReadOrWrite();
      assertCommandTransaction(transactions, false);
    } else {
      assertCommandResponse(invokeApplicationCommand(commandProxy(transactions), "decide"), "decide");
      verifyNoCommandWrites();
      assertCommandTransaction(transactions, true);
    }
    verify(persistence, never()).findJdVersionId(any());
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"addOffer", "correctOffer", "onboard", "hire", "terminationAccess"})
  void headcountEntryUsesOneCurrentCollaboratorSetForAccessAndProcessing(String entry) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    Application app = application(REQUEST_APP, "B".repeat(26), now);
    app.setCurrentStage("OFFER");
    when(persistence.lockApplication(REQUEST_APP)).thenAnswer(call -> {
      events.add("app-lock:" + REQUEST_APP);
      return app;
    });
    when(collaborators.findCollaboratorsForUpdate(REQUEST_APP)).thenAnswer(call -> {
      assertThat(events).contains("app-lock:" + REQUEST_APP);
      events.add("current-collaborators:" + REQUEST_APP);
      return List.of(ACTOR);
    });
    RecruitmentApplicationManager proxy = commandProxy(transactions);
    if (entry.equals("terminationAccess")) {
      assertThat(proxy.requireLockedTerminationAccess(REQUEST_APP)).isSameAs(app);
      verifyNoInteractions(idempotency);
      verifyNoCommandResultReadOrWrite();
    } else {
      when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenReturn(commandReplay(entry));
      configureCommandResult(entry);
      assertCommandResponse(invokeApplicationCommand(proxy, entry), entry);
      verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
      verifyNoCommandWrites();
    }
    verify(collaborators).findCollaboratorsForUpdate(REQUEST_APP);
    verify(collaborators, never()).findCollaborators(any());
    ArgumentCaptor<AuthorizationTarget> target = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization).requireAccess(eq("recruitment.manage"), target.capture());
    assertThat(target.getValue())
        .hasFieldOrPropertyWithValue("objectType", "APPLICATION")
        .hasFieldOrPropertyWithValue("objectId", REQUEST_APP)
        .hasFieldOrPropertyWithValue("ownerUserId", app.getOwnerUserId())
        .hasFieldOrPropertyWithValue("departmentId", app.getOwnerDepartmentId())
        .hasFieldOrPropertyWithValue("collaboratorUserIds", Set.of(ACTOR));
    assertThat(events).containsSubsequence("ordinary-jd-id:" + REQUEST_APP,
        "jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP, "current-collaborators:" + REQUEST_APP);
    assertCommandTransaction(transactions, true);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  private List<String> configureCommandBoundary(CommandBoundaryTransactions transactions) {
    List<String> events = new ArrayList<>();
    Application app = application(REQUEST_APP, ACTOR, now);
    app.setCurrentStage("OFFER");
    when(persistence.findApplication(REQUEST_APP)).thenAnswer(call -> {
      events.add("ordinary-app:" + REQUEST_APP);
      return app;
    });
    when(persistence.findJdVersionId(REQUEST_APP)).thenAnswer(call -> {
      events.add("ordinary-jd-id:" + REQUEST_APP);
      return JD_VERSION;
    });
    JdVersionSnapshot jd = withHeadcount(new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    when(demands.lockJdForHeadcount(JD_VERSION)).thenAnswer(call -> {
      events.add("jd-lock:" + JD_VERSION);
      return jd;
    });
    when(persistence.lockApplication(REQUEST_APP)).thenAnswer(call -> {
      events.add("app-lock:" + REQUEST_APP);
      return app;
    });
    when(collaborators.findCollaborators(REQUEST_APP)).thenAnswer(call -> {
      events.add("ordinary-collaborators:" + REQUEST_APP);
      return List.of();
    });
    when(collaborators.findCollaboratorsForUpdate(REQUEST_APP)).thenAnswer(call -> {
      events.add("current-collaborators:" + REQUEST_APP);
      return List.of();
    });
    return events;
  }

  private Object invokeApplicationCommand(RecruitmentApplicationManager application, String entry) {
    return switch (entry) {
      case "decide" -> application.decideResponse(REQUEST_APP, "PASS", REQUEST_REASON, null, REQUEST_KEY);
      case "correctDecision" -> application.correctDecisionResponse(
          REQUEST_APP, "D".repeat(26), "PASS", REQUEST_REASON, null, REQUEST_KEY);
      case "addOffer" -> application.addOfferResponse(REQUEST_APP, commandOffer(), REQUEST_KEY);
      case "correctOffer" -> application.correctOfferResponse(
          REQUEST_APP, "O".repeat(26), commandOffer(), REQUEST_KEY);
      case "onboard" -> application.onboardResponse(REQUEST_APP, now, "SOURCE", REQUEST_REASON, REQUEST_KEY);
      case "hire" -> application.hireResponse(REQUEST_APP, "ISSUED", REQUEST_REASON, REQUEST_KEY);
      case "reviewOnboarding" -> application.reviewOnboardingResponse(REQUEST_APP, now, REQUEST_REASON, REQUEST_KEY);
      case "returnOnboardingToShared" -> application.returnOnboardingToSharedResponse(
          REQUEST_APP, "LIBRARY_RETURN", "已确认", REQUEST_KEY);
      default -> throw new IllegalArgumentException(entry);
    };
  }

  private OfferCommandDTO commandOffer() {
    return new OfferCommandDTO("ISSUED", null, null, REQUEST_REASON, now, "SOURCE", "ISSUED");
  }

  private String commandAction(String entry) {
    return switch (entry) {
      case "decide" -> "APPLICATION_DECIDE";
      case "correctDecision" -> "APPLICATION_DECISION_CORRECT";
      case "addOffer" -> "OFFER_ADVANCE";
      case "correctOffer" -> "OFFER_CORRECT";
      case "onboard" -> "APPLICATION_ONBOARD";
      case "hire" -> "APPLICATION_HIRE";
      case "reviewOnboarding" -> "ONBOARDING_REVIEW";
      case "returnOnboardingToShared" -> "ONBOARDING_RETURN_TO_SHARED";
      default -> throw new IllegalArgumentException(entry);
    };
  }

  private String commandFingerprint(String entry, String applicationId) {
    return switch (entry) {
      case "decide" -> Sha256Utils.fingerprintObjects(31, applicationId, "PASS", REQUEST_REASON, null);
      case "correctDecision" -> Sha256Utils.fingerprintObjects(
          31, applicationId, "D".repeat(26), "PASS", REQUEST_REASON, null);
      case "addOffer" -> Sha256Utils.fingerprintObjects(31,
          applicationId, "ISSUED", null, null, REQUEST_REASON, now.toString(), "SOURCE", "ISSUED");
      case "correctOffer" -> Sha256Utils.fingerprintObjects(31,
          applicationId, "O".repeat(26), "ISSUED", null, null, REQUEST_REASON, now.toString(), "SOURCE", "ISSUED");
      case "onboard" -> Sha256Utils.fingerprintObjects(31, applicationId, now.toString(), "SOURCE", REQUEST_REASON);
      case "hire" -> Sha256Utils.fingerprintObjects(31, applicationId, "ISSUED", REQUEST_REASON);
      case "reviewOnboarding" -> Sha256Utils.fingerprintObjects(31, applicationId, now.toString(), REQUEST_REASON);
      case "returnOnboardingToShared" -> Sha256Utils.fingerprintObjects(31, applicationId, "LIBRARY_RETURN", "已确认");
      default -> throw new IllegalArgumentException(entry);
    };
  }

  private IdempotencyRecord commandReplay(String entry) {
    String target = entry.equals("hire") || entry.equals("reviewOnboarding") ? REQUEST_APP : REQUEST_RECORD;
    return new IdempotencyRecord(ACTOR, REQUEST_KEY, commandAction(entry), commandFingerprint(entry, REQUEST_APP), target);
  }

  private void configureCommandResult(String entry) {
    if (entry.equals("decide") || entry.equals("correctDecision")) {
      Decision result = new Decision(REQUEST_RECORD, REQUEST_APP, "DECISION", "PASS", REQUEST_REASON,
          "D".repeat(26), "S".repeat(26), ACTOR, now);
      result.setCreateTime(now);
      when(decisions.findDecision(REQUEST_APP, REQUEST_RECORD)).thenReturn(result);
    } else if (entry.equals("onboard")) {
      OnboardingEvent result = new OnboardingEvent(REQUEST_RECORD, REQUEST_APP, now,
          "MANUAL", "SOURCE", REQUEST_REASON, ACTOR);
      result.setCreateTime(now);
      when(onboarding.findOnboardingById(REQUEST_APP, REQUEST_RECORD)).thenReturn(result);
    } else if (entry.equals("hire") || entry.equals("reviewOnboarding")) {
      when(onboardingProcesses.findProcess(REQUEST_APP)).thenReturn(
          onboardingProcess(REQUEST_APP, "IN_PROGRESS", now.minusSeconds(3600), null));
    } else {
      OfferStatusRecord result = new OfferStatusRecord();
      result.setId(REQUEST_RECORD);
      result.setApplicationId(REQUEST_APP);
      result.setRecordType("STATUS");
      result.setStatus("ISSUED");
      result.setReason(REQUEST_REASON);
      result.setCorrectsRecordId("O".repeat(26));
      result.setReopensRecordId("B".repeat(26));
      result.setSource("MANUAL");
      result.setSourceReference("SOURCE");
      result.setSourceStatus("ISSUED");
      result.setOccurredAt(now);
      result.setOperatedByUserId(ACTOR);
      result.setCreateTime(now);
      when(offers.findOffer(REQUEST_APP, REQUEST_RECORD)).thenReturn(result);
    }
  }

  private void assertCommandResponse(Object response, String entry) {
    assertThat(response).hasFieldOrPropertyWithValue("applicationId", REQUEST_APP);
    if (entry.equals("hire") || entry.equals("reviewOnboarding")) {
      assertThat(response).hasFieldOrPropertyWithValue("status", "IN_PROGRESS")
          .hasFieldOrPropertyWithValue("startedAt", now.minusSeconds(3600))
          .hasFieldOrPropertyWithValue("actualOnboardedAt", null)
          .hasFieldOrPropertyWithValue("closedAt", null)
          .hasFieldOrPropertyWithValue("elapsedSeconds", 3600L);
      return;
    }
    assertThat(response).hasFieldOrPropertyWithValue("id", REQUEST_RECORD)
        .hasFieldOrPropertyWithValue("reason", REQUEST_REASON)
        .hasFieldOrPropertyWithValue("createTime", now);
    if (entry.equals("decide") || entry.equals("correctDecision")) {
      assertThat(response).hasFieldOrPropertyWithValue("recordType", "DECISION")
          .hasFieldOrPropertyWithValue("decision", "PASS")
          .hasFieldOrPropertyWithValue("correctsRecordId", "D".repeat(26))
          .hasFieldOrPropertyWithValue("clientSubmissionId", "S".repeat(26))
          .hasFieldOrPropertyWithValue("decidedByUserId", ACTOR)
          .hasFieldOrPropertyWithValue("decidedAt", now);
    } else if (entry.equals("onboard")) {
      assertThat(response).hasFieldOrPropertyWithValue("onboardedAt", now)
          .hasFieldOrPropertyWithValue("source", "MANUAL")
          .hasFieldOrPropertyWithValue("sourceReference", "SOURCE")
          .hasFieldOrPropertyWithValue("operatedByUserId", ACTOR);
    } else {
      assertThat(response).hasFieldOrPropertyWithValue("recordType", "STATUS")
          .hasFieldOrPropertyWithValue("status", "ISSUED")
          .hasFieldOrPropertyWithValue("terminationReason", null)
          .hasFieldOrPropertyWithValue("applicationOutcome", null)
          .hasFieldOrPropertyWithValue("correctsRecordId", "O".repeat(26))
          .hasFieldOrPropertyWithValue("reopensRecordId", "B".repeat(26))
          .hasFieldOrPropertyWithValue("source", "MANUAL")
          .hasFieldOrPropertyWithValue("sourceReference", "SOURCE")
          .hasFieldOrPropertyWithValue("sourceStatus", "ISSUED")
          .hasFieldOrPropertyWithValue("occurredAt", now)
          .hasFieldOrPropertyWithValue("operatedByUserId", ACTOR);
    }
  }

  private void verifyNoCommandWrites() {
    // 明确覆盖八个公开路径会写的领域事实和副作用端口；预约尝试单独验证。
    verify(persistence, never()).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
    verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
    verify(persistence, never()).markOfferInProgress(any(), any(), any(), any(), any());
    verify(decisions, never()).insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(offers, never()).insertOffer(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
    verify(onboarding, never()).insertOnboarding(any(), any(), any(), any(), any(), any(), any(), any());
    verify(onboardingProcesses, never()).insertProcess(any(), any(), any(), any());
    verify(onboardingProcesses, never()).markReturned(any(), any(), anyLong(), any());
    verify(onboardingProcesses, never()).markOnboarded(any(), any(), any(), anyLong(), any());
    verifyNoInteractions(workflowSteps, stageHistory, ownership, communications, followUps, intentions,
        locks, candidateOnboardingReturns, candidates, resumes, screeningTransitions,
        interviews, interviewNotifications, ReflectionTestUtils.getField(service, "followUpPlanLifecycle"));
  }

  private void verifyNoCommandResultReadOrWrite() {
    verifyNoCommandWrites();
    verify(decisions, never()).findDecision(any(), any());
    verify(offers, never()).findOffer(any(), any());
    verify(onboarding, never()).findOnboardingById(any(), any());
    verify(onboardingProcesses, never()).findProcess(any());
  }

  private RecruitmentApplicationManager commandProxy(CommandBoundaryTransactions transactions) {
    ProxyFactory proxy = new ProxyFactory(service);
    proxy.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(interceptor);
    return (RecruitmentApplicationManager) proxy.getProxy();
  }

  private void assertCommandTransaction(CommandBoundaryTransactions transactions, boolean committed) {
    assertThat(transactions.beginCount).isEqualTo(1);
    assertThat(transactions.commitCount).isEqualTo(committed ? 1 : 0);
    assertThat(transactions.rollbackCount).isEqualTo(committed ? 0 : 1);
    assertThat(transactions.state.active).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"same", "normalized-same", "source-reference", "source-status"})
  void offerCorrectionKeyIncludesPersistedSourceFields(String mode) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    String priorId = "O".repeat(26);
    OfferStatusRecord prior = new OfferStatusRecord();
    prior.setId(priorId);
    prior.setApplicationId(REQUEST_APP);
    prior.setStatus("ISSUED");
    prior.setOccurredAt(now.minusSeconds(60));
    AtomicReference<OfferStatusRecord> committedOffer = new AtomicReference<>();
    AtomicReference<IdempotencyRecord> committedKey = new AtomicReference<>();
    when(offers.findOffer(REQUEST_APP, priorId)).thenReturn(prior);
    when(offers.latestOfferForUpdate(REQUEST_APP)).thenAnswer(call ->
        committedOffer.get() == null ? prior : committedOffer.get());
    when(offers.findOffer(REQUEST_APP, REQUEST_RECORD)).thenAnswer(call -> committedOffer.get());
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenAnswer(call -> committedKey.get());
    when(persistence.insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), any(), any(), any(), any()))
        .thenAnswer(call -> {
          committedKey.set(new IdempotencyRecord(call.getArgument(0), call.getArgument(1),
              call.getArgument(2), call.getArgument(3), call.getArgument(4)));
          return 1;
        });
    when(offers.insertOffer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any())).thenAnswer(call -> {
          OfferStatusRecord fact = new OfferStatusRecord(call.getArgument(0), call.getArgument(1),
              call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5),
              call.getArgument(6), call.getArgument(7), call.getArgument(8), call.getArgument(9),
              call.getArgument(10), call.getArgument(11), call.getArgument(12), call.getArgument(13));
          fact.setCreateTime(call.getArgument(14));
          committedOffer.set(fact);
          return 1;
        });
    OfferCommandDTO first = new OfferCommandDTO("ISSUED", null, null, REQUEST_REASON,
        now, "  BEISEN-42  ", "  APPROVED  ");
    RecruitmentApplicationManager proxy = commandProxy(transactions);

    OfferStatusResponse original = proxy.correctOfferResponse(REQUEST_APP, priorId, first, REQUEST_KEY);

    assertThat(original.getSourceReference()).isEqualTo("BEISEN-42");
    assertThat(original.getSourceStatus()).isEqualTo("APPROVED");
    assertThat(committedOffer.get().getSourceReference()).isEqualTo(original.getSourceReference());
    assertThat(committedOffer.get().getSourceStatus()).isEqualTo(original.getSourceStatus());
    assertCommandTransaction(transactions, true);
    OfferCommandDTO retry = new OfferCommandDTO("ISSUED", null, null, REQUEST_REASON, now,
        mode.equals("source-reference") ? "BEISEN-43"
            : mode.equals("normalized-same") ? "BEISEN-42" : first.getSourceReference(),
        mode.equals("source-status") ? "REAPPROVED"
            : mode.equals("normalized-same") ? "APPROVED" : first.getSourceStatus());
    boolean matches = mode.equals("same") || mode.equals("normalized-same");
    if (matches) {
      OfferStatusResponse replay = proxy.correctOfferResponse(REQUEST_APP, priorId, retry, REQUEST_KEY);
      assertThat(replay.getId()).isEqualTo(original.getId());
      assertThat(replay.getSourceReference()).isEqualTo(original.getSourceReference());
      assertThat(replay.getSourceStatus()).isEqualTo(original.getSourceStatus());
    } else {
      assertThatThrownBy(() -> proxy.correctOfferResponse(REQUEST_APP, priorId, retry, REQUEST_KEY))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }
    verify(persistence).insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), eq("OFFER_CORRECT"), any(),
        eq(REQUEST_RECORD), eq(now));
    verify(offers).insertOffer(eq(REQUEST_RECORD), eq(REQUEST_APP), eq("CORRECTION"), eq("ISSUED"),
        isNull(), isNull(), eq(REQUEST_REASON), eq(priorId), isNull(), eq("MANUAL_BEISEN_REFERENCE"),
        eq("BEISEN-42"), eq("APPROVED"), eq(now), eq(ACTOR), eq(now));
    verify(persistence).markOfferInProgress(eq(REQUEST_APP), eq(ACTOR), any(), eq(ACTOR), eq(now));
    verifyNoInteractions(onboarding, onboardingProcesses, workflowSteps, stageHistory,
        interviews, interviewNotifications, locks);
    assertThat(transactions.beginCount).isEqualTo(2);
    assertThat(transactions.commitCount).isEqualTo(matches ? 2 : 1);
    assertThat(transactions.rollbackCount).isEqualTo(matches ? 0 : 1);
  }

  @ParameterizedTest
  @CsvSource({
      "addOffer, TERMINATED, TERMINATED", "correctOffer, TERMINATED, TERMINATED",
      "addOffer, TERMINATED, ISSUED", "correctOffer, TERMINATED, ISSUED",
      "addOffer, ONBOARDED, ISSUED", "correctOffer, ONBOARDED, ISSUED"
  })
  void terminalOfferCommandReplaysCommittedResultWithoutRepeatingEffects(
      String entry, String terminalStage, String originalStatus) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    Application app = application(REQUEST_APP, ACTOR, now);
    app.setCurrentStage(terminalStage);
    app.setTerminalAt(now);
    when(persistence.lockApplication(REQUEST_APP)).thenAnswer(call -> {
      events.add("app-lock:" + REQUEST_APP);
      return app;
    });
    boolean correction = entry.equals("correctOffer");
    String terminationReason = originalStatus.equals("TERMINATED") ? "OTHER" : null;
    String outcome = originalStatus.equals("TERMINATED") ? "WITHDRAW" : null;
    OfferCommandDTO command = new OfferCommandDTO(originalStatus, terminationReason, outcome,
        REQUEST_REASON, now, "SOURCE", originalStatus);
    String fingerprint = correction
        ? Sha256Utils.fingerprintObjects(31, REQUEST_APP, "O".repeat(26), originalStatus,
            terminationReason, outcome, REQUEST_REASON, now.toString(), "SOURCE", originalStatus)
        : Sha256Utils.fingerprintObjects(31, REQUEST_APP, originalStatus, terminationReason,
            outcome, REQUEST_REASON, now.toString(), "SOURCE", originalStatus);
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenReturn(new IdempotencyRecord(
        ACTOR, REQUEST_KEY, commandAction(entry), fingerprint, REQUEST_RECORD));
    OfferStatusRecord committed = new OfferStatusRecord(REQUEST_RECORD, REQUEST_APP,
        correction ? "CORRECTION" : "STATUS", originalStatus, terminationReason, outcome,
        REQUEST_REASON, correction ? "O".repeat(26) : null, null, "MANUAL_BEISEN_REFERENCE",
        "SOURCE", originalStatus, now, ACTOR);
    when(offers.findOffer(REQUEST_APP, REQUEST_RECORD)).thenReturn(committed);
    RecruitmentApplicationManager proxy = commandProxy(transactions);

    Object response = correction
        ? proxy.correctOfferResponse(REQUEST_APP, "O".repeat(26), command, REQUEST_KEY)
        : proxy.addOfferResponse(REQUEST_APP, command, REQUEST_KEY);

    assertThat(response).hasFieldOrPropertyWithValue("id", REQUEST_RECORD)
        .hasFieldOrPropertyWithValue("status", originalStatus)
        .hasFieldOrPropertyWithValue("sourceReference", "SOURCE")
        .hasFieldOrPropertyWithValue("sourceStatus", originalStatus);
    assertThat(app.getCurrentStage()).isEqualTo(terminalStage);
    assertThat(events).containsSubsequence("jd-lock:" + JD_VERSION,
        "app-lock:" + REQUEST_APP, "current-collaborators:" + REQUEST_APP);
    verify(authorization).requireAccess(eq("recruitment.manage"), any());
    verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
    verify(offers, never()).latestOfferForUpdate(any());
    verify(offers, never()).countOccupiedByJd(any());
    verifyNoCommandWrites();
    assertCommandTransaction(transactions, true);
  }

  @ParameterizedTest
  @CsvSource({
      "addOffer, new-key", "correctOffer, new-key",
      "addOffer, changed-payload", "correctOffer, changed-payload",
      "addOffer, revoked-access", "correctOffer, revoked-access",
      "addOffer, lost-processing", "correctOffer, lost-processing"
  })
  void terminalOfferCommandStillRejectsNewWritesAndCurrentAccessFailures(String entry, String mode) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    Application app = application(REQUEST_APP, mode.equals("lost-processing") ? OTHER_REQUEST_APP : ACTOR, now);
    app.setCurrentStage("TERMINATED");
    app.setTerminalAt(now);
    when(persistence.lockApplication(REQUEST_APP)).thenReturn(app);
    if (!mode.equals("new-key")) {
      IdempotencyRecord replay = commandReplay(entry);
      if (mode.equals("changed-payload")) {
        replay = new IdempotencyRecord(ACTOR, REQUEST_KEY, commandAction(entry), "different-payload", REQUEST_RECORD);
      }
      when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenReturn(replay);
    }
    if (mode.equals("revoked-access")) {
      doThrow(PlatformExceptions.forbidden("ACTION_FORBIDDEN", "当前无权访问活动"))
          .when(authorization).requireAccess(eq("recruitment.manage"), any());
    }
    String expected = switch (mode) {
      case "new-key" -> "APPLICATION_ARCHIVED";
      case "changed-payload" -> "IDEMPOTENCY_KEY_REUSED";
      case "revoked-access" -> "ACTION_FORBIDDEN";
      case "lost-processing" -> "RECRUITMENT_PROCESSING_FORBIDDEN";
      default -> throw new IllegalArgumentException(mode);
    };

    assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo(expected));

    if (mode.equals("revoked-access") || mode.equals("lost-processing")) {
      verifyNoInteractions(idempotency);
      verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
    }
    verifyNoCommandResultReadOrWrite();
    verify(offers, never()).latestOfferForUpdate(any());
    assertCommandTransaction(transactions, false);
  }

  // 合成 RR read view 与真实注解代理；不证明真实 MySQL 并发或隔离。
  @ParameterizedTest
  @ValueSource(strings = {"before-snapshot", "matching", "missing-target", "changed-payload"})
  void offerReplayReadsCommittedResultAfterLegalPreLockWinner(String mode) {
    assertOfferReplayReadsCommittedResultAfterLegalPreLockWinner("addOffer", mode);
  }

  @ParameterizedTest
  @ValueSource(strings = {"before-snapshot", "matching", "missing-target", "changed-payload"})
  void offerCorrectionReplayReadsCommittedResultAfterLegalPreLockWinner(String mode) {
    assertOfferReplayReadsCommittedResultAfterLegalPreLockWinner("correctOffer", mode);
  }

  /** 仅共享发放与更正的回放夹具；各公开命令的响应和副作用断言保持完整。 */
  private void assertOfferReplayReadsCommittedResultAfterLegalPreLockWinner(String entry, String mode) {
    assertThat(entry).isIn("addOffer", "correctOffer");
    boolean correction = entry.equals("correctOffer");
    String winnerCommitEvent = correction ? "winner-correction-commit-before-jd-lock"
        : "winner-commit-before-jd-lock";
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    String loserRecordId = "L".repeat(26);
    assertThat(loserRecordId).isNotEqualTo(REQUEST_RECORD).isNotEqualTo("O".repeat(26));
    doReturn(loserRecordId).when(ids).newId();
    boolean beforeSnapshot = mode.equals("before-snapshot");
    boolean successful = beforeSnapshot || mode.equals("matching");
    String winnerReason = mode.equals("changed-payload") ? REQUEST_REASON + "（另一请求）" : REQUEST_REASON;
    String winnerFingerprint = correction
        ? Sha256Utils.fingerprintObjects(31, REQUEST_APP, "O".repeat(26), "ISSUED", null, null,
            winnerReason, now.toString(), "SOURCE", "ISSUED")
        : Sha256Utils.fingerprintObjects(31, REQUEST_APP, "ISSUED", null, null,
            winnerReason, now.toString(), "SOURCE", "ISSUED");
    IdempotencyRecord winnerKey = new IdempotencyRecord(ACTOR, REQUEST_KEY,
        commandAction(entry), winnerFingerprint, REQUEST_RECORD);
    OfferStatusRecord winnerOffer = new OfferStatusRecord();
    winnerOffer.setId(REQUEST_RECORD);
    winnerOffer.setApplicationId(REQUEST_APP);
    winnerOffer.setRecordType(correction ? "CORRECTION" : "STATUS");
    winnerOffer.setStatus("ISSUED");
    winnerOffer.setCorrectsRecordId(correction ? "O".repeat(26) : null);
    winnerOffer.setReason(winnerReason);
    winnerOffer.setSource("MANUAL_BEISEN_REFERENCE");
    winnerOffer.setSourceReference("SOURCE");
    winnerOffer.setSourceStatus("ISSUED");
    winnerOffer.setOccurredAt(now);
    winnerOffer.setOperatedByUserId(ACTOR);
    winnerOffer.setCreateTime(now);
    // 更正分支的旧事实在 T1 首次普通读取前已存在且最新；发放分支不使用它。
    OfferStatusRecord priorOffer = new OfferStatusRecord();
    priorOffer.setId("O".repeat(26));
    priorOffer.setApplicationId(REQUEST_APP);
    priorOffer.setRecordType("STATUS");
    priorOffer.setStatus("ISSUED");
    priorOffer.setOccurredAt(now.minusSeconds(60));
    AtomicReference<OfferStatusRecord> committedLatest =
        new AtomicReference<>(beforeSnapshot ? winnerOffer : (correction ? priorOffer : null));
    AtomicReference<IdempotencyRecord> committedKey = new AtomicReference<>(beforeSnapshot ? winnerKey : null);
    AtomicReference<OfferStatusRecord> committedOffer = new AtomicReference<>(beforeSnapshot ? winnerOffer : null);
    AtomicReference<IdempotencyRecord> snapshotKey = new AtomicReference<>();
    AtomicReference<OfferStatusRecord> snapshotOffer = new AtomicReference<>();
    Mockito.doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(events).doesNotContain("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP);
      snapshotKey.set(committedKey.get());
      snapshotOffer.set(committedOffer.get());
      events.add("scalar-snapshot");
      if (!beforeSnapshot) {
        // T1 尚未调用 JD/App 锁；T2 可提交同申请的发放或最新记录更正。
        // ISSUED 更正的 markOfferInProgress 只写 workflow_step，不更新 Application 主行。
        // missing-target 是损坏的键引用对照，不能冒充合法原子 winner。
        if (correction) {
          assertThat(committedLatest.get()).isSameAs(priorOffer);
          assertThat(winnerOffer.getCorrectsRecordId()).isEqualTo(priorOffer.getId());
          assertThat(winnerOffer.getApplicationId()).isEqualTo(priorOffer.getApplicationId());
          assertThat(winnerOffer.getOccurredAt()).isAfterOrEqualTo(priorOffer.getOccurredAt());
        }
        committedKey.set(winnerKey);
        committedOffer.set(mode.equals("missing-target") ? null : winnerOffer);
        if (!mode.equals("missing-target")) {
          committedLatest.set(winnerOffer);
        }
        events.add(mode.equals("missing-target") ? "inconsistent-key-reference" : winnerCommitEvent);
      }
      return JD_VERSION;
    }).when(persistence).findJdVersionId(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("scalar-snapshot");
      assertThat(events).doesNotContain("app-lock:" + REQUEST_APP);
      if (!beforeSnapshot) {
        assertThat(events).contains(mode.equals("missing-target")
            ? "inconsistent-key-reference" : winnerCommitEvent);
      }
      events.add("jd-lock:" + JD_VERSION);
      return withHeadcount(new JdVersionSnapshot(
          JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    }).when(demands).lockJdForHeadcount(JD_VERSION);
    when(idempotency.findIdempotency(ACTOR, REQUEST_KEY)).thenAnswer(call -> {
      events.add("ordinary-key-snapshot");
      return snapshotKey.get();
    });
    when(persistence.insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), any(), any(), any(), any()))
        .thenAnswer(call -> {
          assertThat(events).contains("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP,
              "current-collaborators:" + REQUEST_APP, "ordinary-key-snapshot");
          assertThat(committedKey.get()).isSameAs(winnerKey);
          assertThat(call.<String>getArgument(4)).isEqualTo(loserRecordId);
          events.add("insert-ignore-zero");
          return 0;
        });
    when(idempotency.findIdempotencyForUpdate(ACTOR, REQUEST_KEY)).thenAnswer(call -> {
      assertThat(events).contains("insert-ignore-zero");
      events.add("current-key");
      return committedKey.get();
    });
    // 两种读取 API 已在基线中存在；只建模事务视图，不伪造 SQL 会话缓存。
    OfferStatusRecordPersistenceService resultOffers = mock(OfferStatusRecordPersistenceService.class, call -> {
      String method = call.getMethod().getName();
      if (method.equals("findOffer") || method.equals("findOfferForUpdate")) {
        assertThat(call.<String>getArgument(0)).isEqualTo(REQUEST_APP);
        assertThat(call.<String>getArgument(1)).isEqualTo(REQUEST_RECORD);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(events).contains("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP);
        events.add(method.equals("findOffer") ? "ordinary-offer-snapshot" : "current-offer");
        return method.equals("findOffer") ? snapshotOffer.get() : committedOffer.get();
      }
      return Mockito.RETURNS_DEFAULTS.answer(call);
    });
    ReflectionTestUtils.setField(service, "offerStatusRecordPersistenceService", resultOffers);
    assertThat(events).isEmpty();
    if (successful) {
      AtomicReference<Object> response = new AtomicReference<>();
      Assertions.assertThatCode(() -> response.set(invokeApplicationCommand(commandProxy(transactions), entry)))
          .doesNotThrowAnyException();
      assertThat(response.get()).hasFieldOrPropertyWithValue("id", REQUEST_RECORD)
          .hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
          .hasFieldOrPropertyWithValue("recordType", correction ? "CORRECTION" : "STATUS")
          .hasFieldOrPropertyWithValue("status", "ISSUED")
          .hasFieldOrPropertyWithValue("reason", REQUEST_REASON)
          .hasFieldOrPropertyWithValue("correctsRecordId", correction ? "O".repeat(26) : null)
          .hasFieldOrPropertyWithValue("reopensRecordId", null)
          .hasFieldOrPropertyWithValue("source", "MANUAL_BEISEN_REFERENCE")
          .hasFieldOrPropertyWithValue("sourceReference", "SOURCE")
          .hasFieldOrPropertyWithValue("sourceStatus", "ISSUED")
          .hasFieldOrPropertyWithValue("terminationReason", null)
          .hasFieldOrPropertyWithValue("applicationOutcome", null)
          .hasFieldOrPropertyWithValue("occurredAt", now)
          .hasFieldOrPropertyWithValue("operatedByUserId", ACTOR)
          .hasFieldOrPropertyWithValue("createTime", now);
    } else {
      assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), entry))
          .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(
              mode.equals("missing-target") ? "IDEMPOTENCY_REPLAY_INCOMPLETE" : "IDEMPOTENCY_KEY_REUSED"));
    }
    if (beforeSnapshot) {
      verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
      verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
    } else {
      verify(persistence).insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), eq(commandAction(entry)),
          eq(commandFingerprint(entry, REQUEST_APP)), eq(loserRecordId), eq(now));
      verify(idempotency).findIdempotencyForUpdate(ACTOR, REQUEST_KEY);
      assertThat(events).containsSubsequence("scalar-snapshot", "jd-lock:" + JD_VERSION,
          "app-lock:" + REQUEST_APP, "current-collaborators:" + REQUEST_APP,
          "ordinary-key-snapshot", "insert-ignore-zero", "current-key");
    }
    if (mode.equals("changed-payload")) {
      verifyNoInteractions(resultOffers);
    } else {
      assertThat(Mockito.mockingDetails(resultOffers).getInvocations()).isNotEmpty()
          .allSatisfy(call -> assertThat(call.getMethod().getName())
              .isIn("findOffer", "findOfferForUpdate"));
    }
    // 检查真实被注入的 Offer mock；旧 offers 字段的零写断言不能替代此项。
    verify(resultOffers, never()).insertOffer(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
    verifyNoCommandWrites();
    assertCommandTransaction(transactions, successful);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  // 合成 RR 数据库读视图与真实 public 事务代理；不替代真实 MySQL 并发验证。
  @ParameterizedTest
  @ValueSource(strings = {"before-snapshot", "matching", "missing-target", "changed-payload"})
  void hireReplayReadsCommittedProcessAfterLegalPreLockWinner(String mode) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    boolean beforeSnapshot = mode.equals("before-snapshot");
    boolean successful = beforeSnapshot || mode.equals("matching");
    // 赢家时钟在 loser 的固定 now 前 60 秒；可在开始后延迟至 scalar 与 JD 锁之间提交。
    Instant winnerStartedAt = now.minusSeconds(60);
    Application currentApp = application(REQUEST_APP, "B".repeat(26), now.minusSeconds(120));
    currentApp.setCurrentStage("OFFER");
    OfferStatusRecord priorOffer = new OfferStatusRecord();
    priorOffer.setId("O".repeat(26));
    priorOffer.setApplicationId(REQUEST_APP);
    priorOffer.setStatus("ISSUED");
    priorOffer.setOccurredAt(now.minusSeconds(120));
    String winnerReason = mode.equals("changed-payload") ? REQUEST_REASON + " changed" : REQUEST_REASON;
    IdempotencyRecord winnerKey = new IdempotencyRecord(ACTOR, REQUEST_KEY, "APPLICATION_HIRE",
        Sha256Utils.fingerprintObjects(31, REQUEST_APP, "ISSUED", winnerReason), REQUEST_APP);
    OnboardingProcess winnerProcess = onboardingProcess(REQUEST_APP, "IN_PROGRESS", winnerStartedAt, null);
    AtomicReference<IdempotencyRecord> committedKey = new AtomicReference<>(beforeSnapshot ? winnerKey : null);
    AtomicReference<OnboardingProcess> committedProcess = new AtomicReference<>(beforeSnapshot ? winnerProcess : null);
    AtomicReference<IdempotencyRecord> snapshotKey = new AtomicReference<>();
    AtomicReference<OnboardingProcess> snapshotProcess = new AtomicReference<>();
    Mockito.doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(events).doesNotContain("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP);
      snapshotKey.set(committedKey.get());
      snapshotProcess.set(committedProcess.get());
      events.add("scalar-snapshot");
      if (!beforeSnapshot) {
        // OFFER + 快照前已 ISSUED + 无预约待办：合法 hire 不更新 Application 或 Offer。
        assertThat(currentApp.getCurrentStage()).isEqualTo("OFFER");
        assertThat(priorOffer.getStatus()).isEqualTo("ISSUED");
        assertThat(priorOffer.getOccurredAt()).isBefore(winnerStartedAt);
        assertThat(winnerKey.getTargetId()).isEqualTo(winnerProcess.getApplicationId()).isEqualTo(REQUEST_APP);
        committedKey.set(winnerKey);
        committedProcess.set(mode.equals("missing-target") ? null : winnerProcess);
        // missing-target 仅是损坏的 key-only 对照，不冒充合法原子提交。
        events.add(mode.equals("missing-target") ? "inconsistent-key-reference" : "winner-hire-commit-before-jd-lock");
      }
      return JD_VERSION;
    }).when(persistence).findJdVersionId(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("scalar-snapshot").doesNotContain("app-lock:" + REQUEST_APP);
      if (!beforeSnapshot) {
        assertThat(events).contains(mode.equals("missing-target")
            ? "inconsistent-key-reference" : "winner-hire-commit-before-jd-lock");
      }
      events.add("jd-lock:" + JD_VERSION);
      return withHeadcount(new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    }).when(demands).lockJdForHeadcount(JD_VERSION);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("jd-lock:" + JD_VERSION);
      events.add("app-lock:" + REQUEST_APP);
      return currentApp;
    }).when(persistence).lockApplication(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("app-lock:" + REQUEST_APP);
      events.add("current-collaborators:" + REQUEST_APP);
      return List.of(ACTOR);
    }).when(collaborators).findCollaboratorsForUpdate(REQUEST_APP);
    Mockito.doAnswer(call -> {
      events.add("ordinary-key-snapshot");
      return snapshotKey.get();
    }).when(idempotency).findIdempotency(ACTOR, REQUEST_KEY);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("current-collaborators:" + REQUEST_APP, "ordinary-key-snapshot");
      assertThat(committedKey.get()).isSameAs(winnerKey);
      assertThat(call.<String>getArgument(4)).isEqualTo(REQUEST_APP);
      events.add("insert-ignore-zero");
      return 0;
    }).when(persistence).insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      assertThat(events).contains("insert-ignore-zero");
      events.add("current-key");
      return committedKey.get();
    }).when(idempotency).findIdempotencyForUpdate(ACTOR, REQUEST_KEY);
    OnboardingProcessPersistenceService resultProcesses = mock(OnboardingProcessPersistenceService.class, call -> {
      String method = call.getMethod().getName();
      if (method.equals("findProcess") || method.equals("lockProcess")) {
        assertThat(call.<String>getArgument(0)).isEqualTo(REQUEST_APP);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(events).contains("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP,
            "current-collaborators:" + REQUEST_APP);
        events.add(method.equals("findProcess") ? "ordinary-process-snapshot" : "current-process");
        return method.equals("findProcess") ? snapshotProcess.get() : committedProcess.get();
      }
      return Mockito.RETURNS_DEFAULTS.answer(call);
    });
    ReflectionTestUtils.setField(service, "onboardingProcessPersistenceService", resultProcesses);
    assertThat(events).isEmpty();
    if (successful) {
      AtomicReference<Object> response = new AtomicReference<>();
      Assertions.assertThatCode(() -> response.set(invokeApplicationCommand(commandProxy(transactions), "hire")))
          .doesNotThrowAnyException();
      assertThat(response.get()).hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
          .hasFieldOrPropertyWithValue("status", "IN_PROGRESS")
          .hasFieldOrPropertyWithValue("startedAt", winnerStartedAt)
          .hasFieldOrPropertyWithValue("actualOnboardedAt", null)
          .hasFieldOrPropertyWithValue("closedAt", null)
          .hasFieldOrPropertyWithValue("elapsedSeconds", 60L);
    } else {
      assertThatThrownBy(() -> invokeApplicationCommand(commandProxy(transactions), "hire"))
          .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(
              mode.equals("missing-target") ? "IDEMPOTENCY_REPLAY_INCOMPLETE" : "IDEMPOTENCY_KEY_REUSED"));
    }
    if (beforeSnapshot) {
      verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
      verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
    } else {
      verify(persistence).insertIdempotency(eq(ACTOR), eq(REQUEST_KEY), eq("APPLICATION_HIRE"),
          eq(commandFingerprint("hire", REQUEST_APP)), eq(REQUEST_APP), eq(now));
      verify(idempotency).findIdempotencyForUpdate(ACTOR, REQUEST_KEY);
    }
    assertThat(events).containsSubsequence("scalar-snapshot", "jd-lock:" + JD_VERSION,
        "app-lock:" + REQUEST_APP, "current-collaborators:" + REQUEST_APP, "ordinary-key-snapshot");
    verify(collaborators).findCollaboratorsForUpdate(REQUEST_APP);
    verify(collaborators, never()).findCollaborators(any());
    ArgumentCaptor<AuthorizationTarget> access = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization).requireAccess(eq("recruitment.manage"), access.capture());
    assertThat(access.getValue()).hasFieldOrPropertyWithValue("objectType", "APPLICATION")
        .hasFieldOrPropertyWithValue("objectId", REQUEST_APP)
        .hasFieldOrPropertyWithValue("ownerUserId", currentApp.getOwnerUserId())
        .hasFieldOrPropertyWithValue("departmentId", currentApp.getOwnerDepartmentId())
        .hasFieldOrPropertyWithValue("collaboratorUserIds", Set.of(ACTOR));
    if (mode.equals("changed-payload")) {
      verifyNoInteractions(resultProcesses);
    } else {
      assertThat(Mockito.mockingDetails(resultProcesses).getInvocations()).isNotEmpty()
          .allSatisfy(call -> assertThat(call.getMethod().getName()).isIn("findProcess", "lockProcess"));
    }
    verify(resultProcesses, never()).insertProcess(any(), any(), any(), any());
    verify(resultProcesses, never()).markReturned(any(), any(), anyLong(), any());
    verify(resultProcesses, never()).markOnboarded(any(), any(), any(), anyLong(), any());
    verifyNoCommandWrites();
    assertCommandTransaction(transactions, successful);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  // 无 HTTP ActivityRequestContext 的 public DEFAULT 代理；模型是数据库读视图，不是真实 MySQL。
  @ParameterizedTest
  @ValueSource(strings = {"before-snapshot", "after-snapshot", "own-decision"})
  void normalHireUsesCurrentStageAfterDecisionCommitWithoutLosingOwnWrites(String mode) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    boolean ownDecision = mode.equals("own-decision");
    Instant winnerTime = now.minusSeconds(60);
    Application initial = application(REQUEST_APP, ACTOR, now.minusSeconds(120));
    initial.setCurrentStage("DECISION");
    initial.setOutcome(null);
    initial.setVersion(0L);
    AtomicReference<Application> storedApp = new AtomicReference<>(initial);
    AtomicReference<Application> snapshotApp = new AtomicReference<>();
    boolean[] viewStarted = {false};
    boolean[] ownAppWritten = {false};
    WorkflowStep storedDecision = pathStep("C".repeat(26), "DECISION", 1, "IN_PROGRESS", false);
    WorkflowStep storedOffer = pathStep("F".repeat(26), "OFFER", 2, "AVAILABLE", false);
    for (WorkflowStep step : List.of(storedDecision, storedOffer)) {
      step.setApplicationId(REQUEST_APP);
      step.setVersion(0L);
    }
    AtomicReference<List<WorkflowStep>> snapshotSteps = new AtomicReference<>();
    List<Decision> committedDecisions = new ArrayList<>();
    List<OfferStatusRecord> committedOffers = new ArrayList<>();
    List<List<Object>> committedHistory = new ArrayList<>();
    List<IdempotencyRecord> committedWinnerKeys = new ArrayList<>();
    List<Decision> ownDecisions = new ArrayList<>();
    List<OfferStatusRecord> ownOffers = new ArrayList<>();
    List<List<Object>> ownHistory = new ArrayList<>();
    List<List<Object>> reservations = new ArrayList<>();
    AtomicReference<OnboardingProcess> process = new AtomicReference<>();
    String winnerKey = "independent-decision-winner";
    String decisionKey = Sha256Utils.hashUtf8(REQUEST_KEY + ":decision");
    String selectedKey = Sha256Utils.hashUtf8(REQUEST_KEY + ":offer-selected");
    String issuedKey = Sha256Utils.hashUtf8(REQUEST_KEY + ":offer-issued");
    assertThat(winnerKey).isNotEqualTo(REQUEST_KEY).isNotEqualTo(decisionKey);
    AtomicInteger ownIds = new AtomicInteger();
    Mockito.doAnswer(call -> "L" + String.format("%025d", ownIds.incrementAndGet())).when(ids).newId();
    Runnable commitWinner = () -> {
      assertThat(storedApp.get().getCurrentStage()).isEqualTo("DECISION");
      assertThat(storedApp.get().getVersion()).isZero();
      assertThat(storedDecision.getStatus()).isEqualTo("IN_PROGRESS");
      assertThat(storedOffer.getStatus()).isEqualTo("AVAILABLE");
      Application winnerApp = new Application();
      BeanUtils.copyProperties(storedApp.get(), winnerApp);
      winnerApp.setCurrentStage("OFFER");
      winnerApp.setOutcome("PASS");
      winnerApp.setActiveSlot(1L);
      winnerApp.setTerminalAt(null);
      winnerApp.setVersion(1L);
      winnerApp.setUpdateId(ACTOR);
      winnerApp.setUpdateTime(winnerTime);
      Decision winnerDecision = new Decision("D".repeat(26), REQUEST_APP, "DECISION", "PASS",
          "winner-pass", null, null, ACTOR, winnerTime);
      winnerDecision.setCreateTime(winnerTime);
      committedDecisions.add(winnerDecision);
      storedDecision.setStatus("PASSED");
      storedDecision.setResultReason("winner-pass");
      storedDecision.setCompletedAt(winnerTime);
      storedDecision.setClientSubmissionId(null);
      storedDecision.setVersion(1L);
      storedDecision.setUpdateId(ACTOR);
      storedDecision.setUpdateTime(winnerTime);
      storedApp.set(winnerApp);
      storedOffer.setStatus("IN_PROGRESS");
      storedOffer.setOwnerUserId(ACTOR);
      storedOffer.setDueAt(winnerTime.plus(72, ChronoUnit.HOURS));
      storedOffer.setStartedAt(winnerTime);
      storedOffer.setCompletedAt(null);
      storedOffer.setResultReason(null);
      storedOffer.setVersion(1L);
      storedOffer.setUpdateId(ACTOR);
      storedOffer.setUpdateTime(winnerTime);
      OfferStatusRecord initialOffer = new OfferStatusRecord();
      initialOffer.setId("O".repeat(26));
      initialOffer.setApplicationId(REQUEST_APP);
      initialOffer.setRecordType("STATUS");
      initialOffer.setStatus("NOT_STARTED");
      initialOffer.setReason("人工决定通过，进入 Offer");
      initialOffer.setSource("MANUAL_BEISEN_REFERENCE");
      initialOffer.setOccurredAt(winnerTime);
      initialOffer.setOperatedByUserId(ACTOR);
      initialOffer.setCreateTime(winnerTime);
      committedOffers.add(initialOffer);
      committedHistory.add(List.of("H".repeat(26), REQUEST_APP, "DECISION", "OFFER", "winner-pass", ACTOR, winnerTime));
      committedWinnerKeys.add(new IdempotencyRecord(ACTOR, winnerKey, "APPLICATION_DECIDE",
          Sha256Utils.fingerprintObjects(31, REQUEST_APP, "PASS", "winner-pass", null), winnerDecision.getId()));
      events.add("winner-decision-full-commit");
    };
    if (mode.equals("before-snapshot")) commitWinner.run();
    Mockito.doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      if (!viewStarted[0]) {
        assertThat(events).doesNotContain("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP);
        Application view = new Application();
        BeanUtils.copyProperties(storedApp.get(), view);
        snapshotApp.set(view);
        snapshotSteps.set(List.of(storedDecision, storedOffer).stream().map(step -> {
          WorkflowStep copy = new WorkflowStep();
          BeanUtils.copyProperties(step, copy);
          return copy;
        }).toList());
        viewStarted[0] = true;
        events.add("scalar-snapshot");
        if (mode.equals("after-snapshot")) commitWinner.run();
      }
      return JD_VERSION;
    }).when(persistence).findJdVersionId(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(viewStarted[0]).isTrue();
      if (!ownDecision) assertThat(committedWinnerKeys).hasSize(1);
      events.add("jd-lock:" + JD_VERSION);
      return withHeadcount(new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPARTMENT, Set.of()));
    }).when(demands).lockJdForHeadcount(JD_VERSION);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("jd-lock:" + JD_VERSION);
      Application locked = new Application();
      BeanUtils.copyProperties(storedApp.get(), locked);
      events.add("app-lock:" + REQUEST_APP);
      return locked;
    }).when(persistence).lockApplication(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("app-lock:" + REQUEST_APP);
      events.add("current-collaborators:" + REQUEST_APP);
      return List.of();
    }).when(collaborators).findCollaboratorsForUpdate(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(viewStarted[0]).isTrue();
      Application visible = new Application();
      BeanUtils.copyProperties(ownAppWritten[0] ? storedApp.get() : snapshotApp.get(), visible);
      events.add(ownAppWritten[0] ? "ordinary-own-app" : "ordinary-snapshot-app");
      return visible;
    }).when(persistence).findApplication(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(call.<String>getArgument(0)).isEqualTo(ACTOR);
      assertThat(call.<String>getArgument(1)).isNotEqualTo(winnerKey);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return null;
    }).when(idempotency).findIdempotency(any(), any());
    Mockito.doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(call.<String>getArgument(0)).isEqualTo(ACTOR);
      assertThat(reservations).noneMatch(row -> row.get(1).equals(call.getArgument(1)));
      reservations.add(Arrays.asList(call.getArguments()));
      return 1;
    }).when(persistence).insertIdempotency(any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      if (mode.equals("after-snapshot")) {
        assertThat(snapshotApp.get().getCurrentStage()).isEqualTo("DECISION");
        assertThat(storedApp.get().getCurrentStage()).isEqualTo("OFFER");
        assertThat(storedDecision.getStatus()).isEqualTo("PASSED");
        assertThat(ownDecisions).isEmpty();
        assertThat(ownAppWritten[0]).isFalse();
      }
      return snapshotSteps.get();
    }).when(workflowSteps).findSteps(REQUEST_APP);
    Mockito.doAnswer(call -> {
      Decision written = new Decision(call.getArgument(0), call.getArgument(1), call.getArgument(2),
          call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6),
          call.getArgument(7), call.getArgument(8));
      written.setCreateTime(call.getArgument(8));
      committedDecisions.add(written);
      ownDecisions.add(written);
      return 1;
    }).when(decisions).insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> committedDecisions.stream().filter(value -> value.getId().equals(call.getArgument(1)))
        .findFirst().orElse(null)).when(decisions).findDecision(eq(REQUEST_APP), any());
    Mockito.doAnswer(call -> {
      if (!storedDecision.getId().equals(call.getArgument(0)) || !"DECISION".equals(storedDecision.getStepCode())
          || !List.of("PENDING", "IN_PROGRESS").contains(storedDecision.getStatus())) return 0;
      storedDecision.setStatus(call.getArgument(1));
      storedDecision.setResultReason(call.getArgument(2));
      storedDecision.setClientSubmissionId(call.getArgument(3));
      storedDecision.setCompletedAt(call.getArgument(5));
      storedDecision.setVersion(storedDecision.getVersion() + 1);
      storedDecision.setUpdateId(call.getArgument(4));
      storedDecision.setUpdateTime(call.getArgument(5));
      return 1;
    }).when(workflowSteps).finishDecisionStep(any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      Application current = storedApp.get();
      if (!current.getId().equals(call.getArgument(0))
          || current.getVersion() != call.<Long>getArgument(5).longValue()) return 0;
      Application written = new Application();
      BeanUtils.copyProperties(current, written);
      written.setCurrentStage(call.getArgument(1));
      written.setOutcome(call.getArgument(2));
      written.setTerminalAt(call.getArgument(3));
      written.setActiveSlot(call.<Integer>getArgument(4).longValue());
      written.setVersion(current.getVersion() + 1);
      written.setUpdateId(call.getArgument(6));
      written.setUpdateTime(call.getArgument(7));
      storedApp.set(written);
      ownAppWritten[0] = true;
      return 1;
    }).when(persistence).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
    Mockito.doAnswer(call -> {
      if (!REQUEST_APP.equals(call.getArgument(0)) || !"OFFER".equals(storedOffer.getStepCode())
          || !List.of("AVAILABLE", "NOT_SELECTED", "FAILED", "WITHDRAWN").contains(storedOffer.getStatus())) return 0;
      storedOffer.setStatus("IN_PROGRESS");
      storedOffer.setOwnerUserId(call.getArgument(1));
      storedOffer.setDueAt(call.getArgument(2));
      storedOffer.setStartedAt(call.getArgument(4));
      storedOffer.setCompletedAt(null);
      storedOffer.setResultReason(null);
      storedOffer.setVersion(storedOffer.getVersion() + 1);
      storedOffer.setUpdateId(call.getArgument(3));
      storedOffer.setUpdateTime(call.getArgument(4));
      return 1;
    }).when(workflowSteps).activateOfferStep(any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      assertThat(call.<String>getArgument(0)).isEqualTo(REQUEST_APP);
      assertThat(storedOffer.getStepCode()).isEqualTo("OFFER");
      if (!List.of("PENDING", "FAILED", "WITHDRAWN").contains(storedOffer.getStatus())) {
        assertThat(storedOffer.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(storedOffer.getVersion()).isEqualTo(1L);
        events.add("mark-offer-zero");
        return 0;
      }
      storedOffer.setStatus("IN_PROGRESS");
      if (storedOffer.getStartedAt() == null) storedOffer.setStartedAt(call.getArgument(4));
      storedOffer.setCompletedAt(null);
      storedOffer.setResultReason(null);
      storedOffer.setOwnerUserId(call.getArgument(1));
      storedOffer.setDueAt(call.getArgument(2));
      storedOffer.setVersion(storedOffer.getVersion() + 1);
      storedOffer.setUpdateId(call.getArgument(3));
      storedOffer.setUpdateTime(call.getArgument(4));
      return 1;
    }).when(persistence).markOfferInProgress(any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      OfferStatusRecord written = new OfferStatusRecord();
      written.setId(call.getArgument(0));
      written.setApplicationId(call.getArgument(1));
      written.setRecordType(call.getArgument(2));
      written.setStatus(call.getArgument(3));
      written.setTerminationReason(call.getArgument(4));
      written.setApplicationOutcome(call.getArgument(5));
      written.setReason(call.getArgument(6));
      written.setCorrectsRecordId(call.getArgument(7));
      written.setReopensRecordId(call.getArgument(8));
      written.setSource(call.getArgument(9));
      written.setSourceReference(call.getArgument(10));
      written.setSourceStatus(call.getArgument(11));
      written.setOccurredAt(call.getArgument(12));
      written.setOperatedByUserId(call.getArgument(13));
      written.setCreateTime(call.getArgument(14));
      committedOffers.add(written);
      ownOffers.add(written);
      return 1;
    }).when(offers).insertOffer(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> committedOffers.isEmpty() ? null : committedOffers.getLast())
        .when(offers).latestOfferForUpdate(REQUEST_APP);
    Mockito.doAnswer(call -> committedOffers.stream().filter(value -> value.getId().equals(call.getArgument(1)))
        .findFirst().orElse(null)).when(offers).findOffer(eq(REQUEST_APP), any());
    Mockito.doAnswer(call -> {
      List<Object> written = Arrays.asList(call.getArguments());
      committedHistory.add(written);
      ownHistory.add(written);
      return 1;
    }).when(stageHistory).insertStageHistory(any(), any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      assertThat(process.get()).isNull();
      process.set(onboardingProcess(call.getArgument(0), "IN_PROGRESS", call.getArgument(1), null));
      process.get().setCreateId(call.getArgument(2));
      process.get().setCreateTime(call.getArgument(3));
      process.get().setUpdateId(call.getArgument(2));
      process.get().setUpdateTime(call.getArgument(3));
      return 1;
    }).when(onboardingProcesses).insertProcess(any(), any(), any(), any());
    Mockito.doAnswer(call -> process.get()).when(onboardingProcesses).findProcess(REQUEST_APP);
    // public 服务代理没有 HTTP 上下文；不注入 origin，不用 spy 绕过 decide/addOffer/assembler。
    assertThat(ReflectionTestUtils.getField(service, "activityOrigins")).isNull();
    AtomicReference<Object> response = new AtomicReference<>();
    Assertions.assertThatCode(() -> response.set(commandProxy(transactions).hireResponse(
        REQUEST_APP, "IN_PROGRESS", REQUEST_REASON, REQUEST_KEY))).doesNotThrowAnyException();
    assertThat(response.get()).hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
        .hasFieldOrPropertyWithValue("status", "IN_PROGRESS")
        .hasFieldOrPropertyWithValue("startedAt", now)
        .hasFieldOrPropertyWithValue("actualOnboardedAt", null)
        .hasFieldOrPropertyWithValue("closedAt", null)
        .hasFieldOrPropertyWithValue("elapsedSeconds", 0L);
    assertThat(storedApp.get()).hasFieldOrPropertyWithValue("currentStage", "OFFER")
        .hasFieldOrPropertyWithValue("outcome", "PASS").hasFieldOrPropertyWithValue("activeSlot", 1L)
        .hasFieldOrPropertyWithValue("terminalAt", null).hasFieldOrPropertyWithValue("version", 1L);
    assertThat(storedDecision).hasFieldOrPropertyWithValue("status", "PASSED")
        .hasFieldOrPropertyWithValue("version", 1L);
    assertThat(storedOffer).hasFieldOrPropertyWithValue("status", "IN_PROGRESS")
        .hasFieldOrPropertyWithValue("version", 1L);
    assertThat(committedDecisions).hasSize(1);
    assertThat(committedHistory).hasSize(1);
    assertThat(committedOffers).extracting(OfferStatusRecord::getStatus)
        .containsExactly("NOT_STARTED", "IN_PROGRESS", "ISSUED");
    assertThat(ownOffers).extracting(OfferStatusRecord::getStatus).containsExactlyElementsOf(
        ownDecision ? List.of("NOT_STARTED", "IN_PROGRESS", "ISSUED") : List.of("IN_PROGRESS", "ISSUED"));
    assertThat(ownDecisions).hasSize(ownDecision ? 1 : 0);
    assertThat(ownHistory).hasSize(ownDecision ? 1 : 0);
    assertThat(ownAppWritten[0]).isEqualTo(ownDecision);
    assertThat(reservations).extracting(row -> row.get(1)).containsExactlyElementsOf(ownDecision
        ? List.of(REQUEST_KEY, decisionKey, selectedKey, issuedKey) : List.of(REQUEST_KEY, selectedKey, issuedKey));
    assertThat(reservations.getFirst()).containsExactly(ACTOR, REQUEST_KEY, "APPLICATION_HIRE",
        Sha256Utils.fingerprintObjects(31, REQUEST_APP, "IN_PROGRESS", REQUEST_REASON), REQUEST_APP, now);
    assertThat(reservations).allSatisfy(row -> {
      assertThat(row.get(0)).isEqualTo(ACTOR);
      assertThat(row.get(5)).isEqualTo(now);
      if (row.get(1).equals(decisionKey)) {
        assertThat(row).containsExactly(ACTOR, decisionKey, "APPLICATION_DECIDE",
            Sha256Utils.fingerprintObjects(31, REQUEST_APP, "PASS", REQUEST_REASON, null), ownDecisions.getFirst().getId(), now);
      } else if (!row.get(1).equals(REQUEST_KEY)) {
        String status = row.get(1).equals(selectedKey) ? "IN_PROGRESS" : "ISSUED";
        String targetId = ownOffers.stream().filter(offer -> offer.getStatus().equals(status)).findFirst().orElseThrow().getId();
        assertThat(row).containsExactly(ACTOR, row.get(1), "OFFER_ADVANCE",
            Sha256Utils.fingerprintObjects(31, REQUEST_APP, status, null, null, REQUEST_REASON, now.toString(), null, null), targetId, now);
      }
    });
    verify(decisions, Mockito.times(ownDecision ? 1 : 0)).insertDecision(any(), eq(REQUEST_APP), eq("DECISION"),
        eq("PASS"), eq(REQUEST_REASON), isNull(), isNull(), eq(ACTOR), eq(now));
    verify(workflowSteps, Mockito.times(ownDecision ? 1 : 0)).finishDecisionStep(
        storedDecision.getId(), "PASSED", REQUEST_REASON, null, ACTOR, now);
    verify(persistence, Mockito.times(ownDecision ? 1 : 0)).updateOutcome(REQUEST_APP, "OFFER", "PASS", null, 1, 0L, ACTOR, now);
    verify(workflowSteps, Mockito.times(ownDecision ? 1 : 0)).activateOfferStep(REQUEST_APP, ACTOR,
        now.plus(72, ChronoUnit.HOURS), ACTOR, now);
    verify(offers, Mockito.times(ownDecision ? 3 : 2)).insertOffer(any(), eq(REQUEST_APP), eq("STATUS"),
        any(), isNull(), isNull(), any(), isNull(), isNull(), eq("MANUAL_BEISEN_REFERENCE"), isNull(), isNull(), any(), eq(ACTOR), any());
    verify(stageHistory, Mockito.times(ownDecision ? 1 : 0)).insertStageHistory(any(), eq(REQUEST_APP),
        eq("DECISION"), eq("OFFER"), eq(REQUEST_REASON), eq(ACTOR), eq(now));
    verify(persistence).markOfferInProgress(REQUEST_APP, ACTOR, now.plus(72, ChronoUnit.HOURS), ACTOR, now);
    assertThat(events).contains("mark-offer-zero");
    verify(onboardingProcesses).insertProcess(REQUEST_APP, now, ACTOR, now);
    verify((FollowUpPlanLifecycleManager) ReflectionTestUtils.getField(service, "followUpPlanLifecycle"))
        .cancelForApplication(REQUEST_APP, REQUEST_REASON, ACTOR, now);
    verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
    verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
    verify(onboardingProcesses, never()).markOnboarded(any(), any(), any(), anyLong(), any());
    verify(onboardingProcesses, never()).markReturned(any(), any(), anyLong(), any());
    verify(workflowSteps, never()).selectStep(any(), any(), any(), any(), any(), any());
    verify(workflowSteps, never()).finishOfferStep(any(), any(), any(), any(), any());
    verify(locks, Mockito.times(2)).activeLock(CANDIDATE);
    verify(locks, never()).recordProgress(any(), any(), any());
    Mockito.verifyNoMoreInteractions(locks);
    verifyNoInteractions(onboarding, ownership, communications, followUps, intentions, candidateOnboardingReturns,
        candidates, resumes, screeningTransitions, interviews, interviewNotifications);
    verify(collaborators, never()).findCollaborators(any());
    ArgumentCaptor<AuthorizationTarget> access = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization, Mockito.atLeastOnce()).requireAccess(eq("recruitment.manage"), access.capture());
    assertThat(access.getAllValues()).allSatisfy(target -> assertThat(target)
        .hasFieldOrPropertyWithValue("objectId", REQUEST_APP).hasFieldOrPropertyWithValue("ownerUserId", ACTOR)
        .hasFieldOrPropertyWithValue("collaboratorUserIds", Set.of()));
    assertThat(events).containsSubsequence("scalar-snapshot", "jd-lock:" + JD_VERSION,
        "app-lock:" + REQUEST_APP, "current-collaborators:" + REQUEST_APP);
    assertCommandTransaction(transactions, true);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  // 无 HTTP 上下文的真实 public DEFAULT 代理；T2 是完整原子状态模型，不是真实 MySQL。
  @ParameterizedTest
  @ValueSource(strings = {"added-before-snapshot", "added-after-snapshot", "active-after-snapshot",
      "owner-after-snapshot", "revoked-after-snapshot", "narrow-added-before-snapshot",
      "narrow-added-after-snapshot", "narrow-active-after-snapshot", "narrow-owner-after-snapshot",
      "narrow-revoked-after-snapshot"})
  void normalHireNextActionUsesCommittedParticipantsAndOwnWrites(String mode) {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = configureCommandBoundary(transactions);
    boolean narrow = mode.startsWith("narrow-");
    String participantMode = narrow ? mode.substring("narrow-".length()) : mode;
    boolean beforeSnapshot = participantMode.equals("added-before-snapshot");
    boolean owner = participantMode.equals("owner-after-snapshot");
    boolean revoked = participantMode.equals("revoked-after-snapshot");
    if (narrow) {
      // 仅测试中组合真实 identity adapter；授权算法与 actor 装载都走实际实现，不抄写访问 predicate。
      IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
      ObjectGrantPersistenceService objectGrants = mock(ObjectGrantPersistenceService.class);
      UserSupportDepartmentPersistenceService supportDepartments = mock(UserSupportDepartmentPersistenceService.class);
      UserAccountRecord account = new UserAccountRecord();
      account.setId(ACTOR);
      account.setDisplayName("HR");
      account.setWorkEmail("hr@example.com");
      account.setHomeDepartmentId(DEPARTMENT);
      account.setStatus("ACTIVE");
      when(accounts.findById(ACTOR)).thenReturn(account);
      when(accounts.findRoleCodes(ACTOR)).thenReturn(List.of("RECRUITER"));
      when(supportDepartments.findSupportDepartmentIds(ACTOR)).thenReturn(List.of());
      CurrentActorProvider actorProvider = () -> Optional.of(
          new CurrentActor(ACTOR, Set.of("recruitment.manage", "demand.read"), false));
      IdentityAuthorizationAdapter identityAuthorization = new IdentityAuthorizationAdapter();
      ReflectionTestUtils.setField(identityAuthorization, "actorProvider", actorProvider);
      ReflectionTestUtils.setField(identityAuthorization, "accountService", accounts);
      ReflectionTestUtils.setField(identityAuthorization, "objectGrantPersistenceService", objectGrants);
      ReflectionTestUtils.setField(identityAuthorization, "userSupportDepartmentPersistenceService", supportDepartments);
      CurrentActorSnapshot actor = identityAuthorization.currentActor();
      assertThat(actor.hasPermission("recruitment.manage")).isTrue();
      assertThat(actor.hasCompanyWideScope()).isFalse();
      assertThat(actor.getManagedUserIds()).isEmpty();
      assertThat(actor.visibleDepartmentIdsFor("APPLICATION")).isEmpty();
      assertThat(objectGrants.countObjectGrant(ACTOR, "APPLICATION", REQUEST_APP, "recruitment.manage")).isZero();
      when(authorization.currentActor()).thenAnswer(call -> identityAuthorization.currentActor());
      Mockito.doAnswer(call -> {
        identityAuthorization.requireAccess(call.getArgument(0), call.getArgument(1));
        return null;
      }).when(authorization).requireAccess(anyString(), any(AuthorizationTarget.class));
      CandidateLibraryQueryService candidateLibrary = (CandidateLibraryQueryService)
          ReflectionTestUtils.getField(service, "candidateLibrary");
      when(candidateLibrary.eligibleCandidateIds(anyCollection())).thenReturn(Set.of());
    }
    String ownerId = owner ? ACTOR : "B".repeat(26);
    Instant winnerTime = now.minusSeconds(60);
    List<String> priorMembers = participantMode.startsWith("added-") ? List.of() : List.of(ACTOR);
    List<String> afterMembers = owner || revoked ? List.of() : List.of(ACTOR);
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(priorMembers);
    AtomicReference<List<String>> snapshotMembers = new AtomicReference<>();
    List<List<Object>> winnerChanges = new ArrayList<>();
    Application initial = application(REQUEST_APP, ownerId, now.minusSeconds(120));
    initial.setCurrentStage("IN_PROCESS");
    initial.setProgressiveFollowUp(true);
    initial.setVersion(0L);
    initial.setCreateId(ownerId);
    initial.setCreateTime(now.minusSeconds(120));
    initial.setUpdateId(ownerId);
    initial.setUpdateTime(now.minusSeconds(120));
    AtomicReference<Application> storedApp = new AtomicReference<>(initial);
    AtomicReference<Application> winnerApp = new AtomicReference<>();
    AtomicReference<Application> snapshotApp = new AtomicReference<>();
    boolean[] viewStarted = {false};
    boolean[] ownAppWritten = {false};
    boolean[] ownStepWritten = {false, false};
    WorkflowStep storedDecision = pathStep("C".repeat(26), "DECISION", 1, "AVAILABLE", false);
    WorkflowStep storedOffer = pathStep("F".repeat(26), "OFFER", 2, "AVAILABLE", false);
    for (WorkflowStep step : List.of(storedDecision, storedOffer)) {
      step.setApplicationId(REQUEST_APP);
      step.setVersion(0L);
      step.setCreateId(ownerId);
      step.setCreateTime(initial.getCreateTime());
    }
    AtomicReference<List<WorkflowStep>> snapshotSteps = new AtomicReference<>();
    List<Decision> committedDecisions = new ArrayList<>();
    List<OfferStatusRecord> committedOffers = new ArrayList<>();
    List<List<Object>> committedHistory = new ArrayList<>();
    List<Decision> ownDecisions = new ArrayList<>();
    List<OfferStatusRecord> ownOffers = new ArrayList<>();
    List<List<Object>> ownHistory = new ArrayList<>();
    List<List<Object>> ownPaths = new ArrayList<>();
    List<List<Object>> reservations = new ArrayList<>();
    List<List<Object>> stepWrites = new ArrayList<>();
    AtomicReference<OnboardingProcess> process = new AtomicReference<>();
    String decisionKey = Sha256Utils.hashUtf8(REQUEST_KEY + ":decision");
    String selectedKey = Sha256Utils.hashUtf8(REQUEST_KEY + ":offer-selected");
    String issuedKey = Sha256Utils.hashUtf8(REQUEST_KEY + ":offer-issued");
    AtomicInteger ownIds = new AtomicInteger();
    Mockito.doAnswer(call -> "L" + String.format("%025d", ownIds.incrementAndGet())).when(ids).newId();
    Runnable commitWinner = () -> {
      assertThat(winnerChanges).isEmpty();
      assertThat(events).doesNotContain("jd-lock:" + JD_VERSION, "app-lock:" + REQUEST_APP);
      assertThat(storedApp.get().getVersion()).isZero();
      assertThat(storedApp.get().getOwnerUserId()).isEqualTo(ownerId);
      assertThat(storedApp.get().getCurrentStage()).isEqualTo("IN_PROCESS");
      assertThat(afterMembers).doesNotHaveDuplicates().allSatisfy(member -> assertThat(member).isEqualTo(ACTOR));
      // 对应 owner 的真实 updateCollaborators：合法 active users、替换成员、版本 CAS 与变更历史同提交。
      Application written = new Application();
      BeanUtils.copyProperties(storedApp.get(), written);
      written.setVersion(1L);
      written.setUpdateId(ownerId);
      written.setUpdateTime(winnerTime);
      String beforeJson = priorMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]";
      String afterJson = afterMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]";
      storedMembers.set(List.copyOf(afterMembers));
      storedApp.set(written);
      winnerApp.set(written);
      winnerChanges.add(List.of("K".repeat(26), REQUEST_APP, beforeJson, afterJson,
          "合规协作者配置变更", 0L, ownerId, winnerTime));
      assertThat(storedDecision.getStatus()).isEqualTo("AVAILABLE");
      assertThat(storedOffer.getStatus()).isEqualTo("AVAILABLE");
      events.add("winner-collaborator-full-commit");
    };
    Mockito.doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      if (!viewStarted[0]) {
        if (beforeSnapshot) commitWinner.run();
        Application view = new Application();
        BeanUtils.copyProperties(storedApp.get(), view);
        snapshotApp.set(view);
        snapshotMembers.set(List.copyOf(storedMembers.get()));
        snapshotSteps.set(List.of(storedDecision, storedOffer).stream().map(step -> {
          WorkflowStep copy = new WorkflowStep();
          BeanUtils.copyProperties(step, copy);
          return copy;
        }).toList());
        viewStarted[0] = true;
        events.add("scalar-snapshot");
        if (!beforeSnapshot) commitWinner.run();
      }
      return JD_VERSION;
    }).when(persistence).findJdVersionId(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(viewStarted[0]).isTrue();
      assertThat(winnerChanges).hasSize(1);
      events.add("jd-lock:" + JD_VERSION);
      return withHeadcount(new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ownerId, DEPARTMENT, Set.of()));
    }).when(demands).lockJdForHeadcount(JD_VERSION);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("jd-lock:" + JD_VERSION);
      Application locked = new Application();
      BeanUtils.copyProperties(storedApp.get(), locked);
      events.add("app-lock:" + REQUEST_APP);
      return locked;
    }).when(persistence).lockApplication(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("app-lock:" + REQUEST_APP);
      events.add("current-collaborators:" + REQUEST_APP);
      return List.copyOf(storedMembers.get());
    }).when(collaborators).findCollaboratorsForUpdate(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(viewStarted[0]).isTrue();
      events.add("ordinary-collaborators:" + REQUEST_APP);
      return List.copyOf(snapshotMembers.get());
    }).when(collaborators).findCollaborators(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(viewStarted[0]).isTrue();
      Application visible = new Application();
      BeanUtils.copyProperties(ownAppWritten[0] ? storedApp.get() : snapshotApp.get(), visible);
      events.add(ownAppWritten[0] ? "ordinary-own-app" : "ordinary-snapshot-app");
      return visible;
    }).when(persistence).findApplication(REQUEST_APP);
    Mockito.doAnswer(call -> {
      assertThat(call.<String>getArgument(0)).isEqualTo(ACTOR);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return null;
    }).when(idempotency).findIdempotency(any(), any());
    Mockito.doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(call.<String>getArgument(0)).isEqualTo(ACTOR);
      assertThat(reservations).noneMatch(row -> row.get(1).equals(call.getArgument(1)));
      reservations.add(Arrays.asList(call.getArguments()));
      return 1;
    }).when(persistence).insertIdempotency(any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> List.of(storedDecision, storedOffer).stream().map(step -> {
      WorkflowStep copy = new WorkflowStep();
      BeanUtils.copyProperties(step, copy);
      return copy;
    }).toList()).when(workflowSteps).lockSteps(REQUEST_APP);
    Mockito.doAnswer(call -> List.of(storedDecision, storedOffer).stream().map(step -> {
      int index = step == storedDecision ? 0 : 1;
      WorkflowStep copy = new WorkflowStep();
      BeanUtils.copyProperties(ownStepWritten[index] ? step : snapshotSteps.get().get(index), copy);
      return copy;
    }).toList()).when(workflowSteps).findSteps(REQUEST_APP);
    Mockito.doAnswer(call -> {
      WorkflowStep supplied = call.getArgument(0);
      WorkflowStep current = supplied.getId().equals(storedDecision.getId()) ? storedDecision
          : supplied.getId().equals(storedOffer.getId()) ? storedOffer : null;
      if (current == null || !REQUEST_APP.equals(supplied.getApplicationId())
          || supplied.getVersion() != current.getVersion()) return false;
      long oldVersion = current.getVersion();
      stepWrites.add(List.of(supplied.getId(), oldVersion, supplied.getStatus(), supplied.getSequenceNo()));
      supplied.setVersion(oldVersion + 1);
      BeanUtils.copyProperties(supplied, current);
      ownStepWritten[current == storedDecision ? 0 : 1] = true;
      return true;
    }).when(workflowSteps).updateById(any(WorkflowStep.class));
    Mockito.doAnswer(call -> {
      Application current = storedApp.get();
      if (!current.getId().equals(call.getArgument(0))
          || current.getVersion() != call.<Long>getArgument(2).longValue()) return 0;
      Application written = new Application();
      BeanUtils.copyProperties(current, written);
      written.setProgressiveFollowUp(true);
      written.setCurrentStage(call.getArgument(1));
      written.setVersion(current.getVersion() + 1);
      written.setUpdateId(call.getArgument(3));
      written.setUpdateTime(call.getArgument(4));
      storedApp.set(written);
      ownAppWritten[0] = true;
      events.add("next-action-own-app-write");
      return 1;
    }).when(persistence).enableProgressiveStage(any(), any(), anyLong(), any(), any());
    Mockito.doAnswer(call -> {
      ownPaths.add(Arrays.asList(call.getArguments()));
      return 1;
    }).when(persistence).insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    Mockito.doAnswer(call -> new ArrayList<>(committedDecisions)).when(decisions).findDecisions(REQUEST_APP);
    Mockito.doAnswer(call -> new ArrayList<>(committedOffers)).when(offers).findOffers(REQUEST_APP);
    Mockito.doAnswer(call -> {
      Decision written = new Decision(call.getArgument(0), call.getArgument(1), call.getArgument(2),
          call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6),
          call.getArgument(7), call.getArgument(8));
      written.setCreateTime(call.getArgument(8));
      committedDecisions.add(written);
      ownDecisions.add(written);
      return 1;
    }).when(decisions).insertDecision(any(), any(), any(), any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> committedDecisions.stream().filter(value -> value.getId().equals(call.getArgument(1)))
        .findFirst().orElse(null)).when(decisions).findDecision(eq(REQUEST_APP), any());
    Mockito.doAnswer(call -> {
      if (!storedDecision.getId().equals(call.getArgument(0)) || !"DECISION".equals(storedDecision.getStepCode())
          || !List.of("PENDING", "IN_PROGRESS").contains(storedDecision.getStatus())) return 0;
      storedDecision.setStatus(call.getArgument(1));
      storedDecision.setResultReason(call.getArgument(2));
      storedDecision.setClientSubmissionId(call.getArgument(3));
      storedDecision.setCompletedAt(call.getArgument(5));
      storedDecision.setVersion(storedDecision.getVersion() + 1);
      storedDecision.setUpdateId(call.getArgument(4));
      storedDecision.setUpdateTime(call.getArgument(5));
      return 1;
    }).when(workflowSteps).finishDecisionStep(any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      Application current = storedApp.get();
      if (!current.getId().equals(call.getArgument(0))
          || current.getVersion() != call.<Long>getArgument(5).longValue()) return 0;
      Application written = new Application();
      BeanUtils.copyProperties(current, written);
      written.setCurrentStage(call.getArgument(1));
      written.setOutcome(call.getArgument(2));
      written.setTerminalAt(call.getArgument(3));
      written.setActiveSlot(call.<Integer>getArgument(4).longValue());
      written.setVersion(current.getVersion() + 1);
      written.setUpdateId(call.getArgument(6));
      written.setUpdateTime(call.getArgument(7));
      storedApp.set(written);
      ownAppWritten[0] = true;
      return 1;
    }).when(persistence).updateOutcome(any(), any(), any(), any(), any(), anyLong(), any(), any());
    Mockito.doAnswer(call -> {
      if (!REQUEST_APP.equals(call.getArgument(0)) || !"OFFER".equals(storedOffer.getStepCode())
          || !List.of("AVAILABLE", "NOT_SELECTED", "FAILED", "WITHDRAWN").contains(storedOffer.getStatus())) return 0;
      storedOffer.setStatus("IN_PROGRESS");
      storedOffer.setOwnerUserId(call.getArgument(1));
      storedOffer.setDueAt(call.getArgument(2));
      storedOffer.setStartedAt(call.getArgument(4));
      storedOffer.setCompletedAt(null);
      storedOffer.setResultReason(null);
      storedOffer.setVersion(storedOffer.getVersion() + 1);
      storedOffer.setUpdateId(call.getArgument(3));
      storedOffer.setUpdateTime(call.getArgument(4));
      return 1;
    }).when(workflowSteps).activateOfferStep(any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      assertThat(call.<String>getArgument(0)).isEqualTo(REQUEST_APP);
      assertThat(storedOffer.getStepCode()).isEqualTo("OFFER");
      if (!List.of("PENDING", "FAILED", "WITHDRAWN").contains(storedOffer.getStatus())) {
        assertThat(storedOffer.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(storedOffer.getVersion()).isEqualTo(2L);
        events.add("mark-offer-zero");
        return 0;
      }
      storedOffer.setStatus("IN_PROGRESS");
      if (storedOffer.getStartedAt() == null) storedOffer.setStartedAt(call.getArgument(4));
      storedOffer.setCompletedAt(null);
      storedOffer.setResultReason(null);
      storedOffer.setOwnerUserId(call.getArgument(1));
      storedOffer.setDueAt(call.getArgument(2));
      storedOffer.setVersion(storedOffer.getVersion() + 1);
      storedOffer.setUpdateId(call.getArgument(3));
      storedOffer.setUpdateTime(call.getArgument(4));
      return 1;
    }).when(persistence).markOfferInProgress(any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      OfferStatusRecord written = new OfferStatusRecord();
      written.setId(call.getArgument(0));
      written.setApplicationId(call.getArgument(1));
      written.setRecordType(call.getArgument(2));
      written.setStatus(call.getArgument(3));
      written.setTerminationReason(call.getArgument(4));
      written.setApplicationOutcome(call.getArgument(5));
      written.setReason(call.getArgument(6));
      written.setCorrectsRecordId(call.getArgument(7));
      written.setReopensRecordId(call.getArgument(8));
      written.setSource(call.getArgument(9));
      written.setSourceReference(call.getArgument(10));
      written.setSourceStatus(call.getArgument(11));
      written.setOccurredAt(call.getArgument(12));
      written.setOperatedByUserId(call.getArgument(13));
      written.setCreateTime(call.getArgument(14));
      committedOffers.add(written);
      ownOffers.add(written);
      return 1;
    }).when(offers).insertOffer(any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> committedOffers.isEmpty() ? null : committedOffers.getLast())
        .when(offers).latestOfferForUpdate(REQUEST_APP);
    Mockito.doAnswer(call -> committedOffers.stream().filter(value -> value.getId().equals(call.getArgument(1)))
        .findFirst().orElse(null)).when(offers).findOffer(eq(REQUEST_APP), any());
    Mockito.doAnswer(call -> {
      List<Object> written = Arrays.asList(call.getArguments());
      committedHistory.add(written);
      ownHistory.add(written);
      return 1;
    }).when(stageHistory).insertStageHistory(any(), any(), any(), any(), any(), any(), any());
    Mockito.doAnswer(call -> {
      assertThat(process.get()).isNull();
      process.set(onboardingProcess(call.getArgument(0), "IN_PROGRESS", call.getArgument(1), null));
      process.get().setCreateId(call.getArgument(2));
      process.get().setCreateTime(call.getArgument(3));
      process.get().setUpdateId(call.getArgument(2));
      process.get().setUpdateTime(call.getArgument(3));
      return 1;
    }).when(onboardingProcesses).insertProcess(any(), any(), any(), any());
    Mockito.doAnswer(call -> process.get()).when(onboardingProcesses).findProcess(REQUEST_APP);
    // 宽管理查看由独立 read contract 允许；真实 processing 根据 owner/实际成员判断，不伪造 access 拒绝。
    assertThat(ReflectionTestUtils.getField(service, "activityOrigins")).isNull();
    AtomicReference<Object> response = new AtomicReference<>();
    if (revoked) {
      assertThatThrownBy(() -> commandProxy(transactions).hireResponse(
          REQUEST_APP, "IN_PROGRESS", REQUEST_REASON, REQUEST_KEY))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo(
                  narrow ? "DATA_SCOPE_FORBIDDEN" : "RECRUITMENT_PROCESSING_FORBIDDEN"));
      verifyNoInteractions(idempotency);
      verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
      verify(persistence, never()).enableProgressiveStage(any(), any(), anyLong(), any(), any());
      verify(persistence, never()).insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
      verifyNoCommandResultReadOrWrite();
      assertThat(reservations).isEmpty();
      assertThat(ownPaths).isEmpty();
      assertThat(ownAppWritten[0]).isFalse();
      assertThat(storedApp.get().getVersion()).isEqualTo(1L);
      assertCommandTransaction(transactions, false);
    } else {
      Assertions.assertThatCode(() -> response.set(commandProxy(transactions).hireResponse(
          REQUEST_APP, "IN_PROGRESS", REQUEST_REASON, REQUEST_KEY))).doesNotThrowAnyException();
      assertThat(response.get()).hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
          .hasFieldOrPropertyWithValue("status", "IN_PROGRESS")
          .hasFieldOrPropertyWithValue("startedAt", now)
          .hasFieldOrPropertyWithValue("actualOnboardedAt", null)
          .hasFieldOrPropertyWithValue("closedAt", null)
          .hasFieldOrPropertyWithValue("elapsedSeconds", 0L);
      assertThat(storedApp.get()).hasFieldOrPropertyWithValue("currentStage", "OFFER")
          .hasFieldOrPropertyWithValue("outcome", "PASS").hasFieldOrPropertyWithValue("activeSlot", 1L)
          .hasFieldOrPropertyWithValue("terminalAt", null).hasFieldOrPropertyWithValue("version", 3L)
          .hasFieldOrPropertyWithValue("progressiveFollowUp", true);
      assertThat(storedDecision).hasFieldOrPropertyWithValue("status", "PASSED")
          .hasFieldOrPropertyWithValue("version", 2L);
      assertThat(storedOffer).hasFieldOrPropertyWithValue("status", "IN_PROGRESS")
          .hasFieldOrPropertyWithValue("version", 2L);
      assertThat(committedDecisions).hasSize(1);
      assertThat(committedHistory).hasSize(2);
      assertThat(committedOffers).extracting(OfferStatusRecord::getStatus)
          .containsExactly("NOT_STARTED", "IN_PROGRESS", "ISSUED");
      assertThat(ownOffers).extracting(OfferStatusRecord::getStatus).containsExactlyElementsOf(
          List.of("NOT_STARTED", "IN_PROGRESS", "ISSUED"));
      assertThat(ownDecisions).hasSize(1);
      assertThat(ownHistory).hasSize(2);
      assertThat(ownAppWritten[0]).isTrue();
      assertThat(reservations).extracting(row -> row.get(1)).containsExactly(REQUEST_KEY, decisionKey, selectedKey, issuedKey);
      assertThat(reservations.getFirst()).containsExactly(ACTOR, REQUEST_KEY, "APPLICATION_HIRE",
          Sha256Utils.fingerprintObjects(31, REQUEST_APP, "IN_PROGRESS", REQUEST_REASON), REQUEST_APP, now);
      assertThat(reservations).allSatisfy(row -> {
        assertThat(row.get(0)).isEqualTo(ACTOR);
        assertThat(row.get(5)).isEqualTo(now);
        if (row.get(1).equals(decisionKey)) {
          assertThat(row).containsExactly(ACTOR, decisionKey, "APPLICATION_DECIDE",
              Sha256Utils.fingerprintObjects(31, REQUEST_APP, "PASS", REQUEST_REASON, null), ownDecisions.getFirst().getId(), now);
        } else if (!row.get(1).equals(REQUEST_KEY)) {
          String status = row.get(1).equals(selectedKey) ? "IN_PROGRESS" : "ISSUED";
          String targetId = ownOffers.stream().filter(offer -> offer.getStatus().equals(status)).findFirst().orElseThrow().getId();
          assertThat(row).containsExactly(ACTOR, row.get(1), "OFFER_ADVANCE",
              Sha256Utils.fingerprintObjects(31, REQUEST_APP, status, null, null, REQUEST_REASON, now.toString(), null, null), targetId, now);
        }
      });
      verify(decisions, Mockito.times(1)).insertDecision(any(), eq(REQUEST_APP), eq("DECISION"),
          eq("PASS"), eq(REQUEST_REASON), isNull(), isNull(), eq(ACTOR), eq(now));
      verify(workflowSteps, Mockito.times(1)).finishDecisionStep(
          storedDecision.getId(), "PASSED", REQUEST_REASON, null, ACTOR, now);
      verify(persistence, Mockito.times(1)).updateOutcome(REQUEST_APP, "OFFER", "PASS", null, 1, 2L, ACTOR, now);
      verify(workflowSteps, Mockito.times(1)).activateOfferStep(REQUEST_APP, ownerId,
          now.plus(72, ChronoUnit.HOURS), ACTOR, now);
      verify(offers, Mockito.times(3)).insertOffer(any(), eq(REQUEST_APP), eq("STATUS"),
          any(), isNull(), isNull(), any(), isNull(), isNull(), eq("MANUAL_BEISEN_REFERENCE"), isNull(), isNull(), any(), eq(ACTOR), any());
      verify(stageHistory, Mockito.times(1)).insertStageHistory(any(), eq(REQUEST_APP),
          eq("DECISION"), eq("OFFER"), eq(REQUEST_REASON), eq(ACTOR), eq(now));
      verify(persistence).markOfferInProgress(REQUEST_APP, ownerId, now.plus(72, ChronoUnit.HOURS), ACTOR, now);
      assertThat(events).contains("mark-offer-zero");
      verify(onboardingProcesses).insertProcess(REQUEST_APP, now, ACTOR, now);
      verify((FollowUpPlanLifecycleManager) ReflectionTestUtils.getField(service, "followUpPlanLifecycle"))
          .cancelForApplication(REQUEST_APP, REQUEST_REASON, ACTOR, now);
      verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
      verify(persistence, never()).updateStage(any(), any(), anyLong(), any(), any());
      verify(onboardingProcesses, never()).markOnboarded(any(), any(), any(), anyLong(), any());
      verify(onboardingProcesses, never()).markReturned(any(), any(), anyLong(), any());
      verify(workflowSteps, never()).selectStep(any(), any(), any(), any(), any(), any());
      verify(workflowSteps, never()).finishOfferStep(any(), any(), any(), any(), any());
      verify(locks, Mockito.times(2)).activeLock(CANDIDATE);
      verify(locks, never()).recordProgress(any(), any(), any());
      Mockito.verifyNoMoreInteractions(locks);
      verifyNoInteractions(onboarding, ownership, communications, followUps, intentions, candidateOnboardingReturns,
          candidates, resumes, screeningTransitions, interviewNotifications);
      assertThat(stepWrites).containsExactly(
          List.of(storedDecision.getId(), 0L, "IN_PROGRESS", 3),
          List.of(storedOffer.getId(), 0L, "AVAILABLE", 4));
      assertThat(storedDecision).hasFieldOrPropertyWithValue("sequenceNo", 3)
          .hasFieldOrPropertyWithValue("ownerUserId", ownerId).hasFieldOrPropertyWithValue("startedAt", now)
          .hasFieldOrPropertyWithValue("completedAt", now).hasFieldOrPropertyWithValue("resultReason", REQUEST_REASON);
      assertThat(storedOffer).hasFieldOrPropertyWithValue("sequenceNo", 4)
          .hasFieldOrPropertyWithValue("ownerUserId", ownerId).hasFieldOrPropertyWithValue("startedAt", now)
          .hasFieldOrPropertyWithValue("dueAt", now.plus(72, ChronoUnit.HOURS));
      assertThat(storedApp.get()).hasFieldOrPropertyWithValue("ownerUserId", ownerId)
          .hasFieldOrPropertyWithValue("createId", ownerId).hasFieldOrPropertyWithValue("createTime", initial.getCreateTime())
          .hasFieldOrPropertyWithValue("updateId", ACTOR).hasFieldOrPropertyWithValue("updateTime", now);
      assertThat(List.of(storedDecision, storedOffer)).allSatisfy(step -> assertThat(step)
          .hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
          .hasFieldOrPropertyWithValue("createId", ownerId).hasFieldOrPropertyWithValue("createTime", initial.getCreateTime())
          .hasFieldOrPropertyWithValue("updateId", ACTOR).hasFieldOrPropertyWithValue("updateTime", now)
          .hasFieldOrPropertyWithValue("clientSubmissionId", null));
      assertThat(storedDecision).hasFieldOrPropertyWithValue("dueAt", null);
      assertThat(storedOffer).hasFieldOrPropertyWithValue("completedAt", null).hasFieldOrPropertyWithValue("resultReason", null);
      assertThat(ownDecisions.getFirst()).hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
          .hasFieldOrPropertyWithValue("recordType", "DECISION").hasFieldOrPropertyWithValue("decision", "PASS")
          .hasFieldOrPropertyWithValue("reason", REQUEST_REASON).hasFieldOrPropertyWithValue("correctsRecordId", null)
          .hasFieldOrPropertyWithValue("clientSubmissionId", null).hasFieldOrPropertyWithValue("decidedByUserId", ACTOR)
          .hasFieldOrPropertyWithValue("decidedAt", now).hasFieldOrPropertyWithValue("createTime", now);
      assertThat(ownOffers).allSatisfy(offer -> assertThat(offer)
          .hasFieldOrPropertyWithValue("applicationId", REQUEST_APP).hasFieldOrPropertyWithValue("recordType", "STATUS")
          .hasFieldOrPropertyWithValue("terminationReason", null).hasFieldOrPropertyWithValue("applicationOutcome", null)
          .hasFieldOrPropertyWithValue("reason", offer.getStatus().equals("NOT_STARTED") ? "人工决定通过，进入 Offer" : REQUEST_REASON)
          .hasFieldOrPropertyWithValue("correctsRecordId", null).hasFieldOrPropertyWithValue("reopensRecordId", null)
          .hasFieldOrPropertyWithValue("source", "MANUAL_BEISEN_REFERENCE").hasFieldOrPropertyWithValue("sourceReference", null)
          .hasFieldOrPropertyWithValue("sourceStatus", null).hasFieldOrPropertyWithValue("occurredAt", now)
          .hasFieldOrPropertyWithValue("operatedByUserId", ACTOR).hasFieldOrPropertyWithValue("createTime", now));
      assertThat(process.get()).hasFieldOrPropertyWithValue("applicationId", REQUEST_APP)
          .hasFieldOrPropertyWithValue("status", "IN_PROGRESS").hasFieldOrPropertyWithValue("startedAt", now)
          .hasFieldOrPropertyWithValue("actualOnboardedAt", null).hasFieldOrPropertyWithValue("closedAt", null)
          .hasFieldOrPropertyWithValue("createId", ACTOR).hasFieldOrPropertyWithValue("createTime", now)
          .hasFieldOrPropertyWithValue("updateId", ACTOR).hasFieldOrPropertyWithValue("updateTime", now);
      assertThat(ownPaths).hasSize(1);
      assertThat(ownPaths.getFirst()).containsExactly(ownPaths.getFirst().getFirst(), REQUEST_APP, "NEXT_ACTION",
          "[]", "[\"" + storedDecision.getId() + "\"]", REQUEST_REASON, 1L, ACTOR, now);
      verify(workflowSteps, Mockito.times(2)).updateById(any(WorkflowStep.class));
      verify(workflowSteps, never()).save(any(WorkflowStep.class));
      verify(persistence).enableProgressiveStage(REQUEST_APP, "DECISION", 1L, ACTOR, now);
      verify(persistence).insertPathChange(any(), eq(REQUEST_APP), eq("NEXT_ACTION"), eq("[]"),
          eq("[\"" + storedDecision.getId() + "\"]"), eq(REQUEST_REASON), eq(1L), eq(ACTOR), eq(now));
      verify(stageHistory).insertStageHistory(any(), eq(REQUEST_APP), eq("IN_PROCESS"), eq("DECISION"),
          eq(REQUEST_REASON), eq(ACTOR), eq(now));
      assertThat(ownHistory).extracting(row -> List.of(row.get(2), row.get(3)))
          .containsExactly(List.of("IN_PROCESS", "DECISION"), List.of("DECISION", "OFFER"));
      assertThat(ownHistory).allSatisfy(row -> assertThat(row).containsExactly(row.getFirst(), REQUEST_APP,
          row.get(2), row.get(3), REQUEST_REASON, ACTOR, now));
      assertThat(mockingDetails(interviews).getInvocations()).allMatch(invocation -> Set.of(
          "latestTypesByStepIds", "activeRoundNosByApplicationIds", "latestCompletedRoundNosByApplicationIds")
          .contains(invocation.getMethod().getName()));
      assertThat(events).containsSubsequence("next-action-own-app-write", "ordinary-own-app");
      assertCommandTransaction(transactions, true);
    }
    assertThat(winnerChanges).hasSize(1);
    assertThat(winnerChanges.getFirst()).containsExactly("K".repeat(26), REQUEST_APP,
        priorMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]",
        afterMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]",
        "合规协作者配置变更", 0L, ownerId, winnerTime);
    assertThat(snapshotApp.get().getVersion()).isEqualTo(beforeSnapshot ? 1L : 0L);
    assertThat(snapshotMembers.get()).containsExactlyElementsOf(beforeSnapshot ? afterMembers : priorMembers);
    assertThat(storedMembers.get()).containsExactlyElementsOf(afterMembers);
    assertThat(winnerApp.get()).hasFieldOrPropertyWithValue("ownerUserId", ownerId)
        .hasFieldOrPropertyWithValue("currentStage", "IN_PROCESS").hasFieldOrPropertyWithValue("version", 1L)
        .hasFieldOrPropertyWithValue("updateId", ownerId).hasFieldOrPropertyWithValue("updateTime", winnerTime)
        .hasFieldOrPropertyWithValue("createId", ownerId).hasFieldOrPropertyWithValue("createTime", initial.getCreateTime());
    if (beforeSnapshot) {
      assertThat(events).containsSubsequence("winner-collaborator-full-commit", "scalar-snapshot", "jd-lock:" + JD_VERSION);
    } else {
      assertThat(events).containsSubsequence("scalar-snapshot", "winner-collaborator-full-commit", "jd-lock:" + JD_VERSION);
    }
    assertThat(mockingDetails(collaborators).getInvocations()).allMatch(invocation ->
        Set.of("findCollaborators", "findCollaboratorsForUpdate").contains(invocation.getMethod().getName()));
    ArgumentCaptor<AuthorizationTarget> access = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization, Mockito.atLeastOnce()).requireAccess(eq("recruitment.manage"), access.capture());
    assertThat(access.getAllValues()).allSatisfy(target -> assertThat(target)
        .hasFieldOrPropertyWithValue("objectId", REQUEST_APP).hasFieldOrPropertyWithValue("ownerUserId", ownerId)
        .hasFieldOrPropertyWithValue("departmentId", DEPARTMENT));
    assertThat(access.getAllValues().getFirst().getCollaboratorUserIds()).containsExactlyInAnyOrderElementsOf(afterMembers);
    assertThat(access.getAllValues()).allSatisfy(target -> assertThat(target.getCollaboratorUserIds())
        .isIn(Set.copyOf(snapshotMembers.get()), Set.copyOf(afterMembers)));
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  @Test
  void directNextActionClosedViewerKeepsTerminationBeforeProcessingRejection() {
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    configureCommandBoundary(transactions);
    Application closed = application(REQUEST_APP, "B".repeat(26), now);
    closed.setCurrentStage("TERMINATED");
    closed.setTerminalAt(now.minusSeconds(60));
    closed.setActiveSlot(null);
    doReturn(closed).when(persistence).lockApplication(REQUEST_APP);
    assertThatThrownBy(() -> commandProxy(transactions).nextActionResponse(
        REQUEST_APP, "DECISION", REQUEST_REASON, closed.getVersion(), null))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_TERMINATED"));
    verify(persistence).lockApplication(REQUEST_APP);
    verify(persistence, never()).findJdVersionId(any());
    verifyNoInteractions(idempotency);
    verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
    verify(persistence, never()).enableProgressiveStage(any(), any(), anyLong(), any(), any());
    verify(persistence, never()).insertPathChange(any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
    verifyNoCommandResultReadOrWrite();
    assertThat(mockingDetails(collaborators).getInvocations()).hasSize(1).allMatch(invocation ->
        Set.of("findCollaborators", "findCollaboratorsForUpdate").contains(invocation.getMethod().getName()));
    ArgumentCaptor<AuthorizationTarget> access = ArgumentCaptor.forClass(AuthorizationTarget.class);
    verify(authorization).requireAccess(eq("recruitment.manage"), access.capture());
    assertThat(access.getValue()).hasFieldOrPropertyWithValue("ownerUserId", "B".repeat(26))
        .hasFieldOrPropertyWithValue("collaboratorUserIds", Set.of());
    assertCommandTransaction(transactions, false);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }


  // 合成已提交事实、RR 普通视图和本事务写入；真实双代理 JOIN，不证明真实 MySQL 隔离或物理回滚。
  @ParameterizedTest
  @ValueSource(strings = {"wide-revoked-after", "narrow-revoked-after", "narrow-active-after",
      "narrow-owner-after", "wide-revoked-before", "narrow-revoked-before"})
  void publicInterviewCreateRechecksCommittedParticipantsInJoinedTransaction(String mode) {
    boolean wide = mode.startsWith("wide-");
    boolean beforeSnapshot = mode.endsWith("-before");
    boolean owner = mode.contains("-owner-");
    boolean denied = mode.contains("-revoked-");
    String appId = "P".repeat(26), stepId = "T".repeat(26), interviewId = "N".repeat(26);
    String ownerId = owner ? ACTOR : "B".repeat(26), interviewerId = "V".repeat(26);
    IdentityAuthorizationAdapter realAuthorization = interviewBoundaryAuthorization(wide);
    ReflectionTestUtils.setField(service, "authorization", realAuthorization);
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = new ArrayList<>();
    Application initial = application(appId, ownerId, now.minusSeconds(120));
    initial.setVersion(0L);
    initial.setCreateId(ownerId);
    initial.setCreateTime(now.minusSeconds(120));
    initial.setUpdateId(ownerId);
    initial.setUpdateTime(now.minusSeconds(120));
    AtomicReference<Application> storedApp = new AtomicReference<>(initial);
    AtomicReference<Application> snapshotApp = new AtomicReference<>();
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(List.of(ACTOR));
    AtomicReference<List<String>> snapshotMembers = new AtomicReference<>();
    WorkflowStep initialStep = pathStep(stepId, "INTERVIEW_ROUND_1", 1, "PENDING", true);
    initialStep.setApplicationId(appId);
    initialStep.setRoundNo(1);
    initialStep.setVersion(0L);
    AtomicReference<WorkflowStep> storedStep = new AtomicReference<>(initialStep);
    AtomicReference<WorkflowStep> snapshotStep = new AtomicReference<>();
    List<List<Object>> winnerChanges = new ArrayList<>(), reservations = new ArrayList<>();
    List<List<Object>> stepWrites = new ArrayList<>(), interviewWrites = new ArrayList<>(), history = new ArrayList<>();
    AtomicReference<Interview> ownInterview = new AtomicReference<>();
    AtomicReference<List<Participant>> ownParticipants = new AtomicReference<>(List.of());
    List<String> afterMembers = denied || owner ? List.of() : List.of(ACTOR);
    AuthorizationTarget noParticipation = new AuthorizationTarget("APPLICATION", appId, ownerId, DEPARTMENT, Set.of());
    assertThat(realAuthorization.canAccess("recruitment.manage", noParticipation)).isEqualTo(wide || owner);
    assertThat(realAuthorization.canAccess("recruitment.manage", new AuthorizationTarget(
        "APPLICATION", appId, ownerId, DEPARTMENT, Set.of(ACTOR)))).isTrue();
    Runnable commitWinner = () -> {
      assertThat(winnerChanges).isEmpty();
      assertThat(events).doesNotContain("application-lock");
      assertThat(storedApp.get().getVersion()).isZero();
      // 模型对应 owner updateCollaborators 的 activeUsers、成员替换、活动版本 CAS 与完整变更历史同提交。
      Application changed = new Application();
      BeanUtils.copyProperties(storedApp.get(), changed);
      changed.setVersion(1L);
      changed.setUpdateId(ownerId);
      changed.setUpdateTime(now.minusSeconds(60));
      storedApp.set(changed);
      storedMembers.set(List.copyOf(afterMembers));
      winnerChanges.add(List.of("K".repeat(26), appId, "[\"" + ACTOR + "\"]",
          afterMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]",
          "合规协作者配置变更", 0L, ownerId, now.minusSeconds(60)));
      assertThat(storedStep.get().getStatus()).isEqualTo("PENDING");
      assertThat(storedStep.get().getVersion()).isZero();
      events.add("winner-full-commit");
    };
    when(persistence.findApplication(appId)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      if (snapshotApp.get() == null) {
        if (beforeSnapshot) commitWinner.run();
        Application view = new Application();
        BeanUtils.copyProperties(storedApp.get(), view);
        snapshotApp.set(view);
        snapshotMembers.set(List.copyOf(storedMembers.get()));
        WorkflowStep stepView = new WorkflowStep();
        BeanUtils.copyProperties(storedStep.get(), stepView);
        snapshotStep.set(stepView);
        events.add("ordinary-snapshot");
      }
      Application view = new Application();
      BeanUtils.copyProperties(snapshotApp.get(), view);
      return view;
    });
    when(collaborators.findCollaborators(appId)).thenAnswer(call -> {
      assertThat(snapshotMembers.get()).isNotNull();
      events.add("ordinary-members");
      return List.copyOf(snapshotMembers.get());
    });
    when(persistence.lockApplication(appId)).thenAnswer(call -> {
      assertThat(winnerChanges).hasSize(1);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      events.add("application-lock");
      Application current = new Application();
      BeanUtils.copyProperties(storedApp.get(), current);
      return current;
    });
    when(collaborators.findCollaboratorsForUpdate(appId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock");
      events.add("current-members");
      return List.copyOf(storedMembers.get());
    });
    when(workflowSteps.findStep(appId, stepId)).thenAnswer(call -> {
      WorkflowStep visible = new WorkflowStep();
      BeanUtils.copyProperties(stepWrites.isEmpty() ? snapshotStep.get() : storedStep.get(), visible);
      events.add(stepWrites.isEmpty() ? "ordinary-step" : "ordinary-own-step");
      return visible;
    });
    when(workflowSteps.lockStep(appId, stepId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock");
      events.add("step-lock");
      WorkflowStep locked = new WorkflowStep();
      BeanUtils.copyProperties(storedStep.get(), locked);
      return locked;
    });
    when(workflowSteps.startStep(stepId, 0L, ACTOR, now)).thenAnswer(call -> {
      assertThat(events).contains("step-lock");
      assertThat(stepWrites).isEmpty();
      WorkflowStep written = new WorkflowStep();
      BeanUtils.copyProperties(storedStep.get(), written);
      written.setStatus("IN_PROGRESS");
      written.setVersion(1L);
      written.setUpdateId(ACTOR);
      written.setUpdateTime(now);
      storedStep.set(written);
      stepWrites.add(Arrays.asList(call.getArguments()));
      events.add("own-step-write");
      return 1;
    });
    when(interviews.countActiveByStep(stepId)).thenAnswer(call -> {
      assertThat(events).contains("ordinary-step");
      assertThat(reservations).hasSize(1);
      if (!beforeSnapshot) commitWinner.run();
      events.add("ordinary-interview-count");
      return 0;
    });
    when(interviews.lockScheduledByStep(stepId)).thenAnswer(call -> {
      assertThat(events).contains("step-lock", "own-step-write");
      events.add("scheduled-lock");
      return null;
    });
    when(interviews.insertIdempotency(eq(ACTOR), eq("interview-current-member"), any(), any(), eq(interviewId), eq(now)))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          assertThat(reservations).isEmpty();
          reservations.add(Arrays.asList(call.getArguments()));
          events.add("own-reservation");
          return 1;
        });
    when(interviews.insertInterview(any(), any(), any(), any(), any(), any(), any(), any(), any(),
        Mockito.anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> {
          assertThat(events).contains("scheduled-lock", "ordinary-own-step");
          assertThat(interviewWrites).isEmpty();
          List<Object> row = Arrays.asList(call.getArguments());
          interviewWrites.add(row);
          Interview value = new Interview();
          value.setId((String) row.get(0)); value.setApplicationId((String) row.get(1));
          value.setCandidateId((String) row.get(2)); value.setJdId((String) row.get(3));
          value.setJdVersionId((String) row.get(4)); value.setWorkflowTemplateVersionId((String) row.get(5));
          value.setWorkflowStepId((String) row.get(6)); value.setStepCode((String) row.get(7));
          value.setInterviewType((String) row.get(8)); value.setRoundNo((Integer) row.get(9));
          value.setClientSubmissionId((String) row.get(10)); value.setScheduledStartAt((Instant) row.get(11));
          value.setScheduledEndAt((Instant) row.get(12)); value.setTimezoneId((String) row.get(13));
          value.setMeetingMode((String) row.get(14)); value.setLocationText((String) row.get(15));
          value.setMeetingUrl((String) row.get(16)); value.setCandidateNotificationChannel((String) row.get(17));
          value.setOwnerUserId((String) row.get(18)); value.setOwnerDepartmentId((String) row.get(19));
          value.setNextResponsibleUserId((String) row.get(20)); value.setCreateId((String) row.get(21));
          value.setCreateTime((Instant) row.get(22)); value.setUpdateId(value.getCreateId()); value.setUpdateTime(value.getCreateTime());
          value.setStatus("SCHEDULED"); value.setCandidateConfirmationStatus("PENDING"); value.setVersion(0L);
          ownInterview.set(value);
          events.add("own-interview-write");
          return 1;
        });
    when(interviews.findInterview(interviewId)).thenAnswer(call -> {
      assertThat(ownInterview.get()).isNotNull();
      Interview visible = new Interview();
      BeanUtils.copyProperties(ownInterview.get(), visible);
      events.add("ordinary-own-interview");
      return visible;
    });
    ParticipantPersistenceService participantRows = mock(ParticipantPersistenceService.class);
    when(participantRows.insertParticipants(interviewId, List.of(interviewerId), "INTERVIEWER", now))
        .thenAnswer(call -> {
          assertThat(events).contains("own-interview-write");
          ownParticipants.set(List.of(new Participant(interviewId, interviewerId, "INTERVIEWER", "PENDING")));
          events.add("own-participants-write");
          return true;
        });
    when(participantRows.findParticipants(interviewId)).thenAnswer(call -> {
      assertThat(events).contains("own-participants-write");
      events.add("ordinary-own-participants");
      return List.copyOf(ownParticipants.get());
    });
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(events).contains("ordinary-own-interview");
      history.add(Arrays.asList(call.getArguments()));
      events.add("own-history-write");
      return 1;
    });
    when(locks.activeLock(CANDIDATE)).thenReturn(new EngagementLockQueryService.LockSummary(
        "L".repeat(26), CANDIDATE, ACTOR, DEPARTMENT, "HR", now, now.plusSeconds(86400), true, "NONE"));
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "INTERNAL", ownerId, DEPARTMENT, Set.of()));
    when(ids.newId()).thenReturn(interviewId, "H".repeat(26));
    InterviewApplicationManager interviewManager = new InterviewApplicationManager();
    ReflectionTestUtils.setField(interviewManager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(interviewManager, "persistence", interviews);
    ReflectionTestUtils.setField(interviewManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(interviews.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(interviewManager, "interviewIdempotencyPersistenceService", mock(InterviewIdempotencyPersistenceService.class));
    ReflectionTestUtils.setField(interviewManager, "participantPersistenceService", participantRows);
    ReflectionTestUtils.setField(interviewManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(interviewManager, "directory", directory);
    ReflectionTestUtils.setField(interviewManager, "applications", commandProxy(transactions));
    ReflectionTestUtils.setField(interviewManager, "demands", demands);
    ReflectionTestUtils.setField(interviewManager, "engagementLocks", locks);
    ReflectionTestUtils.setField(interviewManager, "ids", ids);
    ReflectionTestUtils.setField(interviewManager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ProxyFactory outer = new ProxyFactory(interviewManager);
    outer.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    outer.addAdvice(interceptor);
    InterviewApplicationManager publicEntry = (InterviewApplicationManager) outer.getProxy();
    CreateInterviewDTO command = new CreateInterviewDTO(appId, stepId, now.plusSeconds(3600),
        now.plusSeconds(7200), "Asia/Shanghai", "ONLINE", null, "https://meeting.example/1",
        List.of(interviewerId), "NONE", null, "安排面试");
    if (denied) {
      String expected = wide ? "RECRUITMENT_PROCESSING_FORBIDDEN" : "DATA_SCOPE_FORBIDDEN";
      assertThatThrownBy(() -> publicEntry.create(command, "interview-current-member"))
          .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(expected));
      assertThat(stepWrites).isEmpty(); assertThat(interviewWrites).isEmpty(); assertThat(history).isEmpty();
      assertThat(ownParticipants.get()).isEmpty(); assertThat(ownInterview.get()).isNull();
      verify(locks, never()).recordProgress(any(), any(), any());
      verify(workflowSteps, never()).lockStep(any(), any());
      assertThat(reservations).hasSize(beforeSnapshot && !wide ? 0 : 1);
      assertCommandTransaction(transactions, false);
    } else {
      var response = publicEntry.create(command, "interview-current-member");
      assertThat(response.getInterview().getId()).isEqualTo(interviewId);
      assertThat(response.getInterview().getApplicationId()).isEqualTo(appId);
      assertThat(response.getInterview().getWorkflowStepId()).isEqualTo(stepId);
      assertThat(response.getInterview().getStatus()).isEqualTo("SCHEDULED");
      assertThat(response.getInterview().getOwnerUserId()).isEqualTo(ownerId);
      assertThat(response.getInterview().getOwnerDepartmentId()).isEqualTo(DEPARTMENT);
      assertThat(response.getInterview().getNextResponsibleUserId()).isEqualTo(ownerId);
      assertThat(response.getInterview().getVersion()).isEqualTo("0");
      assertThat(response.getInterview().getScheduledStartAt()).isEqualTo(command.getStartAt());
      assertThat(response.getInterview().getScheduledEndAt()).isEqualTo(command.getEndAt());
      assertThat(response.getInterview().getCreateId()).isEqualTo(ACTOR);
      assertThat(response.getInterview().getCreateTime()).isEqualTo(now);
      assertThat(response.getParticipants()).singleElement().satisfies(participant -> {
        assertThat(participant.getUserId()).isEqualTo(interviewerId);
        assertThat(participant.getParticipantRole()).isEqualTo("INTERVIEWER");
        assertThat(participant.getResponseStatus()).isEqualTo("PENDING");
      });
      assertThat(stepWrites).containsExactly(List.of(stepId, 0L, ACTOR, now));
      assertThat(storedStep.get().getStatus()).isEqualTo("IN_PROGRESS");
      assertThat(storedStep.get().getVersion()).isEqualTo(1L);
      assertThat(interviewWrites).hasSize(1); assertThat(history).hasSize(1); assertThat(reservations).hasSize(1);
      assertThat(history.getFirst().subList(1, 4)).containsExactly(interviewId, "CREATE", null);
      Map<String, String> afterSnapshot = new ObjectMapper().readValue(
          history.getFirst().get(4).toString(), new TypeReference<Map<String, String>>() {});
      assertThat(afterSnapshot).containsExactlyInAnyOrderEntriesOf(Map.of(
          "status", "SCHEDULED", "interviewType", "INTERNAL",
          "startAt", command.getStartAt().toString(), "endAt", command.getEndAt().toString(),
          "timezone", "Asia/Shanghai", "mode", "ONLINE"));
      assertThat(history.getFirst().subList(5, 8)).containsExactly("安排面试", ACTOR, now);
      verify(locks).recordProgress(CANDIDATE, "INTERVIEW_SCHEDULED", "已安排面试");
      verify(demands).requireJdVersion(JD_VERSION);
      verify(directory).activeUsers(Set.of(interviewerId));
      assertCommandTransaction(transactions, true);
    }
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(winnerChanges).hasSize(1);
    assertThat(storedApp.get().getVersion()).isEqualTo(1L);
    assertThat(storedApp.get().getOwnerUserId()).isEqualTo(ownerId);
    assertThat(storedApp.get().getUpdateId()).isEqualTo(ownerId);
    assertThat(storedApp.get().getUpdateTime()).isEqualTo(now.minusSeconds(60));
    assertThat(storedMembers.get()).isEqualTo(afterMembers);
    assertThat(snapshotMembers.get()).isEqualTo(beforeSnapshot ? afterMembers : List.of(ACTOR));
    assertThat(snapshotApp.get().getVersion()).isEqualTo(beforeSnapshot ? 1L : 0L);
    assertThat(events.indexOf("winner-full-commit")).isLessThan(events.indexOf("application-lock") < 0
        ? events.size() : events.indexOf("application-lock"));
  }

  @ParameterizedTest
  @CsvSource({"false,true,DATA_SCOPE_FORBIDDEN", "true,true,APPLICATION_TERMINATED",
      "true,false,RECRUITMENT_PROCESSING_FORBIDDEN"})
  void lockedInterviewContextKeepsViewClosureAndProcessingErrorOrder(boolean wide, boolean terminal, String code) {
    String appId = "P".repeat(26), stepId = "T".repeat(26);
    Application app = application(appId, "B".repeat(26), now);
    if (terminal) app.setTerminalAt(now);
    when(persistence.lockApplication(appId)).thenReturn(app);
    when(collaborators.findCollaborators(appId)).thenReturn(List.of());
    when(collaborators.findCollaboratorsForUpdate(appId)).thenReturn(List.of());
    ReflectionTestUtils.setField(service, "authorization", interviewBoundaryAuthorization(wide));
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    assertThatThrownBy(() -> commandProxy(transactions).requireLockedInterviewContext(appId, stepId))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(code));
    verify(workflowSteps, never()).lockStep(any(), any());
    verifyNoInteractions(interviews);
    assertCommandTransaction(transactions, false);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  // 有客户推荐的独立链路：真实四代理 JOIN 与合成 RR 视图；不证明真实数据库隔离或物理回滚。
  @ParameterizedTest
  @ValueSource(strings = {"wide-revoked-after", "narrow-revoked-after", "narrow-active-after",
      "narrow-owner-after", "wide-revoked-before", "narrow-revoked-before",
      "wide-mismatch-after", "wide-withdrawn-after"})
  void publicClientInterviewCreateRechecksCommittedParticipantsAfterSubmissionLock(String mode) {
    boolean wide = mode.startsWith("wide-");
    boolean beforeSnapshot = mode.endsWith("-before");
    boolean owner = mode.contains("-owner-");
    boolean denied = mode.contains("-revoked-");
    boolean invalid = mode.contains("-mismatch-") || mode.contains("-withdrawn-");
    String appId = "P".repeat(26), stepId = "T".repeat(26), interviewId = "N".repeat(26);
    String submissionId = "Q".repeat(26), clientId = "L".repeat(26);
    String ownerId = owner ? ACTOR : "B".repeat(26), interviewerId = "V".repeat(26);
    IdentityAuthorizationAdapter realAuthorization = interviewBoundaryAuthorization(wide);
    ReflectionTestUtils.setField(service, "authorization", realAuthorization);
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = new ArrayList<>();
    Application initial = application(appId, ownerId, now.minusSeconds(120));
    initial.setRecruitmentType("EXTERNAL");
    initial.setVersion(0L);
    initial.setCreateId(ownerId);
    initial.setCreateTime(now.minusSeconds(120));
    initial.setUpdateId(ownerId);
    initial.setUpdateTime(now.minusSeconds(120));
    AtomicReference<Application> storedApp = new AtomicReference<>(initial);
    AtomicReference<Application> snapshotApp = new AtomicReference<>();
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(List.of(ACTOR));
    AtomicReference<List<String>> snapshotMembers = new AtomicReference<>();
    WorkflowStep initialStep = pathStep(stepId, "INTERVIEW_ROUND_1", 1, "PENDING", true);
    initialStep.setApplicationId(appId);
    initialStep.setRoundNo(1);
    initialStep.setVersion(0L);
    AtomicReference<WorkflowStep> storedStep = new AtomicReference<>(initialStep);
    AtomicReference<WorkflowStep> snapshotStep = new AtomicReference<>();
    List<List<Object>> winnerChanges = new ArrayList<>(), reservations = new ArrayList<>();
    List<List<Object>> stepWrites = new ArrayList<>(), interviewWrites = new ArrayList<>(), history = new ArrayList<>();
    AtomicReference<Interview> ownInterview = new AtomicReference<>();
    AtomicReference<List<Participant>> ownParticipants = new AtomicReference<>(List.of());
    List<String> afterMembers = denied || owner ? List.of() : List.of(ACTOR);
    AuthorizationTarget noParticipation = new AuthorizationTarget("APPLICATION", appId, ownerId, DEPARTMENT, Set.of());
    assertThat(realAuthorization.canAccess("recruitment.manage", noParticipation)).isEqualTo(wide || owner);
    assertThat(realAuthorization.canAccess("recruitment.manage", new AuthorizationTarget(
        "APPLICATION", appId, ownerId, DEPARTMENT, Set.of(ACTOR)))).isTrue();
    Runnable commitWinner = () -> {
      assertThat(winnerChanges).isEmpty();
      assertThat(events).doesNotContain("application-lock");
      assertThat(storedApp.get().getVersion()).isZero();
      // 模型对应 owner updateCollaborators 的 activeUsers、成员替换、活动版本 CAS 与完整变更历史同提交。
      Application changed = new Application();
      BeanUtils.copyProperties(storedApp.get(), changed);
      changed.setVersion(1L);
      changed.setUpdateId(ownerId);
      changed.setUpdateTime(now.minusSeconds(60));
      storedApp.set(changed);
      storedMembers.set(List.copyOf(afterMembers));
      winnerChanges.add(List.of("K".repeat(26), appId, "[\"" + ACTOR + "\"]",
          afterMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]",
          "合规协作者配置变更", 0L, ownerId, now.minusSeconds(60)));
      assertThat(storedStep.get().getStatus()).isEqualTo("PENDING");
      assertThat(storedStep.get().getVersion()).isZero();
      events.add("winner-full-commit");
    };
    when(persistence.findApplication(appId)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      if (snapshotApp.get() == null) {
        if (beforeSnapshot) commitWinner.run();
        Application view = new Application();
        BeanUtils.copyProperties(storedApp.get(), view);
        snapshotApp.set(view);
        snapshotMembers.set(List.copyOf(storedMembers.get()));
        WorkflowStep stepView = new WorkflowStep();
        BeanUtils.copyProperties(storedStep.get(), stepView);
        snapshotStep.set(stepView);
        events.add("ordinary-snapshot");
      }
      Application view = new Application();
      BeanUtils.copyProperties(snapshotApp.get(), view);
      return view;
    });
    when(collaborators.findCollaborators(appId)).thenAnswer(call -> {
      assertThat(snapshotMembers.get()).isNotNull();
      events.add("ordinary-members");
      return List.copyOf(snapshotMembers.get());
    });
    when(persistence.lockApplication(appId)).thenAnswer(call -> {
      assertThat(winnerChanges).hasSize(1);
      assertThat(events).contains("submission-lock");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      events.add("application-lock");
      Application current = new Application();
      BeanUtils.copyProperties(storedApp.get(), current);
      return current;
    });
    when(collaborators.findCollaboratorsForUpdate(appId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock");
      events.add("current-members");
      return List.copyOf(storedMembers.get());
    });
    when(workflowSteps.findStep(appId, stepId)).thenAnswer(call -> {
      WorkflowStep visible = new WorkflowStep();
      BeanUtils.copyProperties(stepWrites.isEmpty() ? snapshotStep.get() : storedStep.get(), visible);
      events.add(stepWrites.isEmpty() ? "ordinary-step" : "ordinary-own-step");
      return visible;
    });
    when(workflowSteps.lockStep(appId, stepId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock");
      events.add("step-lock");
      WorkflowStep locked = new WorkflowStep();
      BeanUtils.copyProperties(storedStep.get(), locked);
      return locked;
    });
    when(workflowSteps.bindClientSubmission(stepId, submissionId, 0L, ACTOR, now)).thenAnswer(call -> {
      assertThat(events).contains("step-lock");
      assertThat(stepWrites).isEmpty();
      WorkflowStep written = new WorkflowStep();
      BeanUtils.copyProperties(storedStep.get(), written);
      written.setClientSubmissionId(submissionId);
      written.setVersion(1L);
      written.setUpdateId(ACTOR);
      written.setUpdateTime(now);
      storedStep.set(written);
      stepWrites.add(Arrays.asList(call.getArguments()));
      events.add("own-step-write");
      return 1;
    });
    when(interviews.countActiveByStep(stepId)).thenAnswer(call -> {
      assertThat(events).contains("ordinary-step");
      assertThat(reservations).hasSize(1);
      events.add("ordinary-interview-count");
      return 0;
    });
    when(interviews.lockScheduledByStep(stepId)).thenAnswer(call -> {
      assertThat(events).contains("submission-lock", "application-lock", "step-lock", "own-step-write");
      events.add("scheduled-lock");
      return null;
    });
    when(interviews.insertIdempotency(eq(ACTOR), eq("client-interview-current-member"), any(), any(), eq(interviewId), eq(now)))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          assertThat(reservations).isEmpty();
          reservations.add(Arrays.asList(call.getArguments()));
          events.add("own-reservation");
          return 1;
        });
    when(interviews.insertInterview(any(), any(), any(), any(), any(), any(), any(), any(), any(),
        Mockito.anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> {
          assertThat(events).contains("scheduled-lock", "ordinary-own-step");
          assertThat(interviewWrites).isEmpty();
          List<Object> row = Arrays.asList(call.getArguments());
          interviewWrites.add(row);
          Interview value = new Interview();
          value.setId((String) row.get(0)); value.setApplicationId((String) row.get(1));
          value.setCandidateId((String) row.get(2)); value.setJdId((String) row.get(3));
          value.setJdVersionId((String) row.get(4)); value.setWorkflowTemplateVersionId((String) row.get(5));
          value.setWorkflowStepId((String) row.get(6)); value.setStepCode((String) row.get(7));
          value.setInterviewType((String) row.get(8)); value.setRoundNo((Integer) row.get(9));
          value.setClientSubmissionId((String) row.get(10)); value.setScheduledStartAt((Instant) row.get(11));
          value.setScheduledEndAt((Instant) row.get(12)); value.setTimezoneId((String) row.get(13));
          value.setMeetingMode((String) row.get(14)); value.setLocationText((String) row.get(15));
          value.setMeetingUrl((String) row.get(16)); value.setCandidateNotificationChannel((String) row.get(17));
          value.setOwnerUserId((String) row.get(18)); value.setOwnerDepartmentId((String) row.get(19));
          value.setNextResponsibleUserId((String) row.get(20)); value.setCreateId((String) row.get(21));
          value.setCreateTime((Instant) row.get(22)); value.setUpdateId(value.getCreateId()); value.setUpdateTime(value.getCreateTime());
          value.setStatus("SCHEDULED"); value.setCandidateConfirmationStatus("PENDING"); value.setVersion(0L);
          ownInterview.set(value);
          events.add("own-interview-write");
          return 1;
        });
    when(interviews.findInterview(interviewId)).thenAnswer(call -> {
      assertThat(ownInterview.get()).isNotNull();
      Interview visible = new Interview();
      BeanUtils.copyProperties(ownInterview.get(), visible);
      events.add("ordinary-own-interview");
      return visible;
    });
    ParticipantPersistenceService participantRows = mock(ParticipantPersistenceService.class);
    when(participantRows.insertParticipants(interviewId, List.of(interviewerId), "INTERVIEWER", now))
        .thenAnswer(call -> {
          assertThat(events).contains("own-interview-write");
          ownParticipants.set(List.of(new Participant(interviewId, interviewerId, "INTERVIEWER", "PENDING")));
          events.add("own-participants-write");
          return true;
        });
    when(participantRows.findParticipants(interviewId)).thenAnswer(call -> {
      assertThat(events).contains("own-participants-write");
      events.add("ordinary-own-participants");
      return List.copyOf(ownParticipants.get());
    });
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(events).contains("ordinary-own-interview");
      history.add(Arrays.asList(call.getArguments()));
      events.add("own-history-write");
      return 1;
    });
    when(locks.activeLock(CANDIDATE)).thenReturn(new EngagementLockQueryService.LockSummary(
        "L".repeat(26), CANDIDATE, ACTOR, DEPARTMENT, "HR", now, now.plusSeconds(86400), true, "NONE"));
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(new JdVersionSnapshot(
        JD_VERSION, JD, DEMAND, "EXTERNAL", ownerId, DEPARTMENT, Set.of()));
    when(ids.newId()).thenReturn(interviewId, "H".repeat(26));
    SubmissionPersistenceService submissionRows = mock(SubmissionPersistenceService.class);
    ClientSubmission sent = new ClientSubmission();
    sent.setId(submissionId); sent.setApplicationId(appId); sent.setCandidateId(CANDIDATE);
    sent.setDemandId(DEMAND); sent.setClientId(mode.contains("-mismatch-") ? "X".repeat(26) : clientId);
    sent.setJdId(JD); sent.setJdVersionId(JD_VERSION); sent.setResumeVersionId(RESUME);
    sent.setDeliveryStatus(mode.contains("-withdrawn-") ? "WITHDRAWN" : "SENT");
    sent.setProcessStatus("SCREENING"); sent.setSentAt(now.minusSeconds(90));
    sent.setOwnerUserId(ownerId); sent.setOwnerDepartmentId(DEPARTMENT); sent.setVersion(0L);
    if (mode.contains("-withdrawn-")) {
      sent.setWithdrawnAt(now.minusSeconds(30)); sent.setWithdrawnByUserId(ownerId);
      sent.setWithdrawalReason("甲方推荐已撤回");
    }
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      events.add(events.contains("application-lock") ? "current-demand-matching" : "early-demand-matching");
      return new DemandQueryService.SubmissionDemandContext(DEMAND, clientId, null, null, "EXTERNAL", ownerId, DEPARTMENT);
    });
    when(submissionRows.lock(submissionId)).thenAnswer(call -> {
      assertThat(events).contains("ordinary-snapshot", "ordinary-step", "ordinary-interview-count", "early-demand-matching");
      assertThat(events).doesNotContain("application-lock");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
      events.add("submission-lock");
      // updateCollaborators 不锁推荐；T1 持推荐锁且尚未持活动锁时，owner 可合法提交成员变更。
      if (!beforeSnapshot) commitWinner.run();
      ClientSubmission current = new ClientSubmission(); BeanUtils.copyProperties(sent, current);
      return current;
    });
    when(submissionRows.find(submissionId)).thenAnswer(call -> {
      assertThat(events).contains("submission-lock", "application-lock", "step-lock", "current-demand-matching");
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      events.add("ordinary-second-submission-matching");
      ClientSubmission visible = new ClientSubmission(); BeanUtils.copyProperties(sent, visible);
      return visible;
    });
    RecruitmentApplicationManager applicationBoundary = commandProxy(transactions);
    ClientSubmissionValidationAdapter matchingTarget = new ClientSubmissionValidationAdapter();
    ReflectionTestUtils.setField(matchingTarget, "persistence", submissionRows);
    ClientSubmissionValidationAdapter matchingBoundary = joinedClientInterviewProxy(matchingTarget, transactions);
    ReflectionTestUtils.setField(service, "submissionValidator", matchingBoundary);
    ClientSubmissionInterviewValidationAdapter validationTarget = new ClientSubmissionInterviewValidationAdapter();
    ReflectionTestUtils.setField(validationTarget, "applications", applicationBoundary);
    ReflectionTestUtils.setField(validationTarget, "demands", demands);
    ReflectionTestUtils.setField(validationTarget, "submissions", matchingBoundary);
    ClientSubmissionInterviewValidationAdapter validationBoundary = joinedClientInterviewProxy(validationTarget, transactions);
    InterviewApplicationManager interviewManager = new InterviewApplicationManager();
    ReflectionTestUtils.setField(interviewManager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(interviewManager, "persistence", interviews);
    ReflectionTestUtils.setField(interviewManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(interviews.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(interviewManager, "interviewIdempotencyPersistenceService", mock(InterviewIdempotencyPersistenceService.class));
    ReflectionTestUtils.setField(interviewManager, "participantPersistenceService", participantRows);
    ReflectionTestUtils.setField(interviewManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(interviewManager, "directory", directory);
    ReflectionTestUtils.setField(interviewManager, "applications", applicationBoundary);
    ReflectionTestUtils.setField(interviewManager, "submissionValidator", validationBoundary);
    ReflectionTestUtils.setField(interviewManager, "demands", demands);
    ReflectionTestUtils.setField(interviewManager, "engagementLocks", locks);
    ReflectionTestUtils.setField(interviewManager, "ids", ids);
    ReflectionTestUtils.setField(interviewManager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    InterviewApplicationManager publicEntry = joinedClientInterviewProxy(interviewManager, transactions);
    CreateInterviewDTO command = new CreateInterviewDTO(appId, stepId, now.plusSeconds(3600),
        now.plusSeconds(7200), "Asia/Shanghai", "ONLINE", null, "https://meeting.example/1",
        List.of(interviewerId), "NONE", submissionId, "安排甲方面试");
    if (denied || invalid) {
      String expected = invalid ? "CLIENT_SUBMISSION_MISMATCH"
          : wide ? "RECRUITMENT_PROCESSING_FORBIDDEN" : "DATA_SCOPE_FORBIDDEN";
      assertThatThrownBy(() -> publicEntry.create(command, "client-interview-current-member"))
          .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(expected));
      assertThat(stepWrites).isEmpty(); assertThat(interviewWrites).isEmpty(); assertThat(history).isEmpty();
      assertThat(ownParticipants.get()).isEmpty(); assertThat(ownInterview.get()).isNull();
      verify(locks, never()).recordProgress(any(), any(), any());
      verify(workflowSteps, never()).lockStep(any(), any());
      verify(workflowSteps, never()).bindClientSubmission(any(), any(), anyLong(), any(), any());
      if (invalid) verify(persistence, never()).lockApplication(any());
      verify(submissionRows, never()).find(any());
      assertThat(reservations).hasSize(beforeSnapshot && !wide ? 0 : 1);
      assertCommandTransaction(transactions, false);
    } else {
      var response = publicEntry.create(command, "client-interview-current-member");
      assertThat(response.getInterview().getId()).isEqualTo(interviewId);
      assertThat(response.getInterview().getApplicationId()).isEqualTo(appId);
      assertThat(response.getInterview().getWorkflowStepId()).isEqualTo(stepId);
      assertThat(response.getInterview().getStatus()).isEqualTo("SCHEDULED");
      assertThat(response.getInterview().getInterviewType()).isEqualTo("EXTERNAL_CLIENT");
      assertThat(response.getInterview().getClientSubmissionId()).isEqualTo(submissionId);
      assertThat(response.getInterview().getOwnerUserId()).isEqualTo(ownerId);
      assertThat(response.getInterview().getOwnerDepartmentId()).isEqualTo(DEPARTMENT);
      assertThat(response.getInterview().getNextResponsibleUserId()).isEqualTo(ownerId);
      assertThat(response.getInterview().getVersion()).isEqualTo("0");
      assertThat(response.getInterview().getScheduledStartAt()).isEqualTo(command.getStartAt());
      assertThat(response.getInterview().getScheduledEndAt()).isEqualTo(command.getEndAt());
      assertThat(response.getInterview().getCreateId()).isEqualTo(ACTOR);
      assertThat(response.getInterview().getCreateTime()).isEqualTo(now);
      assertThat(response.getParticipants()).singleElement().satisfies(participant -> {
        assertThat(participant.getUserId()).isEqualTo(interviewerId);
        assertThat(participant.getParticipantRole()).isEqualTo("INTERVIEWER");
        assertThat(participant.getResponseStatus()).isEqualTo("PENDING");
      });
      assertThat(stepWrites).containsExactly(List.of(stepId, submissionId, 0L, ACTOR, now));
      assertThat(storedStep.get().getStatus()).isEqualTo("PENDING");
      assertThat(storedStep.get().getClientSubmissionId()).isEqualTo(submissionId);
      assertThat(storedStep.get().getVersion()).isEqualTo(1L);
      assertThat(interviewWrites).hasSize(1); assertThat(history).hasSize(1); assertThat(reservations).hasSize(1);
      assertThat(history.getFirst().subList(1, 4)).containsExactly(interviewId, "CREATE", null);
      Map<String, String> afterSnapshot = new ObjectMapper().readValue(
          history.getFirst().get(4).toString(), new TypeReference<Map<String, String>>() {});
      assertThat(afterSnapshot).containsExactlyInAnyOrderEntriesOf(Map.of(
          "status", "SCHEDULED", "interviewType", "EXTERNAL_CLIENT",
          "startAt", command.getStartAt().toString(), "endAt", command.getEndAt().toString(),
          "timezone", "Asia/Shanghai", "mode", "ONLINE"));
      assertThat(history.getFirst().subList(5, 8)).containsExactly("安排甲方面试", ACTOR, now);
      verify(locks).recordProgress(CANDIDATE, "INTERVIEW_SCHEDULED", "已安排面试");
      verify(demands).requireJdVersion(JD_VERSION);
      verify(submissionRows).lock(submissionId);
      verify(submissionRows).find(submissionId);
      assertThat(events.indexOf("submission-lock")).isLessThan(events.indexOf("winner-full-commit"));
      assertThat(events.indexOf("winner-full-commit")).isLessThan(events.indexOf("application-lock"));
      assertThat(events.indexOf("ordinary-second-submission-matching")).isLessThan(events.indexOf("own-step-write"));
      assertThat(interviewWrites.getFirst().get(10)).isEqualTo(submissionId);
      verify(directory).activeUsers(Set.of(interviewerId));
      assertCommandTransaction(transactions, true);
    }
    verify(workflowSteps, never()).startStep(any(), anyLong(), any(), any());
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(winnerChanges).hasSize(1);
    assertThat(storedApp.get().getVersion()).isEqualTo(1L);
    assertThat(storedApp.get().getOwnerUserId()).isEqualTo(ownerId);
    assertThat(storedApp.get().getUpdateId()).isEqualTo(ownerId);
    assertThat(storedApp.get().getUpdateTime()).isEqualTo(now.minusSeconds(60));
    assertThat(storedMembers.get()).isEqualTo(afterMembers);
    assertThat(snapshotMembers.get()).isEqualTo(beforeSnapshot ? afterMembers : List.of(ACTOR));
    assertThat(snapshotApp.get().getVersion()).isEqualTo(beforeSnapshot ? 1L : 0L);
    assertThat(events.indexOf("winner-full-commit")).isLessThan(events.indexOf("application-lock") < 0
        ? events.size() : events.indexOf("application-lock"));
  }

  @ParameterizedTest
  @CsvSource({"false,true,DATA_SCOPE_FORBIDDEN", "true,true,APPLICATION_TERMINATED",
      "true,false,RECRUITMENT_PROCESSING_FORBIDDEN"})
  void clientInterviewBindingKeepsViewClosureAndProcessingErrorOrder(boolean wide, boolean terminal, String code) {
    String appId = "P".repeat(26), stepId = "T".repeat(26);
    Application app = application(appId, "B".repeat(26), now);
    if (terminal) app.setTerminalAt(now);
    when(persistence.lockApplication(appId)).thenReturn(app);
    when(collaborators.findCollaborators(appId)).thenReturn(List.of());
    when(collaborators.findCollaboratorsForUpdate(appId)).thenReturn(List.of());
    ReflectionTestUtils.setField(service, "authorization", interviewBoundaryAuthorization(wide));
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    assertThatThrownBy(() -> commandProxy(transactions).bindClientInterviewStep(appId, stepId, "Q".repeat(26)))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(code));
    verify(workflowSteps, never()).lockStep(any(), any());
    verifyNoInteractions(interviews);
    assertCommandTransaction(transactions, false);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
  }

  @SuppressWarnings("unchecked")
  // 普通面试读取建立全局读视图，再模拟合法owner成员配置提交；非真实MySQL/并发T2/物理回滚。
  @ParameterizedTest
  @ValueSource(strings = {"internal-wide-revoked-after", "client-wide-revoked-after",
      "internal-wide-active-after", "client-wide-active-after",
      "internal-narrow-owner-after", "client-narrow-owner-after",
      "internal-wide-revoked-before", "client-wide-revoked-before"})
  void publicInterviewCancelRechecksCommittedParticipantsInJoinedTransaction(String mode) throws Exception {
    boolean client = mode.startsWith("client-"), wide = mode.contains("-wide-");
    boolean owner = mode.contains("-owner-"), denied = mode.contains("-revoked-");
    boolean beforeSnapshot = mode.endsWith("-before");
    String appId = "P".repeat(26), interviewId = "N".repeat(26), stepId = "T".repeat(26);
    String ownerId = owner ? ACTOR : "B".repeat(26), participantId = "V".repeat(26);
    String type = client ? "EXTERNAL_CLIENT" : "INTERNAL", key = "cancel-current-member";
    IdentityAuthorizationAdapter realAuthorization = interviewBoundaryAuthorization(wide);
    ReflectionTestUtils.setField(service, "authorization", realAuthorization);
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = new ArrayList<>();
    List<List<Object>> winnerChanges = new ArrayList<>(), reservations = new ArrayList<>();
    List<List<Object>> cancellations = new ArrayList<>(), history = new ArrayList<>(), calendar = new ArrayList<>();
    Application initialApp = application(appId, ownerId, now.minusSeconds(300));
    initialApp.setRecruitmentType(client ? "EXTERNAL" : "INTERNAL");
    initialApp.setVersion(0L); initialApp.setCreateId(ownerId); initialApp.setUpdateId(ownerId);
    initialApp.setCreateTime(now.minusSeconds(300)); initialApp.setUpdateTime(now.minusSeconds(300));
    AtomicReference<Application> storedApp = new AtomicReference<>(initialApp);
    AtomicReference<Application> snapshotApp = new AtomicReference<>();
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(List.of(ACTOR));
    AtomicReference<List<String>> snapshotMembers = new AtomicReference<>();
    List<String> afterMembers = denied || owner ? List.of() : List.of(ACTOR);
    Interview initialInterview = new Interview();
    initialInterview.setId(interviewId); initialInterview.setApplicationId(appId);
    initialInterview.setCandidateId(CANDIDATE); initialInterview.setJdId(JD);
    initialInterview.setJdVersionId(JD_VERSION); initialInterview.setWorkflowTemplateVersionId(initialApp.getWorkflowTemplateVersionId());
    initialInterview.setWorkflowStepId(stepId); initialInterview.setStepCode(client ? "CLIENT_INTERVIEW" : "INTERVIEW_ROUND_1");
    initialInterview.setRoundNo(1); initialInterview.setInterviewType(type);
    initialInterview.setClientSubmissionId(client ? "Q".repeat(26) : null);
    initialInterview.setStatus("SCHEDULED"); initialInterview.setVersion(0L);
    initialInterview.setScheduledStartAt(now.plusSeconds(3600)); initialInterview.setScheduledEndAt(now.plusSeconds(7200));
    initialInterview.setTimezoneId("Asia/Shanghai"); initialInterview.setMeetingMode("ONLINE");
    initialInterview.setMeetingUrl("https://meeting.example/1"); initialInterview.setCandidateNotificationChannel("NONE");
    initialInterview.setOwnerUserId(ownerId); initialInterview.setOwnerDepartmentId(DEPARTMENT);
    initialInterview.setNextResponsibleUserId(ownerId); initialInterview.setCreateId(ownerId);
    initialInterview.setUpdateId(ownerId); initialInterview.setCreateTime(now.minusSeconds(300));
    initialInterview.setUpdateTime(now.minusSeconds(300));
    AtomicReference<Interview> storedInterview = new AtomicReference<>(initialInterview);
    Participant person = new Participant(interviewId, participantId, "INTERVIEWER", "PENDING");
    List<Participant> people = List.of(person);
    assertThat(realAuthorization.canAccess("interview.schedule", new AuthorizationTarget(
        "INTERVIEW", interviewId, ownerId, DEPARTMENT, Set.of()))).isTrue();
    assertThat(realAuthorization.canAccess("recruitment.manage", new AuthorizationTarget(
        "APPLICATION", appId, ownerId, DEPARTMENT, Set.of()))).isEqualTo(wide || owner);
    Runnable commitWinner = () -> {
      assertThat(winnerChanges).isEmpty(); assertThat(events).doesNotContain("application-lock", "interview-lock");
      // 对应updateCollaborators的activeUsers、成员替换、活动版本CAS和完整8参数历史；只记录模型同提交事实。
      Application changed = new Application(); BeanUtils.copyProperties(storedApp.get(), changed);
      changed.setVersion(1L); changed.setUpdateId(ownerId); changed.setUpdateTime(now.minusSeconds(60));
      storedApp.set(changed); storedMembers.set(List.copyOf(afterMembers));
      winnerChanges.add(List.of("K".repeat(26), appId, "[\"" + ACTOR + "\"]",
          afterMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]",
          "合规协作者配置变更", 0L, ownerId, now.minusSeconds(60)));
      events.add("winner-full-commit");
    };
    when(interviews.findInterview(interviewId)).thenAnswer(call -> {
      if (snapshotMembers.get() == null) {
        if (beforeSnapshot) commitWinner.run();
        Application view = new Application(); BeanUtils.copyProperties(storedApp.get(), view);
        snapshotApp.set(view); snapshotMembers.set(List.copyOf(storedMembers.get()));
        events.add("ordinary-interview-view");
      }
      Interview view = new Interview(); BeanUtils.copyProperties(storedInterview.get(), view);
      return view; // 本事务自己的CAS取消可见，不复用早期Interview对象。
    });
    when(persistence.lockApplication(appId)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(events).contains("ordinary-interview-view");
      if (winnerChanges.isEmpty()) commitWinner.run();
      events.add("application-lock");
      return storedApp.get();
    });
    when(collaborators.findCollaborators(appId)).thenAnswer(call -> {
      assertThat(snapshotMembers.get()).isNotNull(); events.add("ordinary-members");
      return List.copyOf(snapshotMembers.get());
    });
    when(collaborators.findCollaboratorsForUpdate(appId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock"); events.add("current-members");
      return List.copyOf(storedMembers.get());
    });
    when(interviews.lockInterview(interviewId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock"); events.add("interview-lock");
      Interview locked = new Interview(); BeanUtils.copyProperties(storedInterview.get(), locked); return locked;
    });
    when(interviews.insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(interviewId), eq(now))).thenAnswer(call -> {
      assertThat(events).contains("interview-lock"); assertThat(reservations).isEmpty();
      reservations.add(Arrays.asList(call.getArguments())); events.add("own-reservation"); return 1;
    });
    when(interviews.cancel(interviewId, 0L, ACTOR, now)).thenAnswer(call -> {
      assertThat(events).contains("own-reservation"); assertThat(cancellations).isEmpty();
      Interview old = storedInterview.get(); assertThat(old.getVersion()).isZero(); assertThat(old.getStatus()).isEqualTo("SCHEDULED");
      Interview cancelled = new Interview(); BeanUtils.copyProperties(old, cancelled);
      cancelled.setStatus("CANCELLED"); cancelled.setVersion(1L); cancelled.setUpdateId(ACTOR); cancelled.setUpdateTime(now);
      storedInterview.set(cancelled); cancellations.add(Arrays.asList(call.getArguments()));
      events.add("own-cancel"); return 1;
    });
    when(interviews.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(events).contains("own-cancel"); assertThat(history).isEmpty();
      history.add(Arrays.asList(call.getArguments())); events.add("own-history"); return 1;
    });
    ParticipantPersistenceService participantRows = mock(ParticipantPersistenceService.class);
    when(participantRows.findParticipants(interviewId)).thenReturn(people);
    InterviewExternalCalendarApplicationManager calendars = mock(InterviewExternalCalendarApplicationManager.class);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("own-history"); calendar.add(Arrays.asList(call.getArguments()));
      events.add("calendar-cancel"); return null;
    }).when(calendars).cancel(interviewId, ACTOR);
    JdVersionSnapshot jdSnapshot = new JdVersionSnapshot(JD_VERSION, JD, DEMAND,
        client ? "EXTERNAL" : "INTERNAL", ownerId, DEPARTMENT, Set.of());
    assertThat(initialApp.getRecruitmentType()).isEqualTo(client ? "EXTERNAL" : "INTERNAL");
    assertThat(jdSnapshot.getRecruitmentType()).isEqualTo(initialApp.getRecruitmentType());
    jdSnapshot.setClientId(client ? "L".repeat(26) : null);
    jdSnapshot.setClientName(client ? "既有甲方" : null);
    when(demands.requireJdVersion(JD_VERSION)).thenReturn(jdSnapshot);
    when(ids.newId()).thenReturn("H".repeat(26));
    InterviewApplicationManager interviewManager = new InterviewApplicationManager();
    ReflectionTestUtils.setField(interviewManager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(interviewManager, "persistence", interviews);
    ReflectionTestUtils.setField(interviewManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(interviews.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(interviewManager, "interviewIdempotencyPersistenceService", mock(InterviewIdempotencyPersistenceService.class));
    ReflectionTestUtils.setField(interviewManager, "participantPersistenceService", participantRows);
    ReflectionTestUtils.setField(interviewManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(interviewManager, "applications", joinedClientInterviewProxy(service, transactions));
    ReflectionTestUtils.setField(interviewManager, "externalCalendars", calendars);
    ReflectionTestUtils.setField(interviewManager, "demands", demands);
    ReflectionTestUtils.setField(interviewManager, "ids", ids);
    ReflectionTestUtils.setField(interviewManager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    InterviewApplicationManager publicEntry = joinedClientInterviewProxy(interviewManager, transactions);
    if (denied) {
      assertThatThrownBy(() -> publicEntry.cancelResponse(interviewId, 0L, "取消安排", key))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
      assertThat(reservations).isEmpty(); assertThat(cancellations).isEmpty(); assertThat(history).isEmpty(); assertThat(calendar).isEmpty();
      verify(interviews, never()).lockInterview(any());
      verify(interviews, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
      verify(interviews, never()).cancel(any(), anyLong(), any(), any());
      verify(interviews, never()).insertHistory(any(), any(), any(), any(), any(), any(), any(), any());
      verifyNoInteractions(calendars, participantRows);
      assertThat(storedInterview.get()).isSameAs(initialInterview);
      assertCommandTransaction(transactions, false);
    } else {
      var response = publicEntry.cancelResponse(interviewId, 0L, "取消安排", key);
      assertThat(response.getInterview().getId()).isEqualTo(interviewId);
      assertThat(response.getInterview().getApplicationId()).isEqualTo(appId);
      assertThat(response.getInterview().getStatus()).isEqualTo("CANCELLED");
      assertThat(response.getInterview().getVersion()).isEqualTo("1");
      assertThat(response.getInterview().getInterviewType()).isEqualTo(type);
      assertThat(response.getInterview().getClientId()).isEqualTo(client ? "L".repeat(26) : null);
      assertThat(response.getInterview().getClientName()).isEqualTo(client ? "既有甲方" : null);
      assertThat(response.getInterview().getClientSubmissionId()).isEqualTo(client ? "Q".repeat(26) : null);
      assertThat(response.getInterview().getOwnerUserId()).isEqualTo(ownerId);
      assertThat(response.getInterview().getOwnerDepartmentId()).isEqualTo(DEPARTMENT);
      assertThat(response.getInterview().getNextResponsibleUserId()).isEqualTo(ownerId);
      assertThat(response.getInterview().getCreateId()).isEqualTo(ownerId);
      assertThat(response.getInterview().getCreateTime()).isEqualTo(now.minusSeconds(300));
      assertThat(response.getInterview().getUpdateId()).isEqualTo(ACTOR);
      assertThat(response.getInterview().getUpdateTime()).isEqualTo(now);
      assertThat(response.getInterview().getScheduledStartAt()).isEqualTo(initialInterview.getScheduledStartAt());
      assertThat(response.getInterview().getScheduledEndAt()).isEqualTo(initialInterview.getScheduledEndAt());
      assertThat(response.getParticipants()).singleElement().satisfies(p -> {
        assertThat(p.getUserId()).isEqualTo(participantId); assertThat(p.getParticipantRole()).isEqualTo("INTERVIEWER");
        assertThat(p.getResponseStatus()).isEqualTo("PENDING");
      });
      assertThat(reservations).singleElement().satisfies(row -> {
        assertThat(row.subList(0, 3)).containsExactly(ACTOR, key, "INTERVIEW_CANCEL");
        assertThat(row.subList(4, 6)).containsExactly(interviewId, now);
      });
      assertThat(cancellations).containsExactly(List.of(interviewId, 0L, ACTOR, now));
      assertThat(history).singleElement().satisfies(row -> {
        assertThat(row.subList(0, 3)).containsExactly("H".repeat(26), interviewId, "CANCEL");
        assertThat(row.subList(5, 8)).containsExactly("取消安排", ACTOR, now);
      });
      Map<String, String> expected = Map.of("status", "SCHEDULED", "interviewType", type,
          "startAt", initialInterview.getScheduledStartAt().toString(), "endAt", initialInterview.getScheduledEndAt().toString(),
          "timezone", "Asia/Shanghai", "mode", "ONLINE");
      Map<String, String> before = new ObjectMapper().readValue(history.getFirst().get(3).toString(), new TypeReference<Map<String, String>>() {});
      Map<String, String> after = new ObjectMapper().readValue(history.getFirst().get(4).toString(), new TypeReference<Map<String, String>>() {});
      assertThat(before).containsExactlyInAnyOrderEntriesOf(expected);
      Map<String, String> cancelledSnapshot = new LinkedHashMap<>(expected);
      cancelledSnapshot.put("status", "CANCELLED"); assertThat(after).containsExactlyInAnyOrderEntriesOf(cancelledSnapshot);
      assertThat(calendar).containsExactly(List.of(interviewId, ACTOR));
      verify(demands).requireJdVersion(JD_VERSION);
      assertThat(storedInterview.get().getVersion()).isEqualTo(1L);
      assertCommandTransaction(transactions, true);
    }
    // 旧成功对照不强求尚未引入的当前读取结构；新的锁读一旦出现，单次且普通读不可混用。
    if (events.contains("current-members")) {
      assertThat(events.stream().filter("current-members"::equals).count()).isOne();
      assertThat(events).doesNotContain("ordinary-members");
    }
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(winnerChanges).containsExactly(List.of("K".repeat(26), appId, "[\"" + ACTOR + "\"]",
        afterMembers.isEmpty() ? "[]" : "[\"" + ACTOR + "\"]",
        "合规协作者配置变更", 0L, ownerId, now.minusSeconds(60)));
    assertThat(storedApp.get().getRecruitmentType()).isEqualTo(initialApp.getRecruitmentType());
    assertThat(storedApp.get().getOwnerUserId()).isEqualTo(ownerId);
    assertThat(storedApp.get().getOwnerDepartmentId()).isEqualTo(DEPARTMENT);
    assertThat(storedApp.get().getVersion()).isEqualTo(1L);
    assertThat(storedApp.get().getUpdateId()).isEqualTo(ownerId);
    assertThat(storedApp.get().getUpdateTime()).isEqualTo(now.minusSeconds(60));
    assertThat(storedMembers.get()).isEqualTo(afterMembers);
    assertThat(snapshotMembers.get()).isEqualTo(beforeSnapshot ? afterMembers : List.of(ACTOR));
    assertThat(snapshotApp.get().getVersion()).isEqualTo(beforeSnapshot ? 1L : 0L);
    assertThat(events.indexOf("winner-full-commit")).isLessThan(events.indexOf("application-lock"));
    assertThat(events.indexOf("ordinary-interview-view")).isLessThan(events.indexOf("application-lock"));
    verify(workflowSteps, never()).lockStep(any(), any());
    verify(workflowSteps, never()).startStep(any(), anyLong(), any(), any());
    verify(workflowSteps, never()).bindClientSubmission(any(), any(), anyLong(), any(), any());
    verifyNoInteractions(locks);
  }

  @Test
  void cancellationHelperKeepsTerminalOwnerAccessWithoutOpeningTheApplication() {
    String appId = "P".repeat(26);
    Application terminal = application(appId, ACTOR, now.minusSeconds(300));
    terminal.setCurrentStage("TERMINATED"); terminal.setTerminalAt(now.minusSeconds(60)); terminal.setVersion(2L);
    ReflectionTestUtils.setField(service, "authorization", interviewBoundaryAuthorization(false));
    when(persistence.lockApplication(appId)).thenReturn(terminal);
    when(collaborators.findCollaborators(appId)).thenReturn(List.of());
    when(collaborators.findCollaboratorsForUpdate(appId)).thenReturn(List.of());
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    assertThat(commandProxy(transactions).requireLockedFollowUpCancellation(appId)).isSameAs(terminal);
    assertThat(terminal.getCurrentStage()).isEqualTo("TERMINATED");
    assertThat(terminal.getTerminalAt()).isEqualTo(now.minusSeconds(60)); assertThat(terminal.getVersion()).isEqualTo(2L);
    assertCommandTransaction(transactions, true);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    verifyNoInteractions(workflowSteps, interviews, locks);
  }

  @SuppressWarnings("unchecked")
  // 真实代理加入默认事务；合成读视图与合法成员配置提交模型不代表真实MySQL/并发T2/物理回滚。
  @ParameterizedTest
  @ValueSource(strings = {"internal-wide-revoked-after", "external-wide-revoked-after",
      "internal-wide-active-after", "external-wide-active-after",
      "internal-narrow-owner-after", "external-narrow-owner-after",
      "internal-wide-revoked-before", "external-wide-revoked-before"})
  void publicFeedbackRechecksCommittedParticipantsInJoinedTransaction(String mode) throws Exception {
    boolean external = mode.startsWith("external-"), wide = mode.contains("-wide-");
    boolean owner = mode.contains("-owner-"), denied = mode.contains("-revoked-");
    boolean beforeSnapshot = mode.endsWith("-before");
    String appId = "P".repeat(26), interviewId = "N".repeat(26), stepId = "T".repeat(26);
    String ownerId = owner ? ACTOR : "B".repeat(26), type = external ? "EXTERNAL" : "INTERNAL";
    String feedbackId = "F".repeat(26), versionId = "V".repeat(26), originId = "X".repeat(26);
    String key = "public-feedback-current-members", operation = "cbcc7d89-94f3-4a32-a154-af5068769231";
    Instant scheduledAt = now.minusSeconds(14L * 86400 + 60), occurred = now.minusSeconds(60);
    IdentityAuthorizationAdapter realAuthorization = interviewBoundaryAuthorization(wide);
    ReflectionTestUtils.setField(service, "authorization", realAuthorization);
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = new ArrayList<>();
    List<List<Object>> winnerChanges = new ArrayList<>(), reservations = new ArrayList<>();
    List<List<Object>> feedbackWrites = new ArrayList<>(), versionWrites = new ArrayList<>(), completions = new ArrayList<>();
    List<ActivityOriginEntity> origins = new ArrayList<>();
    Application initialApp = application(appId, ownerId, now.minusSeconds(30L * 86400));
    initialApp.setRecruitmentType("INTERNAL"); initialApp.setCurrentStage("IN_PROCESS");
    initialApp.setVersion(0L); initialApp.setCreateId(ownerId); initialApp.setUpdateId(ownerId);
    initialApp.setCreateTime(now.minusSeconds(30L * 86400)); initialApp.setUpdateTime(initialApp.getCreateTime());
    List<String> beforeMembers = owner ? List.of("C".repeat(26)) : List.of(ACTOR);
    List<String> afterMembers = denied || owner ? List.of() : List.of(ACTOR);
    AtomicReference<Application> storedApp = new AtomicReference<>(initialApp), snapshotApp = new AtomicReference<>();
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(beforeMembers), snapshotMembers = new AtomicReference<>();
    Interview initialInterview = new Interview();
    initialInterview.setId(interviewId); initialInterview.setApplicationId(appId); initialInterview.setCandidateId(CANDIDATE);
    initialInterview.setJdId(JD); initialInterview.setJdVersionId(JD_VERSION);
    initialInterview.setWorkflowTemplateVersionId(initialApp.getWorkflowTemplateVersionId());
    initialInterview.setWorkflowStepId(stepId); initialInterview.setStepCode("INTERVIEW_ROUND_1");
    initialInterview.setRoundNo(1); initialInterview.setInterviewType(type); initialInterview.setStatus("SCHEDULED");
    initialInterview.setVersion(0L); initialInterview.setScheduledStartAt(now.minusSeconds(120));
    initialInterview.setScheduledEndAt(occurred); initialInterview.setTimezoneId("Asia/Shanghai");
    initialInterview.setMeetingMode("ONLINE"); initialInterview.setMeetingUrl("https://meeting.example/feedback");
    initialInterview.setCandidateNotificationChannel("NONE"); initialInterview.setOwnerUserId(ownerId);
    initialInterview.setOwnerDepartmentId(DEPARTMENT); initialInterview.setNextResponsibleUserId(ownerId);
    initialInterview.setCreateId(ownerId); initialInterview.setUpdateId(ownerId);
    initialInterview.setCreateTime(scheduledAt); initialInterview.setUpdateTime(scheduledAt);
    AtomicReference<Interview> storedInterview = new AtomicReference<>(initialInterview);
    AtomicReference<Feedback> storedFeedback = new AtomicReference<>();
    AtomicReference<FeedbackVersion> storedVersion = new AtomicReference<>();
    CurrentActorSnapshot configuringOwner = new CurrentActorSnapshot(ownerId, "负责人", "owner@example.com", DEPARTMENT,
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("recruitment.manage"), false, true);
    assertThat(realAuthorization.canAccess(configuringOwner, "recruitment.manage", new AuthorizationTarget(
        "APPLICATION", appId, ownerId, DEPARTMENT, Set.copyOf(beforeMembers)))).isTrue();
    Runnable commitWinner = () -> {
      assertThat(winnerChanges).isEmpty(); assertThat(events).doesNotContain("application-lock", "interview-lock");
      // updateCollaborators合法activeUsers/成员替换/App版本CAS/8参数审计对应的同提交事实模型。
      Application changed = new Application(); BeanUtils.copyProperties(storedApp.get(), changed);
      changed.setVersion(1L); changed.setUpdateId(ownerId); changed.setUpdateTime(now);
      storedApp.set(changed); storedMembers.set(List.copyOf(afterMembers));
      winnerChanges.add(Arrays.asList("K".repeat(26), appId, JsonText.write(beforeMembers), JsonText.write(afterMembers),
          "合规协作者配置变更", 0L, ownerId, now)); events.add("winner-full-commit");
    };
    when(interviews.findInterview(interviewId)).thenAnswer(call -> {
      if (snapshotMembers.get() == null) {
        if (beforeSnapshot) commitWinner.run();
        Application observed = new Application(); BeanUtils.copyProperties(storedApp.get(), observed);
        snapshotApp.set(observed); snapshotMembers.set(List.copyOf(storedMembers.get())); events.add("ordinary-interview-view");
      }
      Interview observed = new Interview(); BeanUtils.copyProperties(storedInterview.get(), observed); return observed;
    });
    when(persistence.lockApplication(appId)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(events).contains("ordinary-interview-view"); if (winnerChanges.isEmpty()) commitWinner.run();
      events.add("application-lock"); return storedApp.get();
    });
    when(collaborators.findCollaborators(appId)).thenAnswer(call -> {
      assertThat(snapshotMembers.get()).isNotNull(); events.add("ordinary-members"); return List.copyOf(snapshotMembers.get());
    });
    when(collaborators.findCollaboratorsForUpdate(appId)).thenAnswer(call -> {
      assertThat(events).contains("interview-lock"); events.add("current-members"); return List.copyOf(storedMembers.get());
    });
    when(interviews.lockInterview(interviewId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock"); events.add("interview-lock");
      Interview locked = new Interview(); BeanUtils.copyProperties(storedInterview.get(), locked); return locked;
    });
    InterviewFeedbackPersistenceService feedbackRows = mock(InterviewFeedbackPersistenceService.class);
    FeedbackVersionPersistenceService versionRows = mock(FeedbackVersionPersistenceService.class);
    when(feedbackRows.findFeedbackByAuthor(interviewId, ACTOR, "INTERNAL")).thenAnswer(call -> {
      events.add(storedFeedback.get() == null ? "existing-feedback-absent" : "own-feedback-read"); return storedFeedback.get();
    });
    when(interviews.insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(feedbackId), eq(now))).thenAnswer(call -> {
      assertThat(events).contains("interview-lock", "existing-feedback-absent");
      assertThat(reservations).isEmpty(); reservations.add(Arrays.asList(call.getArguments())); events.add("own-reservation"); return 1;
    });
    when(feedbackRows.insertFeedback(feedbackId, interviewId, ACTOR, "INTERNAL", "SUBMITTED", now)).thenAnswer(call -> {
      assertThat(events).contains("own-reservation"); assertThat(storedFeedback.get()).isNull();
      Feedback saved = new Feedback(feedbackId, interviewId, ACTOR, "INTERNAL", "SUBMITTED", 1);
      saved.setCreateId(ACTOR); saved.setUpdateId(ACTOR); saved.setCreateTime(now); saved.setUpdateTime(now);
      storedFeedback.set(saved); feedbackWrites.add(Arrays.asList(call.getArguments())); events.add("own-feedback-insert"); return 1;
    });
    ActivityOriginPersistenceService originRows = mock(ActivityOriginPersistenceService.class);
    when(originRows.save(any(ActivityOriginEntity.class))).thenAnswer(call -> {
      assertThat(events).contains("own-feedback-insert"); assertThat(versionWrites).isEmpty();
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      origins.add(call.getArgument(0)); events.add("own-origin"); return true;
    });
    when(versionRows.insertFeedbackVersion(eq(versionId), eq(feedbackId), eq(1), eq("PASS"), eq("技术基础扎实"),
        isNull(), eq(occurred), eq("正式提交"), eq(ACTOR), eq(now))).thenAnswer(call -> {
      assertThat(events).contains("own-origin"); assertThat(storedVersion.get()).isNull();
      FeedbackVersion saved = new FeedbackVersion(versionId, feedbackId, 1, "PASS", "技术基础扎实", null, occurred, "正式提交");
      saved.setCreateId(ACTOR); saved.setCreateTime(now); storedVersion.set(saved);
      versionWrites.add(Arrays.asList(call.getArguments())); events.add("own-feedback-version"); return 1;
    });
    when(interviews.complete(interviewId, ACTOR, now)).thenAnswer(call -> {
      assertThat(events).contains("own-feedback-version", "own-feedback-read"); assertThat(completions).isEmpty();
      Interview prior = storedInterview.get(); assertThat(prior.getStatus()).isEqualTo("SCHEDULED"); assertThat(prior.getVersion()).isZero();
      Interview completed = new Interview(); BeanUtils.copyProperties(prior, completed);
      completed.setStatus("COMPLETED"); completed.setVersion(1L); completed.setUpdateId(ACTOR); completed.setUpdateTime(now);
      storedInterview.set(completed); completions.add(Arrays.asList(call.getArguments())); events.add("own-interview-complete"); return 1;
    });
    when(versionRows.findFeedbackVersion(feedbackId, 1)).thenAnswer(call -> {
      assertThat(events).contains("own-interview-complete", "expired-engagement-read"); events.add("own-version-read"); return storedVersion.get();
    });
    EngagementLock expiredLock = new EngagementLock("L".repeat(26), CANDIDATE, ownerId, DEPARTMENT, "负责人", "ACTIVE",
        scheduledAt.minusSeconds(86400), scheduledAt.plusSeconds(14L * 86400), "INTERVIEW_SCHEDULED", 1L);
    expiredLock.setCreateId(ownerId); expiredLock.setUpdateId(ownerId);
    expiredLock.setCreateTime(expiredLock.getAcquiredAt()); expiredLock.setUpdateTime(scheduledAt);
    List<Object> priorRenewal = List.of("R".repeat(26), expiredLock.getId(), CANDIDATE, "RENEW", ownerId, ownerId,
        "INTERVIEW_SCHEDULED", "已安排面试", ownerId, scheduledAt);
    List<List<Object>> storedRenewalHistory = new ArrayList<>(List.of(priorRenewal));
    byte[] expiredBefore = new ObjectMapper().writeValueAsBytes(expiredLock);
    EngagementLockPersistenceService engagementRows = mock(EngagementLockPersistenceService.class);
    LockHistoryPersistenceService engagementHistory = mock(LockHistoryPersistenceService.class);
    when(engagementHistory.insertHistory(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> { storedRenewalHistory.add(Arrays.asList(call.getArguments())); return 1; });
    when(engagementRows.findByCandidate(CANDIDATE)).thenAnswer(call -> {
      assertThat(events).contains("own-interview-complete"); events.add("expired-engagement-read"); return expiredLock;
    });
    EngagementLockApplicationManager realEngagement = new EngagementLockApplicationManager();
    ReflectionTestUtils.setField(realEngagement, "candidateCommands", mock(CompanyBlacklistQueryService.class));
    ReflectionTestUtils.setField(realEngagement, "persistence", engagementRows);
    ReflectionTestUtils.setField(realEngagement, "lockHistoryPersistenceService", engagementHistory);
    ReflectionTestUtils.setField(realEngagement, "clock", Clock.fixed(now, ZoneOffset.UTC));
    assertThat(expiredLock.getAcquiredAt()).isBefore(scheduledAt);
    assertThat(scheduledAt).isBefore(initialInterview.getScheduledStartAt());
    assertThat(expiredLock.getExpiresAt()).isEqualTo(initialInterview.getScheduledEndAt()).isBefore(now);
    when(ids.newId()).thenReturn(feedbackId, versionId, originId);
    ActivityOriginManager originManager = new ActivityOriginManager();
    ReflectionTestUtils.setField(originManager, "origins", originRows); ReflectionTestUtils.setField(originManager, "ids", ids);
    ReflectionTestUtils.setField(originManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(originManager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ParticipantPersistenceService participantRows = mock(ParticipantPersistenceService.class);
    when(participantRows.findParticipants(interviewId)).thenReturn(List.of(new Participant(interviewId, ACTOR, "INTERVIEWER", "PENDING")));
    CandidateLibraryQueryService shared = mock(CandidateLibraryQueryService.class);
    when(shared.eligibleCandidateIds(anyCollection())).thenReturn(Set.of());
    InterviewIdempotencyPersistenceService interviewKeys = mock(InterviewIdempotencyPersistenceService.class);
    InterviewApplicationManager interviewManager = new InterviewApplicationManager();
    ReflectionTestUtils.setField(interviewManager, "assembler", new InterviewAssembler());
    ReflectionTestUtils.setField(interviewManager, "persistence", interviews);
    ReflectionTestUtils.setField(interviewManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(interviews.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(interviewManager, "applicationPersistenceService", persistence);

    ReflectionTestUtils.setField(interviewManager, "interviewIdempotencyPersistenceService", interviewKeys);
    ReflectionTestUtils.setField(interviewManager, "interviewFeedbackPersistenceService", feedbackRows);
    ReflectionTestUtils.setField(interviewManager, "feedbackVersionPersistenceService", versionRows);
    ReflectionTestUtils.setField(interviewManager, "participantPersistenceService", participantRows);
    ReflectionTestUtils.setField(interviewManager, "candidateLibrary", shared);
    ReflectionTestUtils.setField(interviewManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(interviewManager, "applications", joinedClientInterviewProxy(service, transactions));
    ReflectionTestUtils.setField(interviewManager, "activityOrigins", joinedClientInterviewProxy(originManager, transactions));
    ReflectionTestUtils.setField(interviewManager, "engagementLocks", realEngagement);
    ReflectionTestUtils.setField(interviewManager, "ids", ids); ReflectionTestUtils.setField(interviewManager, "clock", Clock.fixed(now, ZoneOffset.UTC));
    InterviewApplicationManager publicEntry = joinedClientInterviewProxy(interviewManager, transactions);
    var previousAttributes = RequestContextHolder.getRequestAttributes();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Recruitment-Action", "INTERVIEW"); request.addHeader("X-Recruitment-Operation", operation);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      InterviewFeedbackDTO command = new InterviewFeedbackDTO("PASS", "技术基础扎实", null, occurred, "SUBMITTED", "正式提交");
      if (denied) {
        assertThatThrownBy(() -> publicEntry.saveFeedback(interviewId, command, key))
            .isInstanceOfSatisfying(PlatformException.class,
                error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
        assertThat(reservations).isEmpty(); assertThat(feedbackWrites).isEmpty(); assertThat(versionWrites).isEmpty();
        assertThat(completions).isEmpty(); assertThat(origins).isEmpty();
        verifyNoInteractions(feedbackRows, versionRows, interviewKeys, originRows, engagementRows, engagementHistory);
        verify(interviews, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
        verify(interviews, never()).complete(any(), any(), any()); assertThat(storedInterview.get()).isSameAs(initialInterview);
        assertThat(storedFeedback.get()).isNull(); assertThat(storedVersion.get()).isNull(); assertCommandTransaction(transactions, false);
      } else {
        var response = publicEntry.saveFeedback(interviewId, command, key);
        assertThat(response.getFeedback().getId()).isEqualTo(feedbackId);
        assertThat(response.getFeedback().getInterviewId()).isEqualTo(interviewId);
        assertThat(response.getFeedback().getAuthorUserId()).isEqualTo(ACTOR);
        assertThat(response.getFeedback().getFeedbackKind()).isEqualTo("INTERNAL");
        assertThat(response.getFeedback().getStatus()).isEqualTo("SUBMITTED"); assertThat(response.getFeedback().getCurrentVersionNo()).isEqualTo(1);
        assertThat(response.getFeedback().getCreateTime()).isEqualTo(now); assertThat(response.getFeedback().getUpdateTime()).isEqualTo(now);
        assertThat(response.getCurrentVersion().getId()).isEqualTo(versionId);
        assertThat(response.getCurrentVersion().getFeedbackId()).isEqualTo(feedbackId); assertThat(response.getCurrentVersion().getVersionNo()).isEqualTo(1);
        assertThat(response.getCurrentVersion().getVerdict()).isEqualTo("PASS"); assertThat(response.getCurrentVersion().getShortComment()).isEqualTo("技术基础扎实");
        assertThat(response.getCurrentVersion().getExternalFeedbackSource()).isNull(); assertThat(response.getCurrentVersion().getFeedbackOccurredAt()).isEqualTo(occurred);
        assertThat(response.getCurrentVersion().getChangeReason()).isEqualTo("正式提交"); assertThat(response.getCurrentVersion().getCreateId()).isEqualTo(ACTOR);
        assertThat(response.getCurrentVersion().getCreateTime()).isEqualTo(now);
        assertThat(feedbackWrites).containsExactly(List.of(feedbackId, interviewId, ACTOR, "INTERNAL", "SUBMITTED", now));
        assertThat(versionWrites).containsExactly(Arrays.asList(versionId, feedbackId, 1, "PASS", "技术基础扎实", null, occurred, "正式提交", ACTOR, now));
        assertThat(reservations).singleElement().satisfies(row -> {
          assertThat(row.subList(0, 3)).containsExactly(ACTOR, key, "INTERVIEW_FEEDBACK_SAVE");
          assertThat(row.get(3)).isEqualTo(Sha256Utils.fingerprintObjects(31, interviewId, "INTERNAL", "PASS",
              "技术基础扎实", null, occurred, "SUBMITTED", "正式提交"));
          assertThat(row.subList(4, 6)).containsExactly(feedbackId, now);
        });
        assertThat(completions).containsExactly(List.of(interviewId, ACTOR, now));
        assertThat(origins).singleElement().satisfies(origin -> {
          assertThat(origin.getId()).isEqualTo(originId); assertThat(origin.getApplicationId()).isEqualTo(appId);
          assertThat(origin.getSourceType()).isEqualTo("INTERVIEW_FEEDBACK"); assertThat(origin.getSourceId()).isEqualTo(versionId);
          assertThat(origin.getAction()).isEqualTo("INTERVIEW"); assertThat(origin.getOperationId()).isEqualTo(operation);
          assertThat(origin.getCreateId()).isEqualTo(ACTOR); assertThat(origin.getUpdateId()).isEqualTo(ACTOR);
          assertThat(origin.getCreateName()).isEqualTo("HR"); assertThat(origin.getUpdateName()).isEqualTo("HR");
          assertThat(origin.getCreateTime()).isEqualTo(now); assertThat(origin.getUpdateTime()).isEqualTo(now);
          assertThat(origin.getVersion()).isZero(); assertThat(origin.getExt()).isNull(); assertThat(origin.getRemark()).isNull();
        });
        assertThat(storedFeedback.get().getCreateId()).isEqualTo(ACTOR); assertThat(storedFeedback.get().getUpdateId()).isEqualTo(ACTOR);
        assertThat(storedInterview.get().getStatus()).isEqualTo("COMPLETED"); assertThat(storedInterview.get().getVersion()).isEqualTo(1L);
        assertThat(storedInterview.get().getCreateTime()).isEqualTo(scheduledAt); assertThat(storedInterview.get().getUpdateId()).isEqualTo(ACTOR);
        assertThat(storedInterview.get().getUpdateTime()).isEqualTo(now);
        verify(feedbackRows, Mockito.times(2)).findFeedbackByAuthor(interviewId, ACTOR, "INTERNAL");
        verify(versionRows).findFeedbackVersion(feedbackId, 1); verify(feedbackRows, never()).advanceFeedback(any(), any(), Mockito.anyInt(), any(), any());
        verify(engagementRows).findByCandidate(CANDIDATE); verifyNoInteractions(engagementHistory); assertCommandTransaction(transactions, true);
      }
    } finally {
      if (previousAttributes == null) RequestContextHolder.resetRequestAttributes();
      else RequestContextHolder.setRequestAttributes(previousAttributes);
    }
    verify(interviews).lockInterview(interviewId); verify(persistence, Mockito.times(2)).lockApplication(appId);
    assertThat(events.indexOf("ordinary-interview-view")).isLessThan(events.indexOf("application-lock"));
    assertThat(events.indexOf("application-lock")).isLessThan(events.indexOf("interview-lock"));
    assertThat(events.lastIndexOf("application-lock")).isGreaterThan(events.indexOf("interview-lock"));
    if (events.contains("current-members")) {
      assertThat(events.stream().filter("current-members"::equals).count()).isOne(); assertThat(events).doesNotContain("ordinary-members");
      assertThat(events.indexOf("current-members")).isGreaterThan(events.lastIndexOf("application-lock"));
    }
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(winnerChanges).containsExactly(Arrays.asList("K".repeat(26), appId, JsonText.write(beforeMembers), JsonText.write(afterMembers),
        "合规协作者配置变更", 0L, ownerId, now));
    assertThat(storedApp.get().getVersion()).isEqualTo(1L); assertThat(storedApp.get().getOwnerUserId()).isEqualTo(ownerId);
    assertThat(storedApp.get().getOwnerDepartmentId()).isEqualTo(DEPARTMENT); assertThat(storedApp.get().getRecruitmentType()).isEqualTo("INTERNAL");
    assertThat(storedApp.get().getCurrentStage()).isEqualTo("IN_PROCESS"); assertThat(storedApp.get().getTerminalAt()).isNull();
    assertThat(storedApp.get().getUpdateId()).isEqualTo(ownerId); assertThat(storedApp.get().getUpdateTime()).isEqualTo(now);
    assertThat(storedMembers.get()).isEqualTo(afterMembers); assertThat(snapshotMembers.get()).isEqualTo(beforeSnapshot ? afterMembers : beforeMembers);
    assertThat(snapshotApp.get().getVersion()).isEqualTo(beforeSnapshot ? 1L : 0L);
    assertThat(events.indexOf("winner-full-commit")).isLessThan(events.indexOf("application-lock"));
    assertThat(new ObjectMapper().writeValueAsBytes(expiredLock)).isEqualTo(expiredBefore);
    assertThat(storedRenewalHistory).containsExactly(priorRenewal);
    assertThat(priorRenewal).containsExactly("R".repeat(26), expiredLock.getId(), CANDIDATE, "RENEW", ownerId, ownerId,
        "INTERVIEW_SCHEDULED", "已安排面试", ownerId, scheduledAt);
    verify(engagementRows, never()).renew(any(), any(), any(), anyLong(), any(), any());
    verify(interviews, never()).insertHistory(any(), any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(workflowSteps);
  }

  @Test
  void lockedFeedbackAccessKeepsTerminalOwnerWithoutOpeningTheApplication() {
    String appId = "P".repeat(26); Application terminal = application(appId, ACTOR, now.minusSeconds(300));
    terminal.setCurrentStage("TERMINATED"); terminal.setTerminalAt(now.minusSeconds(60)); terminal.setVersion(2L);
    ReflectionTestUtils.setField(service, "authorization", interviewBoundaryAuthorization(false));
    when(persistence.lockApplication(appId)).thenReturn(terminal);
    when(collaborators.findCollaborators(appId)).thenReturn(List.of()); when(collaborators.findCollaboratorsForUpdate(appId)).thenReturn(List.of());
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    assertThat(commandProxy(transactions).requireLockedFollowUpAccess(appId)).isSameAs(terminal);
    assertThat(terminal.getCurrentStage()).isEqualTo("TERMINATED"); assertThat(terminal.getTerminalAt()).isEqualTo(now.minusSeconds(60));
    assertThat(terminal.getVersion()).isEqualTo(2L); assertCommandTransaction(transactions, true);
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    verifyNoInteractions(workflowSteps, interviews, locks);
  }

  // 公开同对象入口先建立ordinary全局读视图；合法owner写者仅建模，不是真实T2/MySQL/物理回滚。
  @ParameterizedTest
  @ValueSource(strings = {"internal-wide-revoked-after", "external-wide-revoked-after",
      "internal-wide-active-after", "external-wide-active-after",
      "internal-narrow-active-after", "external-narrow-active-after",
      "internal-narrow-owner-after", "external-narrow-owner-after",
      "internal-wide-revoked-before", "external-wide-revoked-before"})
  void publicOnboardingReturnRechecksCurrentParticipantsAfterTalentInJoinedTransaction(String mode) throws Exception {
    boolean external = mode.startsWith("external-"), wide = mode.contains("-wide-");
    boolean owner = mode.contains("-owner-"), denied = mode.contains("-revoked-");
    boolean beforeSnapshot = mode.endsWith("-before");
    String appId = "P".repeat(26), ownerId = owner ? ACTOR : "B".repeat(26);
    String currentResume = "W".repeat(26), recordId = "I".repeat(26), stageId = "S".repeat(26);
    assertThat(currentResume).isNotEqualTo(RESUME);
    String key = "onboarding-return-current-members", clean = "入职库退回：已确认";
    String initialStage = owner ? "ONBOARDED" : "OFFER";
    Instant createdAt = now.minusSeconds(7200), winnerAt = now.minusSeconds(60);
    IdentityAuthorizationAdapter realAuthorization = interviewBoundaryAuthorization(wide);
    ReflectionTestUtils.setField(service, "authorization", realAuthorization);
    CommandBoundaryTransactions transactions = new CommandBoundaryTransactions();
    List<String> events = new ArrayList<>();
    List<List<Object>> winnerChanges = new ArrayList<>(), reservations = new ArrayList<>();
    List<List<Object>> talentChanges = new ArrayList<>(), talentEvents = new ArrayList<>();
    List<List<Object>> offerChanges = new ArrayList<>(), stageChanges = new ArrayList<>(), processChanges = new ArrayList<>();
    List<ActivityOriginEntity> origins = new ArrayList<>();
    Application initial = application(appId, ownerId, createdAt);
    initial.setCurrentStage(initialStage); initial.setRecruitmentType(external ? "EXTERNAL" : "INTERNAL");
    initial.setOutcome(owner ? "PASS" : null); initial.setTerminalAt(owner ? now.minusSeconds(600) : null);
    initial.setActiveSlot(owner ? null : 1L); initial.setVersion(0L);
    initial.setCreateId(ownerId); initial.setUpdateId(ownerId); initial.setCreateTime(createdAt); initial.setUpdateTime(createdAt);
    AtomicReference<Application> storedApp = new AtomicReference<>(initial), snapshotApp = new AtomicReference<>();
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(List.of(ACTOR)), snapshotMembers = new AtomicReference<>();
    List<String> afterMembers = denied || owner ? List.of() : List.of(ACTOR, "V".repeat(26));
    when(directory.activeUsers(Set.copyOf(afterMembers))).thenAnswer(call -> {
      events.add("winner-active-users");
      return afterMembers.stream().collect(Collectors.toMap(id -> id,
          id -> new IdentityDirectoryService.UserReference(id, "启用HR", DEPARTMENT)));
    });
    when(collaborators.deleteCollaborators(appId)).thenAnswer(call -> {
      assertThat(events).contains("winner-active-users"); storedMembers.set(List.of()); return 1;
    });
    when(collaborators.insertCollaborators(appId, afterMembers, ownerId, winnerAt)).thenAnswer(call -> {
      assertThat(storedMembers.get()).isEmpty(); storedMembers.set(List.copyOf(afterMembers)); return true;
    });
    when(persistence.updateStage(appId, initialStage, 0L, ownerId, winnerAt)).thenAnswer(call -> {
      assertThat(storedApp.get().getVersion()).isZero();
      Application changed = new Application(); BeanUtils.copyProperties(storedApp.get(), changed);
      changed.setVersion(1L); changed.setUpdateId(ownerId); changed.setUpdateTime(winnerAt); storedApp.set(changed); return 1;
    });
    when(persistence.insertCollaboratorChange(any(), eq(appId), any(), any(), any(), eq(0L), eq(ownerId), eq(winnerAt)))
        .thenAnswer(call -> { winnerChanges.add(Arrays.asList(call.getArguments())); return 1; });
    Runnable commitWinner = () -> {
      assertThat(winnerChanges).isEmpty(); assertThat(events).doesNotContain("application-lock");
      List<String> old = List.copyOf(storedMembers.get());
      directory.activeUsers(Set.copyOf(afterMembers));
      assertThat(collaborators.deleteCollaborators(appId)).isEqualTo(1);
      assertThat(collaborators.insertCollaborators(appId, afterMembers, ownerId, winnerAt)).isTrue();
      assertThat(persistence.updateStage(appId, initialStage, 0L, ownerId, winnerAt)).isEqualTo(1);
      assertThat(persistence.insertCollaboratorChange("K".repeat(26), appId, JsonText.write(old), JsonText.write(afterMembers),
          "合规协作者配置变更", 0L, ownerId, winnerAt)).isEqualTo(1);
      events.add("modeled-owner-full-commit");
    };
    when(persistence.findApplication(appId)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      if (snapshotApp.get() == null) {
        if (beforeSnapshot) commitWinner.run();
        Application snapshot = new Application(); BeanUtils.copyProperties(storedApp.get(), snapshot);
        snapshotApp.set(snapshot); snapshotMembers.set(List.copyOf(storedMembers.get())); events.add("ordinary-app-view");
      }
      return snapshotApp.get();
    });
    when(collaborators.findCollaborators(appId)).thenAnswer(call -> {
      assertThat(snapshotMembers.get()).isNotNull(); events.add("ordinary-members"); return List.copyOf(snapshotMembers.get());
    });
    when(collaborators.findCollaboratorsForUpdate(appId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock"); events.add("current-members"); return List.copyOf(storedMembers.get());
    });
    when(persistence.lockApplication(appId)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(events).contains("talent-mark", "talent-event", "current-resume-lock");
      if (winnerChanges.isEmpty()) commitWinner.run(); events.add("application-lock"); return storedApp.get();
    });
    when(idempotency.findIdempotency(ACTOR, key)).thenAnswer(call -> {
      assertThat(events).contains("ordinary-app-view"); events.add("ordinary-key-miss"); return null;
    });
    when(persistence.insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(recordId), eq(now))).thenAnswer(call -> {
      assertThat(events).doesNotContain("candidate-lock", "application-lock");
      assertThat(reservations).isEmpty(); reservations.add(Arrays.asList(call.getArguments())); events.add("own-reservation"); return 1;
    });
    CandidatePersistenceService talentCandidates = mock(CandidatePersistenceService.class);
    TalentIntakePersistenceService talentResumes = mock(TalentIntakePersistenceService.class);
    ResumeVersionPersistenceService talentVersions = mock(ResumeVersionPersistenceService.class);
    TalentProcessEventPersistenceService talentEventRows = mock(TalentProcessEventPersistenceService.class);
    Candidate candidate = new Candidate(); candidate.setId(CANDIDATE); candidate.setFullName("正常候选人");
    candidate.setPrimaryEmail("candidate@example.com"); candidate.setPrimaryPhone("13800000000");
    candidate.setStatus("ACTIVE"); candidate.setOwnerUserId(ownerId); candidate.setOwnerDepartmentId(DEPARTMENT);
    candidate.setVersion(0L); candidate.setExt("{\"retained\":\"unchanged\"}");
    candidate.setCreateId(ownerId); candidate.setUpdateId(ownerId); candidate.setCreateTime(createdAt); candidate.setUpdateTime(createdAt);
    AtomicReference<Candidate> storedCandidate = new AtomicReference<>(candidate);
    CandidateResumeRow current = new CandidateResumeRow(); current.setCandidateId(CANDIDATE); current.setResumeVersionId(currentResume);
    current.setJdVersionId(JD_VERSION); current.setDefaultMatchRunId("M".repeat(26));
    ResumeVersion available = new ResumeVersion(); available.setId(currentResume); available.setResumeId("L".repeat(26));
    available.setIntakeId("U".repeat(26)); available.setVersionNo(2); available.setStatus("AVAILABLE");
    available.setOriginalFileName("current-resume.pdf"); available.setMediaType("application/pdf"); available.setSizeBytes(100);
    available.setContentSha256("a".repeat(64)); available.setObjectKey("development/current-resume.pdf");
    available.setCreateId(ownerId); available.setUpdateId(ownerId); available.setCreateTime(createdAt); available.setUpdateTime(createdAt);
    byte[] availableBefore = new ObjectMapper().writeValueAsBytes(available);
    when(talentCandidates.lockCandidate(CANDIDATE)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isNull();
      assertThat(events).contains("own-reservation").doesNotContain("application-lock"); events.add("candidate-lock"); return CANDIDATE;
    });
    when(talentCandidates.findCandidate(CANDIDATE)).thenAnswer(call -> {
      assertThat(events).contains("candidate-lock"); events.add("active-candidate"); return storedCandidate.get();
    });
    when(talentResumes.findCurrentCandidateResumes(List.of(CANDIDATE))).thenAnswer(call -> {
      assertThat(events).contains("active-candidate"); events.add("current-resume-read"); return List.of(current);
    });
    when(talentVersions.lockForRouting(currentResume)).thenAnswer(call -> {
      assertThat(events.stream().filter("candidate-lock"::equals).count()).isEqualTo(2L);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); events.add("current-resume-lock"); return currentResume;
    });
    when(talentResumes.findCandidateResumeVersion(CANDIDATE, currentResume)).thenAnswer(call -> {
      assertThat(events).contains("current-resume-lock"); events.add("available-current-resume"); return available;
    });
    when(talentCandidates.markReturnedToShared(eq(CANDIDATE), eq(0L), any(), eq(ACTOR), eq(now))).thenAnswer(call -> {
      assertThat(events).contains("available-current-resume").doesNotContain("application-lock");
      assertThat(talentChanges).isEmpty(); talentChanges.add(Arrays.asList(call.getArguments()));
      Candidate changed = new Candidate(); BeanUtils.copyProperties(storedCandidate.get(), changed);
      changed.setExt(call.getArgument(2)); changed.setVersion(1L); changed.setUpdateId(ACTOR); changed.setUpdateTime(now);
      storedCandidate.set(changed); events.add("talent-mark"); return 1;
    });
    when(talentEventRows.insertEvent(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(events).contains("talent-mark").doesNotContain("application-lock");
      assertThat(talentEvents).isEmpty(); talentEvents.add(Arrays.asList(call.getArguments())); events.add("talent-event"); return 1;
    });
    CandidateResumeVersionAdapter realRouting = new CandidateResumeVersionAdapter();
    ReflectionTestUtils.setField(realRouting, "candidates", talentCandidates);
    ReflectionTestUtils.setField(realRouting, "versions", talentVersions); ReflectionTestUtils.setField(realRouting, "persistence", talentResumes);
    CandidateResumeVersionService routing = joinedClientInterviewProxy(realRouting, transactions);
    ResumeLifecycleProperties properties = new ResumeLifecycleProperties(); assertThat(properties.isEnabled()).isFalse();
    ResumeLifecycleApplicationManager realLifecycle = spy(new ResumeLifecycleApplicationManager());
    ReflectionTestUtils.setField(realLifecycle, "properties", properties);
    // 默认关闭是源码自然条件；真实routed的RC注解仍加入外层DEFAULT，没有新RC事务或假no-op端口。
    ResumeLifecycleApplicationManager lifecycleProxy = joinedClientInterviewProxy(realLifecycle, transactions);
    PlatformIdGenerator talentIds = mock(PlatformIdGenerator.class); when(talentIds.newId()).thenReturn("E".repeat(26));
    OnboardingReturnApplicationManager realTalent = new OnboardingReturnApplicationManager();
    ReflectionTestUtils.setField(realTalent, "candidates", talentCandidates); ReflectionTestUtils.setField(realTalent, "resumes", talentResumes);
    ReflectionTestUtils.setField(realTalent, "routing", routing); ReflectionTestUtils.setField(realTalent, "lifecycle", lifecycleProxy);
    ReflectionTestUtils.setField(realTalent, "events", talentEventRows); ReflectionTestUtils.setField(realTalent, "ids", talentIds);
    ReflectionTestUtils.setField(realTalent, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ReflectionTestUtils.setField(service, "candidateOnboardingReturns", joinedClientInterviewProxy(realTalent, transactions));
    when(ids.newId()).thenReturn(recordId, stageId);
    ActivityOriginPersistenceService originRows = mock(ActivityOriginPersistenceService.class);
    when(originRows.save(any(ActivityOriginEntity.class))).thenAnswer(call -> {
      assertThat(events).contains("application-lock"); assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      origins.add(call.getArgument(0)); events.add("origin-" + origins.getLast().getSourceType()); return true;
    });
    PlatformIdGenerator originIds = mock(PlatformIdGenerator.class); when(originIds.newId()).thenReturn("Y".repeat(26), "Z".repeat(26));
    ActivityOriginManager realOrigins = new ActivityOriginManager();
    ReflectionTestUtils.setField(realOrigins, "origins", originRows); ReflectionTestUtils.setField(realOrigins, "ids", originIds);
    ReflectionTestUtils.setField(realOrigins, "authorization", realAuthorization); ReflectionTestUtils.setField(realOrigins, "clock", Clock.fixed(now, ZoneOffset.UTC));
    ReflectionTestUtils.setField(service, "activityOrigins", joinedClientInterviewProxy(realOrigins, transactions));
    OnboardingProcess initialProcess = onboardingProcess(appId, owner ? "ONBOARDED" : "IN_PROGRESS", createdAt, owner ? now.minusSeconds(600) : null);
    initialProcess.setStartedByUserId(ownerId); initialProcess.setVersion(4); initialProcess.setCreateId(ownerId); initialProcess.setUpdateId(ownerId);
    initialProcess.setCreateTime(createdAt); initialProcess.setUpdateTime(createdAt);
    AtomicReference<OnboardingProcess> storedProcess = new AtomicReference<>(initialProcess);
    when(onboardingProcesses.lockProcess(appId)).thenAnswer(call -> {
      assertThat(events).contains("application-lock"); events.add("onboarding-lock"); return storedProcess.get();
    });
    OfferStatusRecord latest = new OfferStatusRecord(); latest.setId("F".repeat(26)); latest.setApplicationId(appId);
    latest.setRecordType("STATUS"); latest.setStatus(owner ? "ONBOARDED" : "ISSUED");
    latest.setApplicationOutcome(owner ? "PASS" : null); latest.setSource("MANUAL"); latest.setSourceStatus(latest.getStatus());
    latest.setReason("原入职事实"); latest.setOccurredAt(createdAt); latest.setOperatedByUserId(ownerId);
    latest.setCreateId(ownerId); latest.setUpdateId(ownerId); latest.setCreateTime(createdAt); latest.setUpdateTime(createdAt);
    byte[] latestBefore = new ObjectMapper().writeValueAsBytes(latest);
    Mockito.doAnswer(call -> {
      assertThat(events).contains("onboarding-lock"); events.add("latest-offer-current"); return latest;
    }).when(offers).latestOfferForUpdate(appId);
    AtomicReference<OfferStatusRecord> ownOffer = new AtomicReference<>();
    when(offers.insertOffer(any(), eq(appId), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> {
          assertThat(events).contains("latest-offer-current", "origin-OFFER"); assertThat(ownOffer.get()).isNull();
          offerChanges.add(Arrays.asList(call.getArguments())); OfferStatusRecord row = new OfferStatusRecord();
          row.setId(call.getArgument(0)); row.setApplicationId(call.getArgument(1)); row.setRecordType(call.getArgument(2)); row.setStatus(call.getArgument(3));
          row.setTerminationReason(call.getArgument(4)); row.setApplicationOutcome(call.getArgument(5)); row.setReason(call.getArgument(6));
          row.setCorrectsRecordId(call.getArgument(7)); row.setReopensRecordId(call.getArgument(8)); row.setSource(call.getArgument(9));
          row.setSourceReference(call.getArgument(10)); row.setSourceStatus(call.getArgument(11)); row.setOccurredAt(call.getArgument(12));
          row.setOperatedByUserId(call.getArgument(13)); row.setCreateTime(call.getArgument(14)); row.setCreateId(ACTOR); row.setUpdateId(ACTOR); row.setUpdateTime(now);
          ownOffer.set(row); events.add("own-offer"); return 1;
        });
    when(persistence.updateOutcome(eq(appId), any(), any(), any(), any(), anyLong(), any(), any())).thenAnswer(call -> {
      assertThat(events).contains("own-offer");
      if (call.<Long>getArgument(5).longValue() != storedApp.get().getVersion()) return 0;
      Application changed = new Application(); BeanUtils.copyProperties(storedApp.get(), changed);
      changed.setCurrentStage(call.getArgument(1)); changed.setOutcome(call.getArgument(2)); changed.setTerminalAt(call.getArgument(3));
      changed.setActiveSlot(call.getArgument(4)); changed.setVersion(changed.getVersion() + 1);
      changed.setUpdateId(call.getArgument(6)); changed.setUpdateTime(call.getArgument(7)); storedApp.set(changed);
      events.add("own-application-outcome"); return 1;
    });
    when(stageHistory.insertStageHistory(any(), eq(appId), any(), any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(events).contains("own-application-outcome", "origin-STAGE"); stageChanges.add(Arrays.asList(call.getArguments()));
      events.add("own-stage-history"); return 1;
    });
    when(onboardingProcesses.markReturned(eq(appId), eq(ACTOR), eq(4L), eq(now))).thenAnswer(call -> {
      assertThat(events).contains("own-stage-history"); processChanges.add(Arrays.asList(call.getArguments()));
      OnboardingProcess row = new OnboardingProcess(); BeanUtils.copyProperties(storedProcess.get(), row);
      row.setStatus("RETURNED"); row.setVersion(5); row.setClosedAt(now); row.setCompletedByUserId(ACTOR);
      row.setUpdateId(ACTOR); row.setUpdateTime(now); storedProcess.set(row); events.add("own-onboarding-returned"); return 1;
    });
    when(offers.findOffer(appId, recordId)).thenAnswer(call -> {
      assertThat(events).contains("own-onboarding-returned"); assertThat(ownOffer.get()).isNotNull(); events.add("own-offer-read"); return ownOffer.get();
    });
    String operation = "b5fd95b4-12bb-4af8-9282-29a661795708";
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Recruitment-Action", "ENDED"); request.addHeader("X-Recruitment-Operation", operation);
    var previous = RequestContextHolder.getRequestAttributes(); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      RecruitmentApplicationManager proxy = commandProxy(transactions);
      if (denied) {
        assertThatThrownBy(() -> proxy.returnOnboardingToSharedResponse(appId, "LIBRARY_RETURN", "已确认", key))
            .isInstanceOfSatisfying(PlatformException.class,
                error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
        assertThat(offerChanges).isEmpty(); assertThat(stageChanges).isEmpty(); assertThat(processChanges).isEmpty(); assertThat(origins).isEmpty();
        assertThat(ownOffer.get()).isNull(); assertThat(storedProcess.get()).isSameAs(initialProcess); assertThat(storedApp.get().getVersion()).isEqualTo(1L);
        verify(onboardingProcesses, never()).lockProcess(appId); verify(offers, never()).latestOfferForUpdate(appId);
        verify(offers, never()).findOffer(appId, recordId); assertCommandTransaction(transactions, false);
      } else {
        OfferStatusResponse response = proxy.returnOnboardingToSharedResponse(appId, "LIBRARY_RETURN", "已确认", key);
        assertThat(response).isEqualTo(new OfferStatusResponse(recordId, appId, "STATUS", "TERMINATED", "OTHER", "REJECT", clean,
            null, null, "MANUAL_ONBOARDING_RETURN", null, "ONBOARDING_LIBRARY_RETURN", now, ACTOR, now));
        assertThat(offerChanges).containsExactly(Arrays.asList(recordId, appId, "STATUS", "TERMINATED", "OTHER", "REJECT", clean,
            null, null, "MANUAL_ONBOARDING_RETURN", null, "ONBOARDING_LIBRARY_RETURN", now, ACTOR, now));
        assertThat(stageChanges).containsExactly(Arrays.asList(stageId, appId, initialStage, "TERMINATED", clean, ACTOR, now));
        assertThat(processChanges).containsExactly(Arrays.asList(appId, ACTOR, 4L, now));
        assertThat(storedApp.get().getVersion()).isEqualTo(2L); assertThat(storedApp.get().getCurrentStage()).isEqualTo("TERMINATED");
        assertThat(storedApp.get().getOutcome()).isEqualTo("REJECT"); assertThat(storedApp.get().getTerminalAt()).isEqualTo(now);
        assertThat(storedApp.get().getActiveSlot()).isNull(); assertThat(storedApp.get().getUpdateId()).isEqualTo(ACTOR);
        assertThat(storedApp.get().getUpdateTime()).isEqualTo(now); assertThat(storedProcess.get().getVersion()).isEqualTo(5);
        assertThat(storedProcess.get().getStatus()).isEqualTo("RETURNED"); assertThat(storedProcess.get().getClosedAt()).isEqualTo(now);
        assertThat(storedProcess.get().getStartedAt()).isEqualTo(createdAt); assertThat(storedProcess.get().getActualOnboardedAt()).isEqualTo(initialProcess.getActualOnboardedAt());
        assertThat(storedProcess.get().getCompletedByUserId()).isEqualTo(ACTOR); assertThat(storedProcess.get().getUpdateTime()).isEqualTo(now);
        assertThat(origins).hasSize(2);
        for (int i = 0; i < origins.size(); i++) {
          ActivityOriginEntity origin = origins.get(i);
          assertThat(origin.getId()).isEqualTo((i == 0 ? "Y" : "Z").repeat(26)); assertThat(origin.getApplicationId()).isEqualTo(appId);
          assertThat(origin.getSourceType()).isEqualTo(i == 0 ? "OFFER" : "STAGE"); assertThat(origin.getSourceId()).isEqualTo(i == 0 ? recordId : stageId);
          assertThat(origin.getAction()).isEqualTo("ENDED"); assertThat(origin.getOperationId()).isEqualTo(operation);
          assertThat(origin.getCreateId()).isEqualTo(ACTOR); assertThat(origin.getUpdateId()).isEqualTo(ACTOR);
          assertThat(origin.getCreateName()).isEqualTo("HR"); assertThat(origin.getUpdateName()).isEqualTo("HR");
          assertThat(origin.getCreateTime()).isEqualTo(now); assertThat(origin.getUpdateTime()).isEqualTo(now);
          assertThat(origin.getVersion()).isZero(); assertThat(origin.getExt()).isNull(); assertThat(origin.getRemark()).isNull();
        }
        assertThat(ownOffer.get().getCreateId()).isEqualTo(ACTOR); assertThat(ownOffer.get().getUpdateId()).isEqualTo(ACTOR);
        assertThat(ownOffer.get().getUpdateTime()).isEqualTo(now); assertThat(ownOffer.get().getVersion()).isZero();
        assertCommandTransaction(transactions, true);
      }
    } finally {
      if (previous == null) RequestContextHolder.resetRequestAttributes(); else RequestContextHolder.setRequestAttributes(previous);
    }
    assertThat(transactions.beginIsolations).containsExactly(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(winnerChanges).containsExactly(Arrays.asList("K".repeat(26), appId, JsonText.write(List.of(ACTOR)), JsonText.write(afterMembers),
        "合规协作者配置变更", 0L, ownerId, winnerAt));
    assertThat(storedMembers.get()).isEqualTo(afterMembers); assertThat(snapshotMembers.get()).isEqualTo(beforeSnapshot ? afterMembers : List.of(ACTOR));
    assertThat(snapshotApp.get().getVersion()).isEqualTo(beforeSnapshot ? 1L : 0L);
    assertThat(storedApp.get().getOwnerUserId()).isEqualTo(ownerId); assertThat(storedApp.get().getOwnerDepartmentId()).isEqualTo(DEPARTMENT);
    assertThat(storedApp.get().getRecruitmentType()).isEqualTo(external ? "EXTERNAL" : "INTERNAL");
    if (beforeSnapshot) {
      assertThat(events.indexOf("modeled-owner-full-commit")).isLessThan(events.indexOf("ordinary-app-view"));
      assertThat(reservations).isEmpty(); assertThat(talentChanges).isEmpty(); assertThat(talentEvents).isEmpty();
      assertThat(storedCandidate.get()).isSameAs(candidate); verifyNoInteractions(idempotency, talentCandidates, talentResumes, talentVersions, talentEventRows, originRows);
      verify(persistence, never()).lockApplication(appId); verify(collaborators, never()).findCollaboratorsForUpdate(appId);
    } else {
      assertThat(events).containsSubsequence("ordinary-app-view", "ordinary-key-miss", "own-reservation", "candidate-lock",
          "active-candidate", "current-resume-read", "candidate-lock", "current-resume-lock", "available-current-resume", "talent-mark", "talent-event",
          "modeled-owner-full-commit", "application-lock");
      assertThat(reservations).containsExactly(Arrays.asList(ACTOR, key, "ONBOARDING_RETURN_TO_SHARED",
          Sha256Utils.fingerprintObjects(31, appId, "LIBRARY_RETURN", "已确认"), recordId, now));
      assertThat(talentChanges).hasSize(1); assertThat(talentChanges.getFirst()).hasSize(5);
      assertThat(talentChanges.getFirst().get(0)).isEqualTo(CANDIDATE); assertThat(talentChanges.getFirst().get(1)).isEqualTo(0L);
      assertThat(talentChanges.getFirst().get(3)).isEqualTo(ACTOR); assertThat(talentChanges.getFirst().get(4)).isEqualTo(now);
      var metadata = new ObjectMapper().readTree(storedCandidate.get().getExt());
      assertThat(metadata.path("retained").asString()).isEqualTo("unchanged");
      assertThat(metadata.path("screenedOutResumeVersionId").asString()).isEqualTo(currentResume);
      assertThat(metadata.path("onboardingReturnApplicationResumeVersionId").asString()).isEqualTo(RESUME);
      assertThat(metadata.path("screenedOutByUserId").asString()).isEqualTo(ACTOR); assertThat(metadata.path("screenedOutReason").asString()).isEqualTo(clean);
      assertThat(metadata.path("screenedOutAt").asString()).isEqualTo(now.toString());
      assertThat(storedCandidate.get().getVersion()).isEqualTo(1L); assertThat(storedCandidate.get().getStatus()).isEqualTo("ACTIVE");
      assertThat(storedCandidate.get().getOwnerUserId()).isEqualTo(ownerId); assertThat(storedCandidate.get().getOwnerDepartmentId()).isEqualTo(DEPARTMENT);
      assertThat(storedCandidate.get().getCreateId()).isEqualTo(ownerId); assertThat(storedCandidate.get().getCreateTime()).isEqualTo(createdAt);
      assertThat(storedCandidate.get().getUpdateId()).isEqualTo(ACTOR); assertThat(storedCandidate.get().getUpdateTime()).isEqualTo(now);
      assertThat(talentEvents).containsExactly(Arrays.asList("E".repeat(26), "ONBOARDING_RETURNED_TO_SHARED_LIBRARY", ACTOR, "CANDIDATE", CANDIDATE, clean, now));
      verify(talentCandidates, Mockito.times(2)).lockCandidate(CANDIDATE); verify(talentCandidates).findCandidate(CANDIDATE);
      verify(talentResumes).findCurrentCandidateResumes(List.of(CANDIDATE)); verify(talentVersions).lockForRouting(currentResume);
      verify(talentResumes).findCandidateResumeVersion(CANDIDATE, currentResume); verify(talentIds).newId();
      verify(realLifecycle).routed(CANDIDATE, currentResume, "ONBOARDING_RETURN");
      verify(persistence).lockApplication(appId); verify(idempotency).findIdempotency(ACTOR, key);
      verify(idempotency, never()).findIdempotencyForUpdate(any(), any());
      if (events.contains("current-members")) {
        assertThat(events.stream().filter("current-members"::equals).count()).isOne();
        assertThat(events.indexOf("current-members")).isGreaterThan(events.indexOf("application-lock"));
        assertThat(events.stream().filter("ordinary-members"::equals).count()).isEqualTo(2L);
      }
      // 锁前Talent和reservation已执行；此模型只观察加入与rollback/commit信号，不能声称物理数据已回滚。
    }
    assertThat(new ObjectMapper().writeValueAsBytes(available)).isEqualTo(availableBefore);
    assertThat(new ObjectMapper().writeValueAsBytes(latest)).isEqualTo(latestBefore);
    verifyNoInteractions(workflowSteps, locks, interviews, candidateOnboardingReturns);
    verify(demands, never()).lockJdForHeadcount(any());
  }

  private <T> T joinedClientInterviewProxy(T target, CommandBoundaryTransactions transactions) {
    ProxyFactory proxy = new ProxyFactory(target);
    proxy.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    proxy.addAdvice(interceptor);
    return (T) proxy.getProxy();
  }

  private IdentityAuthorizationAdapter interviewBoundaryAuthorization(boolean wide) {
    IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
    ObjectGrantPersistenceService grants = mock(ObjectGrantPersistenceService.class);
    UserSupportDepartmentPersistenceService departments = mock(UserSupportDepartmentPersistenceService.class);
    UserAccountRecord account = new UserAccountRecord();
    account.setId(ACTOR); account.setDisplayName("HR"); account.setWorkEmail("hr@example.com");
    account.setHomeDepartmentId(DEPARTMENT); account.setStatus("ACTIVE");
    when(accounts.findById(ACTOR)).thenReturn(account);
    when(accounts.findRoleCodes(ACTOR)).thenReturn(List.of(wide ? "HR_DIRECTOR" : "RECRUITER"));
    when(departments.findSupportDepartmentIds(ACTOR)).thenReturn(List.of());
    CurrentActorProvider provider = () -> Optional.of(new CurrentActor(ACTOR,
        Set.of("recruitment.manage", "demand.read", "interview.schedule"), false));
    IdentityAuthorizationAdapter real = new IdentityAuthorizationAdapter();
    ReflectionTestUtils.setField(real, "actorProvider", provider);
    ReflectionTestUtils.setField(real, "accountService", accounts);
    ReflectionTestUtils.setField(real, "objectGrantPersistenceService", grants);
    ReflectionTestUtils.setField(real, "userSupportDepartmentPersistenceService", departments);
    assertThat(real.currentActor().hasCompanyWideScope()).isEqualTo(wide);
    assertThat(real.currentActor().getManagedUserIds()).isEmpty();
    assertThat(real.currentActor().isSuperAdmin()).isFalse();
    if (!wide) assertThat(real.currentActor().visibleDepartmentIdsFor("APPLICATION")).isEmpty();
    return real;
  }

  // 与106的简化TM不同，此TM使用同一state识别已有事务；未验证真实数据库隔离。
  private static final class CommandBoundaryTransactions extends AbstractPlatformTransactionManager {
    private final CommandTransactionState state = new CommandTransactionState();
    private int beginCount;
    private final List<Integer> beginIsolations = new ArrayList<>();
    private int commitCount;
    private int rollbackCount;

    @Override protected Object doGetTransaction() { return state; }
    @Override protected boolean isExistingTransaction(Object transaction) { return state.active; }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
      state.active = true;
      state.rollbackOnly = false;
      beginCount++;
      beginIsolations.add(definition.getIsolationLevel());
    }
    @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { state.rollbackOnly = true; }
    @Override protected void doCommit(DefaultTransactionStatus status) { commitCount++; }
    @Override protected void doRollback(DefaultTransactionStatus status) { rollbackCount++; }
    @Override protected void doCleanupAfterCompletion(Object transaction) { state.active = false; }
  }

  private static final class CommandTransactionState implements SmartTransactionObject {
    private boolean active;
    private boolean rollbackOnly;
    @Override public boolean isRollbackOnly() { return rollbackOnly; }
    @Override public void flush() {}
  }

}
