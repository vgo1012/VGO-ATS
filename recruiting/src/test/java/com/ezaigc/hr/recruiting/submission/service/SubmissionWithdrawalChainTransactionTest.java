package com.ezaigc.hr.recruiting.submission.service;

import com.ezaigc.hr.recruiting.recruitment.application.manager.FollowUpPlanLifecycleManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewNotificationApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;
import com.ezaigc.hr.recruiting.submission.application.manager.SubmissionApplicationManager;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.domain.entity.IdempotencyRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 组合实际推荐、招聘、通知服务代理；持久化替身仅用于观察共享事务提交和回滚，不证明数据库方言。 */
class SubmissionWithdrawalChainTransactionTest {
  private static final String ACTOR = "A".repeat(26);
  private static final String APPLICATION = "P".repeat(26);
  private static final String SUBMISSION = "S".repeat(26);
  private static final String STEP = "W".repeat(26);
  private static final String INTERVIEW = "I".repeat(26);
  private static final String COMPLETED = "H".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-07T10:00:00Z");

  private final Map<String, Object> rows = new HashMap<>();
  private final SharedTransactions transactions = new SharedTransactions(rows);
  private final SubmissionPersistenceService submissions = mock(SubmissionPersistenceService.class);
  private final SubmissionIdempotencyPersistenceService idempotency = mock(SubmissionIdempotencyPersistenceService.class);
  private final ApplicationPersistenceService applications = mock(ApplicationPersistenceService.class);
  private final RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
  private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  private final NotificationIntentPersistenceService notifications = mock(NotificationIntentPersistenceService.class);
  private final StageHistoryPersistenceService history = mock(StageHistoryPersistenceService.class);
  private String failingWrite;

  @ParameterizedTest
  @ValueSource(strings = {"TERMINATED", "ONBOARDED"})
  void withdrawsOutstandingSubmissionWithoutReopeningOrRewritingTerminalApplication(String stage) {
    failingWrite = "NONE";
    rows.put("submission", "SENT");
    rows.put("step", "PASSED");
    rows.put("interview", "COMPLETED");
    rows.put("stage", stage);
    rows.put("terminalAt", NOW);
    rows.put("binding", SUBMISSION);
    SubmissionApplicationManager service = wireActualManagers();
    var result = service.withdraw(SUBMISSION, "客户取消安排", 0, "withdraw-chain");
    assertThat(result.getSubmission().getDeliveryStatus()).isEqualTo("WITHDRAWN");
    assertThat(rows).containsEntry("stage", stage).containsEntry("terminalAt", NOW)
        .containsEntry("step", "PASSED").containsEntry("interview", "COMPLETED");
    verify(steps, never()).lockSteps(APPLICATION);
    verify(applications, never()).updateStage(any(), any(), anyLong(), any(), any());
    assertThat(transactions.commits).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"NONE,false", "INTERVIEW_HISTORY,false", "NOTIFICATION,false", "WORKFLOW,false", "STAGE_HISTORY,false",
      "NONE,true", "INTERVIEW_HISTORY,true", "NOTIFICATION,true", "WORKFLOW,true", "STAGE_HISTORY,true"})
  void withdrawalRollsBackTheWholeChainThenRetriesAndReplays(String failurePoint, boolean legacyBinding) {
    failingWrite = failurePoint;
    rows.put("submission", "SENT");
    rows.put("step", "IN_PROGRESS");
    rows.put("interview", "SCHEDULED");
    rows.put("stage", "IN_PROCESS");
    if (!legacyBinding) rows.put("binding", SUBMISSION);
    Map<String, Object> original = new HashMap<>(rows);
    SubmissionApplicationManager service = wireActualManagers();

    if (!failurePoint.equals("NONE")) {
      assertThatThrownBy(() -> service.withdraw(SUBMISSION, "客户取消安排", 0, "withdraw-chain"))
          .satisfies(error -> {
            if (failurePoint.equals("WORKFLOW")) {
              assertThat(error).isInstanceOfSatisfying(PlatformException.class,
                  failure -> assertThat(failure.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
            } else {
              String message = switch (failurePoint) {
                case "INTERVIEW_HISTORY" -> "推荐撤回时面试取消历史写入失败";
                case "NOTIFICATION" -> "面试通知意图批量写入失败";
                case "STAGE_HISTORY" -> "Application 阶段历史写入失败";
                default -> throw new AssertionError(failurePoint);
              };
              assertThat(error).isInstanceOf(IllegalStateException.class).hasMessage(message);
            }
          });
      assertThat(rows).isEqualTo(original);
      assertThat(transactions.begins).isEqualTo(1);
      assertThat(transactions.rollbacks).isEqualTo(1);
      assertThat(transactions.commits).isZero();
      failingWrite = "NONE";
    }

    var result = service.withdraw(SUBMISSION, "客户取消安排", 0, "withdraw-chain");

    assertThat(result.getSubmission().getDeliveryStatus()).isEqualTo("WITHDRAWN");
    assertThat(rows).containsEntry("submission", "WITHDRAWN")
        .containsEntry("step", "WITHDRAWN").containsEntry("interview", "CANCELLED")
        .containsEntry("stage", "PATH_SELECTION").containsEntry("binding", SUBMISSION)
        .containsKeys("reservation", "interviewHistory", "stageHistory");
    @SuppressWarnings("unchecked")
    List<NotificationIntent> intents = (List<NotificationIntent>) rows.get("notifications");
    assertThat(intents).hasSize(2).allSatisfy(intent -> {
      assertThat(intent.getInterviewId()).isEqualTo(INTERVIEW);
      assertThat(intent.getNotificationType()).isEqualTo("INTERVIEW_CANCEL");
    });
    verify(interviews, never()).cancel(eq(COMPLETED), anyLong(), any(), any());
    verify(steps, never()).withdrawBoundClientStep(eq(COMPLETED), any(), any(), any());
    assertThat(transactions.commits).isEqualTo(1);
    assertThat(transactions.begins).isEqualTo(failurePoint.equals("NONE") ? 1 : 2);

    Map<String, Object> committed = new HashMap<>(rows);
    service.withdraw(SUBMISSION, "客户取消安排", 0, "withdraw-chain");
    assertThat(rows).isEqualTo(committed);
    assertThat(transactions.commits).isEqualTo(2);
  }

  private SubmissionApplicationManager wireActualManagers() {
    AuthorizationService authorization = mock(AuthorizationService.class);
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId(ACTOR);
    when(authorization.currentActor()).thenReturn(actor);
    when(authorization.canAccess(any(), eq("submission.read"), any())).thenReturn(true);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    AtomicInteger generated = new AtomicInteger();
    PlatformIdGenerator ids = () -> String.format("%026d", generated.incrementAndGet());
    when(submissions.lock(SUBMISSION)).thenAnswer(call -> submission());
    when(submissions.find(SUBMISSION)).thenAnswer(call -> submission());
    when(idempotency.idempotency(ACTOR, "withdraw-chain"))
        .thenAnswer(call -> rows.get("reservation"));
    when(submissions.insertIdempotency(any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> write("reservation", new IdempotencyRecord(call.getArgument(0), call.getArgument(1),
            call.getArgument(2), call.getArgument(3), call.getArgument(4))));
    when(submissions.withdraw(SUBMISSION, "客户取消安排", ACTOR, 0, NOW))
        .thenAnswer(call -> write("submission", "WITHDRAWN"));
    when(applications.lockApplication(APPLICATION)).thenAnswer(call -> application());
    when(applications.findApplication(APPLICATION)).thenAnswer(call -> application());
    when(steps.lockSteps(APPLICATION)).thenAnswer(call -> List.of(completedStep(), activeStep()));
    when(steps.bindClientSubmission(eq(STEP), eq(SUBMISSION), anyLong(), eq(ACTOR), eq(NOW)))
        .thenAnswer(call -> write("binding", SUBMISSION));
    when(steps.withdrawBoundClientStep(STEP, "客户取消安排", ACTOR, NOW))
        .thenAnswer(call -> failingWrite.equals("WORKFLOW") ? 0 : write("step", "WITHDRAWN"));
    when(applications.updateStage(eq(APPLICATION), eq("PATH_SELECTION"), anyLong(), eq(ACTOR), eq(NOW)))
        .thenAnswer(call -> write("stage", "PATH_SELECTION"));
    when(history.insertStageHistory(any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(call -> failingWrite.equals("STAGE_HISTORY") ? 0 : write("stageHistory", true));
    when(interviews.lockSubmissionInterviews(APPLICATION, SUBMISSION, null))
        .thenAnswer(call -> List.of(interview(COMPLETED, "COMPLETED"), interview(INTERVIEW, (String) rows.get("interview"))));
    when(interviews.cancel(INTERVIEW, 0, ACTOR, NOW)).thenAnswer(call -> write("interview", "CANCELLED"));
    when(interviews.insertHistory(any(), eq(INTERVIEW), eq("CANCEL"), any(), any(), any(), any(), any()))
        .thenAnswer(call -> failingWrite.equals("INTERVIEW_HISTORY") ? 0 : write("interviewHistory", true));
    when(interviews.findActiveTemplate("INTERVIEW_CANCEL"))
        .thenReturn(new TemplateVersion("T".repeat(26), "T".repeat(26), 1,
            "取消通知", "{{applicationId}} {{stepCode}} {{startAt}}", ACTOR, NOW));
    when(notifications.insertNotifications(any())).thenAnswer(call -> {
      if (failingWrite.equals("NOTIFICATION")) return false;
      List<NotificationIntent> intents = call.getArgument(0);
      write("notifications", List.copyOf(intents));
      return true;
    });

    InterviewNotificationApplicationManager notificationManager = new InterviewNotificationApplicationManager();
    inject(notificationManager, "persistence", interviews, "participantPersistenceService", mock(ParticipantPersistenceService.class),
        "notificationIntentPersistenceService", notifications, "clock", clock, "ids", ids);
    RecruitmentApplicationManager recruitmentManager = new RecruitmentApplicationManager();
    ReflectionTestUtils.setField(recruitmentManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    when(applications.findCandidateId(any())).thenReturn("C".repeat(26));
    when(submissions.findCandidateId(any())).thenReturn("C".repeat(26));
    ReflectionTestUtils.setField(recruitmentManager, "followUpPlanLifecycle", mock(FollowUpPlanLifecycleManager.class));
    inject(recruitmentManager, "persistence", applications, "authorization", authorization,
        "applicationCollaboratorPersistenceService", mock(ApplicationCollaboratorPersistenceService.class),
        "recruitmentWorkflowStepPersistenceService", steps, "stageHistoryPersistenceService", history,
        "interviewPersistenceService", interviews, "interviewNotificationManager", proxy(notificationManager),
        "clock", clock, "ids", ids);
    SubmissionApplicationManager submissionManager = new SubmissionApplicationManager();
    ReflectionTestUtils.setField(submissionManager, "blacklist", mock(CompanyBlacklistQueryService.class));
    inject(submissionManager, "persistence", submissions, "submissionIdempotencyPersistenceService", idempotency,
        "authorization", authorization, "applications", proxy(recruitmentManager), "clock", clock,
        "sendSnapshotPersistenceService", mock(SendSnapshotPersistenceService.class),
        "submissionFeedbackPersistenceService", mock(SubmissionFeedbackPersistenceService.class),
        "riskAcknowledgementPersistenceService", mock(RiskAcknowledgementPersistenceService.class));
    return proxy(submissionManager);
  }

  private int write(String key, Object value) {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    rows.put(key, value);
    return 1;
  }

  private ClientSubmission submission() {
    ClientSubmission value = new ClientSubmission();
    value.setId(SUBMISSION);
    value.setApplicationId(APPLICATION);
    value.setDeliveryStatus((String) rows.get("submission"));
    value.setProcessStatus(rows.get("submission").equals("SENT") ? "INTERVIEWING" : "WITHDRAWN");
    value.setOwnerUserId(ACTOR);
    return value;
  }

  private Application application() {
    Application value = new Application();
    value.setId(APPLICATION);
    value.setCurrentStage((String) rows.get("stage"));
    value.setTerminalAt((Instant) rows.get("terminalAt"));
    value.setOwnerUserId(ACTOR);
    return value;
  }

  private WorkflowStep activeStep() {
    WorkflowStep value = new WorkflowStep();
    value.setId(STEP);
    value.setStepCode("CLIENT_AI_INTERVIEW");
    value.setClientSubmissionId((String) rows.get("binding"));
    value.setStatus((String) rows.get("step"));
    return value;
  }

  private WorkflowStep completedStep() {
    WorkflowStep value = new WorkflowStep();
    value.setId(COMPLETED);
    value.setStepCode("CLIENT_SUBMISSION");
    value.setClientSubmissionId(SUBMISSION);
    value.setStatus("PASSED");
    return value;
  }

  private Interview interview(String id, String status) {
    Interview value = new Interview();
    value.setId(id);
    value.setApplicationId(APPLICATION);
    value.setClientSubmissionId(SUBMISSION);
    value.setWorkflowStepId(id.equals(COMPLETED) ? COMPLETED : STEP);
    value.setStepCode("CLIENT_AI_INTERVIEW");
    value.setOwnerUserId(ACTOR);
    value.setCandidateId("C".repeat(26));
    value.setStatus(status);
    value.setScheduledStartAt(NOW.plusSeconds(3600));
    value.setTimezoneId("Asia/Shanghai");
    value.setMeetingMode("ONLINE");
    value.setCandidateNotificationChannel("EMAIL");
    return value;
  }

  private static void inject(Object target, Object... fields) {
    for (int i = 0; i < fields.length; i += 2) {
      ReflectionTestUtils.setField(target, (String) fields[i], fields[i + 1]);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T proxy(T target) {
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    TransactionInterceptor interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    factory.addAdvice(interceptor);
    return (T) factory.getProxy();
  }

  private static final class SharedTransactions extends AbstractPlatformTransactionManager {
    private final Map<String, Object> rows;
    private final ResourceState resource = new ResourceState();
    private Map<String, Object> snapshot;
    private int begins;
    private int commits;
    private int rollbacks;

    private SharedTransactions(Map<String, Object> rows) { this.rows = rows; }

    @Override protected Object doGetTransaction() { return resource; }
    @Override protected boolean isExistingTransaction(Object transaction) { return resource.active; }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
      resource.active = true;
      snapshot = new HashMap<>(rows);
      begins++;
    }
    @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
    @Override protected void doRollback(DefaultTransactionStatus status) {
      rows.clear();
      rows.putAll(snapshot);
      rollbacks++;
    }
    @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { resource.rollbackOnly = true; }
    @Override protected void doCleanupAfterCompletion(Object transaction) {
      resource.active = false;
      resource.rollbackOnly = false;
    }

    private static final class ResourceState implements SmartTransactionObject {
      private boolean active;
      private boolean rollbackOnly;
      @Override public boolean isRollbackOnly() { return rollbackOnly; }
    }
  }
}
