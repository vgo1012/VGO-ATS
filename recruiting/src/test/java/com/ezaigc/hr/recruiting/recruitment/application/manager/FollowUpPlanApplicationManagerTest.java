package com.ezaigc.hr.recruiting.recruitment.application.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.clearInvocations;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlanHistory;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanHistoryPersistenceService;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.FollowUpPlanAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.FollowUpPlanDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentIdempotencyPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.List;
import java.util.Map;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FollowUpPlanApplicationManagerTest {
  final FollowUpPlanApplicationManager manager=new FollowUpPlanApplicationManager();
  final RecruitmentApplicationManager apps=mock(RecruitmentApplicationManager.class);
  final CompanyBlacklistQueryService blacklist=mock(CompanyBlacklistQueryService.class);
  final FollowUpPlanPersistenceService plans=mock(FollowUpPlanPersistenceService.class);
  final FollowUpPlanLifecycleManager lifecycle=mock(FollowUpPlanLifecycleManager.class);
  final RecruitmentIdempotencyPersistenceService idempotency=mock(RecruitmentIdempotencyPersistenceService.class);
  final ApplicationPersistenceService persistence=mock(ApplicationPersistenceService.class);
  final Application app=new Application(); final FollowUpPlan plan=new FollowUpPlan();
  final Instant now=Instant.parse("2026-09-09T01:00:00Z");
  final String actor="U".repeat(26); final String key="followup-plan-create";
  @BeforeEach void setup() {
    app.setId("A".repeat(26));app.setCandidateId("C".repeat(26));app.setJdVersionId("J".repeat(26));app.setVersion(3);
    plan.setId("P".repeat(26));plan.setApplicationId(app.getId());plan.setStatus("SCHEDULED");plan.setStartsAt(now.minusSeconds(60));plan.setEndsAt(now.plusSeconds(60));plan.setVersion(2);
    var auth=mock(AuthorizationService.class);when(auth.currentActor()).thenReturn(new CurrentActorSnapshot(actor,"HR",null,"D".repeat(26),Set.of(),Set.of(),Set.of(),Set.of(),Set.of(),false,false));
    var demands=mock(DemandQueryService.class);when(demands.requireJdVersion(anyString())).thenReturn(new JdVersionSnapshot());
    when(demands.clientSummariesForVersions(any())).thenReturn(Map.of("J".repeat(26),new JdClientSummary(null,null)));
    var ids=mock(PlatformIdGenerator.class);when(ids.newId()).thenReturn(plan.getId());
    ReflectionTestUtils.setField(manager,"applications",apps);ReflectionTestUtils.setField(manager,"plans",plans);
    ReflectionTestUtils.setField(manager,"blacklist",blacklist);
    ReflectionTestUtils.setField(manager,"lifecycle",lifecycle);ReflectionTestUtils.setField(manager,"idempotency",idempotency);
    ReflectionTestUtils.setField(manager,"applicationPersistence",persistence);ReflectionTestUtils.setField(manager,"authorization",auth);
    ReflectionTestUtils.setField(manager,"demands",demands);ReflectionTestUtils.setField(manager,"assembler",new FollowUpPlanAssembler());
    ReflectionTestUtils.setField(manager,"ids",ids);ReflectionTestUtils.setField(manager,"clock",Clock.fixed(now,ZoneOffset.UTC));
    when(apps.requireLockedFollowUpApplication(app.getId())).thenReturn(app);when(apps.requireLockedFollowUpCancellation(app.getId())).thenReturn(app);when(plans.getById(plan.getId())).thenReturn(plan);
    when(apps.requireLockedFollowUpAccess(app.getId())).thenReturn(app);
    when(persistence.insertIdempotency(any(),any(),any(),any(),any(),any())).thenReturn(1);
    when(plans.save(any(FollowUpPlan.class))).thenReturn(true);when(plans.updateById(any(FollowUpPlan.class))).thenReturn(true);
  }
  FollowUpPlanDTO command(long version) {return new FollowUpPlanDTO(now.plusSeconds(60),now.plusSeconds(1860),"PHONE","双方确认电话沟通",version);}
  @Test void persistsConfirmedTimeWithoutCreatingWorkflowFacts() {
    var response=manager.create(app.getId(),command(3),key);
    assertThat(response.getStartsAt()).isEqualTo(now.plusSeconds(60));assertThat(response.getStatus()).isEqualTo("SCHEDULED");
    verify(plans).save(any(FollowUpPlan.class));verify(apps,never()).nextActionResponse(any(),any(),any(),anyLong());
    verify(lifecycle).audit(any(),any(),any(),any(),any(),any());
    verify(idempotency,never()).findIdempotencyForUpdate(any(),any());
  }
  @Test void retriesCreationUsingSamePersistedPlanWithoutDuplicateAudit() {
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "历史回放时黑名单已生效"))
        .when(blacklist).requireRecruitmentAllowed(any());
    var command=command(3);var replay=new IdempotencyRecord();replay.setTargetId(plan.getId());replay.setActionCode("FOLLOW_UP_PLAN_CREATE");
    replay.setRequestFingerprint(Sha256Utils.fingerprintObjects(31,app.getId(),command.getStartsAt(),command.getEndsAt(),command.getChannel(),command.getNote(),command.getVersion()));
    when(idempotency.findIdempotency(actor,key)).thenReturn(replay);
    assertThat(manager.create(app.getId(),command,key).getId()).isEqualTo(plan.getId());
    verify(plans,never()).save(any(FollowUpPlan.class));verifyNoInteractions(lifecycle);
    verify(idempotency,never()).findIdempotencyForUpdate(any(),any());
  }
  @Test void refusesDifferentPayloadForSameIdempotencyKey() {
    var replay=new IdempotencyRecord();replay.setActionCode("FOLLOW_UP_PLAN_CREATE");replay.setRequestFingerprint("different");
    when(idempotency.findIdempotency(actor,key)).thenReturn(replay);
    assertThatThrownBy(() -> manager.create(app.getId(),command(3),key)).isInstanceOf(PlatformException.class);
    verify(plans,never()).save(any(FollowUpPlan.class));
  }
  @Test void rejectsStaleVersionAndPlanFromAnotherApplication() {
    assertThatThrownBy(() -> manager.update(app.getId(),plan.getId(),command(1))).isInstanceOf(PlatformException.class);
    plan.setApplicationId("OTHER");assertThatThrownBy(() -> manager.update(app.getId(),plan.getId(),command(2))).isInstanceOf(PlatformException.class);
    verify(plans,never()).updateById(any(FollowUpPlan.class));
  }
  @Test void reschedulesWithAuditAndStopsWhenConcurrentUpdateLoses() {
    when(plans.updateById(any(FollowUpPlan.class))).thenReturn(false);
    assertThatThrownBy(() -> manager.update(app.getId(),plan.getId(),command(2))).isInstanceOf(PlatformException.class);
    verifyNoInteractions(lifecycle);
  }
  @Test void completesWithVisibleResultButDoesNotAdvanceWorkflow() {
    var result=manager.finish(app.getId(),plan.getId(),2,"已电话沟通，候选人希望下周面试",true);
    assertThat(result.getStatus()).isEqualTo("COMPLETED");assertThat(result.getResultReason()).contains("下周面试");
    verify(apps,never()).nextActionResponse(any(),any(),any(),anyLong());
  }
  @Test void rejectsFutureCompletionAndAllowsCancellation() {
    plan.setStartsAt(now.plusSeconds(3600));
    assertThatThrownBy(() -> manager.finish(app.getId(),plan.getId(),2,"尚未沟通",true)).isInstanceOf(PlatformException.class);
    assertThat(manager.finish(app.getId(),plan.getId(),2,"双方取消预约",false).getStatus()).isEqualTo("CANCELLED");
  }
  @Test void cancellingUsesClosingPermissionInsteadOfBlacklistBlockingNewContact() {
    doThrow(PlatformExceptions.forbidden("COMPANY_BLACKLISTED","禁止继续联系")).when(apps).requireLockedFollowUpApplication(app.getId());
    assertThat(manager.finish(app.getId(),plan.getId(),2,"停止联系并取消预约",false).getStatus()).isEqualTo("CANCELLED");
    verify(apps).requireLockedFollowUpCancellation(app.getId());
    verify(apps,never()).requireLockedFollowUpApplication(anyString());
  }
  @Test void requiresApplicationProcessingPermissionBeforeWriting() {
    doThrow(PlatformExceptions.forbidden("DENIED","无处理权限")).when(apps).requireLockedFollowUpApplication(app.getId());
    assertThatThrownBy(() -> manager.create(app.getId(),command(3),key)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(plans,idempotency,persistence,lifecycle);
  }
  @Test void calendarFiltersPermissionBeforeReturningOrPaginatingPlans() {
    ReflectionTestUtils.setField(manager,"cursorCodec",new SignedCursorCodec("follow-up-plan-test-signing-key-32bytes"));
    var hidden=new FollowUpPlan();hidden.setId("H".repeat(26));hidden.setApplicationId("X".repeat(26));hidden.setStartsAt(now);
    when(plans.findRange(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),anyInt())).thenReturn(List.of(hidden,plan));
    when(persistence.listByIds(any())).thenReturn(List.of(app));
    when(apps.visibleFollowUpApplicationIds(any())).thenReturn(Set.of(app.getId()));
    when(apps.requireFollowUpApplications(List.of(app.getId()))).thenReturn(Map.of(app.getId(), app));
    var page=manager.list(now.minusSeconds(3600),now.plusSeconds(3600),null,null,null,null,null,null,1);
    assertThat(page.getItems()).extracting(value -> value.getId()).containsExactly(plan.getId());
    assertThat(page.isHasMore()).isFalse();
    verify(apps,never()).requireFollowUpApplication(hidden.getApplicationId());
  }
  @Test void validatesIntervalAndChannel() {
    assertThatThrownBy(() -> FollowUpPlanApplicationManager.validate(new FollowUpPlanDTO(now,now,"PHONE","沟通",0))).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> FollowUpPlanApplicationManager.validate(new FollowUpPlanDTO(now,now.plusSeconds(60),"INVALID","沟通",0))).isInstanceOf(PlatformException.class);
  }

  @Test void completesWithOneSynchronizationAndReplaysBeforeWritingAgain() {
    plan.setChannel("PHONE");
    var result=manager.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,key);
    assertThat(result.getResultReason()).isEqualTo("未填写沟通备注");
    verify(apps).recordPlannedCommunication(app.getId(),"PHONE","未填写沟通备注","CONTINUE",null,null,null);
    verify(plans,times(1)).updateById(any(FollowUpPlan.class));
    verify(lifecycle,times(1)).audit(any(),any(),any(),any(),any(),any());
    verify(blacklist,times(2)).requireRecruitmentAllowed(app.getCandidateId());
    clearInvocations(apps,plans,lifecycle,blacklist);
    when(idempotency.findIdempotency(actor,key)).thenReturn(completionReplay());
    assertThat(manager.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,key).getStatus()).isEqualTo("COMPLETED");
    verify(apps,never()).recordPlannedCommunication(any(),any(),any(),any(),any(),any(),any());
    verify(plans,never()).updateById(any(FollowUpPlan.class));
    verify(lifecycle,never()).audit(any(),any(),any(),any(),any(),any());
    verify(idempotency,never()).findIdempotencyForUpdate(any(),any());
    verify(blacklist,never()).requireRecruitmentAllowed(any());
  }

  @ParameterizedTest @ValueSource(strings={"CREATE","UPDATE","COMPLETE"})
  void activeCompanyBlacklistBlocksNewContactPlansAndCompletion(String action) {
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(app.getCandidateId());
    assertThatThrownBy(() -> {
      switch(action) {
        case "CREATE" -> manager.create(app.getId(),command(3),key);
        case "UPDATE" -> manager.update(app.getId(),plan.getId(),command(2));
        case "COMPLETE" -> manager.complete(app.getId(),plan.getId(),2,"实际联系","CONTINUE","等待确认",null,null,"blocked-complete");
        default -> throw new AssertionError(action);
      }
    }).isInstanceOfSatisfying(PlatformException.class,
        error -> assertThat(error.code()).isEqualTo("COMPANY_BLACKLIST_BLOCKED"));
    verify(plans,never()).save(any(FollowUpPlan.class));
    verify(plans,never()).updateById(any(FollowUpPlan.class));
    verifyNoInteractions(lifecycle);
    verify(apps,never()).recordPlannedCommunication(any(),any(),any(),any(),any(),any(),any());
  }

  @Test void activeCompanyBlacklistStillAllowsPlanCancellation() {
    doThrow(PlatformExceptions.conflict("COMPANY_BLACKLIST_BLOCKED", "有效公司黑名单"))
        .when(blacklist).requireRecruitmentAllowed(any());
    assertThat(manager.finish(app.getId(),plan.getId(),2,"取消招聘联系",false).getStatus()).isEqualTo("CANCELLED");
    verify(blacklist,never()).requireRecruitmentAllowed(any());
    verify(plans).updateById(plan);
  }

  private IdempotencyRecord completionReplay() {
    var replay=new IdempotencyRecord(); replay.setActionCode("FOLLOW_UP_PLAN_COMPLETE");
    replay.setTargetId(plan.getId());
    replay.setRequestFingerprint(Sha256Utils.fingerprintObjects(31,app.getId(),plan.getId(),2L,
        null,"CONTINUE",null,null,null));
    return replay;
  }

  @Test void completionReplayStillWorksAfterApplicationStopsAcceptingNewActions() {
    plan.setStatus("COMPLETED"); plan.setVersion(3);
    when(idempotency.findIdempotency(actor,key)).thenReturn(completionReplay());
    doThrow(PlatformExceptions.conflict("APPLICATION_CLOSED","活动已终止"))
        .when(apps).requireLockedFollowUpApplication(app.getId());
    assertThat(manager.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,key).getStatus()).isEqualTo("COMPLETED");
    verify(apps,never()).requireLockedFollowUpApplication(any());
    verify(apps,never()).recordPlannedCommunication(any(),any(),any(),any(),any(),any(),any());
    verifyNoInteractions(lifecycle);
  }

  @Test void completionReplayRejectsRevokedCurrentProcessingAccess() {
    doThrow(PlatformExceptions.forbidden("RECRUITMENT_PROCESSING_FORBIDDEN","已撤销处理权限"))
        .when(apps).requireLockedFollowUpAccess(app.getId());
    assertThatThrownBy(() -> manager.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,key))
        .isInstanceOf(PlatformException.class);
    verifyNoInteractions(idempotency,persistence,plans,lifecycle);
  }

  @ParameterizedTest
  @ValueSource(strings={"result","reason","nextPlan","stepId","stepVersion","version","planId","applicationId"})
  void completionKeyRejectsEveryChangedPayloadField(String field) {
    when(idempotency.findIdempotency(actor,key)).thenReturn(completionReplay());
    String applicationId=field.equals("applicationId") ? "other-app" : app.getId();
    String planId=field.equals("planId") ? "other-plan" : plan.getId();
    assertThatThrownBy(() -> manager.complete(applicationId,planId,field.equals("version") ? 3 : 2,
        field.equals("reason") ? "其他说明" : null,field.equals("result") ? "FOLLOW_UP" : "CONTINUE",
        field.equals("nextPlan") ? "明天联系" : null,field.equals("stepId") ? "step-1" : null,
        field.equals("stepVersion") ? 1L : null,key))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    verify(apps,never()).recordPlannedCommunication(any(),any(),any(),any(),any(),any(),any());
    verifyNoInteractions(lifecycle);
  }

  @Test void anotherCompletionKeyCannotBypassStalePlanVersion() {
    plan.setStatus("COMPLETED");plan.setVersion(3);
    assertThatThrownBy(() -> manager.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,"new-key"))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("FOLLOW_UP_PLAN_CHANGED"));
    verify(apps,never()).recordPlannedCommunication(any(),any(),any(),any(),any(),any(),any());
  }

  @Test void completionRequiresExplicitIdempotencyKey() {
    assertThatThrownBy(() -> manager.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,null))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_KEY_INVALID"));
    verifyNoInteractions(apps,plans,idempotency,persistence);
  }

  @Test void completionReservationConflictUsesCurrentReadBeforeReplaying() {
    when(idempotency.findIdempotencyForUpdate(actor,key)).thenReturn(completionReplay());
    when(persistence.insertIdempotency(any(),any(),any(),any(),any(),any())).thenReturn(0);
    plan.setStatus("COMPLETED");
    assertThat(manager.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,key).getStatus()).isEqualTo("COMPLETED");
    verify(idempotency).findIdempotency(actor,key);
    verify(idempotency).findIdempotencyForUpdate(actor,key);
    verify(apps,never()).recordPlannedCommunication(any(),any(),any(),any(),any(),any(),any());
  }

  @Test void communicationFailureRollsBackCompletionReservationTransaction() {
    plan.setChannel("PHONE");
    var transactions=mock(PlatformTransactionManager.class);
    var transaction=new SimpleTransactionStatus();
    when(transactions.getTransaction(any())).thenReturn(transaction);
    var interceptor=new TransactionInterceptor();
    interceptor.setTransactionManager(transactions);
    interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
    var factory=new ProxyFactory(manager);factory.addAdvice(interceptor);
    var proxy=(FollowUpPlanApplicationManager)factory.getProxy();
    var failure=new IllegalStateException("synthetic communication persistence failure");
    doThrow(failure).when(apps).recordPlannedCommunication(any(),any(),any(),any(),any(),any(),any());

    assertThatThrownBy(() -> proxy.complete(app.getId(),plan.getId(),2,null,"CONTINUE",null,null,null,key))
        .isSameAs(failure);

    verify(persistence).insertIdempotency(eq(actor),eq(key),eq("FOLLOW_UP_PLAN_COMPLETE"),any(),eq(plan.getId()),eq(now));
    verify(transactions).rollback(transaction);
    verify(transactions,never()).commit(any());
    verify(plans,never()).updateById(any(FollowUpPlan.class));
    verifyNoInteractions(lifecycle);
  }
  @Test void supplementaryPlanNoteAndCancelReasonAreOptional() {
    assertThat(manager.create(app.getId(),new FollowUpPlanDTO(now,now.plusSeconds(60),"PHONE",null,3),key).getNote()).isEmpty();
    verify(lifecycle).audit(any(),any(),eq("CREATED"),eq(""),any(),any());
    assertThat(manager.finish(app.getId(),plan.getId(),2,null,false).getStatus()).isEqualTo("CANCELLED");
  }

  @Test void appointmentHistoryExposesSafeSnapshotsAndRejectsRevokedAccessBeforeReading() {
    var histories=mock(FollowUpPlanHistoryPersistenceService.class);
    ReflectionTestUtils.setField(manager,"histories",histories);
    ReflectionTestUtils.setField(manager,"cursorCodec",new SignedCursorCodec("follow-up-plan-test-signing-key-32bytes"));
    var record=new FollowUpPlanHistory();record.setId("H".repeat(26));record.setPlanId(plan.getId());record.setAction("CREATED");
    record.setAfterSnapshot(JsonText.write(plan));record.setCreateTime(now);record.setCreateId(actor);
    when(histories.findHistory(app.getId(),null,null,51)).thenReturn(List.of(record));
    var page=manager.history(app.getId(),null,50);
    assertThat(page.getItems()).hasSize(1);
    assertThat(page.getItems().getFirst().getBefore()).isNull();
    assertThat(page.getItems().getFirst().getAfter().getStartsAt()).isEqualTo(plan.getStartsAt());
    assertThat(JsonText.write(page)).doesNotContain("createId","afterSnapshot","applicationId");
    doThrow(PlatformExceptions.forbidden("DENIED","权限已变化")).when(apps).requireFollowUpApplication(app.getId());
    assertThatThrownBy(() -> manager.history(app.getId(),null,50)).isInstanceOf(PlatformException.class);
    verify(histories).findHistory(app.getId(),null,null,51);
  }

  @Test void pendingCalendarStatusIsPassedToPersistenceBeforePaginationAndInvalidStatusRejected() {
    ReflectionTestUtils.setField(manager,"cursorCodec",new SignedCursorCodec("follow-up-plan-test-signing-key-32bytes"));
    when(plans.findRange(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),anyInt())).thenReturn(List.of());
    manager.list(now.minusSeconds(60),now.plusSeconds(60),null,null,null,null,null,"SCHEDULED",null,50);
    verify(plans).findRange(now.minusSeconds(60),now.plusSeconds(60),null,null,null,null,null,"SCHEDULED",null,null,200);
    assertThatThrownBy(() -> manager.list(now,now.plusSeconds(60),null,null,null,null,null,"INVALID",null,50))
        .isInstanceOfSatisfying(PlatformException.class,e -> assertThat(e.code()).isEqualTo("FOLLOW_UP_STATUS_INVALID"));
  }
  @ParameterizedTest
  @ValueSource(strings={"FOLLOW_UP_PLAN_CREATE","FOLLOW_UP_PLAN_COMPLETE"})
  void creationReservationCollisionRejectsCommittedKeyForAnotherApplication(String winningAction) {
    var command=command(3);
    String otherApplicationId="B".repeat(26);
    String otherPlanId="Q".repeat(26);
    var winner=new IdempotencyRecord();
    winner.setActorUserId(actor);winner.setIdempotencyKey(key);
    winner.setActionCode(winningAction);winner.setTargetId(otherPlanId);
    winner.setRequestFingerprint("FOLLOW_UP_PLAN_CREATE".equals(winningAction)
        ? Sha256Utils.fingerprintObjects(31,otherApplicationId,command.getStartsAt(),command.getEndsAt(),
            command.getChannel(),command.getNote(),command.getVersion())
        : Sha256Utils.fingerprintObjects(31,otherApplicationId,otherPlanId,2L,
            null,"CONTINUE",null,null,null));
    when(idempotency.findIdempotencyForUpdate(actor,key)).thenReturn(winner);
    when(persistence.insertIdempotency(any(),any(),any(),any(),any(),any())).thenReturn(0);

    assertThatThrownBy(() -> manager.create(app.getId(),command,key))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));

    verify(idempotency).findIdempotency(actor,key);
    verify(idempotency).findIdempotencyForUpdate(actor,key);
    verify(plans,never()).save(any(FollowUpPlan.class));
    verifyNoInteractions(lifecycle);
  }

  @Test void creationReservationConflictWithoutCurrentWinnerKeepsReservationFailure() {
    when(persistence.insertIdempotency(any(),any(),any(),any(),any(),any())).thenReturn(0);

    assertThatThrownBy(() -> manager.create(app.getId(),command(3),key))
        .isInstanceOfSatisfying(PlatformException.class,ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_RESERVATION_FAILED"));

    verify(plans,never()).save(any(FollowUpPlan.class));
    verifyNoInteractions(lifecycle);
  }

}
