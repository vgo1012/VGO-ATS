package com.ezaigc.hr.recruiting.recruitment.application.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService.SubmissionDemandContext;
import com.ezaigc.hr.recruiting.submission.application.adapter.ClientSubmissionValidationAdapter;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.service.SubmissionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.ApplicationAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Decision;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.DecisionPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OfferStatusRecordPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.OnboardingProcessPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.CommunicationPersistenceService;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ProgressiveFollowUpTest {
  final RecruitmentApplicationManager manager = new RecruitmentApplicationManager();
  final ApplicationPersistenceService apps = mock(ApplicationPersistenceService.class);
  final RecruitmentWorkflowStepPersistenceService steps = mock(RecruitmentWorkflowStepPersistenceService.class);
  final CommunicationPersistenceService communications = mock(CommunicationPersistenceService.class);
  final FollowUpPersistenceService followUps = mock(FollowUpPersistenceService.class);
  final StageHistoryPersistenceService histories = mock(StageHistoryPersistenceService.class);
  final DecisionPersistenceService decisions = mock(DecisionPersistenceService.class);
  final OfferStatusRecordPersistenceService offers = mock(OfferStatusRecordPersistenceService.class);
  final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
  final Application app = new Application();
  final List<WorkflowStep> rows = new ArrayList<>();
  final Instant now = Instant.parse("2026-09-09T01:00:00Z");
  @BeforeEach void setup() {
    app.setId("A".repeat(26)); app.setCandidateId("C".repeat(26)); app.setOwnerUserId("U".repeat(26));
    app.setJdVersionId("V".repeat(26));
    app.setRecruitmentType("INTERNAL"); app.setCurrentStage("PATH_SELECTION"); app.setCreateTime(now); app.setVersion(3);
    var actor = new CurrentActorSnapshot(app.getOwnerUserId(),"HR",null,"D".repeat(26),Set.of(),Set.of(),Set.of(),Set.of("RECRUITER"),Set.of("recruitment.manage"),false,false);
    var auth=mock(AuthorizationService.class); when(auth.currentActor()).thenReturn(actor);
    var ids=mock(PlatformIdGenerator.class); when(ids.newId()).thenReturn("N".repeat(26));
    ReflectionTestUtils.setField(manager,"persistence",apps); ReflectionTestUtils.setField(manager,"authorization",auth);
    ReflectionTestUtils.setField(manager,"blacklist",mock(CompanyBlacklistQueryService.class));
    when(apps.findCandidateId(any())).thenReturn(app.getCandidateId());
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager,"ids",ids); ReflectionTestUtils.setField(manager,"assembler",new ApplicationAssembler());
    ReflectionTestUtils.setField(manager,"clock",Clock.fixed(now,ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager,"demands",mock(DemandQueryService.class));
    ReflectionTestUtils.setField(manager,"applicationCollaboratorPersistenceService",mock(ApplicationCollaboratorPersistenceService.class));
    ReflectionTestUtils.setField(manager,"recruitmentWorkflowStepPersistenceService",steps);
    ReflectionTestUtils.setField(manager,"stageHistoryPersistenceService",histories);
    ReflectionTestUtils.setField(manager,"followUpPersistenceService",followUps);
    ReflectionTestUtils.setField(manager,"communicationPersistenceService",communications);
    when(communications.insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
    when(followUps.insertFollowUp(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
    ReflectionTestUtils.setField(manager,"decisionPersistenceService",decisions);
    ReflectionTestUtils.setField(manager,"offerStatusRecordPersistenceService",offers);
    ReflectionTestUtils.setField(manager,"onboardingProcessPersistenceService",mock(OnboardingProcessPersistenceService.class));
    ReflectionTestUtils.setField(manager,"engagementLocks",mock(EngagementLockApplicationManager.class));
    when(apps.lockApplication(app.getId())).thenReturn(app); when(apps.findApplication(app.getId())).thenReturn(app);
    ReflectionTestUtils.setField(manager,"interviewPersistenceService",interviews);
    when(interviews.lockArrangedApplicationInterviews(app.getId())).thenAnswer(call -> rows.stream()
        .filter(row -> row.getStepCode().startsWith("INTERVIEW_ROUND_") && "PASSED".equals(row.getStatus()))
        .map(row -> arranged(row, row.getRoundNo() == null ? 1 : row.getRoundNo())).toList());
    when(steps.lockSteps(app.getId())).thenReturn(rows); when(steps.findSteps(app.getId())).thenReturn(rows);
    when(steps.save(any(WorkflowStep.class))).thenReturn(true); when(steps.updateById(any(WorkflowStep.class))).thenReturn(true);
    when(apps.enableProgressiveStage(any(),any(),anyLong(),any(),any())).thenReturn(1);
    when(apps.insertPathChange(any(),any(),any(),any(),any(),any(),anyLong(),any(),any())).thenReturn(1);
    when(histories.insertStageHistory(any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
  }
  WorkflowStep step(String code,String status,int sequence) {
    var value=new WorkflowStep(); value.setId("S".repeat(25)+sequence); value.setApplicationId(app.getId());
    value.setStepCode(code); value.setStatus(status); value.setSequenceNo(sequence); value.setStepDefinitionId("F".repeat(26));
    rows.add(value); return value;
  }
  @Test void clientRestrictionLabelsDoNotBlockFollowUpOrCancellation() {
    assertThat(manager.requireLockedFollowUpApplication(app.getId())).isEqualTo(app);
    assertThat(manager.requireLockedFollowUpCancellation(app.getId())).isEqualTo(app);
  }
  private Interview arranged(WorkflowStep step, int round) {
    var value = new Interview(); value.setWorkflowStepId(step.getId()); value.setRoundNo(round);
    value.setStatus("SCHEDULED"); value.setInterviewType("INTERNAL"); return value;
  }
  @ParameterizedTest
  @CsvSource({"1,false,3,true", "2,true,3,false", "5,false,3,false", "5,true,3,true"})
  void schedulingEntryRequiresFirstAndHigherRoundButAllowsReplacingCancelledFirst(
      int requestedRound, boolean firstScheduled, int otherRound, boolean allowed) {
    var requested = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 1); requested.setRoundNo(requestedRound);
    var other = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 2); other.setRoundNo(otherRound);
    var schedules = new ArrayList<Interview>(); schedules.add(arranged(other, otherRound));
    if (firstScheduled) {
      var first = new WorkflowStep(); first.setId("original-first"); schedules.add(arranged(first, 1));
    }
    when(steps.lockStep(app.getId(), requested.getId())).thenReturn(requested);
    when(interviews.lockArrangedApplicationInterviews(app.getId())).thenReturn(schedules);
    if (allowed) {
      assertThat(manager.requireLockedInterviewContext(app.getId(), requested.getId()).workflowStepId()).isEqualTo(requested.getId());
    } else {
      assertThatThrownBy(() -> manager.requireLockedInterviewContext(app.getId(), requested.getId()))
          .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("INTERVIEW_ROUND_INVALID"));
    }
  }
  @ParameterizedTest
  @ValueSource(ints = {2, 3})
  void legacyClientSchedulingUsesTheSameLockedRoundGuard(int requestedRound) {
    app.setRecruitmentType("EXTERNAL");
    var requested = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 1); requested.setRoundNo(requestedRound);
    var first = new WorkflowStep(); first.setId("original-first");
    var third = new WorkflowStep(); third.setId("already-third");
    when(steps.lockStep(app.getId(), requested.getId())).thenReturn(requested);
    when(interviews.lockArrangedApplicationInterviews(app.getId())).thenReturn(List.of(arranged(first, 1), arranged(third, 3)));
    assertThatThrownBy(() -> manager.bindClientInterviewStep(app.getId(), requested.getId(), "submission"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("INTERVIEW_ROUND_INVALID"));
    verify(steps, never()).bindClientSubmission(any(), any(), anyLong(), any(), any());
  }

  @Test void schedulingContextDoesNotMistakeItsOwnExistingRoundForANewerRound() {
    var requested = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 1); requested.setRoundNo(3);
    var first = new WorkflowStep(); first.setId("original-first");
    when(steps.lockStep(app.getId(), requested.getId())).thenReturn(requested);
    when(interviews.lockArrangedApplicationInterviews(app.getId())).thenReturn(List.of(arranged(first, 1), arranged(requested, 3)));
    assertThat(manager.requireLockedInterviewContext(app.getId(), requested.getId()).workflowStepId()).isEqualTo(requested.getId());
  }
  @Test void nextActionCanRestoreCancelledFirstBeforeAnyFurtherRound() {
    var third = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 1); third.setRoundNo(3);
    when(interviews.lockArrangedApplicationInterviews(app.getId())).thenReturn(List.of(arranged(third, 3)));
    manager.nextActionResponse(app.getId(), "INTERVIEW_ROUND_1", "补排一面", 3, 1);
    var captured = ArgumentCaptor.forClass(WorkflowStep.class); verify(steps).save(captured.capture());
    assertThat(captured.getValue().getRoundNo()).isEqualTo(1);
    assertThat(third.getStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test void completingOneRoundKeepsOtherScheduledRoundsInProcess() {
    app.setCurrentStage("IN_PROCESS"); app.setProgressiveFollowUp(true);
    var first = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 1); first.setRoundNo(1);
    var third = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 2); third.setRoundNo(3);
    var completed = arranged(first, 1); completed.setStatus("COMPLETED");
    when(interviews.lockLatestByStep(first.getId())).thenReturn(completed);
    when(steps.findStep(app.getId(), first.getId())).thenReturn(first);
    when(steps.findActiveSteps(List.of(app.getId()))).thenReturn(List.of(third));
    when(steps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    manager.completeStep(app.getId(), first.getId(), "PASSED", null, null, 0);
    verify(apps).enableProgressiveStage(app.getId(), "IN_PROCESS", 3, app.getOwnerUserId(), now);
    assertThat(third.getStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test void firstInterviewMustActuallyBeScheduledBeforeSkippingToLaterRound() {
    var first = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 1); first.setRoundNo(1);
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(), "INTERVIEW_ROUND_1", null, 3, 3))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("INTERVIEW_ROUND_INVALID"));
    verify(steps, never()).save(any(WorkflowStep.class));
  }
  @Test void scheduledFirstRoundAllowsThirdRoundWithoutCompletingOrPassingFirstRound() {
    var first = step("INTERVIEW_ROUND_1", "IN_PROGRESS", 1); first.setRoundNo(1);
    when(interviews.lockArrangedApplicationInterviews(app.getId())).thenReturn(List.of(arranged(first, 1)));
    manager.nextActionResponse(app.getId(), "INTERVIEW_ROUND_1", null, 3, 3);
    var captured = ArgumentCaptor.forClass(WorkflowStep.class); verify(steps).save(captured.capture());
    assertThat(captured.getValue().getRoundNo()).isEqualTo(3);
    assertThat(first.getStatus()).isEqualTo("IN_PROGRESS");
    verify(steps, never()).completeStep(any(), any(), any(), any(), anyLong(), any(), any());
  }

  @Test void selectsOnlyCurrentActionWithoutPreselectingFutureRounds() {
    var communication=step("CANDIDATE_COMMUNICATION","AVAILABLE",1); var interview=step("INTERVIEW_ROUND_1","AVAILABLE",2);
    manager.nextActionResponse(app.getId(),"CANDIDATE_COMMUNICATION","HR 确认先沟通",3);
    assertThat(communication.getStatus()).isEqualTo("IN_PROGRESS"); assertThat(communication.getStartedAt()).isEqualTo(now); assertThat(communication.getDueAt()).isNull();
    assertThat(interview.getStatus()).isEqualTo("AVAILABLE"); verify(steps,never()).save(any(WorkflowStep.class));
    verify(apps).enableProgressiveStage(app.getId(),"IN_PROCESS",3,app.getOwnerUserId(),now);
  }
  @ParameterizedTest
  @ValueSource(ints = {4, 6, 7})
  void appendsInterviewBeyondThreeRoundsWithoutRewritingCompletedEvidence(int nextRound) {
    for(int i=1;i<nextRound;i++) step("INTERVIEW_ROUND_1","PASSED",i).setRoundNo(i);
    manager.nextActionResponse(app.getId(),"INTERVIEW_ROUND_1","双方确认追加一轮",3);
    var captured=ArgumentCaptor.forClass(WorkflowStep.class); verify(steps).save(captured.capture());
    assertThat(captured.getValue().getRoundNo()).isEqualTo(nextRound);
    assertThat(captured.getValue().getSequenceNo()).isEqualTo(nextRound);
    assertThat(rows).allSatisfy(value -> assertThat(value.getStatus()).isEqualTo("PASSED"));
    assertThat(rows).extracting(WorkflowStep::getRoundNo).containsExactlyElementsOf(
        IntStream.range(1, nextRound).boxed().toList());
  }
  @Test void rejectsRepeatedRecommendationWhileCompletedSubmissionIsStillSent() {
    completedRecommendation("SENT", "PASSED");
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(), "CLIENT_SUBMISSION", null, 3))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_PATH_STEP_INVALID"));
    verify(steps, never()).save(any(WorkflowStep.class));
    verify(apps, never()).enableProgressiveStage(any(), any(), anyLong(), any(), any());
    assertThat(rows.getFirst().getStatus()).isEqualTo("PASSED");
  }
  @ParameterizedTest
  @ValueSource(strings = {"WITHDRAWN", "SENT"})
  void retiredRecommendationCannotBeSelectedAgain(String delivery) {
    completedRecommendation(delivery, "FAILED");
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(), "CLIENT_SUBMISSION", "重新推荐", 3))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("APPLICATION_PATH_STEP_INVALID"));
    verify(steps, never()).save(any(WorkflowStep.class));
  }
  @Test void replacesExistingDuplicateRecommendationWithInterviewWithoutChangingCompletedEvidence() {
    completedRecommendation("SENT", "PASSED");
    app.setProgressiveFollowUp(true); app.setCurrentStage("IN_PROCESS");
    var duplicate = step("CLIENT_SUBMISSION", "IN_PROGRESS", 2);
    when(steps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    manager.nextActionResponse(app.getId(), "INTERVIEW_ROUND_1", "已推荐，安排甲方面试", 3);
    verify(steps).completeStep(duplicate.getId(), "WITHDRAWN", "已推荐，安排甲方面试", null, 0, app.getOwnerUserId(), now);
    var captured = ArgumentCaptor.forClass(WorkflowStep.class);
    verify(steps).save(captured.capture());
    assertThat(captured.getValue().getStepCode()).isEqualTo("INTERVIEW_ROUND_1");
    assertThat(rows.getFirst().getStatus()).isEqualTo("PASSED");
  }
  @ParameterizedTest
  @ValueSource(strings = {"CANDIDATE_COMMUNICATION", "INTERVIEW_ROUND_1", "DECISION"})
  void retiresUnsentLegacyRecommendationAndContinues(String next) {
    app.setRecruitmentType("EXTERNAL"); app.setCurrentStage("IN_PROCESS");
    var retired = step("CLIENT_SUBMISSION", "IN_PROGRESS", 2);
    when(steps.completeStep(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn(1);
    manager.nextActionResponse(app.getId(), next, "继续招聘流程", 3);
    verify(steps).completeStep(retired.getId(), "WITHDRAWN", "继续招聘流程", null, 0, app.getOwnerUserId(), now);
    var captured = ArgumentCaptor.forClass(WorkflowStep.class);
    verify(steps).save(captured.capture());
    assertThat(captured.getValue().getStepCode()).isEqualTo(next);
  }
  private ClientSubmission completedRecommendation(String deliveryStatus, String stepStatus) {
    app.setRecruitmentType("EXTERNAL"); app.setJdId("J".repeat(26));
    app.setJdVersionId("V".repeat(26)); app.setResumeVersionId("R".repeat(26));
    var previous = step("CLIENT_SUBMISSION", stepStatus, 1);
    previous.setClientSubmissionId("B".repeat(26));
    var submission = new ClientSubmission(); submission.setId(previous.getClientSubmissionId());
    submission.setApplicationId(app.getId()); submission.setCandidateId(app.getCandidateId());
    submission.setClientId("L".repeat(26)); submission.setJdId(app.getJdId());
    submission.setJdVersionId(app.getJdVersionId()); submission.setResumeVersionId(app.getResumeVersionId());
    submission.setDeliveryStatus(deliveryStatus); submission.setSentAt(now);
    var persistence = mock(SubmissionPersistenceService.class);
    when(persistence.find(submission.getId())).thenReturn(submission);
    var validator = new ClientSubmissionValidationAdapter();
    ReflectionTestUtils.setField(validator, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "submissionValidator", validator);
    var demands = mock(DemandQueryService.class);
    when(demands.requireSubmissionContextForJdVersion(app.getJdVersionId()))
        .thenReturn(new SubmissionDemandContext(null, submission.getClientId(), null, null, "EXTERNAL", null, null));
    ReflectionTestUtils.setField(manager, "demands", demands);
    return submission;
  }
  @Test void existingStandardExternalWorkflowCanAddInterviewWithoutInventingDefinition() {
    app.setRecruitmentType("EXTERNAL"); app.setWorkflowTemplateVersionId("T".repeat(26));
    step("CANDIDATE_COMMUNICATION","PASSED",1); step("CLIENT_SUBMISSION","PASSED",2);
    step("DECISION","AVAILABLE",3); step("OFFER","AVAILABLE",4);
    manager.nextActionResponse(app.getId(),"INTERVIEW_ROUND_1","甲方确认安排面试",3);
    var captured=ArgumentCaptor.forClass(WorkflowStep.class); verify(steps).save(captured.capture());
    assertThat(captured.getValue().getStepDefinitionId()).isNull();
    assertThat(captured.getValue().getTemplateVersionId()).isEqualTo(app.getWorkflowTemplateVersionId());
    assertThat(captured.getValue().getRoundNo()).isEqualTo(1);
  }
  @Test void internalRecruitmentRejectsExternalOnlyActionEvenWithoutDefinition() {
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"PRE_SUBMISSION_INTERNAL_INTERVIEW","下一步",3)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"CLIENT_SUBMISSION","下一步",3)).isInstanceOf(PlatformException.class);
    verify(steps,never()).save(any(WorkflowStep.class));
  }
  @Test void offerRemainsAfterActualDecisionWhenRoundsWereAddedDynamically() {
    var decision=step("DECISION","AVAILABLE",1); var offer=step("OFFER","AVAILABLE",2);
    step("INTERVIEW_ROUND_1","PASSED",3); step("INTERVIEW_ROUND_1","PASSED",4);
    manager.nextActionResponse(app.getId(),"DECISION","面试结束，作出人工决定",3);
    assertThat(decision.getSequenceNo()).isEqualTo(5); assertThat(offer.getSequenceNo()).isEqualTo(6);
    assertThat(offer.getStatus()).isEqualTo("AVAILABLE");
  }
  @Test void rejectsAnotherActionWhileCurrentOneIsUnfinished() {
    step("CANDIDATE_COMMUNICATION","PENDING",1);
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"CANDIDATE_COMMUNICATION","下一步",3))
        .isInstanceOfSatisfying(PlatformException.class,error -> assertThat(error.code()).isEqualTo("APPLICATION_ACTION_ACTIVE"));
    verify(steps,never()).updateById(any(WorkflowStep.class));
  }
  @Test void rejectsStaleApplicationVersionAndOfferBypass() {
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"CANDIDATE_COMMUNICATION","下一步",2)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"OFFER","绕过决定",3)).isInstanceOf(PlatformException.class);
    verify(apps,never()).enableProgressiveStage(any(),any(),anyLong(),any(),any());
  }
  @Test void completedActionReturnsToHrWithoutActivatingAnyFutureStep() {
    app.setProgressiveFollowUp(true); app.setCurrentStage("IN_PROCESS");
    var current=step("CANDIDATE_COMMUNICATION","IN_PROGRESS",1); step("INTERVIEW_ROUND_1","AVAILABLE",2);
    when(steps.findStep(app.getId(),current.getId())).thenReturn(current);
    when(followUps.countFollowUps(current.getId())).thenReturn(1);
    when(steps.completeStep(any(),any(),any(),any(),anyLong(),any(),any())).thenReturn(1);
    when(apps.updateStage(any(),any(),anyLong(),any(),any())).thenReturn(1);
    manager.completeStep(app.getId(),current.getId(),"PASSED","沟通后由 HR 决定下一步",null,0);
    verify(apps).enableProgressiveStage(app.getId(),"PATH_SELECTION",3,app.getOwnerUserId(),now);
    verify(steps,never()).selectStep(any(),any(),any(),any(),any(),any());
  }

  @Test void legacyCompletedActionAlsoReturnsToNextActionSelection() {
    app.setCurrentStage("IN_PROCESS");
    var current=step("CANDIDATE_COMMUNICATION","IN_PROGRESS",1);
    step("DECISION","AVAILABLE",2);
    when(steps.findStep(app.getId(),current.getId())).thenReturn(current);
    when(steps.completeStep(any(),any(),any(),any(),anyLong(),any(),any())).thenReturn(1);
    manager.completeStep(app.getId(),current.getId(),"PASSED","沟通已完成",null,0);
    verify(apps).enableProgressiveStage(app.getId(),"PATH_SELECTION",3,app.getOwnerUserId(),now);
    verify(steps,never()).selectStep(any(),any(),any(),any(),any(),any());
  }
  @Test void legacyAutomaticDecisionCanBeWithdrawnForSecondInterview() {
    app.setCurrentStage("DECISION");
    var first=step("INTERVIEW_ROUND_1","PASSED",1); first.setRoundNo(1);
    var automatic=step("DECISION","IN_PROGRESS",2);
    when(steps.completeStep(any(),any(),any(),any(),anyLong(),any(),any())).thenReturn(1);
    manager.nextActionResponse(app.getId(),"INTERVIEW_ROUND_1","继续安排二面",3);
    verify(steps).completeStep(automatic.getId(),"WITHDRAWN","继续安排二面",null,0,app.getOwnerUserId(),now);
    var captured=ArgumentCaptor.forClass(WorkflowStep.class); verify(steps).save(captured.capture());
    assertThat(captured.getValue().getRoundNo()).isEqualTo(2);
    assertThat(first.getStatus()).isEqualTo("PASSED");
    verify(apps).enableProgressiveStage(app.getId(),"IN_PROCESS",3,app.getOwnerUserId(),now);
  }
  @Test void recordedDecisionOrOfferCannotBeReplacedEvenOnLegacyApplication() {
    app.setCurrentStage("DECISION"); step("DECISION","IN_PROGRESS",1);
    when(decisions.findDecisions(app.getId())).thenReturn(List.of(new Decision()));
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"INTERVIEW_ROUND_1",null,3))
        .isInstanceOf(PlatformException.class);
    when(decisions.findDecisions(app.getId())).thenReturn(List.of());
    when(offers.findOffers(app.getId())).thenReturn(List.of(new OfferStatusRecord()));
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"INTERVIEW_ROUND_1",null,3))
        .isInstanceOf(PlatformException.class);
    verify(steps,never()).completeStep(any(),any(),any(),any(),anyLong(),any(),any());
    verify(steps,never()).save(any(WorkflowStep.class));
  }
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void replacementWithdrawalConflictNeverCreatesNextStep(boolean duplicateRecommendation) {
    if (duplicateRecommendation) {
      completedRecommendation("SENT", "PASSED");
      app.setCurrentStage("IN_PROCESS"); step("CLIENT_SUBMISSION", "IN_PROGRESS", 2);
    } else {
      app.setCurrentStage("DECISION"); step("DECISION", "IN_PROGRESS", 1);
    }
    var transactions = mock(PlatformTransactionManager.class);
    var transaction = new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    var interceptor = new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    var factory = new ProxyFactory(manager); factory.setProxyTargetClass(true); factory.addAdvice(interceptor);
    var transactionalManager = (RecruitmentApplicationManager) factory.getProxy();
    assertThatThrownBy(() -> transactionalManager.nextActionResponse(app.getId(),"INTERVIEW_ROUND_1",null,3))
        .isInstanceOfSatisfying(PlatformException.class,error -> assertThat(error.code()).isEqualTo("APPLICATION_STATE_CHANGED"));
    verify(transactions).rollback(transaction);
    verify(transactions,never()).commit(any());
    verify(steps,never()).save(any(WorkflowStep.class));
    verify(apps,never()).enableProgressiveStage(any(),any(),anyLong(),any(),any());
    verify(apps,never()).insertPathChange(any(),any(),any(),any(),any(),any(),anyLong(),any(),any());
  }

  @Test void explicitlySelectedDecisionCannotBeReplacedByNextAction() {
    app.setCurrentStage("DECISION"); app.setProgressiveFollowUp(true);
    step("DECISION","IN_PROGRESS",1);
    assertThatThrownBy(() -> manager.nextActionResponse(app.getId(),"INTERVIEW_ROUND_1",null,3))
        .isInstanceOf(PlatformException.class);
    verify(steps,never()).completeStep(any(),any(),any(),any(),anyLong(),any(),any());
  }

  @Test void staleOrWrongCommunicationReferenceNeverWritesFacts() {
    var current=step("CANDIDATE_COMMUNICATION","IN_PROGRESS",1); current.setVersion(2);
    when(steps.findStep(app.getId(),current.getId())).thenReturn(current);
    assertThatThrownBy(() -> manager.recordPlannedCommunication(app.getId(),"PHONE","实际沟通","CONTINUE",null,current.getId(),1L))
        .isInstanceOfSatisfying(PlatformException.class,e -> assertThat(e.code()).isEqualTo("FOLLOW_UP_STEP_CHANGED"));
    current.setStepCode("INTERVIEW_ROUND_1");
    assertThatThrownBy(() -> manager.recordPlannedCommunication(app.getId(),"PHONE","实际沟通","CONTINUE",null,current.getId(),2L))
        .isInstanceOf(PlatformException.class);
    verify(communications,never()).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
  }
  @Test void completedAppointmentWritesOneCommunicationAndCompletesOnlyConfirmedCommunicationStep() {
    app.setProgressiveFollowUp(true);var current=step("CANDIDATE_COMMUNICATION","IN_PROGRESS",1);
    step("INTERVIEW_ROUND_1","AVAILABLE",2);
    when(steps.findStep(app.getId(),current.getId())).thenReturn(current);
    when(steps.completeStep(any(),any(),any(),any(),anyLong(),any(),any())).thenReturn(1);
    when(apps.updateStage(any(),any(),anyLong(),any(),any())).thenReturn(1);
    manager.recordPlannedCommunication(app.getId(),"PHONE","可参加面试","CONTINUE",null,current.getId(),0L);
    verify(communications).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
    verify(followUps).insertFollowUp(any(),any(),any(),any(),any(),any(),any(),any(),any());
    verify(steps).completeStep(current.getId(),"PASSED","可参加面试",null,0L,app.getOwnerUserId(),now);
    verify(steps,never()).selectStep(any(),any(),any(),any(),any(),any());
  }
  @Test void unansweredAppointmentKeepsCommunicationActiveAndNeverInventsPass() {
    var current=step("CANDIDATE_COMMUNICATION","PENDING",1);
    when(steps.findStep(app.getId(),current.getId())).thenReturn(current);
    when(steps.startStep(any(),anyLong(),any(),any())).thenReturn(1);
    manager.recordPlannedCommunication(app.getId(),"PHONE",null,"NO_RESPONSE",null,current.getId(),0L);
    verify(steps).startStep(current.getId(),0L,app.getOwnerUserId(),now);
    verify(steps,never()).completeStep(any(),any(),any(),any(),anyLong(),any(),any());
  }
  @Test void unassociatedAppointmentCannotCompleteAnyStep() {
    step("INTERVIEW_ROUND_1","IN_PROGRESS",1);
    manager.recordPlannedCommunication(app.getId(),"PHONE",null,"CONTINUE",null,null,null);
    verify(communications).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
    verify(followUps,never()).insertFollowUp(any(),any(),any(),any(),any(),any(),any(),any(),any());
    verify(steps,never()).completeStep(any(),any(),any(),any(),anyLong(),any(),any());
  }
  @Test void failedStepCompareAndSetDoesNotWriteDuplicateCommunication() {
    var current=step("CANDIDATE_COMMUNICATION","IN_PROGRESS",1);
    when(steps.findStep(app.getId(),current.getId())).thenReturn(current);
    assertThatThrownBy(() -> manager.completeStep(app.getId(),current.getId(),"PASSED",null,null,0)).isInstanceOf(PlatformException.class);
    verify(communications,never()).insertCommunication(any(),any(),any(),any(),any(),any(),any(),any(),any());
  }
  @Test void selectingNextActionAllowsNoSupplementaryReason() {
    var current=step("CANDIDATE_COMMUNICATION","AVAILABLE",1);
    manager.nextActionResponse(app.getId(),"CANDIDATE_COMMUNICATION",null,3);
    assertThat(current.getStatus()).isEqualTo("IN_PROGRESS");
  }
  @Test void startingAlreadyCurrentStepIsIdempotentButStaleVersionIsRejected() {
    var current=step("CANDIDATE_COMMUNICATION","IN_PROGRESS",1);
    when(steps.findStep(app.getId(),current.getId())).thenReturn(current);
    assertThat(manager.startStep(app.getId(),current.getId(),0)).isSameAs(current);
    assertThatThrownBy(() -> manager.startStep(app.getId(),current.getId(),1)).isInstanceOf(PlatformException.class);
  }
}
