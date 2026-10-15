package com.ezaigc.hr.recruiting.submission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.identity.application.adapter.IdentityAuthorizationAdapter;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewNotificationApplicationManager;
import com.ezaigc.hr.recruiting.submission.domain.dto.WithdrawSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.entity.RiskAcknowledgement;
import com.ezaigc.hr.recruiting.submission.domain.response.SubmissionDetailsResponse;
import com.ezaigc.hr.recruiting.activity.application.manager.ActivityOriginManager;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.recruiting.submission.domain.dto.ApplicationClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.CreateSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.SendSubmissionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.CorrectClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.dto.DisableClientRestrictionDTO;
import com.ezaigc.hr.recruiting.submission.domain.request.ApplicationClientRestrictionRequest;
import com.ezaigc.hr.recruiting.activity.api.ActivityOriginRecorder;
import com.ezaigc.hr.recruiting.submission.application.assembler.SubmissionAssembler;
import com.ezaigc.hr.recruiting.demand.application.manager.DemandCatalogApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.SubmissionApplicationContext;
import com.ezaigc.hr.recruiting.submission.application.adapter.ClientSubmissionValidationAdapter;
import com.ezaigc.hr.recruiting.submission.application.manager.SubmissionApplicationManager;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import com.ezaigc.hr.recruiting.submission.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.submission.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.submission.domain.entity.SendSnapshot;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.talent.api.ResumeSubmissionQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.validation.Validation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentMatchers;

import static org.mockito.Mockito.spy;

import com.ezaigc.hr.common.core.util.Sha256Utils;

import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;

import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;

import com.ezaigc.hr.recruiting.recruitment.application.manager.EngagementLockApplicationManager;

import com.ezaigc.hr.recruiting.recruitment.application.manager.FollowUpPlanLifecycleManager;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.Decision;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;

import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;

import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;

import com.ezaigc.hr.recruiting.recruitment.service.DecisionPersistenceService;

import com.ezaigc.hr.recruiting.recruitment.service.OfferStatusRecordPersistenceService;

import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentIdempotencyPersistenceService;

import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;

import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;

import java.util.ArrayList;

import org.springframework.transaction.support.SmartTransactionObject;

class SubmissionServiceTest {
  private static final String ACTOR = "A".repeat(26),
      DEPT = "D".repeat(26),
      APPLICATION = "P".repeat(26),
      CANDIDATE = "C".repeat(26);
  private static final String DEMAND = "M".repeat(26),
      CLIENT = "L".repeat(26),
      PROJECT = "O".repeat(26),
      POSITION = "T".repeat(26);
  private static final String JD = "J".repeat(26),
      JD_VERSION = "V".repeat(26),
      RESUME = "R".repeat(26),
      SUBMISSION = "S".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");
  private final SubmissionPersistenceService persistence = mock(SubmissionPersistenceService.class);
  private final ClientRestrictionCurrentPersistenceService currentRestrictions =
      mock(ClientRestrictionCurrentPersistenceService.class);
  private final ClientRestrictionPersistenceService restrictions =
      mock(ClientRestrictionPersistenceService.class);
  private final RiskAcknowledgementPersistenceService acknowledgements =
      mock(RiskAcknowledgementPersistenceService.class);
  private final SendSnapshotPersistenceService snapshots =
      mock(SendSnapshotPersistenceService.class);
  private final SubmissionFeedbackPersistenceService feedback =
      mock(SubmissionFeedbackPersistenceService.class);
  private final SubmissionIdempotencyPersistenceService idempotency =
      mock(SubmissionIdempotencyPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final RecruitmentApplicationManager applications =
      mock(RecruitmentApplicationManager.class);
  private final DemandQueryService demands = mock(DemandQueryService.class);
  private final ResumeSubmissionQueryService resumes = mock(ResumeSubmissionQueryService.class);
  private final CompanyBlacklistQueryService blacklist = mock(CompanyBlacklistQueryService.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private SubmissionApplicationManager service;

  @BeforeEach
  void setUp() {
    when(authorization.currentActor())
        .thenReturn(
            new CurrentActorSnapshot(
                ACTOR,
                "HR",
                "hr@example.com",
                DEPT,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("RECRUITER"),
                Set.of("submission.read", "submission.manage"),
                false,
                false));
    when(authorization.canAccess(
            any(CurrentActorSnapshot.class), eq("submission.read"), any(AuthorizationTarget.class)))
        .thenReturn(true);
    when(ids.newId()).thenReturn(SUBMISSION, "I".repeat(26), "K".repeat(26));
    when(persistence.insertIdempotency(any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.findCandidateId(any())).thenReturn(CANDIDATE);
    service = new SubmissionApplicationManager();
    ReflectionTestUtils.setField(service, "persistence", persistence);
    ReflectionTestUtils.setField(
        service, "clientRestrictionCurrentPersistenceService", currentRestrictions);
    ReflectionTestUtils.setField(service, "clientRestrictionPersistenceService", restrictions);
    ReflectionTestUtils.setField(service, "riskAcknowledgementPersistenceService", acknowledgements);
    ReflectionTestUtils.setField(service, "sendSnapshotPersistenceService", snapshots);
    ReflectionTestUtils.setField(service, "submissionFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(service, "submissionIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(service, "authorization", authorization);
    ReflectionTestUtils.setField(service, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(service, "applications", applications);
    ReflectionTestUtils.setField(service, "demands", demands);
    ReflectionTestUtils.setField(service, "resumes", resumes);
    ReflectionTestUtils.setField(service, "blacklist", blacklist);
    ReflectionTestUtils.setField(service, "ids", ids);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(
        service, "cursorCodec", new SignedCursorCodec("submission-test-signing-key-32-bytes"));
  }

  private ApplicationClientRestrictionDTO applicationRestriction() {
    var app = new Application();
    app.setId(APPLICATION);
    app.setCandidateId(CANDIDATE);
    when(applications.requireLockedTerminationAccess(APPLICATION)).thenReturn(app);
    ReflectionTestUtils.setField(service, "demandCatalog", mock(DemandCatalogApplicationManager.class));
    ReflectionTestUtils.setField(service, "assembler", new SubmissionAssembler());
    ReflectionTestUtils.setField(service, "activityOrigins", mock(ActivityOriginRecorder.class));
    when(persistence.insertRestrictionProjection(any(), any(), any(), eq(true), any(), any(), any())).thenReturn(1);
    when(restrictions.save(any(ClientRestriction.class))).thenReturn(true);
    var command = new ApplicationClientRestrictionDTO();
    command.setClientId(CLIENT);
    command.setReasonDetail("候选人在该公司已有禁推要求");
    command.setSourceChannel("MANUAL");
    command.setEvidenceReference("已确认沟通记录");
    return command;
  }

  @Test
  void retiredSubmissionEntriesRejectCreationAndSending() {
    assertThatThrownBy(() -> service.create(new CreateSubmissionDTO(APPLICATION), "retired-create"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_PATH_STEP_INVALID"));
    assertThatThrownBy(() -> service.send("S".repeat(26), new SendSubmissionDTO("client", "MANUAL", "停用", 0), "retired-send"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_PATH_STEP_INVALID"));
  }

  @Test
  void applicationRestrictionRequiresProcessingAndCreatesNoSubmissionOrFeedback() {
    var result = service.restrictApplication(APPLICATION, applicationRestriction(), "application-ban-1");
    assertThat(result.getClientId()).isEqualTo(CLIENT);
    assertThat(result.getSourceApplicationId()).isEqualTo(APPLICATION);
    assertThat(result.getSourceSubmissionId()).isNull();
    assertThat(result.getEvidenceReference()).isEqualTo("已确认沟通记录");
    assertThat(result.getRestrictionIntent()).isEqualTo("DO_NOT_SUBMIT");
    verify(authorization).requirePermission("submission.manage");
    verify(applications).requireLockedTerminationAccess(APPLICATION);
    verify(persistence).insertRestrictionProjection(eq(CLIENT), eq(CANDIDATE), any(), eq(true), any(), eq(ACTOR), eq(NOW));
    verifyNoInteractions(feedback);
    verify(applications).decide(eq(APPLICATION), eq("REJECT"), eq("候选人在该公司已有禁推要求"), eq(null), any());
  }

  @Test
  void blacklistEndsOfferUsingTheExistingTerminationPath() {
    var command = applicationRestriction();
    var app = applications.requireLockedTerminationAccess(APPLICATION);
    app.setCurrentStage("OFFER");
    service.restrictApplication(APPLICATION, command, "offer-label");
    verify(applications).addOffer(eq(APPLICATION), argThat(value -> value.getStatus().equals("TERMINATED")
        && value.getTerminationReason().equals("COMPANY_WITHDREW") && value.getApplicationOutcome().equals("REJECT")), any());
    verify(applications, never()).decide(any(), any(), any(), any(), any());
  }

  @Test
  void existingTagCanBeAppendedForAnotherRecruitmentWithoutLosingHistory() {
    var command = applicationRestriction();
    var prior = new ClientRestriction();
    prior.setId("B".repeat(26));
    prior.setStatus("ACTIVE");
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    when(persistence.replaceRestrictionProjection(eq(CLIENT), eq(CANDIDATE), eq(prior.getId()), any(), eq(true), any(), eq(ACTOR), eq(NOW))).thenReturn(1);
    service.restrictApplication(APPLICATION, command, "another-label");
    verify(restrictions).save(argThat(value -> value.getCorrectsRecordId().equals(prior.getId())
        && value.getReasonDetail().equals(command.getReasonDetail())));
    verify(applications).decide(eq(APPLICATION), eq("REJECT"), eq(command.getReasonDetail()), eq(null), any());
  }

  @Test
  void failedTagProjectionCannotEndTheRecruitment() {
    var command = applicationRestriction();
    when(persistence.insertRestrictionProjection(any(), any(), any(), eq(true), any(), any(), any())).thenReturn(0);
    assertThatThrownBy(() -> service.restrictApplication(APPLICATION, command, "conflict-label"))
        .isInstanceOf(PlatformException.class);
    verify(applications, never()).decide(any(), any(), any(), any(), any());
  }

  @Test
  void applicationRestrictionAllowsMissingEvidence() {
    var command = applicationRestriction();
    command.setEvidenceReference(" ");
    assertThat(service.restrictApplication(APPLICATION, command, "application-ban-without-evidence")
        .getEvidenceReference()).isNull();
  }

  @Test
  void applicationRestrictionRequestAllowsMissingEvidence() {
    var request = new ApplicationClientRestrictionRequest();
    request.setClientId(CLIENT);
    request.setReasonDetail("候选人在该公司已有禁推要求");
    request.setSourceChannel("MANUAL");
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      assertThat(factory.getValidator().validate(request)).isEmpty();
    }
  }

  @Test
  void concurrentReplacementOfAnExistingTagDoesNotEndTheFlow() {
    var command = applicationRestriction();
    command.setEvidenceReference("证据");
    var prior = new ClientRestriction();
    prior.setId("B".repeat(26));
    prior.setStatus("ACTIVE");
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    assertThatThrownBy(() -> service.restrictApplication(APPLICATION, command, "application-ban-2"))
        .isInstanceOfSatisfying(PlatformException.class,
            e -> assertThat(e.code()).isEqualTo("CLIENT_RESTRICTION_NOT_CURRENT"));
    verify(applications, never()).decide(any(), any(), any(), any(), any());
  }

  @Test
  void applicationRestrictionRejectsOverlongOptionalEvidence() {
    var command = applicationRestriction();
    command.setEvidenceReference("证".repeat(1001));
    assertThatThrownBy(() -> service.restrictApplication(APPLICATION, command, "application-ban-long-evidence"))
        .isInstanceOfSatisfying(PlatformException.class,
            e -> assertThat(e.code()).isEqualTo("FIELD_TOO_LONG"));
  }

  @Test
  void applicationRestrictionDoesNotOverwriteConcurrentProjection() {
    var command = applicationRestriction();
    when(persistence.insertRestrictionProjection(any(), any(), any(), eq(true), any(), any(), any())).thenReturn(0);
    assertThatThrownBy(() -> service.restrictApplication(APPLICATION, command, "application-ban-race"))
        .isInstanceOfSatisfying(PlatformException.class,
            e -> assertThat(e.code()).isEqualTo("CLIENT_RESTRICTION_NOT_CURRENT"));
  }

  @Test
  void applicationRestrictionReplayDoesNotCreateAnotherFact() {
    var command = applicationRestriction();
    var first = service.restrictApplication(APPLICATION, command, "application-ban-replay");
    var action = ArgumentCaptor.forClass(String.class);
    var request = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertIdempotency(eq(ACTOR), eq("application-ban-replay"), action.capture(), request.capture(), any(), eq(NOW));
    var fact = new ClientRestriction();
    fact.setId(first.getId());
    when(restrictions.restriction(first.getId())).thenReturn(fact);
    var replay = new IdempotencyRecord();
    replay.setActionCode(action.getValue());
    replay.setRequestFingerprint(request.getValue());
    replay.setTargetId(first.getId());
    when(idempotency.idempotency(ACTOR, "application-ban-replay")).thenReturn(replay);
    assertThat(service.restrictApplication(APPLICATION, command, "application-ban-replay").getId()).isEqualTo(first.getId());
    verify(restrictions).save(any(ClientRestriction.class));
  }

  @Test
  void applicationRestrictionRejectsUnknownCompanyAndUnauthorizedProcessor() {
    var command = applicationRestriction();
    var catalog = mock(DemandCatalogApplicationManager.class);
    ReflectionTestUtils.setField(service, "demandCatalog", catalog);
    when(catalog.getClient(CLIENT)).thenThrow(PlatformExceptions.notFound("CLIENT_NOT_FOUND", "客户不存在"));
    assertThatThrownBy(() -> service.restrictApplication(APPLICATION, command, "missing-company"))
        .isInstanceOf(PlatformException.class);
    when(applications.requireLockedFollowUpAccess(APPLICATION))
        .thenThrow(PlatformExceptions.forbidden("RECRUITMENT_PROCESSING_FORBIDDEN", "无权处理"));
    assertThatThrownBy(() -> service.restrictApplication(APPLICATION, command, "no-access"))
        .isInstanceOf(PlatformException.class);
    verify(restrictions, never()).save(any(ClientRestriction.class));
  }

  @Test
  void disableApplicationRestrictionRetainsCompanyAndEvidenceWithoutSubmission() {
    applicationRestriction();
    var prior = new ClientRestriction();
    prior.setId("B".repeat(26));
    prior.setClientId(CLIENT);
    prior.setCandidateId(CANDIDATE);
    prior.setSourceApplicationId(APPLICATION);
    prior.setEvidenceReference("原始证据");
    when(restrictions.restriction(prior.getId())).thenReturn(prior);
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    when(persistence.replaceRestrictionProjection(eq(CLIENT), eq(CANDIDATE), eq(prior.getId()),
        any(), eq(false), any(), any(), any())).thenReturn(1);
    var result = service.disableRestriction(prior.getId(), "人工核实解除");
    assertThat(result.getStatus()).isEqualTo("DISABLED");
    assertThat(result.getSourceApplicationId()).isEqualTo(APPLICATION);
    assertThat(result.getEvidenceReference()).isEqualTo("原始证据");
    assertThat(result.getSourceSubmissionId()).isNull();
    verify(applications).requireLockedFollowUpAccess(APPLICATION);
    verify(persistence, never()).find(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void restrictionRevisionKeyReplaysOriginalFactAfterCurrentProjectionChanged(boolean correction) {
    applicationRestriction();
    var prior = new ClientRestriction();
    prior.setId("B".repeat(26));
    prior.setClientId(CLIENT);
    prior.setCandidateId(CANDIDATE);
    prior.setSourceApplicationId(APPLICATION);
    when(restrictions.restriction(prior.getId())).thenReturn(prior);
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    when(persistence.replaceRestrictionProjection(eq(CLIENT), eq(CANDIDATE), eq(prior.getId()),
        any(), eq(correction), any(), any(), any())).thenReturn(1);
    var change = new CorrectClientRestrictionDTO("DO_NOT_SUBMIT", null, "更正原因", "MANUAL", null);
    var disable = new DisableClientRestrictionDTO("停用原因");
    var first = correction ? service.correctRestriction(prior.getId(), change, "revision-once")
        : service.disableRestriction(prior.getId(), disable, "revision-once");
    var request = ArgumentCaptor.forClass(String.class);
    var action = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertIdempotency(eq(ACTOR), eq("revision-once"), action.capture(), request.capture(), eq(prior.getId()), eq(NOW));
    var reservation = new IdempotencyRecord();
    reservation.setActionCode(action.getValue()); reservation.setRequestFingerprint(request.getValue());
    reservation.setTargetId(prior.getId());
    when(idempotency.idempotency(ACTOR, "revision-once")).thenReturn(reservation);
    var saved = ArgumentCaptor.forClass(ClientRestriction.class);
    verify(restrictions).save(saved.capture());
    when(restrictions.revision(prior.getId(), ACTOR)).thenReturn(saved.getValue());
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(saved.getValue());
    var replay = correction ? service.correctRestriction(prior.getId(), change, "revision-once")
        : service.disableRestriction(prior.getId(), disable, "revision-once");
    assertThat(replay.getId()).isEqualTo(first.getId());
    verify(restrictions).save(any(ClientRestriction.class));
    assertThatThrownBy(() -> service.disableRestriction(prior.getId(), new DisableClientRestrictionDTO("其他原因"), "revision-once"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
  }

  @Test
  void concurrentRevisionReservationUsesCurrentReadForReplay() {
    applicationRestriction();
    var prior = new ClientRestriction(); prior.setId("B".repeat(26)); prior.setSourceApplicationId(APPLICATION);
    when(restrictions.restriction(prior.getId())).thenReturn(prior);
    var saved = new ClientRestriction(); saved.setId("E".repeat(26));
    when(restrictions.revision(prior.getId(), ACTOR)).thenReturn(saved);
    var winner = new AtomicReference<IdempotencyRecord>();
    when(persistence.insertIdempotency(eq(ACTOR), eq("concurrent-revision"), any(), any(), eq(prior.getId()), eq(NOW)))
        .thenAnswer(call -> {
          var record = new IdempotencyRecord(); record.setActionCode(call.getArgument(2));
          record.setRequestFingerprint(call.getArgument(3)); record.setTargetId(prior.getId());
          winner.set(record); return 0;
        });
    when(idempotency.idempotencyForUpdate(ACTOR, "concurrent-revision")).thenAnswer(call -> winner.get());
    assertThat(service.disableRestriction(prior.getId(), new DisableClientRestrictionDTO("已解除"), "concurrent-revision").getId())
        .isEqualTo(saved.getId());
    verify(idempotency).idempotencyForUpdate(ACTOR, "concurrent-revision");
    verify(restrictions, never()).save(any(ClientRestriction.class));
  }

  @Test
  void currentRestrictionQueryUsesProjectionInsteadOfLatestHistory() {
    applicationRestriction();
    var current = new ClientRestriction(); current.setId("B".repeat(26)); current.setStatus("CORRECTED");
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(current);
    assertThat(service.currentRestrictionResponses(CANDIDATE, CLIENT)).singleElement()
        .satisfies(value -> assertThat(value.getId()).isEqualTo(current.getId()));
    verify(restrictions, never()).restrictions(any(), any(), anyInt());
  }

  @Test
  void existingCompanyRestrictionDoesNotBlockCreatingARecommendation() {
    context();
    when(currentRestrictions.hasRestriction(CLIENT, CANDIDATE, NOW)).thenReturn(1);
    when(persistence.find(SUBMISSION)).thenReturn(submission("DRAFT", "NOT_STARTED", 0L));
    service.create(APPLICATION, "create-tagged");
    verify(persistence).insertSubmission(eq(SUBMISSION), eq(APPLICATION), eq(CANDIDATE), any(), eq(CLIENT),
        any(), any(), eq(JD), eq(JD_VERSION), eq(RESUME), eq(ACTOR), eq(DEPT), eq(ACTOR), eq(NOW));
    verifyNoInteractions(currentRestrictions);
  }

  @Test
  void sendPersistsImmutableResumeHashSnapshot() {
    ClientSubmission draft = submission("DRAFT", "NOT_STARTED", 0);
    when(persistence.lock(SUBMISSION)).thenReturn(draft);
    when(persistence.markSent(SUBMISSION, 0, ACTOR, NOW)).thenReturn(1);
    when(persistence.find(SUBMISSION)).thenReturn(submission("SENT", "SCREENING", 1));
    when(feedback.feedback(SUBMISSION)).thenReturn(List.of());
    when(acknowledgements.riskAcks(SUBMISSION)).thenReturn(List.of());
    when(resumes.requireSubmissionSnapshot(CANDIDATE, RESUME))
        .thenReturn(
            new ResumeSubmissionQueryService.ResumeSubmissionSnapshot(
                RESUME, CANDIDATE, "resume.pdf", "a".repeat(64)));
    when(snapshots.insertSendSnapshot(any(), eq(SUBMISSION), eq(RESUME), eq("client@example.com"),
        eq("EMAIL"), eq("resume.pdf"), eq("a".repeat(64)), eq(ACTOR), eq(NOW))).thenReturn(1);
    service.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 0, "send-1");
    verify(applications).bindSentClientSubmissionStep(APPLICATION, SUBMISSION);
    verify(snapshots)
        .insertSendSnapshot(
            any(),
            eq(SUBMISSION),
            eq(RESUME),
            eq("client@example.com"),
            eq("EMAIL"),
            eq("resume.pdf"),
            eq("a".repeat(64)),
            eq(ACTOR),
            eq(NOW));
  }

  @Test
  void ordinaryRejectionDoesNotCreateClientRestriction() {
    ClientSubmission sent = submission("SENT", "SCREENING", 2);
    when(persistence.lock(SUBMISSION)).thenReturn(sent);
    when(persistence.updateProcess(SUBMISSION, "REJECTED", 2, ACTOR, NOW)).thenReturn(1);
    Feedback saved =
        new Feedback(
            SUBMISSION,
            SUBMISSION,
            "REJECTED",
            "能力不匹配",
            "结束本次推荐",
            "普通拒绝",
            "EMAIL",
            NOW,
            ACTOR);
    when(feedback.feedback(SUBMISSION)).thenReturn(List.of(saved));
    service.addFeedback(
        SUBMISSION,
        "REJECTED",
        "能力不匹配",
        "结束本次推荐",
        "普通拒绝",
        "EMAIL",
        NOW,
        false,
        null,
        null,
        "feedback-1");
    verify(restrictions, never())
        .insertRestriction(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            any(), any());
  }

  @Test
  void exactValidationRejectsAClientMismatch() {
    when(persistence.find(SUBMISSION)).thenReturn(submission("SENT", "SCREENING", 2));
    ClientSubmissionValidationAdapter validation = new ClientSubmissionValidationAdapter();
    ReflectionTestUtils.setField(validation, "persistence", persistence);
    assertThatThrownBy(
            () ->
                validation.requireSentAndMatching(
                    SUBMISSION, APPLICATION, CANDIDATE, "B".repeat(26), JD, JD_VERSION, RESUME))
        .isInstanceOfSatisfying(
            PlatformException.class,
            e -> assertThat(e.code()).isEqualTo("CLIENT_SUBMISSION_MISMATCH"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void sendCannotCommitWithoutExactlyOneImmutableSnapshot(int snapshotRows) {
    context();
    when(persistence.lock(SUBMISSION)).thenReturn(submission("DRAFT", "NOT_STARTED", 0));
    ClientSubmission sent = submission("SENT", "SCREENING", 1);
    when(persistence.find(SUBMISSION)).thenReturn(sent);
    when(persistence.markSent(SUBMISSION, 0, ACTOR, NOW)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return 1;
    });
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return null;
    }).when(applications).bindSentClientSubmissionStep(APPLICATION, SUBMISSION);
    when(snapshots.insertSendSnapshot(eq(SUBMISSION), eq(SUBMISSION), eq(RESUME),
        eq("client@example.com"), eq("EMAIL"), eq("resume.pdf"), eq("a".repeat(64)),
        eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return snapshotRows;
        });
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(service);
    factory.addAdvice(interceptor);
    SubmissionApplicationManager proxy = (SubmissionApplicationManager) factory.getProxy();

    if (snapshotRows == 1) {
      assertThat(proxy.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 0,
          "snapshot-atomic").getSubmission()).isSameAs(sent);
      assertThat(transactions.committed).isOne();
      assertThat(transactions.rolledBack).isZero();
    } else {
      assertThatThrownBy(() -> proxy.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 0,
          "snapshot-atomic")).isInstanceOf(IllegalStateException.class).hasMessage("客户推荐发送快照写入失败");
      assertThat(transactions.rolledBack).isOne();
      assertThat(transactions.committed).isZero();
      verify(persistence, never()).find(SUBMISSION);
    }
    verify(persistence).markSent(SUBMISSION, 0, ACTOR, NOW);
    verify(applications).bindSentClientSubmissionStep(APPLICATION, SUBMISSION);
    verify(snapshots).insertSendSnapshot(SUBMISSION, SUBMISSION, RESUME, "client@example.com",
        "EMAIL", "resume.pdf", "a".repeat(64), ACTOR, NOW);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void staleRestrictionEditCannotOverwriteAConcurrentRecord(boolean correction) {
    String priorId = "B".repeat(26);
    String concurrentId = "N".repeat(26);
    ClientRestriction prior = new ClientRestriction();
    prior.setId(priorId);
    prior.setClientId(CLIENT);
    prior.setCandidateId(CANDIDATE);
    prior.setSourceSubmissionId(SUBMISSION);
    when(restrictions.restriction(priorId)).thenReturn(prior);
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    when(persistence.find(SUBMISSION)).thenReturn(submission("SENT", "REJECTED", 1));
    AtomicReference<String> currentId = new AtomicReference<>(priorId);
    when(restrictions.insertRestriction(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          // 模拟读取旧记录后、写投影前，另一事务已提交新的当前事实。
          currentId.set(concurrentId);
          return 1;
        });
    when(persistence.projectRestriction(any(), any(), any(), any(Boolean.class), any(), any(), any()))
        .thenAnswer(call -> {
          currentId.set(call.getArgument(2));
          return 2;
        });
    when(persistence.replaceRestrictionProjection(eq(CLIENT), eq(CANDIDATE), eq(priorId),
        eq(SUBMISSION), eq(correction), any(), eq(ACTOR), eq(NOW)))
        .thenAnswer(call -> currentId.compareAndSet(call.getArgument(2), call.getArgument(3)) ? 1 : 0);
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(service);
    factory.addAdvice(interceptor);
    SubmissionApplicationManager proxy = (SubmissionApplicationManager) factory.getProxy();

    assertThatThrownBy(() -> {
      if (correction) {
        proxy.correctRestriction(priorId, "CLIENT_EXPLICIT_BAN", null, "补充明确禁推证据", "EMAIL", NOW);
      } else {
        proxy.disableRestriction(priorId, "客户确认解除限制");
      }
    }).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("CLIENT_RESTRICTION_NOT_CURRENT"));
    assertThat(currentId).hasValue(concurrentId);
    assertThat(transactions.rolledBack).isOne();
    assertThat(transactions.committed).isZero();
    verify(restrictions, never()).restriction(SUBMISSION);
    verify(persistence, never()).projectRestriction(any(), any(), any(), any(Boolean.class), any(), any(), any());
    verify(persistence).replaceRestrictionProjection(CLIENT, CANDIDATE, priorId, SUBMISSION, correction, null, ACTOR, NOW);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void restrictionCorrectionRejectsFutureSourceTimeBeforeHistoryOrProjectionWrite(
      boolean applicationSource, boolean withKey) {
    ClientRestriction prior = currentRestrictionForCorrection(applicationSource);
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(service);
    factory.addAdvice(interceptor);
    SubmissionApplicationManager proxy = (SubmissionApplicationManager) factory.getProxy();
    var command = new CorrectClientRestrictionDTO(
        "CLIENT_EXPLICIT_BAN", null, "更正来源", "EMAIL", NOW.plusSeconds(301));

    assertThatThrownBy(() -> proxy.correctRestriction(
        prior.getId(), command, withKey ? "future-correction" : null))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("SUBMISSION_FEEDBACK_TIME_INVALID");
          assertThat(error.status().value()).isEqualTo(422);
        });
    assertThat(transactions.rolledBack).isOne();
    assertThat(transactions.committed).isZero();
    verify(restrictions, never()).save(any(ClientRestriction.class));
    verify(restrictions, never()).insertRestriction(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
    verify(persistence, never()).replaceRestrictionProjection(
        any(), any(), any(), any(), any(Boolean.class), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"false,-30", "false,0", "false,300", "false,", "true,-30", "true,0", "true,300", "true,"})
  void restrictionCorrectionPreservesSourceTimeDefaultAndClockSkewBoundary(
      boolean applicationSource, Long offset) {
    ClientRestriction prior = currentRestrictionForCorrection(applicationSource);
    Instant supplied = offset == null ? null : NOW.plusSeconds(offset);
    Instant expected = supplied == null ? NOW : supplied;

    service.correctRestriction(prior.getId(), "CLIENT_EXPLICIT_BAN", null,
        "更正来源", "EMAIL", supplied);

    if (applicationSource) {
      verify(restrictions).save(argThat(value -> expected.equals(value.getSourceOccurredAt())
          && prior.getId().equals(value.getCorrectsRecordId())
          && APPLICATION.equals(value.getSourceApplicationId())));
    } else {
      verify(restrictions).insertRestriction(SUBMISSION, CLIENT, CANDIDATE, SUBMISSION,
          "CORRECTION", "CORRECTED", "CLIENT_EXPLICIT_BAN", null,
          "更正来源", "EMAIL", expected, prior.getId(), ACTOR, NOW);
    }
    verify(persistence).replaceRestrictionProjection(
        CLIENT, CANDIDATE, prior.getId(), SUBMISSION, true, null, ACTOR, NOW);
  }

  private ClientRestriction currentRestrictionForCorrection(boolean applicationSource) {
    applicationRestriction();
    ClientRestriction prior = new ClientRestriction();
    prior.setId("B".repeat(26));
    prior.setClientId(CLIENT);
    prior.setCandidateId(CANDIDATE);
    if (applicationSource) prior.setSourceApplicationId(APPLICATION);
    else prior.setSourceSubmissionId(SUBMISSION);
    when(restrictions.restriction(prior.getId())).thenReturn(prior);
    ClientRestriction saved = new ClientRestriction();
    saved.setId(SUBMISSION);
    when(restrictions.restriction(SUBMISSION)).thenReturn(saved);
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    when(persistence.find(SUBMISSION)).thenReturn(submission("SENT", "REJECTED", 1));
    when(persistence.replaceRestrictionProjection(CLIENT, CANDIDATE, prior.getId(), SUBMISSION,
        true, null, ACTOR, NOW)).thenReturn(1);
    return prior;
  }

  @ParameterizedTest
  @CsvSource({"false,-1", "false,0", "false,3600", "false,", "true,3600", "true,"})
  void currentRestrictionEditAppendsHistoryAndReplacesOnlyItsOwnProjection(boolean correction, Long expiryOffset) {
    String priorId = "B".repeat(26);
    Instant expiry = expiryOffset == null ? null : NOW.plusSeconds(expiryOffset);
    ClientRestriction prior = new ClientRestriction();
    prior.setId(priorId);
    prior.setClientId(CLIENT);
    prior.setCandidateId(CANDIDATE);
    prior.setSourceSubmissionId(SUBMISSION);
    prior.setExpiresAt(expiry);
    ClientRestriction saved = new ClientRestriction();
    saved.setId(SUBMISSION);
    saved.setCorrectsRecordId(priorId);
    when(restrictions.restriction(priorId)).thenReturn(prior);
    when(restrictions.restriction(SUBMISSION)).thenReturn(saved);
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    when(persistence.find(SUBMISSION)).thenReturn(submission("SENT", "REJECTED", 1));
    when(restrictions.insertRestriction(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.replaceRestrictionProjection(CLIENT, CANDIDATE, priorId, SUBMISSION,
        correction, expiry, ACTOR, NOW)).thenReturn(1);
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(service);
    factory.addAdvice(interceptor);
    SubmissionApplicationManager proxy = (SubmissionApplicationManager) factory.getProxy();

    ClientRestriction result = correction
        ? proxy.correctRestriction(priorId, "CLIENT_EXPLICIT_BAN", expiry, "更正来源", "EMAIL", NOW)
        : proxy.disableRestriction(priorId, "客户确认解除限制");

    assertThat(result).isSameAs(saved);
    assertThat(transactions.committed).isOne();
    assertThat(transactions.rolledBack).isZero();
    verify(restrictions).insertRestriction(SUBMISSION, CLIENT, CANDIDATE, SUBMISSION,
        correction ? "CORRECTION" : "DISABLE", correction ? "CORRECTED" : "DISABLED",
        correction ? "CLIENT_EXPLICIT_BAN" : "DISABLED", expiry,
        correction ? "更正来源" : "客户确认解除限制", correction ? "EMAIL" : "MANUAL", NOW, priorId, ACTOR, NOW);
    verify(persistence).replaceRestrictionProjection(CLIENT, CANDIDATE, priorId, SUBMISSION,
        correction, expiry, ACTOR, NOW);
    verify(persistence, never()).projectRestriction(any(), any(), any(), any(Boolean.class), any(), any(), any());
  }

  @ParameterizedTest
  @CsvSource({"false,-1", "false,0", "true,-1", "true,0"})
  void activeRestrictionCannotBeCreatedOrCorrectedWithAnExpiredDeadline(boolean correction, long expiryOffset) {
    String priorId = "B".repeat(26);
    ClientRestriction prior = new ClientRestriction();
    prior.setId(priorId);
    prior.setClientId(CLIENT);
    prior.setCandidateId(CANDIDATE);
    prior.setSourceSubmissionId(SUBMISSION);
    when(restrictions.restriction(priorId)).thenReturn(prior);
    when(persistence.currentRestriction(CLIENT, CANDIDATE)).thenReturn(prior);
    ClientSubmission sent = submission("SENT", "SCREENING", 1);
    when(persistence.find(SUBMISSION)).thenReturn(sent);
    when(persistence.lock(SUBMISSION)).thenReturn(sent);
    when(persistence.updateProcess(SUBMISSION, "REJECTED", 1, ACTOR, NOW)).thenReturn(1);

    assertThatThrownBy(() -> {
      if (correction) {
        service.correctRestriction(priorId, "CLIENT_EXPLICIT_BAN", NOW.plusSeconds(expiryOffset), "更正禁推证据", "EMAIL", NOW);
      } else {
        service.addFeedback(SUBMISSION, "REJECTED", "明确禁推", "结束本次推荐", "客户明确拒绝未来推荐",
            "EMAIL", NOW, true, NOW.plusSeconds(expiryOffset), "CLIENT_EXPLICIT_BAN", "expired-restriction");
      }
    }).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("CLIENT_RESTRICTION_EXPIRY_INVALID"));
    verify(restrictions, never()).insertRestriction(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
    verify(persistence, never()).projectRestriction(any(), any(), any(), any(Boolean.class), any(), any(), any());
    verify(persistence, never()).replaceRestrictionProjection(any(), any(), any(), any(), any(Boolean.class), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successfulFeedbackReplaysAfterWithdrawalWithoutNewWrites(boolean explicitTime) {
    String key = "feedback-before-withdrawal";
    Instant suppliedTime = explicitTime ? NOW : null;
    ClientSubmission sent = submission("SENT", "SCREENING", 2);
    when(persistence.lock(SUBMISSION)).thenReturn(sent);
    when(persistence.updateProcess(SUBMISSION, "REJECTED", 2, ACTOR, NOW)).thenReturn(1);
    Feedback saved = new Feedback(SUBMISSION, SUBMISSION, "REJECTED", "能力不匹配",
        "结束本次推荐", "普通拒绝", "EMAIL", NOW, ACTOR);
    when(feedback.feedback(SUBMISSION)).thenReturn(List.of(saved));
    when(feedback.insertFeedback(SUBMISSION, SUBMISSION, "REJECTED", "能力不匹配",
        "结束本次推荐", "普通拒绝", "EMAIL", NOW, ACTOR, NOW)).thenReturn(1);
    AtomicReference<IdempotencyRecord> reservation = new AtomicReference<>();
    when(idempotency.idempotency(ACTOR, key)).thenAnswer(call -> reservation.get());
    when(persistence.insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(SUBMISSION), eq(NOW)))
        .thenAnswer(call -> {
          reservation.set(new IdempotencyRecord(ACTOR, key, call.getArgument(2),
              call.getArgument(3), SUBMISSION));
          return 1;
        });
    assertThat(service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "普通拒绝", "EMAIL", suppliedTime, false, null, null, key)).isSameAs(saved);
    sent.setDeliveryStatus("WITHDRAWN");
    sent.setVersion(4L);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));

    assertThat(service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "普通拒绝", "EMAIL", suppliedTime, false, null, null, key)).isSameAs(saved);
    assertThatThrownBy(() -> service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "修改后的拒绝", "EMAIL", suppliedTime, false, null, null, key))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    assertThatThrownBy(() -> service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "普通拒绝", "EMAIL", suppliedTime, false, null, null, "new-feedback-after-withdrawal"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SUBMISSION_NOT_SENT"));
    when(authorization.canAccess(any(CurrentActorSnapshot.class), eq("submission.read"),
        any(AuthorizationTarget.class))).thenReturn(false);
    assertThatThrownBy(() -> service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "普通拒绝", "EMAIL", suppliedTime, false, null, null, key))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SUBMISSION_ACCESS_FORBIDDEN"));
    verify(feedback).insertFeedback(SUBMISSION, SUBMISSION, "REJECTED", "能力不匹配",
        "结束本次推荐", "普通拒绝", "EMAIL", NOW, ACTOR, NOW);
    verify(persistence).updateProcess(any(), any(), any(Long.class), any(), any());
    verify(restrictions, never()).insertRestriction(any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"UNKNOWN", "CLIENT_BAN", "NO_FUTURE_SUBMISSION"})
  void unsupportedRestrictionIntentIsRejectedBeforeAnyFeedbackOrRestrictionWrite(String intent) {
    ClientSubmission sent = submission("SENT", "SCREENING", 1);
    when(persistence.lock(SUBMISSION)).thenReturn(sent);
    when(persistence.updateProcess(SUBMISSION, "REJECTED", 1, ACTOR, NOW)).thenReturn(1);
    Feedback saved = new Feedback(SUBMISSION, SUBMISSION, "REJECTED", "明确禁推", "结束推荐",
        "客户明确拒绝未来推荐", "EMAIL", NOW, ACTOR);
    when(feedback.feedback(SUBMISSION)).thenReturn(List.of(saved));
    assertThatThrownBy(() -> service.addFeedback(SUBMISSION, "REJECTED", "明确禁推", "结束推荐",
        "客户明确拒绝未来推荐", "EMAIL", NOW, true, null, intent, "invalid-intent"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CLIENT_RESTRICTION_INTENT_INVALID"));
    assertThatThrownBy(() -> service.correctRestriction("B".repeat(26), intent, null,
        "更正禁推证据", "EMAIL", NOW)).isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CLIENT_RESTRICTION_INTENT_INVALID"));
    verify(persistence, never()).updateProcess(any(), any(), any(Long.class), any(), any());
    verify(feedback, never()).insertFeedback(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(restrictions, never()).insertRestriction(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"DO_NOT_SUBMIT", "CLIENT_EXPLICIT_BAN"})
  void pageRestrictionIntentsArePersistedWithoutChangingTheirMeaning(String intent) {
    when(persistence.lock(SUBMISSION)).thenReturn(submission("SENT", "SCREENING", 1));
    when(persistence.updateProcess(SUBMISSION, "REJECTED", 1, ACTOR, NOW)).thenReturn(1);
    Feedback saved = new Feedback(SUBMISSION, SUBMISSION, "REJECTED", "明确禁推", "结束推荐",
        "客户明确拒绝未来推荐", "EMAIL", NOW, ACTOR);
    when(feedback.feedback(SUBMISSION)).thenReturn(List.of(saved));
    when(feedback.insertFeedback(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(restrictions.insertRestriction(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any())).thenReturn(1);
    when(persistence.projectRestriction(CLIENT, CANDIDATE, "I".repeat(26), true, null, ACTOR, NOW)).thenReturn(1);

    assertThat(service.addFeedback(SUBMISSION, "REJECTED", "明确禁推", "结束推荐", "客户明确拒绝未来推荐",
        "EMAIL", NOW, true, null, intent, "valid-intent")).isSameAs(saved);
    verify(restrictions).insertRestriction("I".repeat(26), CLIENT, CANDIDATE, SUBMISSION,
        "ACTIVATION", "ACTIVE", intent, null, "客户明确拒绝未来推荐", "EMAIL", NOW, null, ACTOR, NOW);
    verify(persistence).projectRestriction(CLIENT, CANDIDATE, "I".repeat(26), true, null, ACTOR, NOW);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void feedbackReplayUsesRequestTimeInsteadOfRetryClock(boolean explicitTime) {
    String key = "submission-feedback-stable-time";
    Instant suppliedTime = explicitTime ? NOW.minusSeconds(30) : null;
    Instant storedTime = explicitTime ? suppliedTime : NOW;
    when(persistence.lock(SUBMISSION)).thenReturn(submission("SENT", "SCREENING", 2));
    when(persistence.updateProcess(SUBMISSION, "REJECTED", 2, ACTOR, NOW)).thenReturn(1);
    Feedback saved = new Feedback(SUBMISSION, SUBMISSION, "REJECTED", "能力不匹配",
        "结束本次推荐", "普通拒绝", "EMAIL", storedTime, ACTOR);
    when(feedback.feedback(SUBMISSION)).thenReturn(List.of(saved));
    when(feedback.insertFeedback(SUBMISSION, SUBMISSION, "REJECTED", "能力不匹配",
        "结束本次推荐", "普通拒绝", "EMAIL", storedTime, ACTOR, NOW)).thenReturn(1);
    AtomicReference<IdempotencyRecord> reservation = new AtomicReference<>();
    when(idempotency.idempotency(ACTOR, key)).thenAnswer(call -> reservation.get());
    when(persistence.insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(SUBMISSION), eq(NOW)))
        .thenAnswer(call -> {
          reservation.set(new IdempotencyRecord(ACTOR, key, call.getArgument(2),
              call.getArgument(3), SUBMISSION));
          return 1;
        });
    assertThat(service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "普通拒绝", "EMAIL", suppliedTime, false, null, null, key)).isSameAs(saved);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));

    assertThat(service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "普通拒绝", "EMAIL", suppliedTime, false, null, null, key)).isSameAs(saved);
    assertThatThrownBy(() -> service.addFeedback(SUBMISSION, "REJECTED", "能力不匹配", "结束本次推荐",
        "普通拒绝", "EMAIL", NOW.plusSeconds(60), false, null, null, key))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    verify(feedback).insertFeedback(SUBMISSION, SUBMISSION, "REJECTED", "能力不匹配",
        "结束本次推荐", "普通拒绝", "EMAIL", storedTime, ACTOR, NOW);
    verify(persistence).updateProcess(SUBMISSION, "REJECTED", 2, ACTOR, NOW);
    verify(persistence).insertIdempotency(any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"application", "candidate", "client", "jd", "jdVersion", "resume", "sentAt", "withdrawn"})
  void interviewValidationRejectsTheCurrentLockedRecommendationWhenAnyBindingHasChanged(String mismatch) {
    ClientSubmission current = submission("SENT", "INTERVIEWING", 3L);
    switch (mismatch) {
      case "application" -> current.setApplicationId("B".repeat(26));
      case "candidate" -> current.setCandidateId("B".repeat(26));
      case "client" -> current.setClientId("B".repeat(26));
      case "jd" -> current.setJdId("B".repeat(26));
      case "jdVersion" -> current.setJdVersionId("B".repeat(26));
      case "resume" -> current.setResumeVersionId("B".repeat(26));
      case "sentAt" -> current.setSentAt(null);
      case "withdrawn" -> current.setDeliveryStatus("WITHDRAWN");
      default -> throw new AssertionError(mismatch);
    }
    when(persistence.lock(SUBMISSION)).thenReturn(current);
    when(persistence.find(SUBMISSION)).thenReturn(submission("SENT", "SCREENING", 2L));
    ClientSubmissionValidationAdapter validation = new ClientSubmissionValidationAdapter();
    ReflectionTestUtils.setField(validation, "persistence", persistence);

    assertThatThrownBy(() -> validation.requireLockedSentAndMatching(
        SUBMISSION, APPLICATION, CANDIDATE, CLIENT, JD, JD_VERSION, RESUME))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CLIENT_SUBMISSION_MISMATCH"));
    verify(persistence, never()).find(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SCREENING", "INTERVIEWING"})
  void successfulWithdrawalCanBeReplayedAfterItsStateHasChanged(String process) {
    ClientSubmission value = submission("SENT", process, 2L);
    when(persistence.lock(SUBMISSION)).thenReturn(value);
    when(persistence.find(SUBMISSION)).thenReturn(value);
    AtomicReference<IdempotencyRecord> reservation = new AtomicReference<>();
    when(idempotency.idempotency(ACTOR, "withdraw-once")).thenAnswer(call -> reservation.get());
    when(persistence.insertIdempotency(eq(ACTOR), eq("withdraw-once"), any(), any(), any(), eq(NOW)))
        .thenAnswer(call -> {
          reservation.set(new IdempotencyRecord(ACTOR, "withdraw-once", call.getArgument(2),
              call.getArgument(3), call.getArgument(4)));
          return 1;
        });
    when(persistence.withdraw(SUBMISSION, "甲方取消", ACTOR, 2L, NOW)).thenAnswer(call -> {
      value.setDeliveryStatus("WITHDRAWN");
      value.setProcessStatus("WITHDRAWN");
      value.setVersion(3L);
      return 1;
    });

    assertThat(service.withdraw(SUBMISSION, "甲方取消", 2L, "withdraw-once")).isNotNull();
    assertThat(value.getDeliveryStatus()).isEqualTo("WITHDRAWN");
    assertThat(service.withdraw(SUBMISSION, "甲方取消", 2L, "withdraw-once")).isNotNull();
    assertThatThrownBy(() -> service.withdraw(SUBMISSION, "改写原因", 2L, "withdraw-once"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    verify(persistence).withdraw(SUBMISSION, "甲方取消", ACTOR, 2L, NOW);
    verify(applications).withdrawBoundClientStep(APPLICATION, SUBMISSION, "甲方取消");
  }

  @Test
  void aNewCommandCannotWithdrawAnAlreadyWithdrawnRecommendation() {
    when(persistence.lock(SUBMISSION)).thenReturn(submission("WITHDRAWN", "WITHDRAWN", 3L));

    assertThatThrownBy(() -> service.withdraw(SUBMISSION, "新命令", 3L, "another-withdrawal"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("SUBMISSION_WITHDRAWAL_NOT_ALLOWED"));
    verify(persistence, never()).withdraw(any(), any(), any(), ArgumentMatchers.anyLong(), any());
    verify(applications, never()).withdrawBoundClientStep(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SCREENING", "REJECTED", "WITHDRAWN"})
  void successfulSendReplaysWithoutRepeatingDeliveryOrBypassingCurrentAccess(String laterStatus) {
    context();
    ClientSubmission value = submission("DRAFT", "NOT_STARTED", 0L);
    when(persistence.lock(SUBMISSION)).thenReturn(value);
    when(persistence.find(SUBMISSION)).thenReturn(value);
    AtomicReference<IdempotencyRecord> reservation = new AtomicReference<>();
    when(idempotency.idempotency(ACTOR, "send-once")).thenAnswer(call -> reservation.get());
    when(persistence.insertIdempotency(eq(ACTOR), eq("send-once"), any(), any(), any(), eq(NOW)))
        .thenAnswer(call -> {
          reservation.set(new IdempotencyRecord(ACTOR, "send-once", call.getArgument(2), call.getArgument(3), call.getArgument(4)));
          return 1;
        });
    when(persistence.markSent(SUBMISSION, 0L, ACTOR, NOW)).thenAnswer(call -> {
      value.setDeliveryStatus("SENT"); value.setProcessStatus("SCREENING"); value.setSentAt(NOW); value.setVersion(1L);
      return 1;
    });
    SendSnapshot snapshot = new SendSnapshot("N".repeat(26), SUBMISSION, RESUME, "client@example.com", "EMAIL",
        "resume.pdf", "a".repeat(64), ACTOR, NOW);
    when(snapshots.snapshot(SUBMISSION)).thenReturn(snapshot);
    when(snapshots.insertSendSnapshot(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    assertThat(service.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 0L, "send-once").getSendSnapshot())
        .isSameAs(snapshot);
    value.setProcessStatus(laterStatus);
    if (laterStatus.equals("WITHDRAWN")) {
      value.setDeliveryStatus("WITHDRAWN"); value.setWithdrawnAt(NOW.plusSeconds(60));
    }
    value.setVersion(2L);
    when(blacklist.isCompanyBlacklisted(CANDIDATE)).thenReturn(true);
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "成功后生效的公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(CANDIDATE);
    when(currentRestrictions.hasRestriction(CLIENT, CANDIDATE, NOW)).thenReturn(1);

    assertThat(service.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 0L, "send-once").getSendSnapshot())
        .isSameAs(snapshot);
    assertThatThrownBy(() -> service.send(SUBMISSION, "different@example.com", "EMAIL", "客户确认接收", 0L, "send-once"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    assertThatThrownBy(() -> service.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 2L, "new-send"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("SUBMISSION_NOT_DRAFT"));
    when(authorization.canAccess(any(CurrentActorSnapshot.class), eq("submission.read"), any(AuthorizationTarget.class)))
        .thenReturn(false);
    assertThatThrownBy(() -> service.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 0L, "send-once"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("SUBMISSION_ACCESS_FORBIDDEN"));
    verify(persistence).markSent(SUBMISSION, 0L, ACTOR, NOW);
    verify(applications).bindSentClientSubmissionStep(APPLICATION, SUBMISSION);
    verify(snapshots).insertSendSnapshot(any(), eq(SUBMISSION), eq(RESUME), eq("client@example.com"), eq("EMAIL"),
        eq("resume.pdf"), eq("a".repeat(64)), eq(ACTOR), eq(NOW));
    verify(blacklist).requireRecruitmentAllowed(CANDIDATE);
    verifyNoInteractions(currentRestrictions);
  }

  @Test
  void companyBlacklistBlocksSendingAndEligibility() {
    context();
    var draft = submission("DRAFT", "NOT_STARTED", 0L);
    when(persistence.lock(SUBMISSION)).thenReturn(draft);
    when(persistence.find(SUBMISSION)).thenReturn(draft);
    CompanyBlacklistQueryService blocked = mock(CompanyBlacklistQueryService.class, call -> {
      if (call.getMethod().getName().equals("isCompanyBlacklisted")) return true;
      if (call.getMethod().getName().equals("requireRecruitmentAllowed")) {
        throw PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单阻断推荐");
      }
      return Mockito.RETURNS_DEFAULTS.answer(call);
    });
    ReflectionTestUtils.setField(service, "blacklist", blocked);
    when(persistence.markSent(SUBMISSION, 0L, ACTOR, NOW)).thenReturn(1);
    when(snapshots.insertSendSnapshot(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    assertThatThrownBy(() -> service.send(SUBMISSION, "client@example.com", "EMAIL", "客户确认接收", 0L, "tagged-send"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verify(persistence, never()).markSent(any(), anyLong(), any(), any());
    verify(applications, never()).bindSentClientSubmissionStep(any(), any());
    var eligibility = service.eligibility(APPLICATION);
    assertThat(eligibility.isCompanyBlacklist()).isTrue();
    assertThat(eligibility.isEligible()).isFalse();
    assertThat(eligibility.getBlockingCodes()).contains("COMPANY_BLACKLIST_BLOCKED");
  }

  private void context() {
    when(applications.requireSubmissionContext(APPLICATION))
        .thenReturn(
            new SubmissionApplicationContext(
                APPLICATION,
                CANDIDATE,
                DEMAND,
                JD,
                JD_VERSION,
                RESUME,
                "EXTERNAL",
                "IN_PROCESS",
                ACTOR,
                DEPT,
                Set.of()));
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION))
        .thenReturn(
            new DemandQueryService.SubmissionDemandContext(
                DEMAND, CLIENT, PROJECT, POSITION, "EXTERNAL", ACTOR, DEPT));
    when(resumes.requireSubmissionSnapshot(CANDIDATE, RESUME))
        .thenReturn(
            new ResumeSubmissionQueryService.ResumeSubmissionSnapshot(
                RESUME, CANDIDATE, "resume.pdf", "a".repeat(64)));
  }

  private ClientSubmission submission(String delivery, String process, long version) {
    return new ClientSubmission(
        SUBMISSION,
        APPLICATION,
        CANDIDATE,
        DEMAND,
        CLIENT,
        PROJECT,
        POSITION,
        JD,
        JD_VERSION,
        RESUME,
        delivery,
        process,
        "SENT".equals(delivery) ? NOW : null,
        null,
        null,
        null,
        ACTOR,
        DEPT,
        version);
  }

  @Test
  void jdClientIsEnoughForSubmissionWithoutDemandOrPosition() {
    context();
    when(applications.requireSubmissionContext(APPLICATION)).thenReturn(new SubmissionApplicationContext(
        APPLICATION, CANDIDATE, null, JD, JD_VERSION, RESUME, "EXTERNAL", "IN_PROCESS", ACTOR, DEPT, Set.of()));
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(
        new DemandQueryService.SubmissionDemandContext(null, CLIENT, null, null, "EXTERNAL", ACTOR, DEPT));
    when(persistence.find(SUBMISSION)).thenReturn(submission("DRAFT", "NOT_STARTED", 0));

    service.create(APPLICATION, "jd-only-submission");

    verify(persistence).insertSubmission(any(), eq(APPLICATION), eq(CANDIDATE), eq(null), eq(CLIENT),
        eq(null), eq(null), eq(JD), eq(JD_VERSION), eq(RESUME), eq(ACTOR), eq(DEPT), eq(ACTOR), eq(NOW));
    verify(demands, never()).requireSubmissionContext(any());
  }

  @Test
  void missingJdClientStillRejectsExternalSubmission() {
    context();
    when(demands.requireSubmissionContextForJdVersion(JD_VERSION)).thenReturn(
        new DemandQueryService.SubmissionDemandContext(null, null, null, null, "EXTERNAL", ACTOR, DEPT));

    assertThatThrownBy(() -> service.create(APPLICATION, "missing-jd-client"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CLIENT_BINDING_INCOMPLETE"));
    verify(persistence, never()).insertSubmission(any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "SCREENING-wide-revoked-after", "INTERVIEWING-wide-revoked-after",
      "SCREENING-wide-active-after", "INTERVIEWING-wide-active-after",
      "SCREENING-narrow-active-after", "INTERVIEWING-narrow-active-after",
      "SCREENING-narrow-owner-after", "INTERVIEWING-narrow-owner-after",
      "SCREENING-narrow-denied-before", "INTERVIEWING-narrow-denied-before"})
  void publicTerminalSubmissionWithdrawalRechecksCurrentApplicationParticipants(String mode) throws Exception {
    String process = mode.startsWith("SCREENING-") ? "SCREENING" : "INTERVIEWING";
    boolean wide = mode.contains("-wide-"), revoked = mode.contains("-revoked-");
    boolean owner = mode.contains("-owner-"), initialDenied = mode.endsWith("-before");
    String appOwner = owner ? ACTOR : "B".repeat(26), submissionOwner = initialDenied ? "B".repeat(26) : ACTOR;
    String key = "terminal-submission-withdraw-current-members", reason = "甲方确认撤回旧推荐";
    Instant createdAt = NOW.minusSeconds(7200), sentAt = NOW.minusSeconds(3600);
    Instant terminalAt = NOW.minusSeconds(600), winnerAt = NOW.minusSeconds(60);
    IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
    ObjectGrantPersistenceService grants = mock(ObjectGrantPersistenceService.class);
    UserSupportDepartmentPersistenceService support = mock(UserSupportDepartmentPersistenceService.class);
    UserAccountRecord account = new UserAccountRecord();
    account.setId(ACTOR); account.setDisplayName("HR"); account.setWorkEmail("hr@example.com");
    account.setHomeDepartmentId(DEPT); account.setStatus("ACTIVE");
    when(accounts.findById(ACTOR)).thenReturn(account);
    when(accounts.findRoleCodes(ACTOR)).thenReturn(List.of(wide ? "HR_DIRECTOR" : "RECRUITER"));
    when(support.findSupportDepartmentIds(ACTOR)).thenReturn(List.of());
    CurrentActorProvider provider = () -> Optional.of(new CurrentActor(ACTOR,
        Set.of("submission.manage", "submission.read", "recruitment.manage"), false));
    IdentityAuthorizationAdapter realAuthorization = new IdentityAuthorizationAdapter();
    ReflectionTestUtils.setField(realAuthorization, "actorProvider", provider);
    ReflectionTestUtils.setField(realAuthorization, "accountService", accounts);
    ReflectionTestUtils.setField(realAuthorization, "objectGrantPersistenceService", grants);
    ReflectionTestUtils.setField(realAuthorization, "userSupportDepartmentPersistenceService", support);
    assertThat(realAuthorization.currentActor().hasCompanyWideScope()).isEqualTo(wide);
    assertThat(realAuthorization.currentActor().getManagedUserIds()).isEmpty();
    assertThat(realAuthorization.currentActor().isSuperAdmin()).isFalse();
    if (!wide) assertThat(realAuthorization.currentActor().visibleDepartmentIdsFor("APPLICATION")).isEmpty();
    SharedRestrictionTransactions transactions = new SharedRestrictionTransactions();
    ApplicationPersistenceService applicationFacts = mock(ApplicationPersistenceService.class);
    ApplicationCollaboratorPersistenceService collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    IdentityDirectoryService directory = mock(IdentityDirectoryService.class);
    RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
    InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
    InterviewNotificationApplicationManager notifications = mock(InterviewNotificationApplicationManager.class);
    StageHistoryPersistenceService stageHistory = mock(StageHistoryPersistenceService.class);
    FollowUpPlanLifecycleManager followUpPlans = mock(FollowUpPlanLifecycleManager.class);
    ActivityOriginRecorder origins = mock(ActivityOriginRecorder.class);
    Application terminal = new Application();
    terminal.setId(APPLICATION); terminal.setCandidateId(CANDIDATE); terminal.setDemandId(DEMAND);
    terminal.setJdId(JD); terminal.setJdVersionId(JD_VERSION); terminal.setResumeVersionId(RESUME);
    terminal.setRecruitmentType("EXTERNAL"); terminal.setCurrentStage("ONBOARDED"); terminal.setOutcome("PASS");
    terminal.setTerminalAt(terminalAt); terminal.setActiveSlot(null); terminal.setOwnerUserId(appOwner);
    terminal.setOwnerDepartmentId(DEPT); terminal.setVersion(0L); terminal.setCreateId(appOwner);
    terminal.setUpdateId(appOwner); terminal.setCreateTime(createdAt); terminal.setUpdateTime(createdAt);
    AtomicReference<Application> storedApp = new AtomicReference<>(terminal), snapshotApp = new AtomicReference<>();
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(List.of(ACTOR)), snapshotMembers = new AtomicReference<>();
    AtomicReference<byte[]> terminalAfterWinner = new AtomicReference<>();
    AtomicBoolean modeledOwnerWriter = new AtomicBoolean(false);
    List<String> afterMembers = revoked || owner ? List.of() : List.of(ACTOR, "V".repeat(26));
    List<List<Object>> winnerChanges = new ArrayList<>(), reservations = new ArrayList<>(), withdrawals = new ArrayList<>();
    ObjectMapper mapper = new ObjectMapper();
    doAnswer(call -> {
      assertThat(modeledOwnerWriter.get()).isTrue(); transactions.events.add("winner-active-users");
      return afterMembers.stream().collect(Collectors.toMap(id -> id,
          id -> new IdentityDirectoryService.UserReference(id, "启用HR", DEPT)));
    }).when(directory).activeUsers(Set.copyOf(afterMembers));
    doAnswer(call -> { assertThat(modeledOwnerWriter.get()).isTrue(); storedMembers.set(List.of()); return 1; })
        .when(collaborators).deleteCollaborators(APPLICATION);
    doAnswer(call -> { assertThat(storedMembers.get()).isEmpty(); storedMembers.set(List.copyOf(afterMembers)); return true; })
        .when(collaborators).insertCollaborators(APPLICATION, afterMembers, appOwner, winnerAt);
    doAnswer(call -> {
      assertThat(modeledOwnerWriter.get()).isTrue(); assertThat(storedApp.get().getVersion()).isZero();
      Application changed = new Application(); BeanUtils.copyProperties(storedApp.get(), changed);
      changed.setVersion(1L); changed.setUpdateId(appOwner); changed.setUpdateTime(winnerAt); storedApp.set(changed); return 1;
    }).when(applicationFacts).updateStage(APPLICATION, "ONBOARDED", 0L, appOwner, winnerAt);
    doAnswer(call -> { winnerChanges.add(Arrays.asList(call.getArguments())); return 1; })
        .when(applicationFacts).insertCollaboratorChange(any(), eq(APPLICATION), any(), any(), any(), eq(0L), eq(appOwner), eq(winnerAt));
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isNull();
      if (modeledOwnerWriter.get()) { transactions.events.add("modeled-owner-app-lock"); return storedApp.get(); }
      assertThat(transactions.events).contains("command-reservation", "submission-withdrawal", "modeled-owner-full-commit");
      transactions.read("application-lock"); return storedApp.get();
    }).when(applicationFacts).lockApplication(APPLICATION);
    Runnable commitOwner = () -> {
      assertThat(transactions.events).doesNotContain("application-lock"); assertThat(winnerChanges).isEmpty();
      modeledOwnerWriter.set(true);
      Application ownerLocked = applicationFacts.lockApplication(APPLICATION);
      assertThat(ownerLocked.getVersion()).isZero();
      CurrentActorSnapshot configuringOwner = new CurrentActorSnapshot(appOwner, "负责人", "owner@example.com", DEPT,
          Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("recruitment.manage"), false, false);
      assertThat(realAuthorization.canAccess(configuringOwner, "recruitment.manage",
          new AuthorizationTarget("APPLICATION", APPLICATION, appOwner, DEPT, Set.copyOf(storedMembers.get())))).isTrue();
      List<String> before = List.copyOf(storedMembers.get()); directory.activeUsers(Set.copyOf(afterMembers));
      assertThat(collaborators.deleteCollaborators(APPLICATION)).isEqualTo(1);
      assertThat(collaborators.insertCollaborators(APPLICATION, afterMembers, appOwner, winnerAt)).isTrue();
      assertThat(applicationFacts.updateStage(APPLICATION, "ONBOARDED", 0L, appOwner, winnerAt)).isEqualTo(1);
      assertThat(applicationFacts.insertCollaboratorChange("K".repeat(26), APPLICATION, JsonText.write(before),
          JsonText.write(afterMembers), "合规协作人配置变更", 0L, appOwner, winnerAt)).isEqualTo(1);
      modeledOwnerWriter.set(false); transactions.events.add("modeled-owner-full-commit");
    };
    doAnswer(call -> { transactions.read("ordinary-members"); return List.copyOf(snapshotMembers.get()); })
        .when(collaborators).findCollaborators(APPLICATION);
    doAnswer(call -> {
      assertThat(transactions.events).contains("application-lock"); transactions.read("current-members"); return List.copyOf(storedMembers.get());
    }).when(collaborators).findCollaboratorsForUpdate(APPLICATION);
    ClientSubmission initial = new ClientSubmission();
    initial.setId(SUBMISSION); initial.setApplicationId(APPLICATION); initial.setCandidateId(CANDIDATE);
    initial.setDemandId(DEMAND); initial.setClientId(CLIENT); initial.setProjectId(PROJECT); initial.setPositionId(POSITION);
    initial.setJdId(JD); initial.setJdVersionId(JD_VERSION); initial.setResumeVersionId(RESUME);
    initial.setDeliveryStatus("SENT"); initial.setProcessStatus(process); initial.setSentAt(sentAt);
    initial.setOwnerUserId(submissionOwner); initial.setOwnerDepartmentId("E".repeat(26)); initial.setVersion(2L);
    initial.setCreateId(submissionOwner); initial.setUpdateId(submissionOwner); initial.setCreateTime(createdAt); initial.setUpdateTime(sentAt);
    AtomicReference<ClientSubmission> storedSubmission = new AtomicReference<>(initial);
    assertThat(realAuthorization.canAccess("submission.read", new AuthorizationTarget(
        "CLIENT_SUBMISSION", SUBMISSION, submissionOwner, "E".repeat(26), Set.of()))).isEqualTo(!initialDenied);
    assertThat(realAuthorization.canAccess("recruitment.manage", new AuthorizationTarget(
        "APPLICATION", APPLICATION, appOwner, DEPT, Set.of(ACTOR)))).isTrue();
    doAnswer(call -> { transactions.read("submission-lock"); return storedSubmission.get(); }).when(persistence).lock(SUBMISSION);
    doAnswer(call -> {
      transactions.read("ordinary-key-miss"); assertThat(snapshotMembers.get()).isNull();
      Application snapshot = new Application(); BeanUtils.copyProperties(storedApp.get(), snapshot); snapshotApp.set(snapshot);
      snapshotMembers.set(List.copyOf(storedMembers.get())); commitOwner.run();
      terminalAfterWinner.set(mapper.writeValueAsBytes(storedApp.get())); return null;
    }).when(idempotency).idempotency(ACTOR, key);
    doAnswer(call -> {
      assertThat(transactions.events).contains("ordinary-key-miss", "modeled-owner-full-commit").doesNotContain("application-lock");
      reservations.add(Arrays.asList(call.getArguments())); transactions.write("command-reservation"); return 1;
    }).when(persistence).insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(SUBMISSION), eq(NOW));
    doAnswer(call -> {
      assertThat(transactions.events).contains("command-reservation").doesNotContain("application-lock");
      assertThat(storedSubmission.get().getVersion()).isEqualTo(2L);
      assertThat(storedSubmission.get().getDeliveryStatus()).isEqualTo("SENT");
      assertThat(storedSubmission.get().getProcessStatus()).isEqualTo(process);
      ClientSubmission changed = new ClientSubmission(); BeanUtils.copyProperties(storedSubmission.get(), changed);
      changed.setDeliveryStatus("WITHDRAWN"); changed.setWithdrawnAt(NOW); changed.setWithdrawnByUserId(ACTOR);
      changed.setWithdrawalReason(reason); changed.setUpdateId(ACTOR); changed.setUpdateTime(NOW); changed.setVersion(3L);
      storedSubmission.set(changed); withdrawals.add(Arrays.asList(call.getArguments())); transactions.write("submission-withdrawal"); return 1;
    }).when(persistence).withdraw(SUBMISSION, reason, ACTOR, 2L, NOW);
    doAnswer(call -> {
      assertThat(transactions.events).contains("application-lock", "submission-withdrawal");
      transactions.read("own-submission-reread"); return storedSubmission.get();
    }).when(persistence).find(SUBMISSION);
    SendSnapshot sent = new SendSnapshot(); sent.setId("I".repeat(26)); sent.setSubmissionId(SUBMISSION);
    sent.setResumeVersionId(RESUME); sent.setRecipient("client@example.com"); sent.setChannel("EMAIL");
    sent.setFileName("frozen-resume.pdf"); sent.setContentSha256("a".repeat(64)); sent.setSentByUserId(submissionOwner);
    sent.setSentAt(sentAt); sent.setCreateId(submissionOwner); sent.setUpdateId(submissionOwner);
    sent.setCreateTime(sentAt); sent.setUpdateTime(sentAt); sent.setVersion(0);
    Feedback priorFeedback = new Feedback(); priorFeedback.setId("F".repeat(26)); priorFeedback.setSubmissionId(SUBMISSION);
    priorFeedback.setResult(process); priorFeedback.setReasonCategory("CLIENT_REVIEW"); priorFeedback.setNextAction("继续跟进");
    priorFeedback.setNote("保留原反馈"); priorFeedback.setSourceChannel("EMAIL"); priorFeedback.setSourceOccurredAt(sentAt.plusSeconds(120));
    priorFeedback.setRecordedByUserId(submissionOwner); priorFeedback.setCreateId(submissionOwner); priorFeedback.setUpdateId(submissionOwner);
    priorFeedback.setCreateTime(sentAt.plusSeconds(120)); priorFeedback.setUpdateTime(sentAt.plusSeconds(120)); priorFeedback.setVersion(0);
    RiskAcknowledgement risk = new RiskAcknowledgement(); risk.setId("Q".repeat(26)); risk.setSubmissionId(SUBMISSION);
    risk.setRiskCode("PRIOR_CLIENT_PROCESS"); risk.setReason("人工已确认历史状态"); risk.setAcknowledgedByUserId(submissionOwner);
    risk.setCreateId(submissionOwner); risk.setUpdateId(submissionOwner); risk.setCreateTime(sentAt); risk.setUpdateTime(sentAt); risk.setVersion(0);
    byte[] sentBefore = mapper.writeValueAsBytes(sent), feedbackBefore = mapper.writeValueAsBytes(priorFeedback), riskBefore = mapper.writeValueAsBytes(risk);
    when(snapshots.snapshot(SUBMISSION)).thenReturn(sent); when(feedback.feedback(SUBMISSION)).thenReturn(List.of(priorFeedback));
    when(acknowledgements.riskAcks(SUBMISSION)).thenReturn(List.of(risk));
    RecruitmentApplicationManager applicationManager = new RecruitmentApplicationManager();
    ReflectionTestUtils.setField(applicationManager, "persistence", applicationFacts);
    ReflectionTestUtils.setField(applicationManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(applicationFacts.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(applicationManager, "applicationCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(applicationManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(applicationManager, "recruitmentWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(applicationManager, "interviewPersistenceService", interviews);
    ReflectionTestUtils.setField(applicationManager, "interviewNotificationManager", notifications);
    ReflectionTestUtils.setField(applicationManager, "stageHistoryPersistenceService", stageHistory);
    ReflectionTestUtils.setField(applicationManager, "followUpPlanLifecycle", followUpPlans);
    ReflectionTestUtils.setField(applicationManager, "activityOrigins", origins);
    ReflectionTestUtils.setField(applicationManager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ProxyFactory applicationFactory = new ProxyFactory(applicationManager); applicationFactory.setProxyTargetClass(true);
    TransactionInterceptor applicationInterceptor = new TransactionInterceptor(); applicationInterceptor.setTransactionManager(transactions);
    applicationInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); applicationFactory.addAdvice(applicationInterceptor);
    ReflectionTestUtils.setField(service, "applications", applicationFactory.getProxy());
    ReflectionTestUtils.setField(service, "authorization", realAuthorization); ReflectionTestUtils.setField(service, "assembler", new SubmissionAssembler());
    ProxyFactory submissionFactory = new ProxyFactory(service); submissionFactory.setProxyTargetClass(true);
    TransactionInterceptor submissionInterceptor = new TransactionInterceptor(); submissionInterceptor.setTransactionManager(transactions);
    submissionInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); submissionFactory.addAdvice(submissionInterceptor);
    SubmissionApplicationManager publicProxy = (SubmissionApplicationManager) submissionFactory.getProxy();
    if (initialDenied || revoked) {
      assertThatThrownBy(() -> publicProxy.withdraw(SUBMISSION, new WithdrawSubmissionDTO(reason, 2L), key))
          .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo(
              initialDenied ? "SUBMISSION_ACCESS_FORBIDDEN" : "RECRUITMENT_PROCESSING_FORBIDDEN"));
      assertThat(transactions.commitCount).isZero(); assertThat(transactions.rollbackCount).isOne();
      assertThat(transactions.state.rollbackOnly).isEqualTo(revoked);
      verify(persistence, never()).find(SUBMISSION); verifyNoInteractions(snapshots, feedback, acknowledgements);
      if (initialDenied) {
        assertThat(reservations).isEmpty(); assertThat(withdrawals).isEmpty(); assertThat(winnerChanges).isEmpty();
        assertThat(transactions.joinedCount).isZero(); assertThat(transactions.events).containsExactly("submission-lock");
        verifyNoInteractions(idempotency, applicationFacts, collaborators, directory);
        verify(persistence, never()).withdraw(any(), any(), any(), anyLong(), any());
        verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
      } else {
        assertThat(transactions.joinedCount).isGreaterThanOrEqualTo(1);
        assertThat(transactions.events).containsSubsequence("submission-lock", "ordinary-key-miss", "modeled-owner-full-commit",
            "command-reservation", "submission-withdrawal", "application-lock", "current-members");
        verify(collaborators).findCollaboratorsForUpdate(APPLICATION); verify(collaborators, never()).findCollaborators(APPLICATION);
      }
    } else {
      SubmissionDetailsResponse response = publicProxy.withdraw(SUBMISSION, new WithdrawSubmissionDTO(reason, 2L), key);
      assertThat(transactions.commitCount).isOne(); assertThat(transactions.rollbackCount).isZero();
      assertThat(transactions.state.rollbackOnly).isFalse(); assertThat(transactions.joinedCount).isGreaterThanOrEqualTo(1);
      assertThat(response.getSubmission()).extracting("id", "applicationId", "candidateId", "demandId", "clientId", "projectId",
          "positionId", "jdId", "jdVersionId", "resumeVersionId", "deliveryStatus", "processStatus", "sentAt", "withdrawnAt",
          "withdrawnByUserId", "withdrawalReason", "ownerUserId", "ownerDepartmentId", "createId", "version", "createTime", "updateTime")
          .containsExactly(SUBMISSION, APPLICATION, CANDIDATE, DEMAND, CLIENT, PROJECT, POSITION, JD, JD_VERSION, RESUME,
              "WITHDRAWN", process, sentAt, NOW, ACTOR, reason, submissionOwner, "E".repeat(26), submissionOwner, "3", createdAt, NOW);
      assertThat(response.getSendSnapshot()).extracting("id", "submissionId", "resumeVersionId", "recipient", "channel", "fileName",
          "contentSha256", "sentByUserId", "sentAt").containsExactly("I".repeat(26), SUBMISSION, RESUME, "client@example.com", "EMAIL",
              "frozen-resume.pdf", "a".repeat(64), submissionOwner, sentAt);
      assertThat(response.getFeedback()).singleElement().extracting("id", "submissionId", "result", "reasonCategory", "nextAction", "note",
          "sourceChannel", "sourceOccurredAt", "recordedByUserId", "createTime").containsExactly("F".repeat(26), SUBMISSION, process,
              "CLIENT_REVIEW", "继续跟进", "保留原反馈", "EMAIL", sentAt.plusSeconds(120), submissionOwner, sentAt.plusSeconds(120));
      assertThat(response.getRiskAcknowledgements()).singleElement().extracting("id", "submissionId", "riskCode", "reason",
          "acknowledgedByUserId", "createTime").containsExactly("Q".repeat(26), SUBMISSION, "PRIOR_CLIENT_PROCESS",
              "人工已确认历史状态", submissionOwner, sentAt);
      assertThat(transactions.state.committed).containsExactly("command-reservation", "submission-withdrawal");
      assertThat(transactions.events).containsSubsequence("submission-lock", "ordinary-key-miss", "modeled-owner-full-commit",
          "command-reservation", "submission-withdrawal", "application-lock", "own-submission-reread");
      verify(persistence).find(SUBMISSION);
      if (transactions.events.contains("current-members")) {
        verify(collaborators).findCollaboratorsForUpdate(APPLICATION); verify(collaborators, never()).findCollaborators(APPLICATION);
      } else {
        // 旧生产代码的健康对照也能通过；锁后撤权的失败才是待证明的业务 RED。
        verify(collaborators, never()).findCollaboratorsForUpdate(APPLICATION);
      }
    }
    if (!initialDenied) {
      assertThat(reservations).containsExactly(Arrays.asList(ACTOR, key, "SUBMISSION_WITHDRAW",
          Sha256Utils.fingerprintObjects(31, SUBMISSION, reason, 2L), SUBMISSION, NOW));
      assertThat(withdrawals).containsExactly(Arrays.asList(SUBMISSION, reason, ACTOR, 2L, NOW));
      assertThat(winnerChanges).containsExactly(Arrays.asList("K".repeat(26), APPLICATION, JsonText.write(List.of(ACTOR)),
          JsonText.write(afterMembers), "合规协作人配置变更", 0L, appOwner, winnerAt));
      assertThat(snapshotApp.get().getVersion()).isZero(); assertThat(snapshotMembers.get()).containsExactly(ACTOR);
      assertThat(storedMembers.get()).isEqualTo(afterMembers); assertThat(mapper.writeValueAsBytes(storedApp.get())).isEqualTo(terminalAfterWinner.get());
      assertThat(storedApp.get()).extracting("currentStage", "outcome", "terminalAt", "activeSlot", "version", "ownerUserId",
          "ownerDepartmentId", "createId", "updateId", "createTime", "updateTime").containsExactly("ONBOARDED", "PASS", terminalAt,
              null, 1L, appOwner, DEPT, appOwner, appOwner, createdAt, winnerAt);
      assertThat(storedSubmission.get().getProcessStatus()).isEqualTo(process);
      assertThat(storedSubmission.get().getSentAt()).isEqualTo(sentAt);
      assertThat(storedSubmission.get().getVersion()).isEqualTo(3L); assertThat(storedSubmission.get().getUpdateId()).isEqualTo(ACTOR);
      verify(applicationFacts, never()).findApplication(APPLICATION);
      // 模型的行对象不会被事务句柄恢复；不把句柄回滚当作真实数据库的物理回滚证据。
    }
    assertThat(mapper.writeValueAsBytes(sent)).isEqualTo(sentBefore);
    assertThat(mapper.writeValueAsBytes(priorFeedback)).isEqualTo(feedbackBefore);
    assertThat(mapper.writeValueAsBytes(risk)).isEqualTo(riskBefore);
    assertThat(transactions.beginCount).isOne(); assertThat(transactions.outerIsolation).isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(transactions.state.active).isFalse(); assertThat(transactions.state.pending).isEmpty();
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    verifyNoInteractions(steps, interviews, notifications, stageHistory, followUpPlans, origins);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "SCREENING-owner-change-after-ordinary-view", "INTERVIEWING-owner-change-after-ordinary-view",
      "SCREENING-no-owner-version-change", "INTERVIEWING-no-owner-version-change",
      "SCREENING-owner-change-before-ordinary-view", "INTERVIEWING-owner-change-before-ordinary-view",
      "SCREENING-no-active-client-step", "INTERVIEWING-no-active-client-step",
      "SCREENING-already-PATH_SELECTION", "INTERVIEWING-already-PATH_SELECTION"})
  void publicNonterminalSubmissionWithdrawalUsesLockedApplicationStageVersion(String mode) throws Exception {
    String process = mode.startsWith("SCREENING-") ? "SCREENING" : "INTERVIEWING";
    boolean beforeView = mode.endsWith("owner-change-before-ordinary-view");
    boolean noOwnerChange = mode.endsWith("no-owner-version-change");
    boolean noActive = mode.endsWith("no-active-client-step"), alreadyPath = mode.endsWith("already-PATH_SELECTION");
    boolean scheduled = process.equals("INTERVIEWING") && !noActive;
    String appOwner = "B".repeat(26), submissionOwner = ACTOR;
    String key = "nonterminal-submission-withdraw-locked-version", reason = "甲方确认收尾当前推荐";
    String activeId = "Z".repeat(26), historicalId = "H".repeat(26), otherId = "Y".repeat(26);
    String otherSubmission = "U".repeat(26), scheduledId = "E".repeat(26), completedId = "W".repeat(26);
    Instant createdAt = NOW.minusSeconds(7200), sentAt = NOW.minusSeconds(3600), winnerAt = NOW.minusSeconds(60);
    Instant priorCleanupAt = NOW.minusSeconds(1200);
    long initialVersion = alreadyPath ? 1L : 0L;
    String initialStage = alreadyPath ? "PATH_SELECTION" : "IN_PROCESS";
    // 兼容的外部推荐行独立保持 SENT/SCREENING 或 INTERVIEWING；不是已停用的新 send DTO。
        IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
    ObjectGrantPersistenceService grants = mock(ObjectGrantPersistenceService.class);
    UserSupportDepartmentPersistenceService support = mock(UserSupportDepartmentPersistenceService.class);
    UserAccountRecord account = new UserAccountRecord();
    account.setId(ACTOR); account.setDisplayName("HR"); account.setWorkEmail("hr@example.com");
    account.setHomeDepartmentId(DEPT); account.setStatus("ACTIVE");
    when(accounts.findById(ACTOR)).thenReturn(account);
    when(accounts.findRoleCodes(ACTOR)).thenReturn(List.of("HR_DIRECTOR"));
    when(support.findSupportDepartmentIds(ACTOR)).thenReturn(List.of());
    CurrentActorProvider provider = () -> Optional.of(new CurrentActor(ACTOR,
        Set.of("submission.manage", "submission.read", "recruitment.manage"), false));
    IdentityAuthorizationAdapter realAuthorization = new IdentityAuthorizationAdapter();
    ReflectionTestUtils.setField(realAuthorization, "actorProvider", provider);
    ReflectionTestUtils.setField(realAuthorization, "accountService", accounts);
    ReflectionTestUtils.setField(realAuthorization, "objectGrantPersistenceService", grants);
    ReflectionTestUtils.setField(realAuthorization, "userSupportDepartmentPersistenceService", support);
    assertThat(realAuthorization.currentActor().hasCompanyWideScope()).isTrue();
    assertThat(realAuthorization.currentActor().getManagedUserIds()).isEmpty();
    assertThat(realAuthorization.currentActor().isSuperAdmin()).isFalse();
    SharedRestrictionTransactions transactions = new SharedRestrictionTransactions();
    ApplicationPersistenceService applicationFacts = mock(ApplicationPersistenceService.class);
    ApplicationCollaboratorPersistenceService collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    IdentityDirectoryService directory = mock(IdentityDirectoryService.class);
    RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
    InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
    StageHistoryPersistenceService stageHistory = mock(StageHistoryPersistenceService.class);
    FollowUpPlanLifecycleManager followUpPlans = mock(FollowUpPlanLifecycleManager.class);
    ObjectMapper mapper = new ObjectMapper();
    Application initialApp = new Application();
    initialApp.setId(APPLICATION); initialApp.setCandidateId(CANDIDATE); initialApp.setDemandId(DEMAND);
    initialApp.setJdId(JD); initialApp.setJdVersionId(JD_VERSION); initialApp.setResumeVersionId(RESUME);
    initialApp.setWorkflowTemplateVersionId("1".repeat(26)); initialApp.setMatchPlanVersionId("M".repeat(26));
    initialApp.setProgressiveFollowUp(true);
    initialApp.setRecruitmentType("EXTERNAL"); initialApp.setCurrentStage(initialStage);
    initialApp.setOutcome(null); initialApp.setTerminalAt(null); initialApp.setActiveSlot(1L);
    initialApp.setOwnerUserId(appOwner); initialApp.setOwnerDepartmentId(DEPT); initialApp.setVersion(initialVersion);
    initialApp.setCreateId(appOwner); initialApp.setCreateTime(createdAt); initialApp.setUpdateId(appOwner);
    initialApp.setUpdateTime(alreadyPath ? priorCleanupAt : createdAt);
    AtomicReference<Application> storedApp = new AtomicReference<>(initialApp), snapshotApp = new AtomicReference<>();
    AtomicReference<Application> lockedApp = new AtomicReference<>(), beforeOwnStage = new AtomicReference<>();
    AtomicReference<List<String>> storedMembers = new AtomicReference<>(List.of(ACTOR)), snapshotMembers = new AtomicReference<>();
    AtomicBoolean modeledOwnerWriter = new AtomicBoolean(false), ownStageWritten = new AtomicBoolean(false);
    List<String> afterMembers = List.of(ACTOR, "V".repeat(26));
    List<List<Object>> winnerChanges = new ArrayList<>(), stageAttempts = new ArrayList<>();
    List<List<Object>> reservations = new ArrayList<>(), withdrawals = new ArrayList<>();
    doAnswer(call -> {
      assertThat(modeledOwnerWriter.get()).isTrue(); transactions.events.add("winner-active-users");
      return afterMembers.stream().collect(Collectors.toMap(id -> id,
          id -> new IdentityDirectoryService.UserReference(id, "启用HR", DEPT)));
    }).when(directory).activeUsers(Set.copyOf(afterMembers));
    doAnswer(call -> { assertThat(modeledOwnerWriter.get()).isTrue(); storedMembers.set(List.of()); return 1; })
        .when(collaborators).deleteCollaborators(APPLICATION);
    doAnswer(call -> { assertThat(storedMembers.get()).isEmpty(); storedMembers.set(List.copyOf(afterMembers)); return true; })
        .when(collaborators).insertCollaborators(APPLICATION, afterMembers, appOwner, winnerAt);
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()).isNull();
      if (modeledOwnerWriter.get()) { transactions.events.add("modeled-owner-app-lock"); return storedApp.get(); }
      assertThat(transactions.events).contains("owner-window-closed", "command-reservation", "submission-withdrawal");
      transactions.read("application-lock"); lockedApp.set(storedApp.get()); return lockedApp.get();
    }).when(applicationFacts).lockApplication(APPLICATION);
    doAnswer(call -> {
      String stage = call.getArgument(1), actor = call.getArgument(3); long expected = call.getArgument(2);
      Instant time = call.getArgument(4); Application stored = storedApp.get();
      if (modeledOwnerWriter.get()) {
        assertThat(Arrays.asList(call.getArguments())).containsExactly(APPLICATION, initialStage, initialVersion, appOwner, winnerAt);
        assertThat(stored.getVersion()).isEqualTo(initialVersion);
      } else {
        transactions.read("stage-cas");
        stageAttempts.add(Arrays.asList(stage, expected, stored.getVersion(), expected == stored.getVersion() ? 1 : 0, actor, time));
        assertThat(stage).isEqualTo("PATH_SELECTION"); assertThat(actor).isEqualTo(ACTOR); assertThat(time).isEqualTo(NOW);
        if (expected != stored.getVersion()) return 0;
        beforeOwnStage.set(stored); ownStageWritten.set(true); transactions.write("application-stage");
      }
      Application changed = new Application(); BeanUtils.copyProperties(stored, changed);
      changed.setCurrentStage(stage); changed.setVersion(stored.getVersion() + 1);
      changed.setUpdateId(actor); changed.setUpdateTime(time); storedApp.set(changed); return 1;
    }).when(applicationFacts).updateStage(eq(APPLICATION), any(), anyLong(), any(), any());
    doAnswer(call -> { winnerChanges.add(Arrays.asList(call.getArguments())); return 1; })
        .when(applicationFacts).insertCollaboratorChange(any(), eq(APPLICATION), any(), any(), any(), eq(initialVersion), eq(appOwner), eq(winnerAt));
    Runnable commitOwner = () -> {
      assertThat(transactions.events).doesNotContain("application-lock"); assertThat(winnerChanges).isEmpty();
      modeledOwnerWriter.set(true); Application winnerLocked = applicationFacts.lockApplication(APPLICATION);
      assertThat(winnerLocked.getVersion()).isEqualTo(initialVersion);
      CurrentActorSnapshot configuringOwner = new CurrentActorSnapshot(appOwner, "负责人", "owner@example.com", DEPT,
          Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"), Set.of("recruitment.manage"), false, false);
      assertThat(realAuthorization.canAccess(configuringOwner, "recruitment.manage",
          new AuthorizationTarget("APPLICATION", APPLICATION, appOwner, DEPT, Set.copyOf(storedMembers.get())))).isTrue();
      List<String> prior = List.copyOf(storedMembers.get()); directory.activeUsers(Set.copyOf(afterMembers));
      assertThat(collaborators.deleteCollaborators(APPLICATION)).isEqualTo(1);
      assertThat(collaborators.insertCollaborators(APPLICATION, afterMembers, appOwner, winnerAt)).isTrue();
      assertThat(applicationFacts.updateStage(APPLICATION, initialStage, initialVersion, appOwner, winnerAt)).isEqualTo(1);
      assertThat(applicationFacts.insertCollaboratorChange("K".repeat(26), APPLICATION, JsonText.write(prior),
          JsonText.write(afterMembers), "合规协作人配置变更", initialVersion, appOwner, winnerAt)).isEqualTo(1);
      modeledOwnerWriter.set(false); transactions.events.add("modeled-owner-full-commit");
    };
    doAnswer(call -> { transactions.read("ordinary-members"); return List.copyOf(snapshotMembers.get()); })
        .when(collaborators).findCollaborators(APPLICATION);
    doAnswer(call -> {
      assertThat(transactions.events).contains("application-lock"); transactions.read("current-members");
      return List.copyOf(storedMembers.get());
    }).when(collaborators).findCollaboratorsForUpdate(APPLICATION);
    doAnswer(call -> {
      transactions.read("ordinary-application-projection");
      // 对自己的 Application 写可以读己之写；没有写过 App 时仍是旧普通视图，不能返回锁后行冒充。
      return ownStageWritten.get() ? storedApp.get() : snapshotApp.get();
    }).when(applicationFacts).findApplication(APPLICATION);
    ClientSubmission initial = new ClientSubmission();
    initial.setId(SUBMISSION); initial.setApplicationId(APPLICATION); initial.setCandidateId(CANDIDATE);
    initial.setDemandId(DEMAND); initial.setClientId(CLIENT); initial.setProjectId(PROJECT); initial.setPositionId(POSITION);
    initial.setJdId(JD); initial.setJdVersionId(JD_VERSION); initial.setResumeVersionId(RESUME);
    initial.setDeliveryStatus("SENT"); initial.setProcessStatus(process); initial.setSentAt(sentAt);
    initial.setOwnerUserId(submissionOwner); initial.setOwnerDepartmentId("E".repeat(26)); initial.setVersion(2L);
    initial.setCreateId(submissionOwner); initial.setUpdateId(submissionOwner); initial.setCreateTime(createdAt); initial.setUpdateTime(sentAt);
    AtomicReference<ClientSubmission> storedSubmission = new AtomicReference<>(initial);
    doAnswer(call -> { transactions.read("submission-lock"); return storedSubmission.get(); }).when(persistence).lock(SUBMISSION);
    doAnswer(call -> {
      transactions.read("ordinary-key-miss"); assertThat(snapshotApp.get()).isNull();
      if (beforeView) commitOwner.run();
      Application snapshot = new Application(); BeanUtils.copyProperties(storedApp.get(), snapshot); snapshotApp.set(snapshot);
      snapshotMembers.set(List.copyOf(storedMembers.get()));
      if (!noOwnerChange && !beforeView) commitOwner.run();
      transactions.events.add("owner-window-closed"); return null;
    }).when(idempotency).idempotency(ACTOR, key);
    doAnswer(call -> {
      assertThat(transactions.events).contains("ordinary-key-miss", "owner-window-closed").doesNotContain("application-lock");
      reservations.add(Arrays.asList(call.getArguments())); transactions.write("command-reservation"); return 1;
    }).when(persistence).insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(SUBMISSION), eq(NOW));
    doAnswer(call -> {
      assertThat(transactions.events).contains("command-reservation").doesNotContain("application-lock");
      assertThat(storedSubmission.get().getVersion()).isEqualTo(2L);
      ClientSubmission changed = new ClientSubmission(); BeanUtils.copyProperties(storedSubmission.get(), changed);
      changed.setDeliveryStatus("WITHDRAWN"); changed.setWithdrawnAt(NOW); changed.setWithdrawnByUserId(ACTOR);
      changed.setWithdrawalReason(reason); changed.setUpdateId(ACTOR); changed.setUpdateTime(NOW); changed.setVersion(3L);
      storedSubmission.set(changed); withdrawals.add(Arrays.asList(call.getArguments())); transactions.write("submission-withdrawal"); return 1;
    }).when(persistence).withdraw(SUBMISSION, reason, ACTOR, 2L, NOW);
    doAnswer(call -> {
      assertThat(transactions.events).contains("application-lock", "submission-withdrawal");
      transactions.read("own-submission-reread"); return storedSubmission.get();
    }).when(persistence).find(SUBMISSION);
        WorkflowStep historical = new WorkflowStep();
    historical.setId(historicalId); historical.setApplicationId(APPLICATION); historical.setStepCode("CLIENT_AI_INTERVIEW");
    historical.setTemplateVersionId("1".repeat(26)); historical.setStepDefinitionId("2".repeat(26));
    historical.setSequenceNo(2); historical.setStatus("PASSED"); historical.setClientSubmissionId(SUBMISSION);
    historical.setOwnerUserId(appOwner); historical.setCompletedAt(sentAt.plusSeconds(120));
    historical.setResultReason("保留已完成客户步骤事实"); historical.setVersion(3L);
    historical.setCreateId(appOwner); historical.setUpdateId(appOwner); historical.setCreateTime(createdAt);
    historical.setUpdateTime(sentAt.plusSeconds(120));
    WorkflowStep completedClient = new WorkflowStep(); BeanUtils.copyProperties(historical, completedClient);
    completedClient.setId("X".repeat(26)); completedClient.setStepCode("CLIENT_SUBMISSION");
    completedClient.setStepDefinitionId("8".repeat(26)); completedClient.setSequenceNo(1);
    completedClient.setCompletedAt(sentAt); completedClient.setUpdateTime(sentAt); completedClient.setResultReason("保留已完成客户推荐步骤");
    WorkflowStep active = new WorkflowStep(); BeanUtils.copyProperties(historical, active);
    active.setId(activeId); active.setSequenceNo(3);
    active.setStepDefinitionId((process.equals("SCREENING") ? "8" : "2").repeat(26));
    active.setStepCode(process.equals("SCREENING") ? "CLIENT_SUBMISSION" : "CLIENT_AI_INTERVIEW");
    active.setStatus(process.equals("SCREENING") ? "PENDING" : "IN_PROGRESS");
    active.setClientSubmissionId(process.equals("SCREENING") ? SUBMISSION : null);
    active.setStartedAt(process.equals("SCREENING") ? null : sentAt.plusSeconds(180));
    active.setCompletedAt(null); active.setResultReason(null); active.setVersion(4L); active.setUpdateTime(sentAt);
    WorkflowStep other = new WorkflowStep(); BeanUtils.copyProperties(active, other);
    other.setId(otherId); other.setStepDefinitionId("8".repeat(26)); other.setSequenceNo(4); other.setStepCode("CLIENT_SUBMISSION");
    other.setClientSubmissionId(otherSubmission); other.setStatus(alreadyPath ? "WITHDRAWN" : "PENDING");
    other.setStartedAt(null); other.setCompletedAt(alreadyPath ? priorCleanupAt : null);
    other.setResultReason(alreadyPath ? "另一条独立推荐先前合法撤回" : null); other.setVersion(alreadyPath ? 1L : 0L);
    other.setUpdateTime(alreadyPath ? priorCleanupAt : sentAt);
    // alreadyPath 来自另一推荐的旧收尾0→1；当前推荐步骤没有被其 findFirst 撤回，不能用新selectPath伪造。
    // noActive 仅当前S没有匹配活动步骤，其他推荐仍有合法PENDING步骤，申请不是空活动。
    List<WorkflowStep> workflowRows = noActive ? List.of(completedClient, historical, other)
        : alreadyPath ? List.of(completedClient, historical, active, other) : List.of(completedClient, historical, active);
    AtomicReference<WorkflowStep> storedActive = new AtomicReference<>(active);
    byte[] historicalBefore = mapper.writeValueAsBytes(historical), otherBefore = mapper.writeValueAsBytes(other);
    byte[] completedClientBefore = mapper.writeValueAsBytes(completedClient);
    byte[] activeBefore = mapper.writeValueAsBytes(active);
    List<List<Object>> bindings = new ArrayList<>(), stepWithdrawals = new ArrayList<>();
    doAnswer(call -> {
      assertThat(transactions.events).contains("application-lock", "current-members");
      transactions.read("steps-lock"); return workflowRows;
    }).when(steps).lockSteps(APPLICATION);
    doAnswer(call -> {
      assertThat(scheduled).isTrue(); assertThat(storedActive.get().getClientSubmissionId()).isNull();
      assertThat(storedActive.get().getVersion()).isEqualTo(4L);
      WorkflowStep bound = new WorkflowStep(); BeanUtils.copyProperties(storedActive.get(), bound);
      bound.setClientSubmissionId(SUBMISSION); bound.setVersion(5L); bound.setUpdateId(ACTOR); bound.setUpdateTime(NOW);
      storedActive.set(bound); bindings.add(Arrays.asList(call.getArguments())); transactions.write("step-binding"); return 1;
    }).when(steps).bindClientSubmission(activeId, SUBMISSION, 4L, ACTOR, NOW);
    doAnswer(call -> {
      assertThat(noActive).isFalse(); assertThat(storedActive.get().getClientSubmissionId()).isEqualTo(SUBMISSION);
      WorkflowStep changed = new WorkflowStep(); BeanUtils.copyProperties(storedActive.get(), changed);
      changed.setStatus("WITHDRAWN"); changed.setResultReason(reason); changed.setCompletedAt(NOW);
      changed.setVersion(changed.getVersion() + 1); changed.setUpdateId(ACTOR); changed.setUpdateTime(NOW);
      storedActive.set(changed); stepWithdrawals.add(Arrays.asList(call.getArguments())); transactions.write("step-withdrawal"); return 1;
    }).when(steps).withdrawBoundClientStep(activeId, reason, ACTOR, NOW);
    Interview completed = new Interview();
    completed.setId(completedId); completed.setApplicationId(APPLICATION); completed.setCandidateId(CANDIDATE);
    completed.setJdId(JD); completed.setJdVersionId(JD_VERSION); completed.setWorkflowTemplateVersionId("1".repeat(26));
    completed.setWorkflowStepId(historicalId); completed.setStepCode("CLIENT_AI_INTERVIEW");
    completed.setInterviewType("EXTERNAL_CLIENT_AI"); completed.setClientSubmissionId(SUBMISSION); completed.setStatus("COMPLETED");
    completed.setScheduledStartAt(sentAt.plusSeconds(30)); completed.setScheduledEndAt(sentAt.plusSeconds(90));
    completed.setTimezoneId("Asia/Shanghai"); completed.setMeetingMode("ONLINE"); completed.setMeetingUrl("https://meeting.example.com/completed");
    completed.setCandidateNotificationChannel("EMAIL"); completed.setCandidateConfirmationStatus("CONFIRMED");
    completed.setOwnerUserId(appOwner); completed.setOwnerDepartmentId(DEPT); completed.setNextResponsibleUserId(ACTOR);
    completed.setVersion(3L); completed.setCreateId(appOwner); completed.setUpdateId(appOwner);
    completed.setCreateTime(createdAt); completed.setUpdateTime(sentAt.plusSeconds(90)); completed.setInitialComment("保留已完成面试事实");
    Interview planned = new Interview(); BeanUtils.copyProperties(completed, planned);
    planned.setId(scheduledId); planned.setWorkflowTemplateVersionId(active.getTemplateVersionId()); planned.setWorkflowStepId(activeId); planned.setStatus("SCHEDULED");
    planned.setScheduledStartAt(NOW.plusSeconds(3600)); planned.setScheduledEndAt(NOW.plusSeconds(7200));
    planned.setMeetingUrl("https://meeting.example.com/current"); planned.setCandidateConfirmationStatus("PENDING");
    planned.setVersion(5L); planned.setUpdateTime(sentAt); planned.setInitialComment("当前甲方AI面试排期");
    AtomicReference<Interview> storedInterview = new AtomicReference<>(planned);
    byte[] completedBefore = mapper.writeValueAsBytes(completed), plannedBefore = mapper.writeValueAsBytes(planned);
    List<List<Object>> cancellations = new ArrayList<>(), interviewHistories = new ArrayList<>(), stageHistories = new ArrayList<>();
    doAnswer(call -> {
      assertThat(transactions.events).contains("steps-lock"); transactions.read("submission-interviews-lock");
      return scheduled ? List.of(completed, planned) : List.of(completed);
    }).when(interviews).lockSubmissionInterviews(APPLICATION, SUBMISSION, null);
    doAnswer(call -> {
      assertThat(scheduled).isTrue(); assertThat(storedInterview.get().getStatus()).isEqualTo("SCHEDULED");
      assertThat(storedInterview.get().getVersion()).isEqualTo(5L);
      Interview cancelled = new Interview(); BeanUtils.copyProperties(storedInterview.get(), cancelled);
      cancelled.setStatus("CANCELLED"); cancelled.setVersion(6L); cancelled.setUpdateId(ACTOR); cancelled.setUpdateTime(NOW);
      storedInterview.set(cancelled); cancellations.add(Arrays.asList(call.getArguments())); transactions.write("interview-cancel"); return 1;
    }).when(interviews).cancel(scheduledId, 5L, ACTOR, NOW);
    doAnswer(call -> { interviewHistories.add(Arrays.asList(call.getArguments())); transactions.write("interview-history"); return 1; })
        .when(interviews).insertHistory(any(), eq(scheduledId), eq("CANCEL"), any(), any(), eq(reason), eq(ACTOR), eq(NOW));
    doAnswer(call -> { stageHistories.add(Arrays.asList(call.getArguments())); transactions.write("stage-history"); return 1; })
        .when(stageHistory).insertStageHistory(any(), eq(APPLICATION), eq("IN_PROCESS"), eq("PATH_SELECTION"), eq(reason), eq(ACTOR), eq(NOW));
    PlatformIdGenerator localIds = mock(PlatformIdGenerator.class); AtomicInteger localSequence = new AtomicInteger();
    when(localIds.newId()).thenAnswer(call -> String.format("%026d", localSequence.incrementAndGet()));
    TemplateVersion template = new TemplateVersion("4".repeat(26), "5".repeat(26), 1,
        "取消面试 {{applicationId}}", "{{stepCode}} {{location}}", appOwner, createdAt);
    Participant participant = new Participant(); participant.setInterviewId(scheduledId); participant.setUserId(ACTOR);
    participant.setParticipantRole("INTERVIEWER"); participant.setResponseStatus("ACCEPTED");
    participant.setCreateId(appOwner); participant.setUpdateId(appOwner); participant.setCreateTime(sentAt); participant.setUpdateTime(sentAt);
    ParticipantPersistenceService participants = mock(ParticipantPersistenceService.class);
    NotificationIntentPersistenceService notificationFacts = mock(NotificationIntentPersistenceService.class);
    List<NotificationIntent> notificationRows = new ArrayList<>();
    when(interviews.findActiveTemplate("INTERVIEW_CANCEL")).thenReturn(template);
    doAnswer(call -> {
      assertThat(transactions.events).contains("interview-history"); transactions.read("current-interview-participants"); return List.of(participant);
    }).when(participants).lockParticipantsByInterviewIds(Set.of(scheduledId));
    doAnswer(call -> {
      List<NotificationIntent> intents = call.getArgument(0); assertThat(intents).hasSize(3);
      notificationRows.addAll(intents); transactions.write("notification-intents"); return true;
    }).when(notificationFacts).insertNotifications(any());
    InterviewNotificationApplicationManager notificationManager = new InterviewNotificationApplicationManager();
    ReflectionTestUtils.setField(notificationManager, "persistence", interviews);
    ReflectionTestUtils.setField(notificationManager, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(notificationManager, "notificationIntentPersistenceService", notificationFacts);
    ReflectionTestUtils.setField(notificationManager, "ids", localIds); ReflectionTestUtils.setField(notificationManager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ProxyFactory notificationFactory = new ProxyFactory(notificationManager); notificationFactory.setProxyTargetClass(true);
    notificationFactory.addAdvice(restrictionTransactionInterceptor(transactions));
    InterviewNotificationApplicationManager notifications = (InterviewNotificationApplicationManager) notificationFactory.getProxy();
    ActivityOriginPersistenceService originFacts = mock(ActivityOriginPersistenceService.class);
    List<ActivityOriginEntity> originRows = new ArrayList<>();
    doAnswer(call -> {
      ActivityOriginEntity origin = call.getArgument(0); originRows.add(origin);
      transactions.write("origin-" + origin.getSourceType()); return true;
    }).when(originFacts).save(any(ActivityOriginEntity.class));
    ActivityOriginManager originManager = new ActivityOriginManager();
    ReflectionTestUtils.setField(originManager, "origins", originFacts); ReflectionTestUtils.setField(originManager, "ids", localIds);
    ReflectionTestUtils.setField(originManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(originManager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ProxyFactory originFactory = new ProxyFactory(originManager); originFactory.setProxyTargetClass(true);
    originFactory.addAdvice(restrictionTransactionInterceptor(transactions));
    ActivityOriginRecorder origins = (ActivityOriginRecorder) originFactory.getProxy();
        SendSnapshot sent = new SendSnapshot(); sent.setId("I".repeat(26)); sent.setSubmissionId(SUBMISSION);
    sent.setResumeVersionId(RESUME); sent.setRecipient("client@example.com"); sent.setChannel("EMAIL");
    sent.setFileName("frozen-resume.pdf"); sent.setContentSha256("a".repeat(64)); sent.setSentByUserId(submissionOwner);
    sent.setSentAt(sentAt); sent.setCreateId(submissionOwner); sent.setUpdateId(submissionOwner);
    sent.setCreateTime(sentAt); sent.setUpdateTime(sentAt); sent.setVersion(0);
    Feedback priorFeedback = new Feedback(); priorFeedback.setId("F".repeat(26)); priorFeedback.setSubmissionId(SUBMISSION);
    priorFeedback.setResult(process); priorFeedback.setReasonCategory("CLIENT_REVIEW"); priorFeedback.setNextAction("继续跟进");
    priorFeedback.setNote("保留原反馈"); priorFeedback.setSourceChannel("EMAIL"); priorFeedback.setSourceOccurredAt(sentAt.plusSeconds(120));
    priorFeedback.setRecordedByUserId(submissionOwner); priorFeedback.setCreateId(submissionOwner); priorFeedback.setUpdateId(submissionOwner);
    priorFeedback.setCreateTime(sentAt.plusSeconds(120)); priorFeedback.setUpdateTime(sentAt.plusSeconds(120)); priorFeedback.setVersion(0);
    RiskAcknowledgement risk = new RiskAcknowledgement(); risk.setId("Q".repeat(26)); risk.setSubmissionId(SUBMISSION);
    risk.setRiskCode("PRIOR_CLIENT_PROCESS"); risk.setReason("人工已确认历史状态"); risk.setAcknowledgedByUserId(submissionOwner);
    risk.setCreateId(submissionOwner); risk.setUpdateId(submissionOwner); risk.setCreateTime(sentAt); risk.setUpdateTime(sentAt); risk.setVersion(0);
    byte[] sentBefore = mapper.writeValueAsBytes(sent), feedbackBefore = mapper.writeValueAsBytes(priorFeedback), riskBefore = mapper.writeValueAsBytes(risk);
    when(snapshots.snapshot(SUBMISSION)).thenReturn(sent); when(feedback.feedback(SUBMISSION)).thenReturn(List.of(priorFeedback));
    when(acknowledgements.riskAcks(SUBMISSION)).thenReturn(List.of(risk));
        RecruitmentApplicationManager applicationManager = new RecruitmentApplicationManager();
    ReflectionTestUtils.setField(applicationManager, "persistence", applicationFacts);
    ReflectionTestUtils.setField(applicationManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(applicationFacts.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(applicationManager, "applicationCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(applicationManager, "authorization", realAuthorization);
    ReflectionTestUtils.setField(applicationManager, "recruitmentWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(applicationManager, "interviewPersistenceService", interviews);
    ReflectionTestUtils.setField(applicationManager, "interviewNotificationManager", notifications);
    ReflectionTestUtils.setField(applicationManager, "stageHistoryPersistenceService", stageHistory);
    ReflectionTestUtils.setField(applicationManager, "followUpPlanLifecycle", followUpPlans);
    ReflectionTestUtils.setField(applicationManager, "activityOrigins", origins);
    ReflectionTestUtils.setField(applicationManager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ProxyFactory applicationFactory = new ProxyFactory(applicationManager); applicationFactory.setProxyTargetClass(true);
    TransactionInterceptor applicationInterceptor = new TransactionInterceptor(); applicationInterceptor.setTransactionManager(transactions);
    applicationInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); applicationFactory.addAdvice(applicationInterceptor);
    ReflectionTestUtils.setField(service, "applications", applicationFactory.getProxy());
    ReflectionTestUtils.setField(service, "authorization", realAuthorization); ReflectionTestUtils.setField(service, "assembler", new SubmissionAssembler());
    ProxyFactory submissionFactory = new ProxyFactory(service); submissionFactory.setProxyTargetClass(true);
    TransactionInterceptor submissionInterceptor = new TransactionInterceptor(); submissionInterceptor.setTransactionManager(transactions);
    submissionInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); submissionFactory.addAdvice(submissionInterceptor);
    SubmissionApplicationManager publicProxy = (SubmissionApplicationManager) submissionFactory.getProxy();
        ReflectionTestUtils.setField(applicationManager, "ids", localIds);
    assertThat(realAuthorization.canAccess("submission.read", new AuthorizationTarget(
        "CLIENT_SUBMISSION", SUBMISSION, submissionOwner, "E".repeat(26), Set.of()))).isTrue();
    assertThat(realAuthorization.canAccess("recruitment.manage", new AuthorizationTarget(
        "APPLICATION", APPLICATION, appOwner, DEPT, Set.of(ACTOR)))).isTrue();
    assertThat(RequestContextHolder.getRequestAttributes()).isNull();
    MockHttpServletRequest request = new MockHttpServletRequest();
    String operation = "12200000-0000-4000-8000-000000000001";
    request.addHeader("X-Recruitment-Action", "INTERVIEW"); request.addHeader("X-Recruitment-Operation", operation);
    AtomicReference<SubmissionDetailsResponse> responseHolder = new AtomicReference<>();
    AtomicReference<Throwable> observedFailure = new AtomicReference<>();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      responseHolder.set(publicProxy.withdraw(SUBMISSION, new WithdrawSubmissionDTO(reason, 2L), key));
    } catch (Throwable error) {
      observedFailure.set(error);
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
    assertThat(RequestContextHolder.getRequestAttributes()).isNull();
    assertThat(transactions.beginCount).isOne(); assertThat(transactions.outerIsolation).isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(transactions.joinedCount).isGreaterThanOrEqualTo(1); assertThat(transactions.state.active).isFalse();
    assertThat(transactions.state.pending).isEmpty(); assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(reservations).containsExactly(Arrays.asList(ACTOR, key, "SUBMISSION_WITHDRAW",
        Sha256Utils.fingerprintObjects(31, SUBMISSION, reason, 2L), SUBMISSION, NOW));
    assertThat(withdrawals).containsExactly(Arrays.asList(SUBMISSION, reason, ACTOR, 2L, NOW));
    long lockedVersion = initialVersion + (noOwnerChange ? 0 : 1);
    assertThat(lockedApp.get().getVersion()).isEqualTo(lockedVersion);
    assertThat(lockedApp.get().getCurrentStage()).isEqualTo(initialStage);
    assertThat(snapshotApp.get().getVersion()).isEqualTo(beforeView ? lockedVersion : initialVersion);
    assertThat(snapshotApp.get().getCurrentStage()).isEqualTo(initialStage);
    assertThat(snapshotMembers.get()).contains(ACTOR); assertThat(storedMembers.get()).contains(ACTOR);
    if (noOwnerChange) {
      assertThat(winnerChanges).isEmpty(); assertThat(storedMembers.get()).containsExactly(ACTOR);
      verifyNoInteractions(directory);
    } else {
      assertThat(winnerChanges).containsExactly(Arrays.asList("K".repeat(26), APPLICATION, JsonText.write(List.of(ACTOR)),
          JsonText.write(afterMembers), "合规协作人配置变更", initialVersion, appOwner, winnerAt));
      assertThat(storedMembers.get()).containsExactlyElementsOf(afterMembers);
      assertThat(transactions.events).containsSubsequence("modeled-owner-app-lock", "winner-active-users", "modeled-owner-full-commit", "application-lock");
    }
    assertThat(transactions.events).containsSubsequence("submission-lock", "ordinary-key-miss", "owner-window-closed",
        "command-reservation", "submission-withdrawal", "application-lock", "current-members", "steps-lock", "submission-interviews-lock");
    verify(collaborators).findCollaboratorsForUpdate(APPLICATION); verify(collaborators, never()).findCollaborators(APPLICATION);
    assertThat(mapper.writeValueAsBytes(historical)).isEqualTo(historicalBefore);
    assertThat(mapper.writeValueAsBytes(completedClient)).isEqualTo(completedClientBefore);
    assertThat(mapper.writeValueAsBytes(other)).isEqualTo(otherBefore);
    assertThat(mapper.writeValueAsBytes(completed)).isEqualTo(completedBefore);
    assertThat(mapper.writeValueAsBytes(planned)).isEqualTo(plannedBefore);
    if (noActive) {
      assertThat(bindings).isEmpty(); assertThat(stepWithdrawals).isEmpty();
      assertThat(mapper.writeValueAsBytes(storedActive.get())).isEqualTo(activeBefore);
      verify(steps, never()).withdrawBoundClientStep(any(), any(), any(), any());
    } else {
      assertThat(stepWithdrawals).containsExactly(Arrays.asList(activeId, reason, ACTOR, NOW));
      WorkflowStep expectedStep = new WorkflowStep(); BeanUtils.copyProperties(active, expectedStep);
      expectedStep.setClientSubmissionId(SUBMISSION); expectedStep.setStatus("WITHDRAWN");
      expectedStep.setResultReason(reason); expectedStep.setCompletedAt(NOW);
      expectedStep.setVersion(scheduled ? 6L : 5L); expectedStep.setUpdateId(ACTOR); expectedStep.setUpdateTime(NOW);
      assertThat(mapper.writeValueAsBytes(storedActive.get())).isEqualTo(mapper.writeValueAsBytes(expectedStep));
      if (scheduled) assertThat(bindings).containsExactly(Arrays.asList(activeId, SUBMISSION, 4L, ACTOR, NOW));
      else assertThat(bindings).isEmpty();
    }
    if (scheduled) {
      assertThat(cancellations).containsExactly(Arrays.asList(scheduledId, 5L, ACTOR, NOW));
      Interview expectedInterview = new Interview(); BeanUtils.copyProperties(planned, expectedInterview);
      expectedInterview.setStatus("CANCELLED"); expectedInterview.setVersion(6L); expectedInterview.setUpdateId(ACTOR); expectedInterview.setUpdateTime(NOW);
      assertThat(mapper.writeValueAsBytes(storedInterview.get())).isEqualTo(mapper.writeValueAsBytes(expectedInterview));
      assertThat(interviewHistories).hasSize(1); List<Object> history = interviewHistories.getFirst();
      assertThat(history).hasSize(8); assertThat((String) history.getFirst()).hasSize(26);
      assertThat(history.subList(1, 3)).containsExactly(scheduledId, "CANCEL");
      assertThat(mapper.readTree((String) history.get(3))).isEqualTo(mapper.readTree(JsonText.write(Map.of("status", "SCHEDULED", "clientSubmissionId", SUBMISSION))));
      assertThat(mapper.readTree((String) history.get(4))).isEqualTo(mapper.readTree(JsonText.write(Map.of("status", "CANCELLED", "clientSubmissionId", SUBMISSION))));
      assertThat(history.subList(5, 8)).containsExactly(reason, ACTOR, NOW);
      assertThat(notificationRows.stream().map(row -> List.of(row.getRecipientType(), row.getRecipientReference(), row.getChannel())).toList())
          .containsExactly(List.of("USER", appOwner, "IN_APP"), List.of("USER", ACTOR, "IN_APP"), List.of("CANDIDATE", CANDIDATE, "EMAIL"));
      for (NotificationIntent row : notificationRows) {
        assertThat(row.getId()).hasSize(26);
        assertThat(row).extracting("interviewId", "notificationType", "templateVersionId", "renderedSubject", "renderedBody",
            "status", "retryCount", "version", "externalMessageId", "lastErrorCode", "lastErrorDetail", "ext", "remark",
            "createId", "createName", "createTime", "updateId", "updateName", "updateTime")
            .containsExactly(scheduledId, "INTERVIEW_CANCEL", template.getId(), "取消面试 " + APPLICATION,
                "CLIENT_AI_INTERVIEW https://meeting.example.com/current", "PENDING_MANUAL", 0, 0L,
                null, null, null, null, null, ACTOR, null, NOW, ACTOR, null, NOW);
      }
      assertThat(transactions.events).containsSubsequence("interview-cancel", "origin-INTERVIEW_SCHEDULE", "interview-history",
          "current-interview-participants", "notification-intents", "step-binding", "step-withdrawal");
    } else {
      assertThat(cancellations).isEmpty(); assertThat(interviewHistories).isEmpty(); assertThat(notificationRows).isEmpty();
      assertThat(mapper.writeValueAsBytes(storedInterview.get())).isEqualTo(plannedBefore);
      verifyNoInteractions(participants, notificationFacts);
    }
    for (ActivityOriginEntity row : originRows) {
      assertThat(row.getId()).hasSize(26); assertThat(row.getSourceId()).hasSize(26);
      assertThat(row).extracting("applicationId", "action", "operationId", "version", "ext", "remark",
          "createId", "createName", "createTime", "updateId", "updateName", "updateTime")
          .containsExactly(APPLICATION, "INTERVIEW", operation, 0, null, null, ACTOR, "HR", NOW, ACTOR, "HR", NOW);
    }
    if (scheduled) {
      assertThat(originRows.stream().filter(row -> row.getSourceType().equals("INTERVIEW_SCHEDULE")).toList())
          .singleElement().extracting("sourceId").isEqualTo(interviewHistories.getFirst().getFirst());
    }
    if (observedFailure.get() != null) {
      assertThat(mode.endsWith("owner-change-after-ordinary-view")).as("其他健康对照不得因夹具或权限失败").isTrue();
      assertThat(observedFailure.get()).isInstanceOfSatisfying(PlatformException.class,
          error -> assertThat(error.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
      assertThat(stageAttempts).containsExactly(Arrays.asList("PATH_SELECTION", 0L, 1L, 0, ACTOR, NOW));
      assertThat(transactions.commitCount).isZero(); assertThat(transactions.rollbackCount).isOne();
      assertThat(transactions.state.rollbackOnly).isTrue(); assertThat(transactions.state.committed).isEmpty();
      assertThat(stageHistories).isEmpty(); assertThat(ownStageWritten.get()).isFalse();
      assertThat(originRows).hasSize(scheduled ? 1 : 0);
      verify(persistence, never()).find(SUBMISSION); verifyNoInteractions(snapshots, feedback, acknowledgements);
      // 合成行不会物理回滚；这里只验证真实 joining proxy 回滚句柄与真实业务失败，不宣称MySQL/T2。
    }
    assertThatCode(() -> { if (observedFailure.get() != null) throw observedFailure.get(); })
        .as("仍有当前处理权限的合法推荐撤回应成功，CAS记录=%s", stageAttempts).doesNotThrowAnyException();
    SubmissionDetailsResponse response = responseHolder.get(); assertThat(response).isNotNull();
          assertThat(response.getSubmission()).extracting("id", "applicationId", "candidateId", "demandId", "clientId", "projectId",
          "positionId", "jdId", "jdVersionId", "resumeVersionId", "deliveryStatus", "processStatus", "sentAt", "withdrawnAt",
          "withdrawnByUserId", "withdrawalReason", "ownerUserId", "ownerDepartmentId", "createId", "version", "createTime", "updateTime")
          .containsExactly(SUBMISSION, APPLICATION, CANDIDATE, DEMAND, CLIENT, PROJECT, POSITION, JD, JD_VERSION, RESUME,
              "WITHDRAWN", process, sentAt, NOW, ACTOR, reason, submissionOwner, "E".repeat(26), submissionOwner, "3", createdAt, NOW);
      assertThat(response.getSendSnapshot()).extracting("id", "submissionId", "resumeVersionId", "recipient", "channel", "fileName",
          "contentSha256", "sentByUserId", "sentAt").containsExactly("I".repeat(26), SUBMISSION, RESUME, "client@example.com", "EMAIL",
              "frozen-resume.pdf", "a".repeat(64), submissionOwner, sentAt);
      assertThat(response.getFeedback()).singleElement().extracting("id", "submissionId", "result", "reasonCategory", "nextAction", "note",
          "sourceChannel", "sourceOccurredAt", "recordedByUserId", "createTime").containsExactly("F".repeat(26), SUBMISSION, process,
              "CLIENT_REVIEW", "继续跟进", "保留原反馈", "EMAIL", sentAt.plusSeconds(120), submissionOwner, sentAt.plusSeconds(120));
      assertThat(response.getRiskAcknowledgements()).singleElement().extracting("id", "submissionId", "riskCode", "reason",
          "acknowledgedByUserId", "createTime").containsExactly("Q".repeat(26), SUBMISSION, "PRIOR_CLIENT_PROCESS",
              "人工已确认历史状态", submissionOwner, sentAt);
    assertThat(mapper.writeValueAsBytes(sent)).isEqualTo(sentBefore);
    assertThat(mapper.writeValueAsBytes(priorFeedback)).isEqualTo(feedbackBefore);
    assertThat(mapper.writeValueAsBytes(risk)).isEqualTo(riskBefore);
    assertThat(storedSubmission.get().getProcessStatus()).isEqualTo(process);
    assertThat(storedSubmission.get().getSentAt()).isEqualTo(sentAt);
    assertThat(storedSubmission.get().getVersion()).isEqualTo(3L); assertThat(storedSubmission.get().getUpdateId()).isEqualTo(ACTOR);
    assertThat(transactions.commitCount).isOne(); assertThat(transactions.rollbackCount).isZero();
    assertThat(transactions.state.rollbackOnly).isFalse();
    Application expectedApp = new Application(); BeanUtils.copyProperties(lockedApp.get(), expectedApp);
    List<String> committed = new ArrayList<>(List.of("command-reservation", "submission-withdrawal"));
    if (scheduled) committed.addAll(List.of("interview-cancel", "origin-INTERVIEW_SCHEDULE", "interview-history", "notification-intents"));
    if (!noActive) {
      if (scheduled) committed.add("step-binding");
      committed.add("step-withdrawal");
    }
    if (noActive || alreadyPath) {
      assertThat(stageAttempts).isEmpty(); assertThat(stageHistories).isEmpty(); assertThat(ownStageWritten.get()).isFalse();
      verifyNoInteractions(stageHistory); assertThat(originRows).hasSize(scheduled ? 1 : 0);
    } else {
      assertThat(stageAttempts).containsExactly(Arrays.asList("PATH_SELECTION", lockedVersion, lockedVersion, 1, ACTOR, NOW));
      assertThat(beforeOwnStage.get()).isSameAs(lockedApp.get());
      expectedApp.setCurrentStage("PATH_SELECTION"); expectedApp.setVersion(lockedVersion + 1);
      expectedApp.setUpdateId(ACTOR); expectedApp.setUpdateTime(NOW);
      assertThat(stageHistories).hasSize(1); List<Object> history = stageHistories.getFirst();
      assertThat(history).hasSize(7); assertThat((String) history.getFirst()).hasSize(26);
      assertThat(history.subList(1, 7)).containsExactly(APPLICATION, "IN_PROCESS", "PATH_SELECTION", reason, ACTOR, NOW);
      assertThat(originRows).hasSize(scheduled ? 2 : 1);
      assertThat(originRows.stream().filter(row -> row.getSourceType().equals("STAGE")).toList())
          .singleElement().extracting("sourceId").isEqualTo(history.getFirst());
      committed.addAll(List.of("application-stage", "origin-STAGE", "stage-history"));
    }
    assertThat(mapper.writeValueAsBytes(storedApp.get())).isEqualTo(mapper.writeValueAsBytes(expectedApp));
    assertThat(transactions.state.committed).containsExactlyElementsOf(committed);
    assertThat(transactions.events).containsSubsequence("application-lock", "current-members", "own-submission-reread");
    verify(persistence).find(SUBMISSION); verifyNoInteractions(followUpPlans);
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    private int committed;
    private int rolledBack;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      committed++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rolledBack++;
    }
  }

  @ParameterizedTest
  @CsvSource({
      "DECISION, conflict", "OFFER, conflict",
      "DECISION, normal", "OFFER, normal",
      "DECISION, response-failure", "OFFER, response-failure"
  })
  void applicationRestrictionAndTerminationShareOneOuterTransaction(String stage, String outcome) {
    ApplicationClientRestrictionDTO command = applicationRestriction();
    SharedRestrictionTransactions transactions = new SharedRestrictionTransactions();
    ApplicationPersistenceService applicationFacts = mock(ApplicationPersistenceService.class);
    ApplicationCollaboratorPersistenceService collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    DecisionPersistenceService decisions = mock(DecisionPersistenceService.class);
    OfferStatusRecordPersistenceService offers = mock(OfferStatusRecordPersistenceService.class);
    RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
    StageHistoryPersistenceService history = mock(StageHistoryPersistenceService.class);
    RecruitmentIdempotencyPersistenceService applicationRequests = mock(RecruitmentIdempotencyPersistenceService.class);
    FollowUpPlanLifecycleManager followUpPlans = mock(FollowUpPlanLifecycleManager.class);
    InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
    ActivityOriginRecorder origins = mock(ActivityOriginRecorder.class);
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot(ACTOR, "HR", "hr@example.com", DEPT,
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"),
        Set.of("submission.manage", "recruitment.manage"), false, false));
    Application app = new Application();
    app.setId(APPLICATION);
    app.setCandidateId(CANDIDATE);
    app.setResumeVersionId(RESUME);
    app.setJdId(JD);
    app.setJdVersionId(JD_VERSION);
    app.setRecruitmentType("INTERNAL");
    app.setOwnerUserId(ACTOR);
    app.setOwnerDepartmentId(DEPT);
    app.setVersion(1L);
    app.setCurrentStage(stage);
    when(applicationFacts.findJdVersionId(APPLICATION)).thenAnswer(call -> {
      transactions.read("ordinary-jd-id");
      return JD_VERSION;
    });
    when(demands.lockJdForHeadcount(JD_VERSION)).thenAnswer(call -> {
      transactions.read("jd-lock");
      return new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", ACTOR, DEPT, Set.of());
    });
    when(applicationFacts.lockApplication(APPLICATION)).thenAnswer(call -> {
      transactions.read("app-lock");
      return app;
    });
    when(applicationFacts.findApplication(APPLICATION)).thenReturn(app);
    when(collaborators.findCollaborators(APPLICATION)).thenReturn(List.of());
    when(restrictions.save(any(ClientRestriction.class))).thenAnswer(call -> {
      ClientRestriction value = call.getArgument(0);
      assertThat(value.getSourceApplicationId()).isEqualTo(APPLICATION);
      assertThat(value.getRecordedByUserId()).isEqualTo(ACTOR);
      transactions.write("restriction-history");
      return true;
    });
    doAnswer(call -> {
      transactions.write("restriction-origin");
      return null;
    }).when(origins).record(eq(APPLICATION), eq("RESTRICTION"), eq(SUBMISSION));
    when(persistence.insertRestrictionProjection(eq(CLIENT), eq(CANDIDATE), eq(SUBMISSION), eq(true),
        any(), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          transactions.write("restriction-projection");
          return 1;
        });
    String endKey = "blacklist-end-" + SUBMISSION;
    AtomicReference<com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord> winner = new AtomicReference<>();
    when(applicationRequests.findIdempotency(ACTOR, endKey)).thenAnswer(call -> {
      transactions.read("ordinary-command-key-miss");
      return null;
    });
    when(applicationFacts.insertIdempotency(eq(ACTOR), eq(endKey), any(), any(), any(), eq(NOW)))
        .thenAnswer(call -> {
          assertThat(transactions.events).containsSubsequence("restriction-history", "restriction-projection",
              "ordinary-command-key-miss");
          if (outcome.equals("conflict")) {
            // 同一actor的另一个Application/另一个JD已提交合法决定；不在已持app锁上提交winner。
            Application other = new Application();
            other.setId("Z".repeat(26));
            other.setJdId("X".repeat(26));
            other.setJdVersionId("Y".repeat(26));
            assertThat(other.getId()).isNotEqualTo(app.getId());
            assertThat(other.getJdId()).isNotEqualTo(app.getJdId());
            assertThat(other.getJdVersionId()).isNotEqualTo(app.getJdVersionId());
            winner.set(new com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord(ACTOR, endKey, "APPLICATION_DECIDE",
                Sha256Utils.fingerprintObjects(31, other.getId(), "REJECT", command.getReasonDetail(), null),
                "Q".repeat(26)));
            transactions.read("other-application-commit:" + other.getId() + ":" + other.getJdId() + ":" + other.getJdVersionId());
            transactions.read("command-reservation-conflict");
            return 0;
          }
          transactions.write("command-reservation");
          return 1;
        });
    when(applicationRequests.findIdempotencyForUpdate(ACTOR, endKey)).thenAnswer(call -> {
      transactions.read("current-command-key");
      return winner.get();
    });
    WorkflowStep decisionStep = new WorkflowStep();
    decisionStep.setId("E".repeat(26));
    decisionStep.setApplicationId(APPLICATION);
    decisionStep.setStepCode("DECISION");
    decisionStep.setStatus("IN_PROGRESS");
    when(steps.findSteps(APPLICATION)).thenReturn(List.of(decisionStep));
    when(steps.finishDecisionStep(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      transactions.write("decision-step");
      return 1;
    });
    when(steps.withdrawActiveSteps(any(), any(), any(), any())).thenAnswer(call -> {
      transactions.write("withdraw-active-steps");
      return 0;
    });
    when(steps.closeUnreachedSteps(any(), any(), any())).thenAnswer(call -> {
      transactions.write("close-unreached-steps");
      return 0;
    });
    when(steps.finishOfferStep(any(), any(), any(), any(), any())).thenAnswer(call -> {
      transactions.write("offer-step");
      return 1;
    });
    when(decisions.insertDecision(any(), eq(APPLICATION), eq("DECISION"), eq("REJECT"),
        eq(command.getReasonDetail()), any(), any(), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          transactions.write("decision-fact");
          return 1;
        });
    when(decisions.findDecision(eq(APPLICATION), any())).thenReturn(new Decision());
    OfferStatusRecord previous = new OfferStatusRecord();
    previous.setId("B".repeat(26));
    previous.setApplicationId(APPLICATION);
    previous.setStatus("ISSUED");
    previous.setOccurredAt(NOW.minusSeconds(60));
    when(offers.latestOfferForUpdate(APPLICATION)).thenReturn(previous);
    when(offers.insertOffer(any(), eq(APPLICATION), eq("STATUS"), eq("TERMINATED"), eq("COMPANY_WITHDREW"),
        eq("REJECT"), eq(command.getReasonDetail()), any(), any(), any(), any(), any(), eq(NOW), eq(ACTOR), eq(NOW)))
        .thenAnswer(call -> {
          transactions.write("offer-fact");
          return 1;
        });
    when(offers.findOffer(eq(APPLICATION), any())).thenReturn(new OfferStatusRecord());
    when(applicationFacts.updateOutcome(eq(APPLICATION), eq("TERMINATED"), eq("REJECT"), eq(NOW),
        any(), eq(1L), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          transactions.write("application-outcome");
          return 1;
        });
    when(history.insertStageHistory(any(), eq(APPLICATION), eq(stage), eq("TERMINATED"),
        eq(command.getReasonDetail()), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          transactions.write("application-stage-history");
          return 1;
        });
    when(interviews.lockScheduledApplicationInterviews(eq(APPLICATION), any())).thenReturn(List.of());
    doAnswer(call -> {
      transactions.write("follow-up-cancel");
      return null;
    }).when(followUpPlans).cancelForApplication(eq(APPLICATION), eq(command.getReasonDetail()), eq(ACTOR), eq(NOW));
    RecruitmentApplicationManager realApplications = new RecruitmentApplicationManager();
    ReflectionTestUtils.setField(realApplications, "persistence", applicationFacts);
    ReflectionTestUtils.setField(realApplications, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(applicationFacts.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(realApplications, "applicationCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(realApplications, "recruitmentIdempotencyPersistenceService", applicationRequests);
    ReflectionTestUtils.setField(realApplications, "decisionPersistenceService", decisions);
    ReflectionTestUtils.setField(realApplications, "offerStatusRecordPersistenceService", offers);
    ReflectionTestUtils.setField(realApplications, "recruitmentWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(realApplications, "stageHistoryPersistenceService", history);
    ReflectionTestUtils.setField(realApplications, "followUpPlanLifecycle", followUpPlans);
    ReflectionTestUtils.setField(realApplications, "interviewPersistenceService", interviews);
    ReflectionTestUtils.setField(realApplications, "engagementLocks", mock(EngagementLockApplicationManager.class));
    ReflectionTestUtils.setField(realApplications, "authorization", authorization);
    ReflectionTestUtils.setField(realApplications, "demands", demands);
    ReflectionTestUtils.setField(realApplications, "ids", ids);
    ReflectionTestUtils.setField(realApplications, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ProxyFactory applicationFactory = new ProxyFactory(realApplications);
    applicationFactory.setProxyTargetClass(true);
    applicationFactory.addAdvice(restrictionTransactionInterceptor(transactions));
    ReflectionTestUtils.setField(service, "applications", applicationFactory.getProxy());
    ReflectionTestUtils.setField(service, "activityOrigins", origins);
    SubmissionAssembler assembler = spy(new SubmissionAssembler());
    doAnswer(call -> {
      transactions.read("restriction-response");
      if (outcome.equals("response-failure")) throw new IllegalStateException("限制响应组装失败");
      return call.callRealMethod();
    }).when(assembler).toResponse(any(ClientRestriction.class));
    ReflectionTestUtils.setField(service, "assembler", assembler);
    ProxyFactory submissionFactory = new ProxyFactory(service);
    submissionFactory.setProxyTargetClass(true);
    submissionFactory.addAdvice(restrictionTransactionInterceptor(transactions));
    SubmissionApplicationManager outer = (SubmissionApplicationManager) submissionFactory.getProxy();

    if (outcome.equals("normal")) {
      var response = outer.restrictApplication(APPLICATION, command, "outer-restriction-once");
      assertThat(response.getId()).isEqualTo(SUBMISSION);
      assertThat(response.getSourceApplicationId()).isEqualTo(APPLICATION);
      assertThat(response.getClientId()).isEqualTo(CLIENT);
      assertThat(response.getStatus()).isEqualTo("ACTIVE");
    } else if (outcome.equals("conflict")) {
      assertThatThrownBy(() -> outer.restrictApplication(APPLICATION, command, "outer-restriction-once"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    } else {
      assertThatThrownBy(() -> outer.restrictApplication(APPLICATION, command, "outer-restriction-once"))
          .isInstanceOf(IllegalStateException.class).hasMessage("限制响应组装失败");
    }

    verify(restrictions).save(any(ClientRestriction.class));
    verify(persistence).insertRestrictionProjection(eq(CLIENT), eq(CANDIDATE), eq(SUBMISSION), eq(true),
        any(), eq(ACTOR), eq(NOW));
    assertThat(transactions.beginCount).isEqualTo(1);
    assertThat(transactions.joinedCount).isGreaterThanOrEqualTo(2);
    assertThat(transactions.outerIsolation).isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(transactions.commitCount).isEqualTo(outcome.equals("normal") ? 1 : 0);
    assertThat(transactions.rollbackCount).isEqualTo(outcome.equals("normal") ? 0 : 1);
    assertThat(transactions.state.active).isFalse();
    assertThat(transactions.state.pending).isEmpty();
    assertThat(transactions.events).containsSubsequence("ordinary-jd-id", "jd-lock", "app-lock",
        "restriction-history", "restriction-projection", "ordinary-command-key-miss");
    if (outcome.equals("conflict")) {
      assertThat(transactions.events).containsSubsequence("ordinary-command-key-miss",
          "other-application-commit:" + "Z".repeat(26) + ":" + "X".repeat(26) + ":" + "Y".repeat(26),
          "command-reservation-conflict", "current-command-key");
      assertThat(transactions.events).doesNotContain("decision-fact", "offer-fact", "application-outcome",
          "application-stage-history", "restriction-response");
    } else {
      assertThat(transactions.events).containsSubsequence("restriction-projection", "command-reservation",
          stage.equals("OFFER") ? "offer-fact" : "decision-fact", "application-outcome",
          "application-stage-history", "restriction-response");
    }
    if (outcome.equals("normal")) {
      assertThat(transactions.state.committed).contains("restriction-history", "restriction-projection",
          stage.equals("OFFER") ? "offer-fact" : "decision-fact", "application-outcome", "application-stage-history");
    } else {
      assertThat(transactions.state.committed).isEmpty();
    }
    verifyNoInteractions(snapshots, feedback, acknowledgements);
  }

  @ParameterizedTest
  @ValueSource(strings = {"added", "existing", "revoked"})
  void restrictionDecisionUsesCommittedCollaboratorPermissionAfterHeadcountSnapshot(String membership) {
    ApplicationClientRestrictionDTO command = applicationRestriction();
    SharedRestrictionTransactions transactions = new SharedRestrictionTransactions();
    ApplicationPersistenceService applicationFacts = mock(ApplicationPersistenceService.class);
    ApplicationCollaboratorPersistenceService collaborators = mock(ApplicationCollaboratorPersistenceService.class);
    DecisionPersistenceService decisions = mock(DecisionPersistenceService.class);
    RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
    StageHistoryPersistenceService history = mock(StageHistoryPersistenceService.class);
    RecruitmentIdempotencyPersistenceService applicationRequests = mock(RecruitmentIdempotencyPersistenceService.class);
    FollowUpPlanLifecycleManager followUpPlans = mock(FollowUpPlanLifecycleManager.class);
    InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
    ActivityOriginRecorder origins = mock(ActivityOriginRecorder.class);
    String owner = "H".repeat(26);
    Application app = new Application();
    app.setId(APPLICATION);
    app.setCandidateId(CANDIDATE);
    app.setResumeVersionId(RESUME);
    app.setJdId(JD);
    app.setJdVersionId(JD_VERSION);
    app.setRecruitmentType("INTERNAL");
    app.setOwnerUserId(owner);
    app.setOwnerDepartmentId(DEPT);
    app.setVersion(1L);
    app.setCurrentStage("DECISION");
    Application snapshotApplication = new Application();
    snapshotApplication.setId(APPLICATION);
    snapshotApplication.setVersion(1L);
    snapshotApplication.setCurrentStage("DECISION");
    Application storedApplication = new Application();
    storedApplication.setId(APPLICATION);
    storedApplication.setVersion(1L);
    storedApplication.setCurrentStage("DECISION");
    boolean[] ownApplicationWrite = {false};
    assertThat(owner).isNotEqualTo(ACTOR);
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot(ACTOR, "HR", "hr@example.com", DEPT,
        Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"),
        Set.of("submission.manage", "recruitment.manage"), false, false));
    List<String> snapshotMembers = membership.equals("added") ? List.of() : List.of(ACTOR);
    AtomicReference<List<String>> committedMembers = new AtomicReference<>(snapshotMembers);
    List<AuthorizationTarget> accessTargets = new ArrayList<>();
    int[] applicationLocks = {0};
    when(applicationFacts.findJdVersionId(APPLICATION)).thenAnswer(call -> {
      transactions.read("ordinary-jd-id");
      assertThat(applicationLocks[0]).isZero();
      assertThat(transactions.events).doesNotContain("jd-lock", "app-lock");
      if (!membership.equals("existing")) {
        // 模拟同app的合法协作人更新已提交，窗口在T1首次标量查询后、JD/app锁之前。
        // updateCollaborators的app锁先于其成员替换；本替身不运行真实并发事务。
        transactions.read("winner-app-lock-before-t1-locks");
        committedMembers.set(membership.equals("added") ? List.of(ACTOR) : List.of());
        // 合法updateCollaborators还会updateStage递增app版本；锁读必须看到新版本。
        app.setVersion(2L);
        storedApplication.setVersion(2L);
        transactions.read("winner-collaborators-commit-before-t1-locks");
      }
      return JD_VERSION;
    });
    when(demands.lockJdForHeadcount(JD_VERSION)).thenAnswer(call -> {
      transactions.read("jd-lock");
      return new JdVersionSnapshot(JD_VERSION, JD, DEMAND, "INTERNAL", owner, DEPT, Set.of());
    });
    when(applicationFacts.lockApplication(APPLICATION)).thenAnswer(call -> {
      transactions.read("app-lock");
      applicationLocks[0]++;
      return app;
    });
    when(applicationFacts.findApplication(APPLICATION)).thenAnswer(call -> {
      transactions.read(ownApplicationWrite[0] ? "own-written-app" : "snapshot-app");
      return ownApplicationWrite[0] ? storedApplication : snapshotApplication;
    });
    when(collaborators.findCollaboratorsForUpdate(APPLICATION)).thenAnswer(call -> {
      transactions.read("current-members");
      assertThat(applicationLocks[0]).isPositive();
      return committedMembers.get();
    });
    when(collaborators.findCollaborators(APPLICATION)).thenAnswer(call -> {
      transactions.read("snapshot-members");
      assertThat(applicationLocks[0]).isGreaterThanOrEqualTo(2);
      assertThat(transactions.state.pending).contains("restriction-history", "restriction-origin", "restriction-projection");
      // 写restriction可清SESSION cache，不能据此把外层RR已建立的读视图当作新视图。
      return snapshotMembers;
    });
    doAnswer(call -> {
      AuthorizationTarget target = call.getArgument(1);
      assertThat(target.getObjectId()).isEqualTo(APPLICATION);
      assertThat(target.getOwnerUserId()).isEqualTo(owner);
      accessTargets.add(target);
      transactions.read("object-access");
      // 与既有夹具一样允许管理查看；本用例检验实际manager的归属/协作人处理权限。
      return null;
    }).when(authorization).requireAccess(eq("recruitment.manage"), any(AuthorizationTarget.class));
    when(restrictions.save(any(ClientRestriction.class))).thenAnswer(call -> {
      ClientRestriction value = call.getArgument(0);
      assertThat(value.getSourceApplicationId()).isEqualTo(APPLICATION);
      assertThat(value.getRecordedByUserId()).isEqualTo(ACTOR);
      transactions.write("restriction-history");
      return true;
    });
    doAnswer(call -> {
      transactions.write("restriction-origin");
      return null;
    }).when(origins).record(eq(APPLICATION), eq("RESTRICTION"), eq(SUBMISSION));
    when(persistence.insertRestrictionProjection(eq(CLIENT), eq(CANDIDATE), eq(SUBMISSION), eq(true),
        any(), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          transactions.write("restriction-projection");
          return 1;
        });
    String endKey = "blacklist-end-" + SUBMISSION;
    when(applicationRequests.findIdempotency(ACTOR, endKey)).thenAnswer(call -> {
      transactions.read("ordinary-command-key-miss");
      return null;
    });
    when(applicationFacts.insertIdempotency(eq(ACTOR), eq(endKey), any(), any(), any(), eq(NOW)))
        .thenAnswer(call -> {
          transactions.write("command-reservation");
          return 1;
        });
    WorkflowStep decisionStep = new WorkflowStep();
    decisionStep.setId("E".repeat(26));
    decisionStep.setApplicationId(APPLICATION);
    decisionStep.setStepCode("DECISION");
    decisionStep.setStatus("IN_PROGRESS");
    when(steps.findSteps(APPLICATION)).thenReturn(List.of(decisionStep));
    when(steps.finishDecisionStep(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      transactions.write("decision-step");
      return 1;
    });
    when(steps.withdrawActiveSteps(any(), any(), any(), any())).thenReturn(0);
    when(decisions.insertDecision(any(), eq(APPLICATION), eq("DECISION"), eq("REJECT"),
        eq(command.getReasonDetail()), any(), any(), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          transactions.write("decision-fact");
          return 1;
        });
    when(decisions.findDecision(eq(APPLICATION), any())).thenReturn(new Decision());
    when(applicationFacts.updateOutcome(eq(APPLICATION), eq("TERMINATED"), eq("REJECT"), eq(NOW),
        any(), anyLong(), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          long expectedVersion = call.getArgument(5, Long.class);
          if (expectedVersion != storedApplication.getVersion()) {
            transactions.read("projection-version-conflict");
            return 0;
          }
          transactions.write("application-outcome");
          storedApplication.setVersion(expectedVersion + 1);
          storedApplication.setCurrentStage(call.getArgument(1));
          storedApplication.setOutcome(call.getArgument(2));
          storedApplication.setTerminalAt(call.getArgument(3));
          Integer activeSlot = call.getArgument(4);
          storedApplication.setActiveSlot(activeSlot == null ? null : activeSlot.longValue());
          ownApplicationWrite[0] = true;
          return 1;
        });
    when(history.insertStageHistory(any(), eq(APPLICATION), eq("DECISION"), eq("TERMINATED"),
        eq(command.getReasonDetail()), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
          transactions.write("application-stage-history");
          return 1;
        });
    when(interviews.lockScheduledApplicationInterviews(eq(APPLICATION), any())).thenReturn(List.of());
    doAnswer(call -> {
      transactions.write("follow-up-cancel");
      return null;
    }).when(followUpPlans).cancelForApplication(eq(APPLICATION), eq(command.getReasonDetail()), eq(ACTOR), eq(NOW));
    RecruitmentApplicationManager realApplications = new RecruitmentApplicationManager();
    ReflectionTestUtils.setField(realApplications, "persistence", applicationFacts);
    ReflectionTestUtils.setField(realApplications, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(applicationFacts.findCandidateId(any())).thenReturn(CANDIDATE);
    ReflectionTestUtils.setField(realApplications, "applicationCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(realApplications, "recruitmentIdempotencyPersistenceService", applicationRequests);
    ReflectionTestUtils.setField(realApplications, "decisionPersistenceService", decisions);
    ReflectionTestUtils.setField(realApplications, "recruitmentWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(realApplications, "stageHistoryPersistenceService", history);
    ReflectionTestUtils.setField(realApplications, "followUpPlanLifecycle", followUpPlans);
    ReflectionTestUtils.setField(realApplications, "interviewPersistenceService", interviews);
    ReflectionTestUtils.setField(realApplications, "authorization", authorization);
    ReflectionTestUtils.setField(realApplications, "demands", demands);
    ReflectionTestUtils.setField(realApplications, "ids", ids);
    ReflectionTestUtils.setField(realApplications, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ProxyFactory applicationFactory = new ProxyFactory(realApplications);
    applicationFactory.setProxyTargetClass(true);
    applicationFactory.addAdvice(restrictionTransactionInterceptor(transactions));
    ReflectionTestUtils.setField(service, "applications", applicationFactory.getProxy());
    ReflectionTestUtils.setField(service, "activityOrigins", origins);
    SubmissionAssembler assembler = spy(new SubmissionAssembler());
    doAnswer(call -> {
      transactions.read("restriction-response");
      return call.callRealMethod();
    }).when(assembler).toResponse(any(ClientRestriction.class));
    ReflectionTestUtils.setField(service, "assembler", assembler);
    ProxyFactory submissionFactory = new ProxyFactory(service);
    submissionFactory.setProxyTargetClass(true);
    submissionFactory.addAdvice(restrictionTransactionInterceptor(transactions));
    SubmissionApplicationManager outer = (SubmissionApplicationManager) submissionFactory.getProxy();

    if (membership.equals("revoked")) {
      assertThatThrownBy(() -> outer.restrictApplication(APPLICATION, command, "outer-permission-current"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
      verifyNoInteractions(restrictions, decisions, steps, history, followUpPlans);
      assertThat(applicationLocks[0]).isEqualTo(1);
      assertThat(storedApplication.getVersion()).isEqualTo(2L);
      assertThat(storedApplication.getCurrentStage()).isEqualTo("DECISION");
      assertThat(storedApplication.getOutcome()).isNull();
      assertThat(ownApplicationWrite[0]).isFalse();
      assertThat(transactions.events).doesNotContain("restriction-history", "restriction-origin",
          "restriction-projection", "snapshot-members", "ordinary-command-key-miss", "restriction-response");
    } else {
      // 独立业务期望：锁前已加入且仍生效的非owner应原子完成标签与人工终止。
      assertThatCode(() -> {
        var response = outer.restrictApplication(APPLICATION, command, "outer-permission-current");
        assertThat(response.getId()).isEqualTo(SUBMISSION);
        assertThat(response.getSourceApplicationId()).isEqualTo(APPLICATION);
        assertThat(response.getClientId()).isEqualTo(CLIENT);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
      }).doesNotThrowAnyException();
      assertThat(applicationLocks[0]).isEqualTo(2);
      assertThat(transactions.joinedCount).isGreaterThanOrEqualTo(2);
      assertThat(transactions.events).containsSubsequence("current-members", "restriction-history",
          "restriction-origin", "restriction-projection", "app-lock", "ordinary-command-key-miss",
          "command-reservation", "decision-fact", "decision-step", "application-outcome",
          "application-stage-history", "restriction-response");
      assertThat(transactions.state.committed).contains("restriction-history", "restriction-origin",
          "restriction-projection", "command-reservation", "decision-fact", "decision-step",
          "application-outcome", "application-stage-history", "follow-up-cancel");
      assertThat(accessTargets).hasSize(2).allSatisfy(target ->
          assertThat(target.getCollaboratorUserIds()).containsExactly(ACTOR));
      assertThat(storedApplication.getVersion()).isEqualTo(membership.equals("added") ? 3L : 2L);
      assertThat(storedApplication.getCurrentStage()).isEqualTo("TERMINATED");
      assertThat(storedApplication.getOutcome()).isEqualTo("REJECT");
      assertThat(storedApplication.getTerminalAt()).isEqualTo(NOW);
      assertThat(storedApplication.getActiveSlot()).isNull();
      assertThat(ownApplicationWrite[0]).isTrue();
    }
    assertThat(transactions.beginCount).isEqualTo(1);
    assertThat(transactions.outerIsolation).isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
    assertThat(transactions.commitCount).isEqualTo(membership.equals("revoked") ? 0 : 1);
    assertThat(transactions.rollbackCount).isEqualTo(membership.equals("revoked") ? 1 : 0);
    assertThat(transactions.state.active).isFalse();
    assertThat(transactions.state.pending).isEmpty();
    assertThat(transactions.events).containsSubsequence("ordinary-jd-id", "jd-lock", "app-lock", "current-members");
    assertThat(snapshotApplication.getVersion()).isEqualTo(1L);
    assertThat(snapshotApplication).isNotSameAs(app).isNotSameAs(storedApplication);
    if (!membership.equals("existing")) {
      assertThat(transactions.events).containsSubsequence("ordinary-jd-id", "winner-app-lock-before-t1-locks",
          "winner-collaborators-commit-before-t1-locks", "jd-lock", "app-lock");
    }
    verifyNoInteractions(snapshots, feedback, acknowledgements);
    // 两个真实公开proxy/注解与shared REQUIRED事务是实际代码；RR读视图/并发提交是替身，未验证MySQL。
  }

  private TransactionInterceptor restrictionTransactionInterceptor(SharedRestrictionTransactions transactions) {
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    return interceptor;
  }

  // 同一shared state识别真实公开cross-proxy的REQUIRED加入；合成账本不代替MySQL事务。
  private static final class SharedRestrictionTransactions extends AbstractPlatformTransactionManager {
    private final RestrictionTransactionState state = new RestrictionTransactionState();
    private final List<String> events = new ArrayList<>();
    private int beginCount;
    private int joinedCount;
    private int commitCount;
    private int rollbackCount;
    private int outerIsolation;
    private void read(String event) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(state.active).isTrue();
      events.add(event);
    }
    private void write(String fact) {
      read(fact);
      state.pending.add(fact);
    }
    @Override protected Object doGetTransaction() { return state; }
    @Override protected boolean isExistingTransaction(Object transaction) {
      if (state.active) joinedCount++;
      return state.active;
    }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
      state.active = true;
      state.rollbackOnly = false;
      outerIsolation = definition.getIsolationLevel();
      beginCount++;
    }
    @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { state.rollbackOnly = true; }
    @Override protected void doCommit(DefaultTransactionStatus status) {
      commitCount++;
      state.committed.addAll(state.pending);
      state.pending.clear();
    }
    @Override protected void doRollback(DefaultTransactionStatus status) {
      rollbackCount++;
      state.pending.clear();
    }
    @Override protected void doCleanupAfterCompletion(Object transaction) { state.active = false; }
  }

  private static final class RestrictionTransactionState implements SmartTransactionObject {
    private boolean active;
    private boolean rollbackOnly;
    private final List<String> pending = new ArrayList<>();
    private final List<String> committed = new ArrayList<>();
    @Override public boolean isRollbackOnly() { return rollbackOnly; }
    @Override public void flush() {}
  }

}
