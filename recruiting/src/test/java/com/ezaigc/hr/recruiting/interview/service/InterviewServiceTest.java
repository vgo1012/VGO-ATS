package com.ezaigc.hr.recruiting.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.identity.application.adapter.IdentityAuthorizationAdapter;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import com.ezaigc.hr.recruiting.activity.application.manager.ActivityOriginManager;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowTemplateEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.interview.application.assembler.InterviewAssembler;
import com.ezaigc.hr.recruiting.interview.domain.request.InterviewFeedbackRequest;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackSummaryResponse;
import com.ezaigc.hr.recruiting.interview.domain.response.InterviewFeedbackVersionResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.EngagementLock;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.EngagementLockPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.LockHistoryPersistenceService;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.mockito.Answers;
import org.springframework.beans.BeanUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort.DeliveryResult;
import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort.NotificationMessage;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewNotificationApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.dto.CreateInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.InterviewFeedbackDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.UpdateInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.dto.RescheduleInterviewDTO;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackView;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationType;
import com.ezaigc.hr.recruiting.recruitment.application.manager.EngagementLockApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService.LockSummary;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.InterviewContext;
import com.ezaigc.hr.recruiting.submission.application.adapter.ClientSubmissionInterviewValidationAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ezaigc.hr.recruiting.interview.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewDetails;
import java.util.function.Function;
import java.util.function.Supplier;
import org.mockito.Mockito;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;

class InterviewServiceTest {
  private static final String ACTOR = "A".repeat(26);
  private static final String DEPARTMENT = "D".repeat(26);
  private static final String INTERVIEW_ID = "I".repeat(26);
  private static final String APPLICATION_ID = "P".repeat(26);
  private static final String CANDIDATE_ID = "C".repeat(26);
  private static final String JD_ID = "J".repeat(26);
  private static final String JD_VERSION_ID = "V".repeat(26);
  private static final String WORKFLOW_VERSION_ID = "W".repeat(26);
  private static final String STEP_ID = "S".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

  private final InterviewPersistenceService persistence = mock(InterviewPersistenceService.class);
  private final ApplicationPersistenceService applicationPersistence = mock(ApplicationPersistenceService.class);
  private final FeedbackVersionPersistenceService feedbackVersions =
      mock(FeedbackVersionPersistenceService.class);
  private final InterviewFeedbackPersistenceService feedback =
      mock(InterviewFeedbackPersistenceService.class);
  private final InterviewIdempotencyPersistenceService idempotency =
      mock(InterviewIdempotencyPersistenceService.class);
  private final NotificationAttemptPersistenceService notificationAttempts =
      mock(NotificationAttemptPersistenceService.class);
  private final NotificationIntentPersistenceService notifications =
      mock(NotificationIntentPersistenceService.class);
  private final ParticipantPersistenceService participants =
      mock(ParticipantPersistenceService.class);
  private final ScheduleHistoryPersistenceService scheduleHistory =
      mock(ScheduleHistoryPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final IdentityDirectoryService directory = mock(IdentityDirectoryService.class);
  private final RecruitmentApplicationManager applications =
      mock(RecruitmentApplicationManager.class);
  private final ClientSubmissionInterviewValidationAdapter validator =
      mock(ClientSubmissionInterviewValidationAdapter.class);
  private final NotificationDeliveryPort delivery = mock(NotificationDeliveryPort.class);
  private final EngagementLockApplicationManager engagementLocks =
      mock(EngagementLockApplicationManager.class);
  private final PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
  private InterviewApplicationManager service;
  private final CompanyBlacklistQueryService blacklist = mock(CompanyBlacklistQueryService.class);
  private InterviewNotificationApplicationManager notificationManager;

  @BeforeEach
  void setUp() {
    when(persistence.lockInterview(any())).thenAnswer(call -> persistence.findInterview(call.getArgument(0)));
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
                Set.of(
                    "interview.schedule",
                    "interview.feedback.submit-own",
                    "interview.external-feedback.record",
                    "interview.feedback.read-authorized"),
                false,
                false));
    when(persistence.complete(INTERVIEW_ID, ACTOR, NOW)).thenReturn(1);
    when(ids.newId()).thenReturn("N".repeat(26), "O".repeat(26), "Q".repeat(26));
    when(persistence.insertIdempotency(any(), any(), any(), any(), any(), any())).thenReturn(1);
    service = new InterviewApplicationManager();
    when(persistence.findCandidateId(any())).thenReturn("C".repeat(26));
    when(persistence.findNotificationCandidateId(any())).thenReturn("C".repeat(26));
    ReflectionTestUtils.setField(service, "blacklist", blacklist);
    notificationManager = new InterviewNotificationApplicationManager();
    ReflectionTestUtils.setField(notificationManager, "persistence", persistence);
    ReflectionTestUtils.setField(notificationManager, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(notificationManager, "notificationIntentPersistenceService", notifications);
    ReflectionTestUtils.setField(notificationManager, "ids", ids);
    ReflectionTestUtils.setField(notificationManager, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(service, "persistence", persistence);
    ReflectionTestUtils.setField(service, "feedbackVersionPersistenceService", feedbackVersions);
    ReflectionTestUtils.setField(service, "interviewFeedbackPersistenceService", feedback);
    ReflectionTestUtils.setField(service, "interviewIdempotencyPersistenceService", idempotency);
    ReflectionTestUtils.setField(
        service, "notificationAttemptPersistenceService", notificationAttempts);
    ReflectionTestUtils.setField(service, "notificationIntentPersistenceService", notifications);
    ReflectionTestUtils.setField(service, "participantPersistenceService", participants);
    ReflectionTestUtils.setField(service, "scheduleHistoryPersistenceService", scheduleHistory);
    ReflectionTestUtils.setField(service, "authorization", authorization);
    ReflectionTestUtils.setField(service, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(service, "directory", directory);
    ReflectionTestUtils.setField(service, "applications", applications);
    ReflectionTestUtils.setField(service, "applicationPersistenceService", applicationPersistence);
    when(applicationPersistence.lockApplication(APPLICATION_ID)).thenReturn(new Application());
    ReflectionTestUtils.setField(service, "submissionValidator", validator);
    ReflectionTestUtils.setField(service, "delivery", delivery);
    ReflectionTestUtils.setField(service, "engagementLocks", engagementLocks);
    ReflectionTestUtils.setField(service, "ids", ids);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(
        service, "transactionTemplate", new TransactionTemplate(new TestTransactionManager()));
    ReflectionTestUtils.setField(
        service, "cursorCodec", new SignedCursorCodec("interview-test-signing-key-32-bytes"));
  }

  @Test
  void createWritesNothingWhenTheApplicationStepIsNotAnActiveInterviewStep() {
    when(applications.requireInterviewContext(APPLICATION_ID, STEP_ID))
        .thenThrow(
            new PlatformException(
                HttpStatus.UNPROCESSABLE_CONTENT, "INTERVIEW_STEP_REQUIRED", "不是可排期步骤", false));

    assertThatThrownBy(
            () ->
                service.createDetails(
                    new CreateInterviewDTO(
                        APPLICATION_ID,
                        STEP_ID,
                        NOW.plusSeconds(3600),
                        NOW.plusSeconds(7200),
                        "Asia/Shanghai",
                        "ONLINE",
                        null,
                        "https://meeting.example/1",
                        List.of(ACTOR),
                        "IN_APP",
                        null,
                        "安排面试"),
                    "create-1"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_STEP_REQUIRED"));

    verifyNoInteractions(persistence);
  }

  @ParameterizedTest
  @CsvSource({"INTERNAL,false", "EXTERNAL,true", "EXTERNAL,false"})
  void createPreservesEveryParticipantWithinTheDeclaredFiftyPersonLimit(String recruitmentType, boolean withSubmission) {
    AtomicInteger generatedIdSequence = new AtomicInteger();
    when(ids.newId())
        .thenAnswer(
            ignored -> {
              int sequence = generatedIdSequence.getAndIncrement();
              if (sequence == 0) {
                return "N".repeat(26);
              }
              if (sequence == 1) {
                return "O".repeat(26);
              }
              return String.format("9%025d", sequence);
            });
    List<String> participantIds =
        IntStream.rangeClosed(1, 21).mapToObj(index -> String.format("%026d", index)).toList();
    Interview saved = interview("EXTERNAL".equals(recruitmentType) ? "EXTERNAL_CLIENT" : "INTERNAL", "SCHEDULED");
    saved.setId("N".repeat(26));
    when(applications.requireInterviewContext(APPLICATION_ID, STEP_ID))
        .thenReturn(
            new InterviewContext(
                APPLICATION_ID,
                CANDIDATE_ID,
                JD_ID,
                JD_VERSION_ID,
                WORKFLOW_VERSION_ID,
                recruitmentType,
                STEP_ID,
                "INTERVIEW_ROUND_1",
                "PENDING",
                ACTOR,
                DEPARTMENT,
                Set.of()));
    String currentOwner = "B".repeat(26), currentDepartment = "F".repeat(26);
    InterviewContext currentContext = new InterviewContext(
        APPLICATION_ID, CANDIDATE_ID, JD_ID, JD_VERSION_ID, WORKFLOW_VERSION_ID, recruitmentType,
        STEP_ID, "INTERVIEW_ROUND_1", "PENDING", currentOwner, currentDepartment, Set.of());
    when(applications.requireLockedInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(currentContext);
    when(applications.bindClientInterviewStep(APPLICATION_ID, STEP_ID, "B".repeat(26))).thenReturn(currentContext);
    saved.setOwnerUserId(currentOwner);
    saved.setOwnerDepartmentId(currentDepartment);
    when(persistence.findInterview("N".repeat(26))).thenReturn(saved);
    when(persistence.findActiveTemplate("INTERVIEW_INVITATION"))
        .thenReturn(
            new TemplateVersion(
                "T".repeat(26),
                "K".repeat(26),
                1,
                "面试邀请",
                "{{applicationId}} {{startAt}} {{timezone}} {{meetingMode}} {{location}}",
                ACTOR,
                NOW));
    when(participants.insertParticipants(
            "N".repeat(26), participantIds, "INTERVIEWER", NOW))
        .thenReturn(true);
    List<Participant> savedParticipants = participantIds.stream()
        .map(userId -> new Participant("N".repeat(26), userId, "INTERVIEWER", "PENDING"))
        .toList();
    when(participants.findParticipants("N".repeat(26))).thenReturn(savedParticipants);
    when(participants.lockParticipantsByInterviewIds(List.of("N".repeat(26)))).thenReturn(savedParticipants);

    when(persistence.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    service.createDetails(
        new CreateInterviewDTO(
            APPLICATION_ID,
            STEP_ID,
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200),
            "Asia/Shanghai",
            "ONLINE",
            null,
            "https://meeting.example/1",
            participantIds,
            "IN_APP",
            withSubmission ? "B".repeat(26) : null,
            "安排面试"),
        "create-21-participants");

    verify(directory).activeUsers(Set.copyOf(participantIds));
    Object[] inserted = mockingDetails(persistence).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("insertInterview"))
        .findFirst().orElseThrow().getArguments();
    assertThat(inserted[18]).isEqualTo(currentOwner);
    assertThat(inserted[19]).isEqualTo(currentDepartment);
    if (withSubmission) {
      var order = Mockito.inOrder(validator, applications, persistence);
      order.verify(validator).requireSentAndMatching("B".repeat(26), APPLICATION_ID, JD_ID, JD_VERSION_ID);
      order.verify(applications).bindClientInterviewStep(APPLICATION_ID, STEP_ID, "B".repeat(26));
      order.verify(persistence).lockScheduledByStep(STEP_ID);
    } else {
      verify(applications, never()).bindClientInterviewStep(any(), any(), any());
      var order = Mockito.inOrder(applications, persistence);
      order.verify(applications).requireLockedInterviewContext(APPLICATION_ID, STEP_ID);
      order.verify(persistence).lockScheduledByStep(STEP_ID);
    }
    verify(participants)
        .insertParticipants("N".repeat(26), participantIds, "INTERVIEWER", NOW);
    verifyNoInteractions(notifications);
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "EXTERNAL"})
  void displayLabelCannotBypassLegacyClientAiSubmissionRequirements(String label) {
    InterviewContext context = new InterviewContext(APPLICATION_ID, CANDIDATE_ID, JD_ID, JD_VERSION_ID,
        WORKFLOW_VERSION_ID, "EXTERNAL", STEP_ID, "CLIENT_AI_INTERVIEW", "IN_PROGRESS", ACTOR, DEPARTMENT, Set.of());
    when(applications.requireInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(context);
    var command = new CreateInterviewDTO(APPLICATION_ID, STEP_ID, NOW.plusSeconds(3600), NOW.plusSeconds(7200),
        "Asia/Shanghai", "ONLINE", null, null, List.of(ACTOR), "NONE", null, "安排面试");
    command.setInterviewType(label);
    assertThatThrownBy(() -> service.createDetails(command, "legacy-label"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("INTERVIEW_TYPE_INVALID"));
    assertThat(mockingDetails(persistence).getInvocations())
        .noneMatch(invocation -> invocation.getMethod().getName().equals("insertInterview"));
  }

  @ParameterizedTest
  @CsvSource({"INTERNAL,true", "INTERNAL,false", "EXTERNAL,true", "EXTERNAL,false"})
  void independentTypeLabelAndOptionalInitialCommentPersistWithoutClientSubmission(String label, boolean hasComment) {
    InterviewContext context = new InterviewContext(APPLICATION_ID, CANDIDATE_ID, JD_ID, JD_VERSION_ID,
        WORKFLOW_VERSION_ID, "EXTERNAL", STEP_ID, "INTERVIEW_ROUND_1", "IN_PROGRESS", ACTOR, DEPARTMENT, Set.of());
    when(applications.requireInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(context);
    when(applications.requireLockedInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(context);
    Interview saved = interview(label, "SCHEDULED"); saved.setId("N".repeat(26));
    when(persistence.findInterview(saved.getId())).thenReturn(saved);
    when(persistence.updateById(any(Interview.class))).thenAnswer(call -> {
      Interview patch = call.getArgument(0); saved.setInitialComment(patch.getInitialComment()); return true;
    });
    when(participants.insertParticipants(any(), any(), any(), any())).thenReturn(true);
    when(persistence.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    var command = new CreateInterviewDTO(APPLICATION_ID, STEP_ID, NOW.plusSeconds(3600), NOW.plusSeconds(7200),
        "Asia/Shanghai", "ONLINE", null, null, "INTERNAL".equals(label) ? List.of(ACTOR) : List.of(),
        "NONE", null, "安排面试");
    command.setInterviewType(label); command.setInitialComment(hasComment ? "  候选人确认参加  " : null);
    var result = service.createDetails(command, "label-comment");
    assertThat(result.getInterview().getInitialComment()).isEqualTo(hasComment ? "候选人确认参加" : null);
    Object[] inserted = mockingDetails(persistence).getInvocations().stream()
        .filter(invocation -> invocation.getMethod().getName().equals("insertInterview"))
        .findFirst().orElseThrow().getArguments();
    assertThat(inserted[8]).isEqualTo(label);
    assertThat(inserted[10]).isNull();
    verify(applications, never()).bindClientInterviewStep(any(), any(), any());
    verifyNoInteractions(validator, feedback, feedbackVersions);
    verify(persistence, never()).complete(any(), any(), any());
    if (!hasComment) verify(persistence, never()).updateById(any(Interview.class));
  }

  @ParameterizedTest
  @CsvSource({"INTERNAL,INTERVIEW_ROUND_1", "EXTERNAL,PRE_SUBMISSION_INTERNAL_INTERVIEW"})
  void internalSchedulingRejectsAnInterviewCommittedAfterTheInitialSnapshot(String recruitmentType, String stepCode) {
    InterviewContext context = new InterviewContext(
        APPLICATION_ID, CANDIDATE_ID, JD_ID, JD_VERSION_ID, WORKFLOW_VERSION_ID,
        recruitmentType, STEP_ID, stepCode, "PENDING", ACTOR, DEPARTMENT, Set.of());
    when(applications.requireInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(context);
    when(applications.requireLockedInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(context);
    when(persistence.countActiveByStep(STEP_ID)).thenReturn(0);
    when(persistence.lockScheduledByStep(STEP_ID)).thenReturn(interview("INTERNAL", "SCHEDULED"));

    assertThatThrownBy(() -> service.createDetails(new CreateInterviewDTO(
        APPLICATION_ID, STEP_ID, NOW.plusSeconds(3600), NOW.plusSeconds(7200), "Asia/Shanghai",
        "ONLINE", null, "https://meeting.example/1", List.of(ACTOR), "IN_APP", null, "安排面试"), "parallel-create"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("ACTIVE_INTERVIEW_EXISTS"));
    assertThat(mockingDetails(persistence).getInvocations())
        .noneMatch(invocation -> invocation.getMethod().getName().equals("insertInterview"));
    verifyNoInteractions(participants, notifications, delivery);
  }

  @ParameterizedTest
  @ValueSource(strings = {"UPDATE", "RESCHEDULE", "CANCEL"})
  void blacklistBlocksScheduleChangesButAllowsCancellation(String action) {
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(any());
    Interview before = interview("INTERNAL", "SCHEDULED");
    Interview after = interview("INTERNAL", "CANCEL".equals(action) ? "CANCELLED" : "SCHEDULED");
    doReturn(before).when(persistence).lockInterview(INTERVIEW_ID);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(after);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);
    when(persistence.updateSchedule(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    when(persistence.cancel(INTERVIEW_ID, 0L, ACTOR, NOW)).thenReturn(1);
    when(persistence.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(participants.insertParticipants(any(), any(), any(), any())).thenReturn(true);
    when(persistence.findActiveTemplate(any())).thenReturn(new TemplateVersion(
        "T".repeat(26), "K".repeat(26), 1, "面试安排变更", "{{applicationId}} {{startAt}}", ACTOR, NOW));
    when(notifications.insertNotifications(any())).thenReturn(true);
    Supplier<InterviewDetails> command = () -> switch (action) {
      case "UPDATE" -> service.updateDetails(INTERVIEW_ID, new UpdateInterviewDTO(NOW.plusSeconds(10800), NOW.plusSeconds(14400),
          "Asia/Shanghai", "ONLINE", null, "https://meeting.example/2", List.of(ACTOR), "EMAIL", ACTOR, 0L, "调整面试安排"), "schedule-blacklist");
      case "RESCHEDULE" -> service.rescheduleDetails(INTERVIEW_ID,
          new RescheduleInterviewDTO(NOW.plusSeconds(10800), NOW.plusSeconds(14400), "Asia/Shanghai", 0L, "调整面试安排"), "schedule-blacklist");
      case "CANCEL" -> service.cancel(INTERVIEW_ID, 0L, "取消面试安排", "schedule-blacklist");
      default -> throw new AssertionError(action);
    };

      if ("CANCEL".equals(action)) {
        assertThat(command.get().getInterview()).isSameAs(after);
        verify(blacklist, never()).requireRecruitmentAllowed(any());
        verify(persistence).cancel(INTERVIEW_ID, 0L, ACTOR, NOW);
      } else {
        assertThatThrownBy(command::get).isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
        verify(persistence, never()).updateSchedule(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any());
        verifyNoInteractions(participants, notifications, delivery);
      }
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERVIEW_INVITATION", "INTERVIEW_RESCHEDULE", "INTERVIEW_CANCEL"})
  void blacklistBlocksNewScheduleNotificationButAllowsCancellationNotification(String type) {
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(any());
    NotificationIntent pending = notification("PENDING_MANUAL", 0, 0L, NOW, null);
    pending.setNotificationType(type);
    NotificationIntent claimed = notification("SENDING_EXTERNAL", 1, 1L, NOW, null);
    NotificationIntent sent = notification("SENT_EXTERNAL", 1, 2L, NOW, "message");
    doReturn(interview("INTERNAL", "INTERVIEW_CANCEL".equals(type) ? "CANCELLED" : "SCHEDULED"))
        .when(persistence).lockInterview(INTERVIEW_ID);
    when(persistence.lockNotification(pending.getId())).thenReturn(pending, claimed);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);
    when(notifications.updateNotification(any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), any(), any()))
        .thenReturn(1);
    when(notificationAttempts.insertNotificationAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(notifications.findNotification(pending.getId())).thenReturn(sent);
    when(delivery.deliver(any())).thenReturn(new DeliveryResult(true, "message", null, null));


    if ("INTERVIEW_CANCEL".equals(type)) {
      assertThat(service.retryNotification(pending.getId(), "retry-blacklist")).isSameAs(sent);
      verify(blacklist, never()).requireRecruitmentAllowed(any());
      verify(delivery).deliver(any());
    } else {
      assertThatThrownBy(() -> service.retryNotification(pending.getId(), "retry-blacklist"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
      verifyNoInteractions(delivery);
      verify(notifications, never()).updateNotification(any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), any(), any());
    }
  }

  @ParameterizedTest
  @CsvSource({"UPDATE,COMPLETED", "UPDATE,CANCELLED", "RESCHEDULE,COMPLETED", "RESCHEDULE,CANCELLED", "CANCEL,CANCELLED"})
  void completedScheduleCommandsReplayAfterStateChangesWithoutBypassingAuthorization(String action, String currentStatus) {
    Interview before = interview("INTERNAL", "SCHEDULED");
    Interview after = interview("INTERNAL", "CANCEL".equals(action) ? "CANCELLED" : "SCHEDULED");
    doReturn(before).when(persistence).lockInterview(INTERVIEW_ID);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(after);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);
    when(persistence.updateSchedule(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    when(persistence.cancel(INTERVIEW_ID, 0L, ACTOR, NOW)).thenReturn(1);
    when(persistence.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(participants.insertParticipants(any(), any(), any(), any())).thenReturn(true);
    when(persistence.findActiveTemplate(any())).thenReturn(new TemplateVersion(
        "T".repeat(26), "K".repeat(26), 1, "面试安排变更", "{{applicationId}} {{startAt}}", ACTOR, NOW));
    when(notifications.insertNotifications(any())).thenReturn(true);
    Function<String, InterviewDetails> command = key -> switch (action) {
      case "UPDATE" -> service.updateDetails(INTERVIEW_ID, new UpdateInterviewDTO(NOW.plusSeconds(10800), NOW.plusSeconds(14400),
          "Asia/Shanghai", "ONLINE", null, "https://meeting.example/2", List.of(ACTOR), "EMAIL", ACTOR, 0L, "调整面试安排"), key);
      case "RESCHEDULE" -> service.rescheduleDetails(INTERVIEW_ID,
          new RescheduleInterviewDTO(NOW.plusSeconds(10800), NOW.plusSeconds(14400), "Asia/Shanghai", 0L, "调整面试安排"), key);
      case "CANCEL" -> service.cancel(INTERVIEW_ID, 0L, "取消面试安排", key);
      default -> throw new AssertionError(action);
    };
    assertThat(command.apply("completed-schedule").getInterview()).isSameAs(after);
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertIdempotency(eq(ACTOR), eq("completed-schedule"), eq("INTERVIEW_" + action),
        fingerprint.capture(), eq(INTERVIEW_ID), eq(NOW));
    when(idempotency.findIdempotency(ACTOR, "completed-schedule"))
        .thenReturn(new IdempotencyRecord(
            ACTOR, "completed-schedule", "INTERVIEW_" + action, fingerprint.getValue(), INTERVIEW_ID));
    after.setStatus(currentStatus);
    doReturn(after).when(persistence).lockInterview(INTERVIEW_ID);
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "成功后生效的公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(any());

    assertThat(command.apply("completed-schedule").getInterview()).isSameAs(after);
    assertThatThrownBy(() -> command.apply("new-schedule-command"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_NOT_SCHEDULED"));
    verify(persistence).insertHistory(any(), eq(INTERVIEW_ID), eq(action), any(), any(), any(), eq(ACTOR), eq(NOW));
    verifyNoInteractions(notifications);
    doThrow(PlatformExceptions.forbidden("PERMISSION_DENIED", "当前已无排期权限"))
        .when(authorization).requireAccess(eq("interview.schedule"), any(AuthorizationTarget.class));
    assertThatThrownBy(() -> command.apply("completed-schedule"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("PERMISSION_DENIED"));
  }

  @Test
  void invitedUserWithoutRecruitmentProcessingAccessCannotSubmitInternalFeedback() {
    Interview interview = interview("INTERNAL", "SCHEDULED");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(participants.findParticipants(INTERVIEW_ID))
        .thenReturn(List.of(new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING")));
    when(authorization.canAccess(
            any(CurrentActorSnapshot.class),
            eq("interview.feedback.read-authorized"),
            any(AuthorizationTarget.class)))
        .thenReturn(true);
    doThrow(PlatformExceptions.forbidden("RECRUITMENT_PROCESSING_FORBIDDEN", "无活动处理权限"))
        .when(applications).requireLockedFollowUpAccess(APPLICATION_ID);

    assertThatThrownBy(
            () ->
                service.saveFeedbackView(
                    INTERVIEW_ID,
                    new InterviewFeedbackDTO("PASS", "通过", null, NOW, "SUBMITTED", "首次提交"),
                    "feedback-1"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));

    verify(feedback, never()).insertFeedback(any(), any(), any(), any(), any(), any());
  }

  @Test
  void draftFeedbackIsRejectedWithoutWrites() {
    Interview interview = interview("INTERNAL", "SCHEDULED");
    Participant participant = new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(participants.findParticipants(INTERVIEW_ID)).thenReturn(List.of(participant));

    assertThatThrownBy(() -> service.saveFeedbackView(
        INTERVIEW_ID,
        new InterviewFeedbackDTO("PASS", "技术基础扎实", null, NOW, "DRAFT", "保存草稿"),
        "feedback-draft-1"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_FEEDBACK_STATUS_INVALID"));

    verifyNoInteractions(feedback, feedbackVersions);
    verify(persistence, never()).complete(any(), any(), any());
  }

  @ParameterizedTest @ValueSource(strings={"PASS","FAIL"})
  void activeCompanyBlacklistBlocksFormalFeedbackIncludingFailedNonterminalInterview(String verdict) {
    Interview scheduled = interview("INTERNAL", "SCHEDULED");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(scheduled);
    when(persistence.lockInterview(INTERVIEW_ID)).thenReturn(scheduled);
    when(participants.findParticipants(INTERVIEW_ID))
        .thenReturn(List.of(new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING")));
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(CANDIDATE_ID);

    assertThatThrownBy(() -> service.saveFeedbackView(INTERVIEW_ID,
        new InterviewFeedbackDTO(verdict, "正式业务结果", null, NOW, "SUBMITTED", "正式提交"), "blocked-feedback"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verify(feedback, never()).insertFeedback(any(), any(), any(), any(), any(), any());
    verify(feedbackVersions, never()).insertFeedbackVersion(any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any());
    verify(persistence, never()).complete(any(), any(), any());
    verify(engagementLocks, never()).recordProgress(any(), any(), any());
  }

  @Test
  void externalFeedbackRequiresAnExplicitHumanSource() {
    Interview interview = interview("EXTERNAL_CLIENT", "SCHEDULED");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(participants.findParticipants(INTERVIEW_ID)).thenReturn(List.of());
    when(authorization.canAccess(
            any(CurrentActorSnapshot.class),
            eq("interview.feedback.read-authorized"),
            any(AuthorizationTarget.class)))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service.saveFeedbackView(
                    INTERVIEW_ID,
                    new InterviewFeedbackDTO("PASS", "甲方通过", null, NOW, "SUBMITTED", "人工代录"),
                    "external-feedback-1"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("EXTERNAL_FEEDBACK_SOURCE_REQUIRED"));
  }

  @Test
  void unavailableExternalNotificationIsPersistedAsFailureWithoutChangingInterviewTruth() {
    Interview interview = interview("INTERNAL", "SCHEDULED");
    NotificationIntent intent =
        new NotificationIntent(
            "T".repeat(26),
            INTERVIEW_ID,
            "INTERVIEW_INVITATION",
            "CANDIDATE",
            CANDIDATE_ID,
            "EMAIL",
            "M".repeat(26),
            "面试邀请",
            "请参加面试",
            "PENDING_MANUAL",
            null,
            null,
            null,
            0,
            0);
    NotificationIntent failed =
        new NotificationIntent(
            intent.getId(),
            INTERVIEW_ID,
            intent.getNotificationType(),
            intent.getRecipientType(),
            intent.getRecipientReference(),
            intent.getChannel(),
            intent.getTemplateVersionId(),
            intent.getRenderedSubject(),
            intent.getRenderedBody(),
            "FAILED_EXTERNAL",
            null,
            "DISABLED_NOT_CONFIGURED",
            "外部通知渠道未配置",
            1,
            2);
    NotificationIntent claimed =
        new NotificationIntent(
            intent.getId(),
            INTERVIEW_ID,
            intent.getNotificationType(),
            intent.getRecipientType(),
            intent.getRecipientReference(),
            intent.getChannel(),
            intent.getTemplateVersionId(),
            intent.getRenderedSubject(),
            intent.getRenderedBody(),
            "SENDING_EXTERNAL",
            null,
            null,
            null,
            1,
            1);
    when(persistence.lockNotification(intent.getId())).thenReturn(intent, claimed);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(participants.findParticipants(INTERVIEW_ID)).thenReturn(List.of());
    when(authorization.canAccess(
            any(CurrentActorSnapshot.class),
            eq("interview.feedback.read-authorized"),
            any(AuthorizationTarget.class)))
        .thenReturn(true);
    when(delivery.deliver(any()))
        .thenReturn(new DeliveryResult(false, null, "DISABLED_NOT_CONFIGURED", "外部通知渠道未配置"));
    when(notifications.updateNotification(
            eq(intent.getId()),
            eq("EMAIL"),
            eq("SENDING_EXTERNAL"),
            isNull(),
            isNull(),
            isNull(),
            eq(1),
            eq(0L),
            eq(ACTOR),
            eq(NOW)))
        .thenReturn(1);
    when(notifications.updateNotification(
            eq(intent.getId()),
            eq("EMAIL"),
            eq("FAILED_EXTERNAL"),
            isNull(),
            eq("DISABLED_NOT_CONFIGURED"),
            eq("外部通知渠道未配置"),
            eq(0),
            eq(1L),
            eq(ACTOR),
            eq(NOW)))
        .thenReturn(1);
    when(notificationAttempts.insertNotificationAttempt(
            any(),
            eq(intent.getId()),
            eq("EXTERNAL_RETRY"),
            eq("EMAIL"),
            eq("FAILED"),
            isNull(),
            eq("DISABLED_NOT_CONFIGURED"),
            eq("外部通知渠道未配置"),
            eq(ACTOR),
            eq(NOW)))
        .thenReturn(1);
    when(notifications.findNotification(intent.getId())).thenReturn(failed);

    NotificationIntent result = service.retryNotification(intent.getId(), "retry-1");

    assertThat(result.getStatus()).isEqualTo("FAILED_EXTERNAL");
    verify(notificationAttempts)
        .insertNotificationAttempt(
            any(),
            eq(intent.getId()),
            eq("EXTERNAL_RETRY"),
            eq("EMAIL"),
            eq("FAILED"),
            isNull(),
            eq("DISABLED_NOT_CONFIGURED"),
            eq("外部通知渠道未配置"),
            eq(ACTOR),
            eq(NOW));
    verify(persistence, never()).cancel(any(), anyLong(), any(), any());
    verify(persistence, never()).complete(any(), any(), any());
  }

  @Test
  void activeExternalDeliveryLeaseRejectsConcurrentRetry() {
    Interview interview = interview("INTERNAL", "SCHEDULED");
    NotificationIntent sending =
        new NotificationIntent(
            "T".repeat(26),
            INTERVIEW_ID,
            "INTERVIEW_INVITATION",
            "CANDIDATE",
            CANDIDATE_ID,
            "EMAIL",
            "M".repeat(26),
            "面试邀请",
            "请参加面试",
            "SENDING_EXTERNAL",
            null,
            null,
            null,
            1,
            1);
    sending.setUpdateTime(NOW.minusSeconds(60));
    when(persistence.lockNotification(sending.getId())).thenReturn(sending);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(participants.findParticipants(INTERVIEW_ID)).thenReturn(List.of());
    when(authorization.canAccess(
            any(CurrentActorSnapshot.class),
            eq("interview.feedback.read-authorized"),
            any(AuthorizationTarget.class)))
        .thenReturn(true);

    assertThatThrownBy(() -> service.retryNotification(sending.getId(), "retry-concurrent-1"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("NOTIFICATION_DELIVERY_IN_PROGRESS"));

    verifyNoInteractions(delivery);
    verifyNoInteractions(notifications);
    verifyNoInteractions(notificationAttempts);
  }

  @Test
  void expiredExternalDeliveryLeaseCanBeRecoveredWithStableProviderIdempotencyKey() {
    Interview interview = interview("INTERNAL", "SCHEDULED");
    NotificationIntent stale =
        notification("SENDING_EXTERNAL", 1, 1, NOW.minusSeconds(301), null);
    NotificationIntent claimed = notification("SENDING_EXTERNAL", 2, 2, NOW, null);
    NotificationIntent sent = notification("SENT_EXTERNAL", 2, 3, NOW, "provider-message-2");
    when(persistence.lockNotification(stale.getId())).thenReturn(stale, claimed);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(participants.findParticipants(INTERVIEW_ID)).thenReturn(List.of());
    when(authorization.canAccess(
            any(CurrentActorSnapshot.class),
            eq("interview.feedback.read-authorized"),
            any(AuthorizationTarget.class)))
        .thenReturn(true);
    when(notifications.updateNotification(
            eq(stale.getId()),
            eq("EMAIL"),
            eq("SENDING_EXTERNAL"),
            isNull(),
            isNull(),
            isNull(),
            eq(1),
            eq(1L),
            eq(ACTOR),
            eq(NOW)))
        .thenReturn(1);
    when(delivery.deliver(any()))
        .thenAnswer(
            invocation -> {
              NotificationMessage message = invocation.getArgument(0);
              assertThat(message.notificationId()).isEqualTo(stale.getId());
              return new DeliveryResult(true, "provider-message-2", null, null);
            });
    when(notifications.updateNotification(
            eq(stale.getId()),
            eq("EMAIL"),
            eq("SENT_EXTERNAL"),
            eq("provider-message-2"),
            isNull(),
            isNull(),
            eq(0),
            eq(2L),
            eq(ACTOR),
            eq(NOW)))
        .thenReturn(1);
    when(notificationAttempts.insertNotificationAttempt(
            any(),
            eq(stale.getId()),
            eq("EXTERNAL_RETRY"),
            eq("EMAIL"),
            eq("SENT"),
            eq("provider-message-2"),
            isNull(),
            isNull(),
            eq(ACTOR),
            eq(NOW)))
        .thenReturn(1);
    when(notifications.findNotification(stale.getId())).thenReturn(sent);

    NotificationIntent result = service.retryNotification(stale.getId(), "retry-recovery-1");

    assertThat(result.getStatus()).isEqualTo("SENT_EXTERNAL");
    assertThat(result.getVersion()).isEqualTo(3L);
  }

  private static NotificationIntent notification(
      String status, int retryCount, long version, Instant updateTime, String externalMessageId) {
    NotificationIntent value =
        new NotificationIntent(
            "T".repeat(26),
            INTERVIEW_ID,
            "INTERVIEW_INVITATION",
            "CANDIDATE",
            CANDIDATE_ID,
            "EMAIL",
            "M".repeat(26),
            "面试邀请",
            "请参加面试",
            status,
            externalMessageId,
            null,
            null,
            retryCount,
            version);
    value.setUpdateTime(updateTime);
    return value;
  }

  private static Interview interview(String type, String status) {
    return new Interview(
        INTERVIEW_ID,
        APPLICATION_ID,
        CANDIDATE_ID,
        JD_ID,
        JD_VERSION_ID,
        WORKFLOW_VERSION_ID,
        STEP_ID,
        "INTERVIEW_ROUND_1",
        type,
        1,
        null,
        status,
        NOW.plusSeconds(3600),
        NOW.plusSeconds(7200),
        "Asia/Shanghai",
        "ONLINE",
        null,
        "https://meeting.example/1",
        "IN_APP",
        "PENDING",
        ACTOR,
        DEPARTMENT,
        ACTOR,
        0,
        null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancelledInterviewCannotSendItsOldInvitation(boolean external) {
    Interview cancelled = interview("EXTERNAL_CLIENT", "CANCELLED");
    NotificationIntent invitation = notification("PENDING_MANUAL", 0, 0L, NOW, null);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(cancelled);
    when(persistence.lockNotification(invitation.getId())).thenReturn(invitation);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);

    assertThatThrownBy(() -> {
      if (external) {
        service.retryNotification(invitation.getId(), "cancelled-invitation");
      } else {
        service.recordManualSend(invitation.getId(), "EMAIL", "message", NOW, "cancelled-invitation");
      }
    }).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("INTERVIEW_CANCELLED"));
    verifyNoInteractions(delivery, notifications, notificationAttempts);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  void notificationWritesRequireSchedulingPermission(boolean external, boolean maySchedule) {
    CurrentActorSnapshot actor = authorization.currentActor();
    actor.setPermissions(maySchedule
        ? Set.of("interview.feedback.read-authorized", "interview.schedule")
        : Set.of("interview.feedback.read-authorized"));
    doAnswer(call -> {
      if (!actor.hasPermission(call.getArgument(0))) {
        throw PlatformExceptions.forbidden("PERMISSION_DENIED", "缺少操作权限");
      }
      return null;
    }).when(authorization).requireAccess(anyString(), any(AuthorizationTarget.class));
    Interview interview = interview("INTERNAL", "SCHEDULED");
    NotificationIntent pending = notification("PENDING_MANUAL", 0, 0L, NOW, null);
    NotificationIntent claimed = notification("SENDING_EXTERNAL", 1, 1L, NOW, null);
    NotificationIntent sent = notification(external ? "SENT_EXTERNAL" : "SENT_MANUAL", 1, 2L, NOW, "message");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview);
    when(persistence.lockNotification(pending.getId())).thenReturn(pending, claimed);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);
    when(notifications.updateNotification(any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), any(), any()))
        .thenReturn(1);
    when(notificationAttempts.insertNotificationAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(notifications.findNotification(pending.getId())).thenReturn(sent);
    when(delivery.deliver(any())).thenReturn(new DeliveryResult(true, "message", null, null));

    Supplier<NotificationIntent> command = () -> external
        ? service.retryNotification(pending.getId(), "notification-write-permission")
        : service.recordManualSend(pending.getId(), "EMAIL", "message", NOW, "notification-write-permission");

    if (maySchedule) {
      assertThat(command.get()).isSameAs(sent);
      verify(authorization).requireAccess("interview.schedule", new AuthorizationTarget(
          "INTERVIEW", INTERVIEW_ID, ACTOR, DEPARTMENT, Set.of()));
    } else {
      assertThatThrownBy(command::get).isInstanceOfSatisfying(PlatformException.class,
          error -> assertThat(error.code()).isEqualTo("PERMISSION_DENIED"));
      verifyNoInteractions(delivery, notifications, notificationAttempts);
      verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
    }
  }

  @ParameterizedTest
  @CsvSource({
      "false,SCHEDULED", "true,SCHEDULED",
      "false,CANCELLED", "true,CANCELLED",
      "false,COMPLETED", "true,COMPLETED"})
  void feedbackReplayUsesRequestTimeInsteadOfRetryClock(boolean explicitTime, String statusAfterSave) {
    String key = "feedback-stable-time";
    String feedbackId = "F".repeat(26);
    Instant suppliedTime = explicitTime ? NOW.minusSeconds(30) : null;
    Instant storedTime = explicitTime ? suppliedTime : NOW;
    Feedback saved = new Feedback(feedbackId, INTERVIEW_ID, ACTOR, "INTERNAL", "SUBMITTED", 1);
    FeedbackVersion version = new FeedbackVersion("X".repeat(26), feedbackId, 1,
        "PASS", "技术基础扎实", null, storedTime, "正式提交");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview("INTERNAL", "SCHEDULED"));
    when(participants.findParticipants(INTERVIEW_ID))
        .thenReturn(List.of(new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING")));
    when(ids.newId()).thenReturn(feedbackId, version.getId());
    when(feedback.findFeedbackByAuthor(INTERVIEW_ID, ACTOR, "INTERNAL")).thenReturn(null, saved);
    when(feedback.insertFeedback(feedbackId, INTERVIEW_ID, ACTOR, "INTERNAL", "SUBMITTED", NOW)).thenReturn(1);
    when(feedbackVersions.insertFeedbackVersion(version.getId(), feedbackId, 1,
        "PASS", "技术基础扎实", null, storedTime, "正式提交", ACTOR, NOW)).thenReturn(1);
    when(feedbackVersions.findFeedbackVersion(feedbackId, 1)).thenReturn(version);
    AtomicReference<IdempotencyRecord> reservation = new AtomicReference<>();
    when(idempotency.findIdempotency(ACTOR, key)).thenAnswer(call -> reservation.get());
    when(persistence.insertIdempotency(eq(ACTOR), eq(key), any(), any(), eq(feedbackId), eq(NOW)))
        .thenAnswer(call -> {
          reservation.set(new IdempotencyRecord(ACTOR, key, call.getArgument(2),
              call.getArgument(3), feedbackId));
          return 1;
        });
    InterviewFeedbackDTO command = new InterviewFeedbackDTO(
        "PASS", "技术基础扎实", null, suppliedTime, "SUBMITTED", "正式提交");
    assertThat(service.saveFeedbackView(INTERVIEW_ID, command, key).getCurrentVersion()).isSameAs(version);
    ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));
    if (!statusAfterSave.equals("SCHEDULED")) {
      when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview("INTERNAL", statusAfterSave));
    }

    assertThat(service.saveFeedbackView(INTERVIEW_ID, command, key).getCurrentVersion()).isSameAs(version);
    command.setFeedbackOccurredAt(NOW.plusSeconds(60));
    assertThatThrownBy(() -> service.saveFeedbackView(INTERVIEW_ID, command, key))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    verify(feedback).insertFeedback(feedbackId, INTERVIEW_ID, ACTOR, "INTERNAL", "SUBMITTED", NOW);
    verify(feedback, never()).advanceFeedback(any(), any(), anyInt(), any(), any());
    verify(feedbackVersions).insertFeedbackVersion(version.getId(), feedbackId, 1,
        "PASS", "技术基础扎实", null, storedTime, "正式提交", ACTOR, NOW);
    verify(persistence).insertIdempotency(any(), any(), any(), any(), any(), any());
    verify(persistence).complete(INTERVIEW_ID, ACTOR, NOW);
    if (!statusAfterSave.equals("SCHEDULED")) {
      assertThatThrownBy(() -> service.saveFeedbackView(INTERVIEW_ID, command, "new-feedback-after-terminal"))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("INTERVIEW_" + statusAfterSave));
      doThrow(PlatformExceptions.forbidden("PERMISSION_REQUIRED", "权限已撤销"))
          .when(applications).requireLockedFollowUpAccess(APPLICATION_ID);
      assertThatThrownBy(() -> service.saveFeedbackView(INTERVIEW_ID, command, key))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("PERMISSION_REQUIRED"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "EXTERNAL_CLIENT", "EXTERNAL_CLIENT_AI"})
  void submittedFeedbackCannotBecomePrivateAgain(String type) {
    boolean internal = type.equals("INTERNAL");
    String kind = internal ? "INTERNAL" : "EXTERNAL_RECORDED";
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview(type,"SCHEDULED"));
    when(participants.findParticipants(INTERVIEW_ID))
        .thenReturn(List.of(new Participant(INTERVIEW_ID,ACTOR,"INTERVIEWER","PENDING")));
    var submitted = new Feedback("F".repeat(26),INTERVIEW_ID,ACTOR,kind,"SUBMITTED",3);
    when(feedback.findFeedbackByAuthor(INTERVIEW_ID,ACTOR,kind)).thenReturn(submitted);
    assertThatThrownBy(()->service.saveFeedbackView(INTERVIEW_ID,
        new InterviewFeedbackDTO("PASS","补充意见",internal?null:"甲方邮件",NOW,"DRAFT","试图退回草稿"),
        "submitted-cannot-be-private"))
        .isInstanceOfSatisfying(PlatformException.class,
            error->assertThat(error.code()).isEqualTo("INTERVIEW_FEEDBACK_STATUS_INVALID"));
    verify(feedback,never()).advanceFeedback(any(),any(),anyInt(),any(),any());
    verifyNoInteractions(feedbackVersions);
    assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");
    assertThat(submitted.getCurrentVersionNo()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "EXTERNAL_CLIENT", "EXTERNAL_CLIENT_AI"})
  void completedInterviewCannotAppendFeedback(String type) {
    boolean internal = type.equals("INTERNAL");
    String kind = internal ? "INTERNAL" : "EXTERNAL_RECORDED";
    Feedback submitted = new Feedback("F".repeat(26), INTERVIEW_ID, ACTOR, kind, "SUBMITTED", 1);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview(type, "COMPLETED"));
    when(participants.findParticipants(INTERVIEW_ID))
        .thenReturn(List.of(new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING")));
    when(feedback.findFeedbackByAuthor(INTERVIEW_ID, ACTOR, kind)).thenReturn(submitted);

    assertThatThrownBy(() -> service.saveFeedbackView(
        INTERVIEW_ID,
        new InterviewFeedbackDTO(
            "PASS", "补充意见", internal ? null : "甲方邮件", NOW, "SUBMITTED", "追加反馈"),
        "feedback-after-completion"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_COMPLETED"));

    verify(feedback, never()).advanceFeedback(any(), any(), anyInt(), any(), any());
    verifyNoInteractions(feedbackVersions);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void completedInterviewCannotSendItsOldInvitation(boolean external) {
    Interview completed = interview("EXTERNAL_CLIENT", "COMPLETED");
    NotificationIntent invitation = notification("PENDING_MANUAL", 0, 0L, NOW, null);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(completed);
    when(persistence.lockNotification(invitation.getId())).thenReturn(invitation);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);

    assertThatThrownBy(() -> {
      if (external) {
        service.retryNotification(invitation.getId(), "completed-invitation");
      } else {
        service.recordManualSend(
            invitation.getId(), "EMAIL", "message", NOW, "completed-invitation");
      }
    }).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("INTERVIEW_COMPLETED"));

    verifyNoInteractions(delivery, notifications, notificationAttempts);
  }

  @ParameterizedTest
  @CsvSource({"false,CANCELLED", "true,CANCELLED", "false,COMPLETED", "true,COMPLETED"})
  void completedNotificationRequestReplaysAfterTerminalStateWithoutSendingAgain(
      boolean external, String terminalStatus) {
    String key = "completed-notification";
    String action = external ? "NOTIFICATION_RETRY" : "NOTIFICATION_MANUAL_SEND";
    NotificationIntent pending = notification("PENDING_MANUAL", 0, 0L, NOW, null);
    NotificationIntent claimed = notification("SENDING_EXTERNAL", 1, 1L, NOW, null);
    NotificationIntent sent = notification(external ? "SENT_EXTERNAL" : "SENT_MANUAL", 1, 2L, NOW, "message");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview("INTERNAL", "SCHEDULED"));
    when(persistence.lockNotification(pending.getId())).thenReturn(pending, claimed);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);
    when(notifications.updateNotification(any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), any(), any()))
        .thenReturn(1);
    when(notificationAttempts.insertNotificationAttempt(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(notifications.findNotification(pending.getId())).thenReturn(sent);
    when(delivery.deliver(any())).thenReturn(new DeliveryResult(true, "message", null, null));
    Function<String, NotificationIntent> command = requestKey -> external
        ? service.retryNotification(pending.getId(), requestKey)
        : service.recordManualSend(pending.getId(), "EMAIL", "message", NOW, requestKey);
    assertThat(command.apply(key)).isSameAs(sent);
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    verify(persistence).insertIdempotency(eq(ACTOR), eq(key), eq(action),
        fingerprint.capture(), eq(pending.getId()), eq(NOW));
    when(idempotency.findIdempotency(ACTOR, key)).thenReturn(
        new IdempotencyRecord(ACTOR, key, action, fingerprint.getValue(), pending.getId()));
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview("INTERNAL", terminalStatus));
    doReturn(sent).when(persistence).lockNotification(pending.getId());
    Mockito.clearInvocations(persistence, notifications, notificationAttempts, delivery, blacklist, authorization);

    assertThat(command.apply(key)).isSameAs(sent);
    verify(authorization).requireAccess("interview.schedule", new AuthorizationTarget(
        "INTERVIEW", INTERVIEW_ID, ACTOR, DEPARTMENT, Set.of()));
    verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
    verify(notifications, never()).updateNotification(any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), any(), any());
    verifyNoInteractions(notificationAttempts, delivery);
    verify(blacklist, never()).requireRecruitmentAllowed(any());

    assertThatThrownBy(() -> command.apply("new-notification-after-terminal"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_" + terminalStatus));
    doThrow(PlatformExceptions.forbidden("PERMISSION_DENIED", "缺少操作权限"))
        .when(authorization).requireAccess(eq("interview.schedule"), any(AuthorizationTarget.class));
    assertThatThrownBy(() -> command.apply(key)).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("PERMISSION_DENIED"));
    verify(notifications, never()).updateNotification(any(), any(), any(), any(), any(), any(), anyInt(), anyLong(), any(), any());
    verifyNoInteractions(notificationAttempts, delivery);
    verify(blacklist, never()).requireRecruitmentAllowed(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "EXTERNAL_CLIENT"})
  void invitedInterviewerCannotCancelAnotherRecruitersSchedule(String type) {
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview(type, "SCHEDULED"));
    when(applications.requireLockedFollowUpCancellation(APPLICATION_ID))
        .thenThrow(PlatformExceptions.forbidden("RECRUITMENT_PROCESSING_FORBIDDEN", "仅负责人或流程协作人可处理"));

    assertThatThrownBy(() -> service.cancel(INTERVIEW_ID, 0L, "取消面试", "invited-cancel"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_PROCESSING_FORBIDDEN"));
    verify(persistence, never()).lockInterview(any());
    verify(persistence, never()).cancel(any(), anyLong(), any(), any());
    verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "EXTERNAL_CLIENT"})
  void cancellationRechecksStateAfterApplicationLock(String type) {
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview(type, "SCHEDULED"));
    when(persistence.lockInterview(INTERVIEW_ID)).thenReturn(interview(type, "CANCELLED"));

    assertThatThrownBy(() -> service.cancel(INTERVIEW_ID, 0L, "取消面试", "late-cancel"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_NOT_SCHEDULED"));
    var locks = Mockito.inOrder(applications, persistence);
    locks.verify(persistence).findInterview(INTERVIEW_ID);
    locks.verify(applications).requireLockedFollowUpCancellation(APPLICATION_ID);
    locks.verify(persistence).lockInterview(INTERVIEW_ID);
    verify(authorization, never()).canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class));
    verify(persistence, never()).cancel(any(), anyLong(), any(), any());
    verify(persistence, never()).insertHistory(any(), any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(notifications, engagementLocks);
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "EXTERNAL_CLIENT"})
  void cancellationRechecksSchedulePermissionAfterApplicationLock(String type) {
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview(type, "SCHEDULED"));
    when(persistence.lockInterview(INTERVIEW_ID)).thenReturn(interview(type, "SCHEDULED"));
    Mockito.doNothing().doThrow(PlatformExceptions.forbidden("PERMISSION_DENIED", "当前已无排期权限"))
        .when(authorization).requireAccess(eq("interview.schedule"), any(AuthorizationTarget.class));

    assertThatThrownBy(() -> service.cancel(INTERVIEW_ID, 0L, "取消面试", "revoked-cancel"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("PERMISSION_DENIED"));
    var locks = Mockito.inOrder(applications, persistence);
    locks.verify(applications).requireLockedFollowUpCancellation(APPLICATION_ID);
    locks.verify(persistence).lockInterview(INTERVIEW_ID);
    verify(persistence, never()).insertIdempotency(any(), any(), any(), any(), any(), any());
    verify(persistence, never()).cancel(any(), anyLong(), any(), any());
    verifyNoInteractions(notifications, engagementLocks);
  }

  @ParameterizedTest
  @ValueSource(strings = {"INTERNAL", "EXTERNAL_CLIENT"})
  void feedbackRechecksCancellationWithACurrentReadBeforeWriting(String type) {
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview(type, "SCHEDULED"));
    when(persistence.lockInterview(INTERVIEW_ID)).thenReturn(interview(type, "CANCELLED"));
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);

    assertThatThrownBy(() -> service.saveFeedbackView(INTERVIEW_ID,
        new InterviewFeedbackDTO("PASS", "甲方通过", "甲方邮件", NOW, "SUBMITTED", "人工代录"), "late-feedback"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTERVIEW_CANCELLED"));
    var locks = Mockito.inOrder(applicationPersistence, persistence);
    locks.verify(applicationPersistence).lockApplication(APPLICATION_ID);
    locks.verify(persistence).lockInterview(INTERVIEW_ID);
    verify(feedback).findFeedbackByAuthor(INTERVIEW_ID, ACTOR, type.equals("INTERNAL") ? "INTERNAL" : "EXTERNAL_RECORDED");
    Mockito.verifyNoMoreInteractions(feedback);
    verifyNoInteractions(feedbackVersions, engagementLocks);
    verify(persistence, never()).complete(any(), any(), any());
  }

  @Test
  void manualSendCannotSucceedWithoutItsAuditAttempt() {
    NotificationIntent pending = notification("PENDING_MANUAL", 0, 0L, NOW, null);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview("INTERNAL", "SCHEDULED"));
    when(persistence.lockNotification(pending.getId())).thenReturn(pending);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);
    when(notifications.updateNotification(eq(pending.getId()), eq("EMAIL"), eq("SENT_MANUAL"),
        eq("message"), isNull(), isNull(), eq(0), eq(0L), eq(ACTOR), eq(NOW))).thenReturn(1);
    when(notificationAttempts.insertNotificationAttempt(any(), eq(pending.getId()), eq("MANUAL_SEND"),
        eq("EMAIL"), eq("SENT"), eq("message"), isNull(), isNull(), eq(ACTOR), eq(NOW))).thenReturn(0);
    when(notifications.findNotification(pending.getId()))
        .thenReturn(notification("SENT_MANUAL", 0, 1L, NOW, "message"));
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.addAdvice(interceptor);
    InterviewApplicationManager proxy = (InterviewApplicationManager) proxyFactory.getProxy();

    assertThatThrownBy(() -> proxy.recordManualSend(pending.getId(), "EMAIL", "message", NOW, "manual-audit-failure"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("通知发送尝试记录写入失败");
    assertThat(transactions.committed).isZero();
    assertThat(transactions.rolledBack).isOne();
    verify(notifications, never()).findNotification(any());
    verifyNoInteractions(delivery);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancellationNotificationsPreserveDistinctRecipientsAndRequireTheBatchWrite(boolean saved) {
    Interview cancelled = interview("INTERNAL", "CANCELLED");
    cancelled.setCandidateNotificationChannel("EMAIL");
    String interviewer = "U".repeat(26), templateVersion = "T".repeat(26);
    when(persistence.findActiveTemplate("INTERVIEW_CANCEL")).thenReturn(new TemplateVersion(
        templateVersion, "K".repeat(26), 1, "面试取消", "{{applicationId}} {{startAt}} {{timezone}} {{location}}", ACTOR, NOW));
    when(participants.lockParticipantsByInterviewIds(any())).thenReturn(List.of(
        new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING"),
        new Participant(INTERVIEW_ID, interviewer, "INTERVIEWER", "PENDING"),
        new Participant(INTERVIEW_ID, interviewer, "INTERVIEWER", "PENDING")));
    when(notifications.insertNotifications(any())).thenReturn(saved);

    Runnable prepare = () -> notificationManager.createNotificationsBatch(
        List.of(cancelled), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR);
    if (saved) {
      prepare.run();
    } else {
      assertThatThrownBy(prepare::run)
          .isInstanceOf(IllegalStateException.class).hasMessage("面试通知意图批量写入失败");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    ArgumentCaptor<List<NotificationIntent>> batch = ArgumentCaptor.forClass((Class) List.class);
    verify(notifications).insertNotifications(batch.capture());
    assertThat(batch.getValue()).extracting(NotificationIntent::getRecipientReference)
        .containsExactly(ACTOR, interviewer, CANDIDATE_ID);
    assertThat(batch.getValue()).extracting(NotificationIntent::getChannel).containsExactly("IN_APP", "IN_APP", "EMAIL");
    assertThat(batch.getValue()).allSatisfy(intent -> {
      assertThat(intent.getNotificationType()).isEqualTo("INTERVIEW_CANCEL");
      assertThat(intent.getTemplateVersionId()).isEqualTo(templateVersion);
      assertThat(intent.getStatus()).isEqualTo("PENDING_MANUAL");
      assertThat(intent.getRenderedBody()).contains(APPLICATION_ID).doesNotContain("{{");
      assertThat(intent.getCreateId()).isEqualTo(ACTOR);
    });
    verify(participants).lockParticipantsByInterviewIds(Set.of(INTERVIEW_ID));
    verify(participants, never()).findParticipants(any());
    verify(participants, never()).findParticipantsByInterviewIds(any());
    verifyNoInteractions(delivery);
  }

  @ParameterizedTest
  @CsvSource({"FEEDBACK,0", "FEEDBACK,2", "VERSION,0", "VERSION,2", "VERSION,1", "FEEDBACK,1"})
  void formalFeedbackAndItsAuditFactMustSucceedTogether(String write, int affectedRows) {
    String feedbackId = "F".repeat(26), versionId = "X".repeat(26);
    int nextVersion = 1;
    Feedback saved = new Feedback(feedbackId, INTERVIEW_ID, ACTOR, "INTERNAL", "SUBMITTED", nextVersion);
    FeedbackVersion version = new FeedbackVersion(versionId, feedbackId, nextVersion,
        "PASS", "技术基础扎实", null, NOW, "正式提交");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview("INTERNAL", "SCHEDULED"));
    when(participants.findParticipants(INTERVIEW_ID))
        .thenReturn(List.of(new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING")));
    when(ids.newId()).thenReturn(feedbackId, versionId);
    when(feedback.findFeedbackByAuthor(INTERVIEW_ID, ACTOR, "INTERNAL"))
        .thenReturn(null, saved);
    when(feedback.insertFeedback(feedbackId, INTERVIEW_ID, ACTOR, "INTERNAL", "SUBMITTED", NOW))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return write.equals("FEEDBACK") ? affectedRows : 1;
        });
    when(feedbackVersions.insertFeedbackVersion(versionId, feedbackId, nextVersion,
        "PASS", "技术基础扎实", null, NOW, "正式提交", ACTOR, NOW)).thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return write.equals("VERSION") ? affectedRows : 1;
        });
    when(feedbackVersions.findFeedbackVersion(feedbackId, nextVersion)).thenReturn(version);
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.addAdvice(interceptor);
    InterviewApplicationManager proxy = (InterviewApplicationManager) proxyFactory.getProxy();
    Supplier<FeedbackView> command = () -> proxy.saveFeedbackView(INTERVIEW_ID,
        new InterviewFeedbackDTO("PASS", "技术基础扎实", null, NOW, "SUBMITTED", "正式提交"), "atomic-feedback");

    if (affectedRows == 1) {
      assertThat(command.get().getCurrentVersion()).isSameAs(version);
      assertThat(transactions.committed).isOne();
      assertThat(transactions.rolledBack).isZero();
    } else {
      assertThatThrownBy(command::get).isInstanceOf(IllegalStateException.class)
          .hasMessage(write.equals("FEEDBACK") ? "面评记录写入失败" : "面评版本记录写入失败");
      assertThat(transactions.committed).isZero();
      assertThat(transactions.rolledBack).isOne();
      verify(feedbackVersions, never()).findFeedbackVersion(any(), anyInt());
      if (write.equals("FEEDBACK")) {
        verifyNoInteractions(feedbackVersions);
      }
    }
    verify(feedback, never()).advanceFeedback(any(), any(), anyInt(), any(), any());
    if (affectedRows == 1) verify(persistence).complete(INTERVIEW_ID, ACTOR, NOW);
    else {
      verify(persistence, never()).complete(any(), any(), any());
      verifyNoInteractions(engagementLocks);
    }
  }

  @ParameterizedTest
  @CsvSource({
      "INTERNAL,SCHEDULED,0,0", "INTERNAL,SCHEDULED,0,2",
      "EXTERNAL_CLIENT,SCHEDULED,3,0", "EXTERNAL_CLIENT,SCHEDULED,3,2",
      "INTERNAL,SCHEDULED,0,1", "EXTERNAL_CLIENT,SCHEDULED,3,1",
      "EXTERNAL_CLIENT_AI,SCHEDULED,3,1", "INTERNAL,SCHEDULED,1,0"})
  void onlySuccessfulFirstCompletionRenewsTheEngagementLock(
      String type, String status, int recordedInterviewerCount, int completionRows) {
    String feedbackId = "F".repeat(26), versionId = "X".repeat(26);
    boolean internal = type.equals("INTERNAL");
    boolean completing = status.equals("SCHEDULED");
    String kind = internal ? "INTERNAL" : "EXTERNAL_RECORDED";
    String source = internal ? null : "甲方邮件";
    Feedback before = new Feedback(feedbackId, INTERVIEW_ID, ACTOR, kind,
        status.equals("COMPLETED") ? "SUBMITTED" : "DRAFT", 1);
    Feedback saved = new Feedback(feedbackId, INTERVIEW_ID, ACTOR, kind, "SUBMITTED", 2);
    FeedbackVersion version = new FeedbackVersion(versionId, feedbackId, 2,
        "PASS", "补充面评事实", source, NOW, "补充说明");
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(interview(type, "SCHEDULED"));
    doReturn(interview(type, status)).when(persistence).lockInterview(INTERVIEW_ID);
    List<Participant> recorded = new ArrayList<>();
    for (int index = 0; index < recordedInterviewerCount; index++) {
      recorded.add(new Participant(INTERVIEW_ID, "recorded-" + index, "INTERVIEWER", "PENDING"));
    }
    when(participants.findParticipants(INTERVIEW_ID)).thenReturn(recorded);
    when(authorization.canAccess(any(CurrentActorSnapshot.class),
        eq("interview.feedback.read-authorized"), any(AuthorizationTarget.class))).thenReturn(true);
    when(ids.newId()).thenReturn(versionId);
    when(feedback.findFeedbackByAuthor(INTERVIEW_ID, ACTOR, kind)).thenReturn(before, saved);
    when(feedback.advanceFeedback(feedbackId, "SUBMITTED", 1, ACTOR, NOW)).thenReturn(1);
    when(feedbackVersions.insertFeedbackVersion(versionId, feedbackId, 2,
        "PASS", "补充面评事实", source, NOW, "补充说明", ACTOR, NOW)).thenReturn(1);
    when(feedbackVersions.findFeedbackVersion(feedbackId, 2)).thenReturn(version);
    when(persistence.complete(INTERVIEW_ID, ACTOR, NOW)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return completionRows;
    });
    LockSummary lock = mock(LockSummary.class);
    when(lock.isCanContact()).thenReturn(true);
    when(engagementLocks.activeLock(CANDIDATE_ID)).thenReturn(lock);
    doAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return null;
    }).when(engagementLocks).recordProgress(any(), any(), any());
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.addAdvice(interceptor);
    InterviewApplicationManager proxy = (InterviewApplicationManager) proxyFactory.getProxy();
    Supplier<FeedbackView> command = () -> proxy.saveFeedbackView(INTERVIEW_ID,
        new InterviewFeedbackDTO("PASS", "补充面评事实", source, NOW, "SUBMITTED", "补充说明"),
        "completion-progress");

    if (completing && completionRows != 1) {
      assertThatThrownBy(command::get).isInstanceOfSatisfying(PlatformException.class,
          error -> assertThat(error.code()).isEqualTo("INTERVIEW_STATE_CHANGED"));
      assertThat(transactions.committed).isZero();
      assertThat(transactions.rolledBack).isOne();
    } else {
      assertThat(command.get().getCurrentVersion()).isSameAs(version);
      assertThat(transactions.committed).isOne();
      assertThat(transactions.rolledBack).isZero();
    }
    verify(feedbackVersions).insertFeedbackVersion(versionId, feedbackId, 2,
        "PASS", "补充面评事实", source, NOW, "补充说明", ACTOR, NOW);
    if (completing && completionRows == 1) {
      verify(engagementLocks).recordProgress(CANDIDATE_ID, "INTERVIEW_COMPLETED", "面试反馈已正式提交");
    } else {
      verifyNoInteractions(engagementLocks);
    }
    if (!completing) {
      verify(persistence, never()).complete(any(), any(), any());
    }
    if (internal) verify(applications).requireLockedFollowUpAccess(APPLICATION_ID);
    verify(authorization, never()).requirePermission("interview.feedback.submit-own");

  }

  @Test
  void emptyOrInvalidNotificationBatchesDoNotAccessPersistence() {
    notificationManager.createNotificationsBatch(List.of(), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR);
    Interview value = interview("INTERNAL", "CANCELLED");
    assertThatThrownBy(() -> notificationManager.createNotificationsBatch(null, InterviewNotificationType.INTERVIEW_CANCEL, ACTOR))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> notificationManager.createNotificationsBatch(Collections.nCopies(101, value), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> notificationManager.createNotificationsBatch(List.of(value, value), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> notificationManager.createNotificationsBatch(List.of(new Interview()), InterviewNotificationType.INTERVIEW_CANCEL, ACTOR))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(persistence, participants, notifications);
  }

  @Test
  void aFullNotificationPageUsesOneTemplateAndParticipantReadWithBoundedWrites() {
    List<Interview> page = IntStream.range(0, 100).mapToObj(index -> {
      Interview value = interview("INTERNAL", "CANCELLED");
      value.setId(String.format("%026d", index));
      return value;
    }).toList();
    when(persistence.findActiveTemplate("INTERVIEW_CANCEL")).thenReturn(new TemplateVersion(
        "V".repeat(26), "T".repeat(26), 1, "取消面试", "{{applicationId}} {{location}}", ACTOR, NOW));
    when(participants.lockParticipantsByInterviewIds(any())).thenReturn(List.of());
    when(notifications.insertNotifications(any())).thenReturn(true);
    AtomicInteger generated = new AtomicInteger();
    when(ids.newId()).thenAnswer(call -> String.format("N%025d", generated.incrementAndGet()));

    notificationManager.createNotificationsBatch(page, InterviewNotificationType.INTERVIEW_CANCEL, ACTOR);

    verify(persistence).findActiveTemplate("INTERVIEW_CANCEL");
    verify(participants).lockParticipantsByInterviewIds(page.stream().map(Interview::getId).collect(Collectors.toSet()));
    verify(participants, never()).findParticipantsByInterviewIds(any());
    verify(participants, never()).findParticipants(any());
    @SuppressWarnings({"unchecked", "rawtypes"})
    ArgumentCaptor<List<NotificationIntent>> batches = ArgumentCaptor.forClass((Class) List.class);
    verify(notifications, Mockito.times(100)).insertNotifications(batches.capture());
    assertThat(batches.getAllValues()).allSatisfy(batch -> {
      assertThat(batch).hasSize(2);
      assertThat(batch).extracting(NotificationIntent::getRecipientReference).containsExactly(ACTOR, CANDIDATE_ID);
      assertThat(batch).extracting(NotificationIntent::getInterviewId).containsOnly(batch.getFirst().getInterviewId());
    });
    assertThat(batches.getAllValues().stream().flatMap(List::stream).map(NotificationIntent::getId).toList())
        .doesNotHaveDuplicates();
    assertThat(batches.getAllValues().stream().map(batch -> batch.getFirst().getInterviewId()).toList())
        .containsExactlyElementsOf(page.stream().map(Interview::getId).toList());
  }

  @ParameterizedTest
  @CsvSource({"CREATE,0", "CREATE,1", "CREATE,2", "UPDATE,0", "UPDATE,1", "UPDATE,2",
      "RESCHEDULE,0", "RESCHEDULE,1", "RESCHEDULE,2", "CANCEL,0", "CANCEL,1", "CANCEL,2"})
  void scheduleFactsCannotCommitWithoutExactlyOneHistoryRow(String action, int historyRows) {
    Interview before = interview("INTERNAL", "SCHEDULED");
    Interview after = interview("INTERNAL", action.equals("CANCEL") ? "CANCELLED" : "SCHEDULED");
    doReturn(before).when(persistence).lockInterview(INTERVIEW_ID);
    when(persistence.findInterview(INTERVIEW_ID)).thenReturn(after);
    when(ids.newId()).thenReturn(INTERVIEW_ID, "H".repeat(26));
    InterviewContext context = new InterviewContext(APPLICATION_ID, CANDIDATE_ID, JD_ID, JD_VERSION_ID,
        WORKFLOW_VERSION_ID, "INTERNAL", STEP_ID, "INTERVIEW_ROUND_1", "PENDING", ACTOR, DEPARTMENT, Set.of());
    when(applications.requireInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(context);
    when(applications.requireLockedInterviewContext(APPLICATION_ID, STEP_ID)).thenReturn(context);
    when(persistence.insertInterview(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(),
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    when(persistence.updateSchedule(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), any()))
        .thenReturn(1);
    when(persistence.cancel(INTERVIEW_ID, 0L, ACTOR, NOW)).thenReturn(1);
    when(participants.insertParticipants(any(), any(), any(), any())).thenReturn(true);
    when(persistence.insertHistory(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return historyRows;
    });
    TestTransactionManager transactions = new TestTransactionManager();
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(service);
    factory.addAdvice(interceptor);
    InterviewApplicationManager proxy = (InterviewApplicationManager) factory.getProxy();
    Supplier<InterviewDetails> command = () -> switch (action) {
      case "CREATE" -> proxy.createDetails(new CreateInterviewDTO(APPLICATION_ID, STEP_ID, NOW.plusSeconds(3600),
          NOW.plusSeconds(7200), "Asia/Shanghai", "ONLINE", null, "https://meeting.example/1", List.of(ACTOR),
          "IN_APP", null, "安排面试"), "history-atomic");
      case "UPDATE" -> proxy.updateDetails(INTERVIEW_ID, new UpdateInterviewDTO(NOW.plusSeconds(10800), NOW.plusSeconds(14400),
          "Asia/Shanghai", "ONLINE", null, "https://meeting.example/2", List.of(ACTOR), "EMAIL", ACTOR, 0L, "调整面试安排"), "history-atomic");
      case "RESCHEDULE" -> proxy.rescheduleDetails(INTERVIEW_ID,
          new RescheduleInterviewDTO(NOW.plusSeconds(10800), NOW.plusSeconds(14400), "Asia/Shanghai", 0L, "调整面试安排"), "history-atomic");
      case "CANCEL" -> proxy.cancel(INTERVIEW_ID, 0L, "取消面试安排", "history-atomic");
      default -> throw new AssertionError(action);
    };
    if (historyRows != 1) {
      assertThatThrownBy(command::get).isInstanceOf(IllegalStateException.class).hasMessage("面试排期历史写入失败");
      assertThat(transactions.rolledBack).isOne();
      assertThat(transactions.committed).isZero();
      verifyNoInteractions(engagementLocks);
    } else {
      assertThat(command.get().getInterview()).isSameAs(after);
      assertThat(transactions.committed).isOne();
      assertThat(transactions.rolledBack).isZero();
    }
    verify(persistence).insertHistory(any(), eq(INTERVIEW_ID), eq(action), any(), any(), any(), eq(ACTOR), eq(NOW));
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
  @CsvSource({"INTERNAL,after-view", "EXTERNAL_CLIENT_AI,after-view",
      "INTERNAL,before-view", "EXTERNAL_CLIENT_AI,before-view",
      "INTERNAL,fresh", "EXTERNAL_CLIENT_AI,fresh",
      "INTERNAL,defensive", "EXTERNAL_CLIENT_AI,defensive",
      "INTERNAL,replay", "EXTERNAL_CLIENT_AI,replay"})
  void publicFeedbackCollisionReadsCommittedWinnerAfterFailedReservation(String type, String mode) throws Exception {
    boolean external = type.equals("EXTERNAL_CLIENT_AI");
    boolean winnerRequired = Set.of("after-view", "before-view", "replay").contains(mode);
    String kind = external ? "EXTERNAL_RECORDED" : "INTERNAL";
    String code = external ? "CLIENT_AI_INTERVIEW" : "INTERVIEW_ROUND_1";
    String key = "public-feedback-key-collision", source = external ? "客户正式邮件纪要" : null;
    String winnerComment = "赢家完整反馈", loserComment = mode.equals("replay") ? winnerComment : "另一份合法请求";
    Instant occurred = NOW.minusSeconds(60), created = NOW.minusSeconds(30L * 86400);
    ObjectMapper json = new ObjectMapper();
    ThreadLocal<String> caller = ThreadLocal.withInitial(() -> "loser");
    List<String> events = new ArrayList<>();
    List<List<Object>> effects = new ArrayList<>(), inserts = new ArrayList<>();
    AtomicReference<Interview> storedInterview = new AtomicReference<>();
    AtomicReference<Feedback> storedFeedback = new AtomicReference<>();
    AtomicReference<FeedbackVersion> storedVersion = new AtomicReference<>();
    AtomicReference<IdempotencyRecord> storedKey = new AtomicReference<>();
    List<ActivityOriginEntity> origins = new ArrayList<>();
    Function<Interview, Interview> copyInterview = value -> {
      if (value == null) return null; Interview copy = new Interview(); BeanUtils.copyProperties(value, copy); return copy;
    };
    Function<Feedback, Feedback> copyFeedback = value -> {
      if (value == null) return null; Feedback copy = new Feedback(); BeanUtils.copyProperties(value, copy); return copy;
    };
    Function<FeedbackVersion, FeedbackVersion> copyVersion = value -> {
      if (value == null) return null; FeedbackVersion copy = new FeedbackVersion(); BeanUtils.copyProperties(value, copy); return copy;
    };
    Function<IdempotencyRecord, IdempotencyRecord> copyKey = value -> {
      if (value == null) return null; IdempotencyRecord copy = new IdempotencyRecord(); BeanUtils.copyProperties(value, copy); return copy;
    };
    class CollisionState implements SmartTransactionObject {
      boolean active, rollbackOnly, snapshotTaken, ownFeedback, ownVersion, ownKey;
      int identity; Interview interview; Feedback feedback; FeedbackVersion version; IdempotencyRecord reservation;
      @Override public boolean isRollbackOnly() { return rollbackOnly; }
      @Override public void flush() {}
      void snapshot() {
        if (snapshotTaken) return;
        interview = copyInterview.apply(storedInterview.get()); feedback = copyFeedback.apply(storedFeedback.get());
        version = copyVersion.apply(storedVersion.get()); reservation = copyKey.apply(storedKey.get()); snapshotTaken = true;
        events.add(caller.get() + ":ordinary-view:" + identity);
      }
    }
    ThreadLocal<CollisionState> states = ThreadLocal.withInitial(CollisionState::new);
    Map<String, Integer> committed = new LinkedHashMap<>(), rolledBack = new LinkedHashMap<>();
    AtomicInteger transactionIds = new AtomicInteger();
    class CollisionTransactions extends AbstractPlatformTransactionManager {
      @Override protected Object doGetTransaction() { return states.get(); }
      @Override protected boolean isExistingTransaction(Object transaction) { return ((CollisionState) transaction).active; }
      @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
        CollisionState state = (CollisionState) transaction; state.active = true; state.identity = transactionIds.incrementAndGet();
        assertThat(definition.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_DEFAULT);
        events.add(caller.get() + ":begin:" + state.identity);
      }
      @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((CollisionState) status.getTransaction()).rollbackOnly = true; }
      @Override protected void doCommit(DefaultTransactionStatus status) { committed.put(caller.get(), states.get().identity); }
      @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack.put(caller.get(), states.get().identity); }
      @Override protected void doCleanupAfterCompletion(Object transaction) { ((CollisionState) transaction).active = false; }
    }
    CollisionTransactions transactions = new CollisionTransactions();
    Function<Object, Object> proxy = target -> {
      ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
      TransactionInterceptor advice = new TransactionInterceptor(); advice.setTransactionManager(transactions);
      advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); factory.addAdvice(advice); return factory.getProxy();
    };
    IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
    ObjectGrantPersistenceService grants = mock(ObjectGrantPersistenceService.class);
    UserSupportDepartmentPersistenceService departments = mock(UserSupportDepartmentPersistenceService.class);
    UserAccountRecord account = new UserAccountRecord(); account.setId(ACTOR); account.setDisplayName("HR");
    account.setWorkEmail("hr@example.com"); account.setHomeDepartmentId(DEPARTMENT); account.setStatus("ACTIVE");
    when(accounts.findById(ACTOR)).thenReturn(account); when(accounts.findRoleCodes(ACTOR)).thenReturn(List.of("HR_DIRECTOR"));
    when(departments.findSupportDepartmentIds(ACTOR)).thenReturn(List.of());
    CurrentActorProvider actorProvider = () -> Optional.of(new CurrentActor(ACTOR,
        Set.of("recruitment.manage", "interview.external-feedback.record", "interview.feedback.read-authorized"), false));
    IdentityAuthorizationAdapter realAuthorization = new IdentityAuthorizationAdapter();
    ReflectionTestUtils.setField(realAuthorization, "actorProvider", actorProvider);
    ReflectionTestUtils.setField(realAuthorization, "accountService", accounts);
    ReflectionTestUtils.setField(realAuthorization, "objectGrantPersistenceService", grants);
    ReflectionTestUtils.setField(realAuthorization, "userSupportDepartmentPersistenceService", departments);
    assertThat(realAuthorization.currentActor().hasCompanyWideScope()).isTrue();
    assertThat(realAuthorization.currentActor().isSuperAdmin()).isFalse();
    Application app = new Application(APPLICATION_ID, CANDIDATE_ID, "H".repeat(26), JD_ID, JD_VERSION_ID,
        "R".repeat(26), WORKFLOW_VERSION_ID, "M".repeat(26), external ? "EXTERNAL" : "INTERNAL",
        "IN_PROCESS", null, ACTOR, DEPARTMENT, 1L, null, 0L);
    app.setCreateId(ACTOR); app.setUpdateId(ACTOR); app.setCreateTime(created); app.setUpdateTime(created);
    WorkflowStep step = new WorkflowStep(STEP_ID, APPLICATION_ID, WORKFLOW_VERSION_ID, "E".repeat(26), code,
        1, false, "PHONE", "IN_PROGRESS", ACTOR, NOW, NOW.minusSeconds(120), null, null,
        external ? "U".repeat(26) : null, 0L); step.setRoundNo(1);
    step.setCreateId(ACTOR); step.setUpdateId(ACTOR); step.setCreateTime(created); step.setUpdateTime(created);
    WorkflowTemplateEntity template = new WorkflowTemplateEntity(); template.setId(WORKFLOW_VERSION_ID);
    template.setJdVersionId(JD_VERSION_ID); template.setVersionNo(1); template.setRecruitmentType(app.getRecruitmentType());
    template.setStatus("ACTIVE"); template.setActiveSlot(1); template.setVersion(0L);
    template.setActivatedByUserId(ACTOR); template.setActivatedAt(created);
    template.setCreateId(ACTOR); template.setUpdateId(ACTOR); template.setCreateTime(created); template.setUpdateTime(created);
    WorkflowStepEntity frozenDefinition = new WorkflowStepEntity(); frozenDefinition.setId(step.getStepDefinitionId());
    frozenDefinition.setTemplateVersionId(WORKFLOW_VERSION_ID); frozenDefinition.setStepCode(code); frozenDefinition.setSequenceNo(1);
    frozenDefinition.setCandidateOptional(false); frozenDefinition.setDefaultFollowUpChannel("PHONE");
    frozenDefinition.setCreateId(ACTOR); frozenDefinition.setUpdateId(ACTOR); frozenDefinition.setCreateTime(created); frozenDefinition.setUpdateTime(created);
    ClientSubmission submission = null;
    if (external) {
      submission = new ClientSubmission(); submission.setId(step.getClientSubmissionId()); submission.setApplicationId(APPLICATION_ID);
      submission.setCandidateId(CANDIDATE_ID); submission.setDemandId("H".repeat(26)); submission.setClientId("B".repeat(26));
      submission.setProjectId("G".repeat(26)); submission.setPositionId("T".repeat(26)); submission.setJdId(JD_ID);
      submission.setJdVersionId(JD_VERSION_ID); submission.setResumeVersionId("R".repeat(26));
      submission.setDeliveryStatus("SENT"); submission.setProcessStatus("INTERVIEWING"); submission.setSentAt(created);
      submission.setOwnerUserId(ACTOR); submission.setOwnerDepartmentId(DEPARTMENT); submission.setVersion(0L);
      submission.setCreateId(ACTOR); submission.setUpdateId(ACTOR); submission.setCreateTime(created); submission.setUpdateTime(created);
    }
    Interview initial = new Interview(); initial.setId(INTERVIEW_ID); initial.setApplicationId(APPLICATION_ID);
    initial.setCandidateId(CANDIDATE_ID); initial.setJdId(JD_ID); initial.setJdVersionId(JD_VERSION_ID);
    initial.setWorkflowTemplateVersionId(WORKFLOW_VERSION_ID); initial.setWorkflowStepId(STEP_ID); initial.setStepCode(code);
    initial.setInterviewType(type); initial.setRoundNo(1); initial.setClientSubmissionId(step.getClientSubmissionId());
    initial.setStatus("SCHEDULED"); initial.setVersion(0L); initial.setScheduledStartAt(NOW.minusSeconds(120));
    initial.setScheduledEndAt(occurred); initial.setTimezoneId("Asia/Shanghai"); initial.setMeetingMode("ONLINE");
    initial.setMeetingUrl("https://meeting.example/key-collision"); initial.setCandidateNotificationChannel("NONE");
    initial.setOwnerUserId(ACTOR); initial.setOwnerDepartmentId(DEPARTMENT); initial.setNextResponsibleUserId(ACTOR);
    initial.setCreateId(ACTOR); initial.setUpdateId(ACTOR); initial.setCreateTime(created); initial.setUpdateTime(created);
    storedInterview.set(initial);
    EngagementLock expiredLease = new EngagementLock("L".repeat(26), CANDIDATE_ID, ACTOR, DEPARTMENT, "HR", "ACTIVE",
        created, occurred, "INTERVIEW_SCHEDULED", 1L);
    expiredLease.setCreateId(ACTOR); expiredLease.setUpdateId(ACTOR); expiredLease.setCreateTime(created); expiredLease.setUpdateTime(created);
    LockHistory priorRenewal = new LockHistory("K".repeat(26), expiredLease.getId(), CANDIDATE_ID, "RENEW", ACTOR, ACTOR,
        "INTERVIEW_SCHEDULED", "已安排面试", ACTOR, created);
    priorRenewal.setCreateId(ACTOR); priorRenewal.setUpdateId(ACTOR); priorRenewal.setCreateTime(created); priorRenewal.setUpdateTime(created);
    List<LockHistory> leaseHistory = new ArrayList<>(List.of(priorRenewal));
    List<Object> frozenContext = Arrays.asList(app, template, step, frozenDefinition, submission, expiredLease, leaseHistory);
    byte[] frozenFacts = json.writeValueAsBytes(frozenContext);
    assertThat(step.getTemplateVersionId()).isEqualTo(template.getId()); assertThat(app.getWorkflowTemplateVersionId()).isEqualTo(template.getId());
    assertThat(step.getStepDefinitionId()).isEqualTo(frozenDefinition.getId()); assertThat(step.getStepCode()).isEqualTo(frozenDefinition.getStepCode());
    ApplicationPersistenceService applicationRows = mock(ApplicationPersistenceService.class);
    ApplicationCollaboratorPersistenceService members = mock(ApplicationCollaboratorPersistenceService.class);
    ParticipantPersistenceService participantRows = mock(ParticipantPersistenceService.class);
    CandidateLibraryQueryService shared = mock(CandidateLibraryQueryService.class);
    when(shared.eligibleCandidateIds(any())).thenReturn(Set.of());
    when(participantRows.findParticipants(INTERVIEW_ID)).thenReturn(List.of(new Participant(INTERVIEW_ID, ACTOR, "INTERVIEWER", "PENDING")));
    when(members.findCollaboratorsForUpdate(APPLICATION_ID)).thenAnswer(call -> {
      events.add(caller.get() + ":current-members"); return List.of(ACTOR);
    });
    InterviewPersistenceService interviewRows = mock(InterviewPersistenceService.class);
    InterviewFeedbackPersistenceService feedbackRows = mock(InterviewFeedbackPersistenceService.class);
    FeedbackVersionPersistenceService versionRows = mock(FeedbackVersionPersistenceService.class);
    PlatformIdGenerator generatedIds = mock(PlatformIdGenerator.class);
    Map<String, AtomicInteger> idSequences = Map.of("winner", new AtomicInteger(), "loser", new AtomicInteger());
    when(generatedIds.newId()).thenAnswer(call -> {
      String[] values = caller.get().equals("winner") ? new String[]{"F", "Z", "X"} : new String[]{"N", "O", "Q"};
      return values[idSequences.get(caller.get()).getAndIncrement()].repeat(26);
    });
    InterviewIdempotencyPersistenceService keyRows = mock(InterviewIdempotencyPersistenceService.class, call -> {
      String method = call.getMethod().getName();
      if (!Set.of("findIdempotency", "findIdempotencyForUpdate").contains(method)) return Answers.RETURNS_DEFAULTS.answer(call);
      assertThat(call.getArgument(0, String.class)).isEqualTo(ACTOR); assertThat(call.getArgument(1, String.class)).isEqualTo(key);
      CollisionState state = states.get(); assertThat(state.active).isTrue();
      boolean current = method.equals("findIdempotencyForUpdate"); events.add(caller.get() + ":key-" + (current ? "current" : "ordinary"));
      if (current) {
        assertThat(events).contains(caller.get() + ":insert-0", caller.get() + ":interview-lock"); return copyKey.apply(storedKey.get());
      }
      state.snapshot(); return copyKey.apply(state.ownKey ? storedKey.get() : state.reservation);
    });
    when(interviewRows.findInterview(INTERVIEW_ID)).thenAnswer(call -> { states.get().snapshot(); return copyInterview.apply(states.get().interview); });
    when(interviewRows.lockInterview(INTERVIEW_ID)).thenAnswer(call -> {
      assertThat(events).contains(caller.get() + ":app-lock"); events.add(caller.get() + ":interview-lock"); return copyInterview.apply(storedInterview.get());
    });
    when(feedbackRows.findFeedbackByAuthor(INTERVIEW_ID, ACTOR, kind)).thenAnswer(call -> {
      CollisionState state = states.get(); state.snapshot(); events.add(caller.get() + ":feedback-read");
      return copyFeedback.apply(state.ownFeedback ? storedFeedback.get() : state.feedback);
    });
    when(interviewRows.insertIdempotency(eq(ACTOR), eq(key), any(), any(), any(), eq(NOW))).thenAnswer(call -> {
      assertThat(events).contains(caller.get() + ":interview-lock", caller.get() + ":key-ordinary");
      inserts.add(Arrays.asList(caller.get(), call.getArgument(2), call.getArgument(3), call.getArgument(4)));
      if (storedKey.get() != null || mode.equals("defensive")) { events.add(caller.get() + ":insert-0"); return 0; }
      IdempotencyRecord row = new IdempotencyRecord(ACTOR, key, call.getArgument(2), call.getArgument(3), call.getArgument(4));
      row.setCreateTime(NOW); storedKey.set(row); states.get().ownKey = true; events.add(caller.get() + ":insert-1");
      effects.add(List.of(caller.get(), "reservation")); return 1;
    });
    when(feedbackRows.insertFeedback(any(), eq(INTERVIEW_ID), eq(ACTOR), eq(kind), eq("SUBMITTED"), eq(NOW))).thenAnswer(call -> {
      assertThat(storedFeedback.get()).isNull(); Feedback row = new Feedback(call.getArgument(0), INTERVIEW_ID, ACTOR, kind, "SUBMITTED", 1);
      row.setCreateId(ACTOR); row.setUpdateId(ACTOR); row.setCreateTime(NOW); row.setUpdateTime(NOW);
      storedFeedback.set(row); states.get().ownFeedback = true; effects.add(List.of(caller.get(), "feedback")); return 1;
    });
    when(versionRows.insertFeedbackVersion(any(), any(), eq(1), eq("PASS"), any(), eq(source), eq(occurred), eq("正式提交"), eq(ACTOR), eq(NOW))).thenAnswer(call -> {
      assertThat(storedVersion.get()).isNull(); FeedbackVersion row = new FeedbackVersion(call.getArgument(0), call.getArgument(1), 1,
          "PASS", call.getArgument(4), source, occurred, "正式提交"); row.setCreateId(ACTOR); row.setCreateTime(NOW);
      storedVersion.set(row); states.get().ownVersion = true; effects.add(List.of(caller.get(), "version")); return 1;
    });
    when(interviewRows.complete(INTERVIEW_ID, ACTOR, NOW)).thenAnswer(call -> {
      Interview prior = storedInterview.get(); assertThat(prior.getStatus()).isEqualTo("SCHEDULED"); assertThat(prior.getVersion()).isZero();
      Interview changed = copyInterview.apply(prior); changed.setStatus("COMPLETED"); changed.setVersion(1L);
      changed.setUpdateId(ACTOR); changed.setUpdateTime(NOW); storedInterview.set(changed); effects.add(List.of(caller.get(), "complete")); return 1;
    });
    when(versionRows.findFeedbackVersion(any(), eq(1))).thenAnswer(call -> {
      CollisionState state = states.get(); assertThat(call.getArgument(0, String.class)).isEqualTo(storedFeedback.get().getId());
      return copyVersion.apply(state.ownVersion ? storedVersion.get() : state.version);
    });
    ActivityOriginPersistenceService originRows = mock(ActivityOriginPersistenceService.class);
    when(originRows.save(any(ActivityOriginEntity.class))).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); origins.add(call.getArgument(0));
      effects.add(List.of(caller.get(), "origin")); return true;
    });
    EngagementLockPersistenceService leaseRows = mock(EngagementLockPersistenceService.class);
    LockHistoryPersistenceService leaseAudit = mock(LockHistoryPersistenceService.class);
    when(leaseRows.findByCandidate(CANDIDATE_ID)).thenAnswer(call -> {
      events.add(caller.get() + ":expired-lease-read"); return expiredLease;
    });
    EngagementLockApplicationManager realLease = new EngagementLockApplicationManager();
    ReflectionTestUtils.setField(realLease, "candidateCommands", blacklist);
    ReflectionTestUtils.setField(realLease, "persistence", leaseRows); ReflectionTestUtils.setField(realLease, "lockHistoryPersistenceService", leaseAudit);
    ReflectionTestUtils.setField(realLease, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    RecruitmentApplicationManager realApplications = new RecruitmentApplicationManager();
    ReflectionTestUtils.setField(realApplications, "persistence", applicationRows);
    ReflectionTestUtils.setField(realApplications, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(applicationRows.findCandidateId(any())).thenReturn("C".repeat(26));
    ReflectionTestUtils.setField(realApplications, "applicationCollaboratorPersistenceService", members);
    ReflectionTestUtils.setField(realApplications, "authorization", realAuthorization);
    ActivityOriginManager realOrigins = new ActivityOriginManager();
    ReflectionTestUtils.setField(realOrigins, "origins", originRows); ReflectionTestUtils.setField(realOrigins, "ids", generatedIds);
    ReflectionTestUtils.setField(realOrigins, "authorization", realAuthorization); ReflectionTestUtils.setField(realOrigins, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    InterviewApplicationManager manager = new InterviewApplicationManager(); InterviewAssembler assembler = new InterviewAssembler();
    ReflectionTestUtils.setField(manager, "assembler", assembler); ReflectionTestUtils.setField(manager, "persistence", interviewRows);
    ReflectionTestUtils.setField(manager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(interviewRows.findCandidateId(any())).thenReturn("C".repeat(26));
    ReflectionTestUtils.setField(manager, "applicationPersistenceService", applicationRows);
    ReflectionTestUtils.setField(manager, "interviewIdempotencyPersistenceService", keyRows);
    ReflectionTestUtils.setField(manager, "interviewFeedbackPersistenceService", feedbackRows);
    ReflectionTestUtils.setField(manager, "feedbackVersionPersistenceService", versionRows);
    ReflectionTestUtils.setField(manager, "participantPersistenceService", participantRows); ReflectionTestUtils.setField(manager, "candidateLibrary", shared);
    ReflectionTestUtils.setField(manager, "authorization", realAuthorization); ReflectionTestUtils.setField(manager, "ids", generatedIds);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(NOW, ZoneOffset.UTC)); ReflectionTestUtils.setField(manager, "engagementLocks", realLease);
    ReflectionTestUtils.setField(manager, "applications", proxy.apply(realApplications)); ReflectionTestUtils.setField(manager, "activityOrigins", proxy.apply(realOrigins));
    InterviewApplicationManager publicEntry = (InterviewApplicationManager) proxy.apply(manager);
    Function<String, InterviewFeedbackResponse> submit = comment -> {
      var oldAttributes = RequestContextHolder.getRequestAttributes(); MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Recruitment-Action", "INTERVIEW"); request.addHeader("X-Recruitment-Operation",
          caller.get().equals("winner") ? "847cd090-47c9-4891-a4a5-77a784b77c1d" : "558c23d8-297a-4d1c-bb88-0d7fbfe2d125");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      try {
        InterviewFeedbackRequest command = new InterviewFeedbackRequest("PASS", comment, source, occurred, "SUBMITTED", "正式提交");
        try (ValidatorFactory validation = Validation.byDefaultProvider().configure()
            .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) { assertThat(validation.getValidator().validate(command)).isEmpty(); }
        return publicEntry.saveFeedback(INTERVIEW_ID, assembler.toDTO(command), key);
      } finally { RequestContextHolder.setRequestAttributes(oldAttributes); }
    };
    AtomicReference<InterviewFeedbackResponse> winnerResponse = new AtomicReference<>();
    AtomicReference<byte[]> committedWinnerRows = new AtomicReference<>();
    Runnable runWinner = () -> {
      try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
        winnerResponse.set(executor.submit(() -> {
          caller.set("winner"); try { return submit.apply(winnerComment); } finally { caller.remove(); }
        }).get(10, TimeUnit.SECONDS));
      } catch (Exception failure) { throw new AssertionError("independent public winner failed", failure); }
      assertThat(committed).containsKey("winner"); assertThat(rolledBack).doesNotContainKey("winner");
      InterviewFeedbackResponse expectedWinner = new InterviewFeedbackResponse(
          new InterviewFeedbackSummaryResponse("F".repeat(26), INTERVIEW_ID, ACTOR, kind, "SUBMITTED", 1, NOW, NOW),
          new InterviewFeedbackVersionResponse("Z".repeat(26), "F".repeat(26), 1, "PASS", winnerComment, source, occurred, "正式提交", ACTOR, NOW));
      try { assertThat(json.writeValueAsBytes(winnerResponse.get())).isEqualTo(json.writeValueAsBytes(expectedWinner)); }
      catch (Exception failure) { throw new AssertionError("winner full public response", failure); }
      assertThat(storedInterview.get().getStatus()).isEqualTo("COMPLETED"); assertThat(storedInterview.get().getVersion()).isEqualTo(1L);
      try {
        committedWinnerRows.set(json.writeValueAsBytes(Arrays.asList(storedInterview.get(), storedFeedback.get(), storedVersion.get(), storedKey.get(), origins)));
      } catch (Exception failure) { throw new AssertionError("winner raw facts serialization", failure); }
    };
    when(applicationRows.lockApplication(APPLICATION_ID)).thenAnswer(call -> {
      if (caller.get().equals("loser") && mode.equals("after-view") && winnerResponse.get() == null) {
        assertThat(states.get().snapshotTaken).isTrue(); assertThat(states.get().interview.getStatus()).isEqualTo("SCHEDULED");
        runWinner.run(); assertThat(committed.get("winner")).isNotEqualTo(states.get().identity);
      }
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); events.add(caller.get() + ":app-lock");
      Application row = new Application(); BeanUtils.copyProperties(app, row); return row;
    });
    if (winnerRequired && !mode.equals("after-view")) runWinner.run();
    PlatformException error = null; InterviewFeedbackResponse response = null;
    try { response = submit.apply(loserComment); } catch (PlatformException failure) { error = failure; }
    assertThat(states.get().active).isFalse(); assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(transactionIds.get()).isEqualTo(winnerRequired ? 2 : 1);
    if (winnerRequired) {
      assertThat(committed.get("winner")).isNotEqualTo(states.get().identity);
      assertThat(effects).hasSize(5).allSatisfy(row -> assertThat(row.get(0)).isEqualTo("winner"));
      assertThat(json.writeValueAsBytes(Arrays.asList(storedInterview.get(), storedFeedback.get(), storedVersion.get(), storedKey.get(), origins)))
          .isEqualTo(committedWinnerRows.get());
    }
    assertThat(initial.getClientSubmissionId()).isEqualTo(step.getClientSubmissionId());
    if (external) { assertThat(submission.getId()).isEqualTo(initial.getClientSubmissionId()); assertThat(submission.getApplicationId()).isEqualTo(app.getId()); }
    assertThat(json.writeValueAsBytes(frozenContext)).isEqualTo(frozenFacts);
    verifyNoInteractions(leaseAudit);
    boolean healthy = mode.equals("fresh") || mode.equals("replay");
    if (healthy) {
      assertThat(error).isNull(); assertThat(response).isNotNull(); assertThat(committed).containsKey("loser");
      String feedbackId = mode.equals("replay") ? "F".repeat(26) : "N".repeat(26);
      String versionId = mode.equals("replay") ? "Z".repeat(26) : "O".repeat(26);
      InterviewFeedbackResponse expected = new InterviewFeedbackResponse(
          new InterviewFeedbackSummaryResponse(feedbackId, INTERVIEW_ID, ACTOR, kind, "SUBMITTED", 1, NOW, NOW),
          new InterviewFeedbackVersionResponse(versionId, feedbackId, 1, "PASS", loserComment, source, occurred, "正式提交", ACTOR, NOW));
      assertThat(json.writeValueAsBytes(response)).isEqualTo(json.writeValueAsBytes(expected));
      if (mode.equals("replay")) {
        assertThat(json.writeValueAsBytes(response)).isEqualTo(json.writeValueAsBytes(winnerResponse.get()));
        assertThat(effects).allSatisfy(row -> assertThat(row.get(0)).isEqualTo("winner"));
        assertThat(inserts).hasSize(1); assertThat(events).doesNotContain("loser:key-current", "loser:insert-0", "loser:insert-1");
      } else {
        assertThat(effects).hasSize(5).allSatisfy(row -> assertThat(row.get(0)).isEqualTo("loser"));
        assertThat(inserts).hasSize(1); assertThat(events).contains("loser:insert-1", "loser:expired-lease-read").doesNotContain("loser:key-current");
      }
    } else {
      assertThat(error).isNotNull(); assertThat(response).isNull(); assertThat(rolledBack).containsKey("loser");
      assertThat(effects).noneSatisfy(row -> assertThat(row.get(0)).isEqualTo("loser"));
      if (mode.equals("defensive")) {
        assertThat(storedKey.get()).isNull(); assertThat(storedFeedback.get()).isNull(); assertThat(storedVersion.get()).isNull();
        assertThat(storedInterview.get().getStatus()).isEqualTo("SCHEDULED"); assertThat(storedInterview.get().getVersion()).isZero();
        assertThat(origins).isEmpty(); assertThat(inserts).hasSize(1); assertThat(events).contains("loser:insert-0");
      } else {
        assertThat(storedKey.get().getTargetId()).isEqualTo("F".repeat(26));
        assertThat(storedKey.get().getRequestFingerprint()).isEqualTo(Sha256Utils.fingerprintObjects(31,
            INTERVIEW_ID, kind, "PASS", winnerComment, source, occurred, "SUBMITTED", "正式提交"));
        assertThat(storedFeedback.get().getCurrentVersionNo()).isEqualTo(1); assertThat(storedVersion.get().getShortComment()).isEqualTo(winnerComment);
        assertThat(storedInterview.get().getStatus()).isEqualTo("COMPLETED"); assertThat(storedInterview.get().getVersion()).isEqualTo(1L);
        if (mode.equals("after-view")) {
          assertThat(states.get().reservation).isNull(); assertThat(states.get().feedback).isNull(); assertThat(states.get().version).isNull();
          assertThat(inserts).hasSize(2); assertThat(events).contains("loser:insert-0");
          assertThat(List.of("IDEMPOTENCY_RESERVATION_FAILED", "IDEMPOTENCY_KEY_REUSED")).contains(error.code());
        } else { assertThat(inserts).hasSize(1); assertThat(events).doesNotContain("loser:insert-0", "loser:key-current"); }
      }
    }
    if (!mode.equals("defensive")) {
      assertThat(origins).hasSize(1); ActivityOriginEntity origin = origins.getFirst();
      assertThat(origin.getApplicationId()).isEqualTo(APPLICATION_ID); assertThat(origin.getSourceType()).isEqualTo("INTERVIEW_FEEDBACK");
      assertThat(origin.getSourceId()).isEqualTo(storedVersion.get().getId()); assertThat(origin.getAction()).isEqualTo("INTERVIEW");
      assertThat(origin.getOperationId()).isEqualTo(winnerRequired ? "847cd090-47c9-4891-a4a5-77a784b77c1d" : "558c23d8-297a-4d1c-bb88-0d7fbfe2d125");
      assertThat(origin.getCreateId()).isEqualTo(ACTOR); assertThat(origin.getUpdateId()).isEqualTo(ACTOR);
      assertThat(origin.getCreateName()).isEqualTo("HR"); assertThat(origin.getUpdateName()).isEqualTo("HR");
      assertThat(origin.getCreateTime()).isEqualTo(NOW); assertThat(origin.getUpdateTime()).isEqualTo(NOW);
      assertThat(origin.getId()).isEqualTo(winnerRequired ? "X".repeat(26) : "Q".repeat(26));
      assertThat(origin.getVersion()).isZero(); assertThat(origin.getExt()).isNull(); assertThat(origin.getRemark()).isNull();
      assertThat(storedFeedback.get().getId()).isEqualTo(winnerRequired ? "F".repeat(26) : "N".repeat(26));
      assertThat(storedFeedback.get().getInterviewId()).isEqualTo(INTERVIEW_ID); assertThat(storedFeedback.get().getAuthorUserId()).isEqualTo(ACTOR);
      assertThat(storedFeedback.get().getFeedbackKind()).isEqualTo(kind); assertThat(storedFeedback.get().getStatus()).isEqualTo("SUBMITTED");
      assertThat(storedFeedback.get().getCreateId()).isEqualTo(ACTOR); assertThat(storedFeedback.get().getUpdateId()).isEqualTo(ACTOR);
      assertThat(storedFeedback.get().getCreateTime()).isEqualTo(NOW); assertThat(storedFeedback.get().getUpdateTime()).isEqualTo(NOW);
      assertThat(storedVersion.get().getId()).isEqualTo(winnerRequired ? "Z".repeat(26) : "O".repeat(26));
      assertThat(storedVersion.get().getFeedbackId()).isEqualTo(storedFeedback.get().getId()); assertThat(storedVersion.get().getVersionNo()).isEqualTo(1);
      assertThat(storedVersion.get().getVerdict()).isEqualTo("PASS"); assertThat(storedVersion.get().getExternalFeedbackSource()).isEqualTo(source);
      assertThat(storedVersion.get().getFeedbackOccurredAt()).isEqualTo(occurred); assertThat(storedVersion.get().getChangeReason()).isEqualTo("正式提交");
      assertThat(storedVersion.get().getCreateId()).isEqualTo(ACTOR); assertThat(storedVersion.get().getCreateTime()).isEqualTo(NOW);
      assertThat(storedKey.get().getActorUserId()).isEqualTo(ACTOR); assertThat(storedKey.get().getIdempotencyKey()).isEqualTo(key);
      assertThat(storedKey.get().getActionCode()).isEqualTo("INTERVIEW_FEEDBACK_SAVE"); assertThat(storedKey.get().getTargetId()).isEqualTo(storedFeedback.get().getId());
      assertThat(storedKey.get().getCreateTime()).isEqualTo(NOW);
    }
    Map<String, Object> diagnostic = new LinkedHashMap<>(); diagnostic.put("type", type); diagnostic.put("mode", mode);
    diagnostic.put("actualCode", error == null ? null : error.code()); diagnostic.put("events", events);
    diagnostic.put("inserts", inserts); diagnostic.put("effects", effects); diagnostic.put("committed", committed); diagnostic.put("rolledBack", rolledBack);
    diagnostic.put("winnerResponse", winnerResponse.get()); diagnostic.put("interview", storedInterview.get());
    diagnostic.put("feedback", storedFeedback.get()); diagnostic.put("version", storedVersion.get()); diagnostic.put("key", storedKey.get());
    diagnostic.put("origins", origins); diagnostic.put("loserTransactionIdentity", states.get().identity);
    diagnostic.put("currentKeyReadCount", events.stream().filter(event -> event.equals("loser:key-current")).count());
    System.out.println("INTERVIEW_KEY_COLLISION_PROBE " + json.writeValueAsString(diagnostic));
    assertThat(events.indexOf("loser:ordinary-view:" + states.get().identity)).isLessThan(events.indexOf("loser:app-lock"));
    assertThat(events.indexOf("loser:app-lock")).isLessThan(events.indexOf("loser:interview-lock"));
    assertThat(events.stream().filter(event -> event.equals("loser:key-current")).count()).isLessThanOrEqualTo(1);
    if (events.contains("loser:key-current")) {
      assertThat(events.indexOf("loser:interview-lock")).isLessThan(events.indexOf("loser:insert-0"));
      assertThat(events.indexOf("loser:insert-0")).isLessThan(events.indexOf("loser:key-current"));
    }
    if (!healthy) assertThat(error.code()).isEqualTo(mode.equals("defensive") ? "IDEMPOTENCY_RESERVATION_FAILED" : "IDEMPOTENCY_KEY_REUSED");
  }

}
