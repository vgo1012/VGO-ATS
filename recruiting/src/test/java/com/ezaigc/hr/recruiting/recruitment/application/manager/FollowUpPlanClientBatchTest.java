package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdClientSummary;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.FollowUpPlanAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanPersistenceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class FollowUpPlanClientBatchTest {
  private static final Instant START=Instant.parse("2026-09-12T00:00:00Z");
  private final FollowUpPlanApplicationManager manager=new FollowUpPlanApplicationManager();
  private final RecruitmentApplicationManager applications=mock(RecruitmentApplicationManager.class);
  private final FollowUpPlanPersistenceService plans=mock(FollowUpPlanPersistenceService.class);
  private final ApplicationPersistenceService persistence=mock(ApplicationPersistenceService.class);
  private final DemandQueryService demands=mock(DemandQueryService.class);

  @BeforeEach void setUp() {
    var authorization=mock(AuthorizationService.class);
    var actor=new CurrentActorSnapshot(id("U",1),"HR",null,id("D",1),Set.of(),Set.of(),Set.of(),Set.of(),Set.of("recruitment.manage"),false,false);
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager,"authorization",authorization);
    ReflectionTestUtils.setField(manager,"applications",applications);
    ReflectionTestUtils.setField(manager,"plans",plans);
    ReflectionTestUtils.setField(manager,"applicationPersistence",persistence);
    ReflectionTestUtils.setField(manager,"demands",demands);
    ReflectionTestUtils.setField(manager,"assembler",new FollowUpPlanAssembler());
    ReflectionTestUtils.setField(manager,"cursorCodec",new SignedCursorCodec("follow-up-client-batch-key-32bytes"));
    when(persistence.listByIds(any())).thenReturn(List.of());
    when(demands.requireJdVersion(any())).thenReturn(new JdVersionSnapshot());
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 100})
  void returnedPageBatchesDistinctVersionsAndRechecksApplicationsInOneBatch(int applicationCount) {
    List<Application> apps=IntStream.range(0,applicationCount).mapToObj(index -> application(index,index%2)).toList();
    when(applications.requireFollowUpApplications(apps.stream().map(Application::getId).toList()))
        .thenReturn(apps.stream().collect(Collectors.toMap(Application::getId, app -> app)));
    List<FollowUpPlan> returned=IntStream.range(0,100)
        .mapToObj(index -> plan(index,apps.get(index%applicationCount).getId())).toList();
    FollowUpPlan hidden=plan(100,id("A",100));
    FollowUpPlan ahead=plan(101,id("A",101));
    List<FollowUpPlan> scanned=new ArrayList<>();scanned.add(hidden);scanned.addAll(returned);scanned.add(ahead);
    Set<String> visible=apps.stream().map(Application::getId).collect(Collectors.toSet());
    visible.add(ahead.getApplicationId());
    when(applications.visibleFollowUpApplicationIds(any())).thenReturn(visible);
    when(plans.findRange(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),anyInt()))
        .thenReturn(scanned);
    when(demands.clientSummariesForVersions(Set.of(id("V",0),id("V",1))))
        .thenReturn(Map.of(id("V",0),new JdClientSummary(id("C",1),"历史客户"),
            id("V",1),new JdClientSummary(null,null)));

    var page=manager.list(START,START.plusSeconds(86400),null,null,null,null,null,null,100);

    assertThat(page.getItems()).extracting(value -> value.getId())
        .containsExactlyElementsOf(returned.stream().map(FollowUpPlan::getId).toList());
    assertThat(page.isHasMore()).isTrue();assertThat(page.getNextCursor()).isNotBlank();
    assertThat(page.getItems().getFirst().getClientId()).isEqualTo(id("C",1));
    assertThat(page.getItems().getFirst().getClientName()).isEqualTo("历史客户");
    assertThat(page.getItems().get(1).getClientId()).isNull();
    assertThat(page.getItems().get(1).getClientName()).isNull();
    verify(applications).requireFollowUpApplications(apps.stream().map(Application::getId).toList());
    verify(applications,never()).requireFollowUpApplication(any());
    verify(demands).clientSummariesForVersions(Set.of(id("V",0),id("V",1)));
    verify(demands,never()).requireJdVersion(any());
  }

  @Test void emptyPageDoesNotLoadClientSummaries() {
    when(plans.findRange(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),anyInt()))
        .thenReturn(List.of());
    var page=manager.list(START,START.plusSeconds(86400),null,null,null,null,null,null,100);
    assertThat(page.getItems()).isEmpty();assertThat(page.isHasMore()).isFalse();
    verifyNoInteractions(demands);
    verify(applications,never()).requireFollowUpApplications(any());
  }

  @Test void revokedReturnedApplicationAccessStopsBeforeCustomerLookup() {
    var app=application(0,0);var plan=plan(0,app.getId());
    when(plans.findRange(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),anyInt()))
        .thenReturn(List.of(plan));
    when(applications.visibleFollowUpApplicationIds(any())).thenReturn(Set.of(app.getId()));
    var denied=PlatformExceptions.forbidden("FORBIDDEN","当前权限已撤销");
    doThrow(denied).when(applications).requireFollowUpApplications(List.of(app.getId()));
    assertThatThrownBy(() -> manager.list(START,START.plusSeconds(86400),null,null,null,null,null,null,100))
        .isSameAs(denied);
    verifyNoInteractions(demands);
  }

  private static String id(String prefix,int number) {return prefix+String.format("%025d",number);}
  private static Application application(int index,int version) {
    var app=new Application();app.setId(id("A",index));app.setJdId(id("J",version));
    app.setJdVersionId(id("V",version));app.setCandidateId(id("N",index));
    app.setOwnerUserId(id("U",1));app.setRecruitmentType("INTERNAL");return app;
  }
  private static FollowUpPlan plan(int index,String applicationId) {
    var plan=new FollowUpPlan();plan.setId(id("P",index));plan.setApplicationId(applicationId);
    plan.setStartsAt(START.plusSeconds(index));plan.setEndsAt(START.plusSeconds(index+3600));
    plan.setStatus("SCHEDULED");plan.setVersion(1);return plan;
  }
}
