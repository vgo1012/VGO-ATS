package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowTemplateEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.WorkflowTemplatePersistenceService;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecruitmentReadinessTest {
  private final DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
  private final DemandCatalogPersistenceService catalog = mock(DemandCatalogPersistenceService.class);
  private final WorkflowTemplatePersistenceService workflows = mock(WorkflowTemplatePersistenceService.class);
  private final DemandCollaboratorPersistenceService collaborators = mock(DemandCollaboratorPersistenceService.class);
  private final CurrentActorSnapshot actor = mock(CurrentActorSnapshot.class);
  private final JdEntity jd = new JdEntity();
  private final DemandEntity demand = new DemandEntity();
  private final JdEntityPersistenceService jds = mock(JdEntityPersistenceService.class);
  private final DemandWorkflowStepPersistenceService steps = mock(DemandWorkflowStepPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CursorCodec cursors = mock(CursorCodec.class);

  @BeforeEach void setUp() {
    var versions = mock(JdVersionPersistenceService.class);
    var demands = mock(DemandEntityPersistenceService.class);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", catalog);
    ReflectionTestUtils.setField(manager, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "demandEntityPersistenceService", demands);
    ReflectionTestUtils.setField(manager, "demandCollaboratorPersistenceService", collaborators);
    ReflectionTestUtils.setField(manager, "workflowTemplatePersistenceService", workflows);
    ReflectionTestUtils.setField(manager, "demandWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(manager, "cursorCodec", cursors);
    when(cursors.encode(any(), any())).thenReturn("next-cursor");
    ReflectionTestUtils.setField(manager, "jdEntityPersistenceService", jds);
    ReflectionTestUtils.setField(manager, "demandIdempotencyPersistenceService", mock(DemandIdempotencyPersistenceService.class));
    ReflectionTestUtils.setField(manager, "demandAuditEventPersistenceService", mock(DemandAuditEventPersistenceService.class));
    ReflectionTestUtils.setField(manager, "ids", mock(PlatformIdGenerator.class));
    ReflectionTestUtils.setField(manager, "clock", Clock.systemUTC());
    when(authorization.currentActor()).thenReturn(actor);
    when(actor.getUserId()).thenReturn("owner");
    jd.setId("jd"); jd.setOwnerUserId("owner"); jd.setDemandId("demand"); jd.setRecruitmentType("INTERNAL");
    demand.setId("demand"); demand.setOwnerUserId("owner"); demand.setRecruitmentType("INTERNAL");
    var version = new JdVersionEntity(); version.setId("version"); version.setJdId("jd"); version.setTitle("验收岗位");
    when(catalog.findJd("jd")).thenReturn(jd);
    when(versions.findJdVersion("version")).thenReturn(version);
    when(demands.findDemand("demand")).thenReturn(demand);
    when(collaborators.findCollaboratorIds("demand")).thenReturn(List.of("collaborator"));
    when(workflows.findWorkflowTemplates(eq("version"), any(), any(), anyInt())).thenReturn(List.of());
  }

  @Test void readinessBatchesDraftStepsAcrossPagesAndKeepsActiveDetails() {
    var first = IntStream.range(0, 100).mapToObj(i -> template("draft-" + i, "DRAFT")).toList();
    var last = template("last", "DRAFT");
    var active = template("active", "ACTIVE");
    when(workflows.findActiveWorkflow("version")).thenReturn(active);
    when(workflows.findWorkflowTemplates(eq("version"), any(), any(), eq(100)))
        .thenReturn(first, List.of(active, template("old", "SUPERSEDED"), last));
    var firstSteps = first.stream().map(t -> step(t.getId(), 1)).toList();
    var lastStep = step("last", 1);
    var activeStep = step("active", 1);
    when(steps.findWorkflowStepsByTemplateIds(first.stream().map(WorkflowTemplateEntity::getId).toList()))
        .thenReturn(firstSteps);
    when(steps.findWorkflowStepsByTemplateIds(List.of("last"))).thenReturn(List.of(lastStep));
    when(steps.findWorkflowSteps("active")).thenReturn(List.of(activeStep));

    var ready = manager.recruitmentReadiness("version");

    assertThat(ready.getDraftWorkflows()).hasSize(101);
    for (int i = 0; i < 100; i++) {
      assertThat(ready.getDraftWorkflows().get(i).getTemplate()).isSameAs(first.get(i));
      assertThat(ready.getDraftWorkflows().get(i).getSteps()).containsExactly(firstSteps.get(i));
    }
    assertThat(ready.getDraftWorkflows().getLast().getSteps()).containsExactly(lastStep);
    assertThat(ready.getActiveWorkflow().getSteps()).containsExactly(activeStep);
    verify(steps, times(2)).findWorkflowStepsByTemplateIds(any());
    verify(steps, times(1)).findWorkflowSteps(any());
    verify(workflows).findWorkflowTemplates("version", first.getLast().getCreateTime(), "draft-99", 100);
  }

  @Test void templatePageBatchesOnlyReturnedRowsAndPreservesEmptyAndOrderedSteps() {
    var first = template("first", "DRAFT");
    var empty = template("empty", "DRAFT");
    var next = template("next", "ACTIVE");
    when(workflows.findWorkflowTemplates("version", null, null, 3))
        .thenReturn(List.of(first, empty, next));
    var decision = step("first", 1);
    var offer = step("first", 2);
    when(steps.findWorkflowStepsByTemplateIds(List.of("first", "empty")))
        .thenReturn(List.of(decision, offer));

    var page = manager.listWorkflowTemplates("version", null, 2);

    assertThat(page.getItems()).extracting(item -> item.getTemplate().getId()).containsExactly("first", "empty");
    assertThat(page.getItems().getFirst().getSteps()).containsExactly(decision, offer);
    assertThat(page.getItems().getLast().getSteps()).isEmpty();
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getNextCursor()).isEqualTo("next-cursor");
    verify(cursors).encode(any(), eq(List.of(empty.getCreateTime().toString(), "empty")));
    verify(steps).findWorkflowStepsByTemplateIds(List.of("first", "empty"));
    verify(steps, never()).findWorkflowSteps(any());
  }

  @Test void emptyTemplateResultsDoNotLoadSteps() {
    assertThat(manager.listWorkflowTemplates("version", null, 10).getItems()).isEmpty();
    assertThat(manager.recruitmentReadiness("version").getDraftWorkflows()).isEmpty();
    verify(steps, never()).findWorkflowStepsByTemplateIds(any());
    verify(steps, never()).findWorkflowSteps(any());
  }

  @Test void deniedAccessDoesNotReadTemplatesOrSteps() {
    doThrow(PlatformExceptions.forbidden("TEST_FORBIDDEN", "无权访问"))
        .when(authorization).requireAccess(any(), any());

    assertThatThrownBy(() -> manager.listWorkflowTemplates("version", null, 10))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.recruitmentReadiness("version"))
        .isInstanceOf(PlatformException.class);
    verify(workflows, never()).findWorkflowTemplates(any(), any(), any(), anyInt());
    verify(workflows, never()).findActiveWorkflow(any());
    verify(steps, never()).findWorkflowStepsByTemplateIds(any());
    verify(steps, never()).findWorkflowSteps(any());
  }

  private static WorkflowTemplateEntity template(String id, String status) {
    var template = new WorkflowTemplateEntity();
    template.setId(id);
    template.setStatus(status);
    template.setCreateTime(Instant.parse("2026-09-12T00:00:00Z"));
    return template;
  }

  private static WorkflowStepEntity step(String templateId, int sequence) {
    var step = new WorkflowStepEntity();
    step.setId(templateId + "-" + sequence);
    step.setTemplateVersionId(templateId);
    step.setSequenceNo(sequence);
    return step;
  }

  @Test void sourceOwnerCanPrepareBindingAndManagersCannotProcessSomeoneElsesJd() {
    jd.setDemandId(null);
    var source = manager.recruitmentReadiness("version");
    assertThat(source.isCanProcess()).isTrue();
    assertThat(source.getDemandId()).isNull();
    assertThat(source.getJdTitle()).isEqualTo("验收岗位");
    when(actor.getUserId()).thenReturn("admin");
    assertThat(manager.recruitmentReadiness("version").isCanProcess()).isFalse();
    jd.setDemandId("demand");
    assertThatThrownBy(() -> manager.activateStandardWorkflow("version", "明确启用", "key"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("JD_WORKFLOW_FORBIDDEN"));
  }

  @Test void readinessExposesHeadcountWithoutChangingProcessingPermission() {
    jd.setHeadcount(2);
    when(catalog.countOccupiedHeadcount("jd")).thenReturn(2);

    var ready = manager.recruitmentReadiness("version");

    assertThat(ready.getHeadcount()).isEqualTo(2);
    assertThat(ready.getOccupiedHeadcount()).isEqualTo(2);
    assertThat(ready.getRemainingHeadcount()).isZero();
    assertThat(ready.isCanProcess()).isTrue();
  }

  @Test void draftsAreNotOverwrittenAndExistingActiveTemplateIsPreserved() {
    when(catalog.findMaxWorkflowVersionNo("version")).thenReturn(1);
    assertThatThrownBy(() -> manager.activateStandardWorkflow("version", "明确启用", "key"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("WORKFLOW_CONFIGURATION_EXISTS"));
    var active = new WorkflowTemplateEntity(); active.setId("configured-template");
    when(workflows.findActiveWorkflow("version")).thenReturn(active);
    assertThat(manager.activateStandardWorkflow("version", "明确启用", "key").getActiveWorkflowId()).isEqualTo("configured-template");
    verify(workflows, never()).supersedeActiveWorkflow(any(), any(), any());
  }

  @Test void collaboratorCanPrepareExternalStandardWithoutMandatoryAi() {
    when(actor.getUserId()).thenReturn("collaborator");
    demand.setRecruitmentType("EXTERNAL");
    jd.setRecruitmentType("EXTERNAL");
    var ready = manager.recruitmentReadiness("version");
    assertThat(ready.isCanProcess()).isTrue();
    assertThat(ready.getStandardSteps()).containsExactly("CANDIDATE_COMMUNICATION", "DECISION", "OFFER");
  }

  @Test void legacyPublicFlagDoesNotGrantProcessingOrWorkflowManagement() {
    jd.setDemandId(null); jd.setPublicVisible(true);
    when(actor.getUserId()).thenReturn("another-hr");
    var ready = manager.recruitmentReadiness("version");
    assertThat(ready.isCanProcess()).isFalse();
    assertThat(ready.isCanManageWorkflow()).isFalse();
    assertThat(ready.getDemandId()).isNull();
    assertThat(ready.getRecruitmentType()).isEqualTo("INTERNAL");
  }

  @Test void deletedJdRemainsReadableButCannotStartAnotherRecruitment() {
    jd.setDemandId(null); jd.setDeleted(true);
    var ready = manager.recruitmentReadiness("version");
    assertThat(ready.isCanProcess()).isFalse();
    assertThat(ready.isCanManageWorkflow()).isFalse();
    assertThatThrownBy(() -> manager.activateStandardWorkflow("version", "使用标准流程", "deleted"))
        .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo("JD_DELETED"));
  }

  @Test void sourceBindingPreservesJdAndVersionAndRejectsOccupiedOrUnauthorizedTargets() {
    when(actor.isSuperAdmin()).thenReturn(true);
    jd.setDemandId(null); jd.setCurrentVersionNo(3);
    when(jds.countJdsForDemand("demand")).thenReturn(1);
    assertThatThrownBy(() -> manager.bindSourceJdToDemand("jd", "demand", "关联正式需求", "binding-key"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("DEMAND_JD_ALREADY_EXISTS"));
    verify(jds, never()).bindDemand(any(), any(), any(), any());
    when(jds.countJdsForDemand("demand")).thenReturn(0);
    when(jds.bindDemand(eq("jd"), eq("demand"), eq("owner"), any())).thenAnswer(invocation -> {
      jd.setDemandId("demand"); return 1;
    });
    var bound = manager.bindSourceJdToDemand("jd", "demand", "关联正式需求", "binding-key");
    assertThat(bound.getId()).isEqualTo("jd");
    assertThat(bound.getCurrentVersionNo()).isEqualTo(3);
    assertThat(bound.getDemandId()).isEqualTo("demand");
    when(actor.getUserId()).thenReturn("admin");
    when(actor.isSuperAdmin()).thenReturn(false);
    assertThatThrownBy(() -> manager.bindSourceJdToDemand("jd", "demand", "无归属权限", "other-key"))
        .isInstanceOfSatisfying(PlatformException.class, error -> assertThat(error.code()).isEqualTo("ADMIN_REQUIRED"));
  }
}
