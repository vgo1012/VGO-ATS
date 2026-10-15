package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.recruiting.demand.domain.entity.IdempotencyRecordEntity;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.identity.api.IdentityDirectoryService.UserReference;
import com.ezaigc.hr.recruiting.demand.domain.dto.CreateJdVersionDTO;
import com.ezaigc.hr.recruiting.demand.application.assembler.DemandCatalogAssembler;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdCodeOptionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdSourceEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandIdempotencyPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdCodeOptionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdSourcePersistenceService;
import com.ezaigc.hr.recruiting.demand.service.WorkflowTemplatePersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandWorkflowStepPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.validator.WorkflowTemplateValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;

class SourceJdDraftTest {
  private final DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
  private final CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
  private final CandidateScreeningStateQuery screening = mock(CandidateScreeningStateQuery.class);
  private final ApplicationPersistenceService applications = mock(ApplicationPersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final IdentityDirectoryService identityDirectory = mock(IdentityDirectoryService.class);
  private final JdEntityPersistenceService jds = mock(JdEntityPersistenceService.class);
  private final JdCodeOptionPersistenceService codeOptions = mock(JdCodeOptionPersistenceService.class);
  private final JdVersionPersistenceService versions = mock(JdVersionPersistenceService.class);
  private final DemandCatalogPersistenceService catalog = mock(DemandCatalogPersistenceService.class);
  private final DemandEntityPersistenceService demands = mock(DemandEntityPersistenceService.class);
  private final JdVersionEntity version = new JdVersionEntity();
  private final WorkflowTemplatePersistenceService workflows = mock(WorkflowTemplatePersistenceService.class);
  private final DemandWorkflowStepPersistenceService steps = mock(DemandWorkflowStepPersistenceService.class);
  private final JdSourcePersistenceService sources = mock(JdSourcePersistenceService.class);
  private final DemandIdempotencyPersistenceService idempotency = mock(DemandIdempotencyPersistenceService.class);
  private final DemandOwnershipHistoryPersistenceService ownershipHistory =
      mock(DemandOwnershipHistoryPersistenceService.class);
  private final DemandAuditEventPersistenceService auditEvents = mock(DemandAuditEventPersistenceService.class);

  @BeforeEach
  void setup() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot("H".repeat(26), "测试HR",
        "hr@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("J".repeat(26), "V".repeat(26), "A".repeat(26));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", library);
    ReflectionTestUtils.setField(manager, "candidateScreening", screening);
    ReflectionTestUtils.setField(manager, "applications", applications);
    when(versions.findVersionIds(anyString())).thenReturn(List.of("old-version", "current-version"));
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
    ReflectionTestUtils.setField(manager, "identityDirectory", identityDirectory);
    ReflectionTestUtils.setField(manager, "jdEntityPersistenceService", jds);
    ReflectionTestUtils.setField(manager, "jdCodeOptionPersistenceService", codeOptions);
    ReflectionTestUtils.setField(manager, "jdVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "persistence", catalog);
    ReflectionTestUtils.setField(manager, "demandEntityPersistenceService", demands);
    ReflectionTestUtils.setField(manager, "workflowTemplatePersistenceService", workflows);
    ReflectionTestUtils.setField(manager, "demandWorkflowStepPersistenceService", steps);
    ReflectionTestUtils.setField(manager, "workflowValidator", new WorkflowTemplateValidator());
    ReflectionTestUtils.setField(manager, "jdSourcePersistenceService", sources);
    ReflectionTestUtils.setField(manager, "demandIdempotencyPersistenceService",
        idempotency);
    ReflectionTestUtils.setField(manager, "demandOwnershipHistoryPersistenceService",
        ownershipHistory);
    ReflectionTestUtils.setField(manager, "demandAuditEventPersistenceService", auditEvents);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC));
    version.setId("V".repeat(26));
    version.setJdId("J".repeat(26));
    version.setTitle("IT运维工程师");
    version.setRawText("Linux");
    version.setRecruitmentType("INTERNAL");
    when(versions.findJdVersion(version.getId())).thenReturn(version);
    when(versions.findJdVersions(anyString(), any(), any(), anyInt())).thenReturn(List.of(version));
    when(jds.departmentCode(anyString())).thenReturn("01");
    when(jds.nextCode("01", "001", "001")).thenReturn("JD-01001001001");
    var existingOption = new JdCodeOptionEntity(); existingOption.setCode("001");
    when(codeOptions.findCodeOptionByName(anyString(), anyString())).thenReturn(existingOption);
    when(jds.save(any(JdEntity.class))).thenReturn(true);
    when(versions.save(any(JdVersionEntity.class))).thenReturn(true);
    when(workflows.insertWorkflowTemplate(any(), any(), anyInt(), any(), any(), any())).thenReturn(1);
    when(workflows.activateWorkflowTemplate(any(), anyLong(), any(), any())).thenReturn(1);
    when(steps.insertWorkflowSteps(anyList())).thenReturn(true);
    when(sources.save(any(JdSourceEntity.class))).thenReturn(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"createJd", "createSourceJdDraft", "addJdVersion"})
  void numberAllocationAndJdCreationShareAnApplicationWriteTransaction(String methodName)
      throws NoSuchMethodException {
    var parameters = "addJdVersion".equals(methodName)
        ? new Class<?>[] {String.class, CreateJdVersionDTO.class, String.class}
        : new Class<?>[] {CreateJdVersionDTO.class, String.class};
    var transaction = DemandCatalogApplicationManager.class.getMethod(methodName, parameters)
        .getAnnotation(Transactional.class);
    assertThat(transaction).isNotNull();
    assertThat(transaction.readOnly()).isFalse();
    assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
  }

  @Test
  void createsOnlyAnOwnedJdAndVersionWithoutInventingFormalDemandMetadata() {
    var input = command("Linux\r\nGPU"); input.setWorkRegionName("北京");
    var result = manager.createSourceJdDraft(input, "source-create-key");
    assertThat(result.getId()).isEqualTo(version.getId());
    assertThat(result.getRawText()).isEqualTo("Linux\r\nGPU");
    assertThat(result.getSourceType()).isEqualTo("MANUAL");
    assertThat(result.getJdCode()).isEqualTo("JD-01001001001");
    assertThat(new DemandCatalogAssembler().toJdVersionResponse(result).getJdCode()).isEqualTo("JD-01001001001");
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    var titleOrder = inOrder(jds, versions);
    titleOrder.verify(jds).save(any(JdEntity.class));
    titleOrder.verify(versions).save(any(JdVersionEntity.class));
    assertThat(saved.getValue().getCode()).isEqualTo("JD-01001001001");
    assertThat(saved.getValue().getOwnerUserId()).isEqualTo("H".repeat(26));
    assertThat(saved.getValue().getOwnerDepartmentId()).isEqualTo("D".repeat(26));
    assertThat(saved.getValue().getDemandId()).isNull();
    assertThat(saved.getValue().getClientId()).isNull();
    assertThat(saved.getValue().getRecruitmentType()).isEqualTo("INTERNAL");
    assertThat(saved.getValue().getRecruitmentCycleDays()).isEqualTo(45);
    assertThat(new DemandCatalogAssembler().toJdResponse(saved.getValue()).getRecruitmentCycleDays())
        .isEqualTo(45);
    verify(versions).save(result);
    verify(workflows).activateWorkflowTemplate(any(), eq(0L), eq("H".repeat(26)), any());
    verify(demands, never()).findDemand(anyString());
  }

  @Test
  void appliesCurrentPublicSourceAsIndependentOwnedJdWithoutSubmittingTitleReview() {
    var sourceVersion = new JdVersionEntity();
    sourceVersion.setTitle("IT运维工程师"); sourceVersion.setRawText("自己的招聘原文");
    sourceVersion.setId("S".repeat(26)); sourceVersion.setJdId("Q".repeat(26));
    sourceVersion.setVersionNo(2); sourceVersion.setStatus("PUBLISHED");
    var source = ownedJd();
    source.setId(sourceVersion.getJdId()); source.setStatus("ACTIVE"); source.setCurrentVersionNo(2);
    source.setContentShared(true); source.setOwnerDepartmentId("X".repeat(26));
    when(versions.findJdVersion(sourceVersion.getId())).thenReturn(sourceVersion);
    when(catalog.findJdForUpdate(source.getId())).thenReturn(source);
    var input = command("自己的招聘原文");
    input.setWorkRegionName("北京"); input.setAppliedFromJdVersionId(sourceVersion.getId());
    input.setHeadcount(3);

    manager.createJd(input, "apply-source");

    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getId()).isNotEqualTo(source.getId());
    assertThat(saved.getValue().getAppliedFromJdVersionId()).isEqualTo(sourceVersion.getId());
    assertThat(saved.getValue().getOwnerUserId()).isEqualTo("H".repeat(26));
    assertThat(saved.getValue().getOwnerDepartmentId()).isEqualTo("D".repeat(26));
    assertThat(saved.getValue().getHeadcount()).isEqualTo(3);
    assertThat(saved.getValue().isContentShared()).isFalse();
    var order = inOrder(jds, catalog);
    order.verify(jds).lockCodeAllocation();
    order.verify(catalog).findJdForUpdate(source.getId());
  }

  @Test
  void rejectsClosedOrChangedSourceBeforeCreatingCopy() {
    var sourceVersion = new JdVersionEntity();
    sourceVersion.setTitle("IT运维工程师"); sourceVersion.setRawText("自己的招聘原文");
    sourceVersion.setId("S".repeat(26)); sourceVersion.setJdId("Q".repeat(26));
    sourceVersion.setVersionNo(1); sourceVersion.setStatus("PUBLISHED");
    var source = ownedJd();
    source.setId(sourceVersion.getJdId()); source.setStatus("CLOSED"); source.setContentShared(true);
    when(versions.findJdVersion(sourceVersion.getId())).thenReturn(sourceVersion);
    when(catalog.findJdForUpdate(source.getId())).thenReturn(source);
    var input = command("自己的招聘原文");
    input.setWorkRegionName("北京"); input.setAppliedFromJdVersionId(sourceVersion.getId());

    assertThatThrownBy(() -> manager.createJd(input, "closed-source"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_SOURCE_CHANGED"));
    source.setStatus("ACTIVE"); source.setCurrentVersionNo(2);
    assertThatThrownBy(() -> manager.createJd(input, "changed-source"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_SOURCE_CHANGED"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void rejectsPrivateSourceAndForgedOwner() {
    var sourceVersion = new JdVersionEntity();
    sourceVersion.setTitle("IT运维工程师"); sourceVersion.setRawText("自己的招聘原文");
    sourceVersion.setId("S".repeat(26)); sourceVersion.setJdId("Q".repeat(26));
    var source = ownedJd(); source.setId(sourceVersion.getJdId());
    source.setOwnerDepartmentId("X".repeat(26)); source.setCatalogOwnerDepartmentId("X".repeat(26));
    when(versions.findJdVersion(sourceVersion.getId())).thenReturn(sourceVersion);
    when(catalog.findJdForUpdate(source.getId())).thenReturn(source);
    var input = command("自己的招聘原文");
    input.setWorkRegionName("北京"); input.setAppliedFromJdVersionId(sourceVersion.getId());
    assertThatThrownBy(() -> manager.createJd(input, "private-source"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_SOURCE_NOT_FOUND"));
    source.setContentShared(true);
    input.setAppliedFromJdVersionId("F".repeat(26));
    assertThatThrownBy(() -> manager.createJd(input, "forged-version"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_SOURCE_NOT_FOUND"));
    input.setOwnerUserId("X".repeat(26));
    assertThatThrownBy(() -> manager.createJd(input, "forged-owner"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_OWNER_FORBIDDEN"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void applicationRejectsModifiedTitleMissingHeadcountAndDisabledClient() {
    var sourceVersion = new JdVersionEntity();
    sourceVersion.setTitle("IT运维工程师"); sourceVersion.setRawText("自己的招聘原文");
    sourceVersion.setId("S".repeat(26)); sourceVersion.setJdId("Q".repeat(26));
    sourceVersion.setVersionNo(1); sourceVersion.setStatus("PUBLISHED");
    var source = ownedJd(); source.setId(sourceVersion.getJdId()); source.setStatus("ACTIVE"); source.setContentShared(true);
    when(versions.findJdVersion(sourceVersion.getId())).thenReturn(sourceVersion);
    when(catalog.findJdForUpdate(source.getId())).thenReturn(source);
    var input = command("自己的招聘原文");
    input.setWorkRegionName("北京"); input.setAppliedFromJdVersionId(sourceVersion.getId());
    input.setTitle("伪造岗位名");
    assertThatThrownBy(() -> manager.createJd(input, "changed-title"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_TITLE_IMMUTABLE"));

    input.setTitle(source.getTitle()); input.setHeadcount(null);
    assertThatThrownBy(() -> manager.createJd(input, "missing-hc"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_HEADCOUNT_INVALID"));

    input.setHeadcount(2); input.setRecruitmentType("EXTERNAL");
    input.setClientId("C".repeat(26));
    var client = new ClientEntity(); client.setId(input.getClientId()); client.setStatus("DISABLED");
    when(catalog.findClient(client.getId())).thenReturn(client);
    assertThatThrownBy(() -> manager.createJd(input, "disabled-client"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CLIENT_NOT_AVAILABLE"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void automaticNamesReuseDimensionsAndDifferentNamesChangeTheCorrectPrefix() {
    Map<String, JdCodeOptionEntity> dictionary = new HashMap<>();
    Map<String, Integer> pairSequences = new HashMap<>();
    when(codeOptions.findCodeOptionByName(anyString(), anyString())).thenAnswer(call ->
        dictionary.get(call.getArgument(0) + ":" + call.getArgument(1)));
    when(codeOptions.lastOptionCode(anyString())).thenAnswer(call -> dictionary.values().stream()
        .filter(option -> option.getKind().equals(call.getArgument(0)))
        .map(JdCodeOptionEntity::getCode).max(String::compareTo).orElse(null));
    when(codeOptions.insertCodeOption(any())).thenAnswer(call -> {
      JdCodeOptionEntity option = call.getArgument(0);
      dictionary.put(option.getKind() + ":" + option.getName(), option);
      return 1;
    });
    when(jds.nextCode(anyString(), anyString(), anyString())).thenAnswer(call -> {
      String pair = call.getArgument(0, String.class) + call.getArgument(1, String.class)
          + call.getArgument(2, String.class);
      return "JD-" + pair + String.format(Locale.ROOT, "%03d", pairSequences.merge(pair, 1, Integer::sum));
    });

    var first = automaticCommand("  IT运维工程师  ", " 北京 ", "一组");
    assertThat(manager.createJd(first, "auto-one").getJdCode()).isEqualTo("JD-01001001001");
    assertThat(manager.createJd(automaticCommand("IT运维工程师", "北京", "二组"), "auto-two").getJdCode())
        .isEqualTo("JD-01001001002");
    assertThat(manager.createJd(automaticCommand("IT运维工程师", "上海", "三组"), "auto-three").getJdCode())
        .isEqualTo("JD-01001002001");
    assertThat(manager.createJd(automaticCommand("暖通工程师", "北京", "四组"), "auto-four").getJdCode())
        .isEqualTo("JD-01002001001");
    var savedVersions = ArgumentCaptor.forClass(JdVersionEntity.class);
    verify(versions, times(4)).save(savedVersions.capture());
    assertThat(savedVersions.getAllValues()).extracting(JdVersionEntity::getWorkRegionCode)
        .containsExactly("001", "001", "002", "001");
    assertThat(dictionary).hasSize(4);
    verify(codeOptions, times(4)).insertCodeOption(any());
    verify(jds, times(4)).departmentCode("D".repeat(26));
  }

  @Test
  void missingOrOversizedAutomaticInputsCannotFallBackToGlobalSequence() {
    var missing = command("Linux");
    assertThatThrownBy(() -> manager.createJd(missing, "auto-missing"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_WORK_REGION_REQUIRED"));
    missing.setWorkRegionName("   ");
    assertThatThrownBy(() -> manager.createJd(missing, "auto-blank")).isInstanceOf(PlatformException.class);
    missing.setWorkRegionName("区".repeat(81));
    assertThatThrownBy(() -> manager.createJd(missing, "auto-long-region")).isInstanceOf(PlatformException.class);
    missing.setWorkRegionName("北京"); missing.setTitle("岗".repeat(81));
    assertThatThrownBy(() -> manager.createJd(missing, "auto-long-title"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CODE_TITLE_TOO_LONG"));
    verify(jds, never()).departmentCode(anyString());
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());
    verify(codeOptions, never()).insertCodeOption(any());
  }

  private CreateJdVersionDTO automaticCommand(String title, String region, String distinction) {
    var input = command("Linux");
    input.setTitle(title); input.setWorkRegionName(region); input.setDistinction(distinction);
    return input;
  }

  @Test
  void createsWithSelectedDimensionsAndKeepsNumberWhenClassificationChanges() {
    when(codeOptions.findCodeOption(eq("JOB_TYPE"), anyString())).thenReturn(new JdCodeOptionEntity());
    var regionOption = new JdCodeOptionEntity(); regionOption.setName("北京市");
    when(codeOptions.findCodeOption(eq("REGION"), anyString())).thenReturn(regionOption);
    var input = command("Linux"); input.setJobTypeCode("001"); input.setWorkRegionCode("002");
    input.setWorkRegionName("北京");
    assertThatThrownBy(() -> createManualJd(input, "dimension-create"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CODE_INPUT_CONFLICT"));

    var existing = ownedJd(); existing.setCode("JD-901902001");
    existing.setJobTypeCode("001"); existing.setWorkRegionCode("002");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var update = command("Linux"); update.setVersion(2L);
    update.setJobTypeCode("003"); update.setWorkRegionCode("004");
    assertThat(manager.updateJd(existing.getId(), update, "dimension-update").getJdCode()).isEqualTo("JD-901902001");
    assertThat(existing.getJobTypeCode()).isEqualTo("003");
    assertThat(existing.getWorkRegionCode()).isEqualTo("004");
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());
  }

  @Test
  void editingLegacyRegionFillsBothDimensionsAndPreservesPermanentCode() {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var input = command("新正文"); input.setVersion(2L); input.setWorkRegionName("北京");
    var updated = manager.updateJd(existing.getId(), input, "legacy-region");
    assertThat(updated.getJdCode()).isEqualTo("JD-000001");
    assertThat(existing.getJobTypeCode()).isEqualTo("001");
    assertThat(existing.getWorkRegionCode()).isEqualTo("001");
    var order = inOrder(jds, catalog);
    order.verify(jds).lockCodeAllocation();
    order.verify(catalog).findJdForUpdate(existing.getId());
    when(codeOptions.findCodeOption(anyString(), eq("001"))).thenReturn(new JdCodeOptionEntity());
    when(jds.updateRecruitmentMetadata(anyString(), anyInt(), anyInt(), anyString(), anyString(), anyBoolean(), anyLong(), anyString(), any())).thenReturn(1);
    when(versions.findJdVersions(anyString(), any(), any(), anyInt())).thenReturn(List.of(updated));
    var next = command("新正文"); next.setVersion(3L); next.setWorkRegionName("北京");
    manager.updateJd(existing.getId(), next, "legacy-region-again");
  }

  @Test
  void rejectsIncompleteAndUnknownDimensionSelections() {
    var input = command("Linux"); input.setJobTypeCode("001");
    assertThatThrownBy(() -> createManualJd(input, "missing-region"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CODE_INPUT_CONFLICT"));
    input.setWorkRegionCode("002");
    assertThatThrownBy(() -> createManualJd(input, "unknown-options"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CODE_INPUT_CONFLICT"));
    verify(jds, never()).save(any(JdEntity.class));
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());
  }

  @Test
  void sameTitleAndRegionAllowIndependentJdsWithoutDistinction() {
    when(jds.nextCode("01", "001", "001"))
        .thenReturn("JD-01001001001", "JD-01001001002");
    var first = automaticCommand("IT运维工程师", "北京", null);
    var second = automaticCommand("IT运维工程师", "北京", null);
    createManualJd(first, "independent-one");
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot("R".repeat(26), "另一位HR",
        "other@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("demand.manage", "demand.read"), false, false));
    createManualJd(second, "independent-two");
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds, times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).extracting(JdEntity::getOwnerUserId)
        .containsExactly("H".repeat(26), "R".repeat(26));
    assertThat(saved.getAllValues()).extracting(JdEntity::getCode)
        .containsExactly("JD-01001001001", "JD-01001001002");
  }

  @Test
  void newLegacyDraftRequiresWorkRegionAndDepartmentCodeCapacity() {
    assertThatThrownBy(() -> manager.createSourceJdDraft(command("Linux"), "legacy-no-region"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_WORK_REGION_REQUIRED"));
    when(jds.departmentCode(anyString())).thenReturn(null);
    assertThatThrownBy(() -> createManualJd(command("Linux"), "departments-full"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_DEPARTMENT_CODES_EXHAUSTED"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void missingManagePermissionStopsBeforeAnyDraftLookupOrWrite() {
    doThrow(new SecurityException("forbidden")).when(authorization).requirePermission("demand.manage");
    assertThatThrownBy(() -> manager.createSourceJdDraft(command("Linux"), "source-denied"))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void applyingPublicContentRequiresReadPermissionBeforeSourceLookup() {
    var denied = new SecurityException("缺少公共 JD 读取权限");
    doThrow(denied).when(authorization).requirePermission("demand.read");
    var input = command("Linux"); input.setWorkRegionName("北京");
    input.setAppliedFromJdVersionId("S".repeat(26));

    assertThatThrownBy(() -> manager.createJd(input, "apply-read-denied")).isSameAs(denied);
    verify(versions, never()).findJdVersion(anyString());
    verify(catalog, never()).findJdForUpdate(anyString());
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void recruiterCannotCreatePublicJd() {
    var input = command("Linux"); input.setPublicVisible(true);
    assertThatThrownBy(() -> createManualJd(input, "public-denied"))
        .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo("PUBLIC_JD_UNSUPPORTED"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void ownerCanShareNewJdAndAppliedCopyDefaultsToPrivate() {
    var input = command("公开内容"); input.setContentShared(true);
    createManualJd(input, "share-new");
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().isContentShared()).isTrue();
    assertThat(new DemandCatalogAssembler().toJdResponse(saved.getValue()).isContentShared()).isTrue();
  }

  @Test
  void delegatedOwnerCannotShareOnCreation() {
    var input = command("公开内容"); input.setContentShared(true);
    input.setOwnerUserId("N".repeat(26));
    when(identityDirectory.activeUser("N".repeat(26)))
        .thenReturn(new UserReference("N".repeat(26), "代建对象", "D".repeat(26)));
    when(identityDirectory.isRecruiterOwner("N".repeat(26))).thenReturn(true);
    CurrentActorSnapshot managerActor = new CurrentActorSnapshot("H".repeat(26), "管理者",
        "manager@example.invalid", "D".repeat(26), Set.of(), Set.of("N".repeat(26)), Set.of(),
        Set.of("HR_DIRECTOR"), Set.of("demand.manage", "demand.read"), true, false);
    when(authorization.currentActor()).thenReturn(managerActor);
    assertThatThrownBy(() -> manager.createJd(input, "delegated-share"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CONTENT_SHARE_FORBIDDEN"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void legacyForceNewStillCreatesAnIndependentJd() {
    var input = command("另一份正文"); input.setForceNew(true);
    createManualJd(input, "force-new");
    verify(jds).save(any(JdEntity.class));
  }

  @Test
  void sameRawContentWithDifferentTitleCreatesIndependentJds() {
    var first = command("相同正文");
    var second = command("相同正文");
    second.setTitle("平台运维工程师");

    createManualJd(first, "title-one");
    createManualJd(second, "title-two");

    verify(jds, times(2)).save(any(JdEntity.class));
  }

  @Test
  void blankDistinctionIsOptional() {
    var input = command("Linux"); input.setDistinction(" ");
    createManualJd(input, "blank-distinction");
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getDistinction()).isNull();
  }

  @Test
  void legacyDraftEntryDefaultsDepartmentAndRecruitmentType() {
    var input = command("旧入口正文");
    input.setOwnerDepartmentId(null);
    input.setRecruitmentType(null);
    input.setWorkRegionName("北京");

    manager.createSourceJdDraft(input, "legacy-source-entry");

    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getOwnerDepartmentId()).isEqualTo("D".repeat(26));
    assertThat(saved.getValue().getRecruitmentType()).isEqualTo("INTERNAL");
  }

  @Test
  void createRejectsMissingOrInactiveDepartment() {
    var missing = command("Linux");
    missing.setOwnerDepartmentId(null);
    assertThatThrownBy(() -> createManualJd(missing, "missing-department"))
        .isInstanceOf(PlatformException.class);

    doThrow(new PlatformException(HttpStatus.UNPROCESSABLE_CONTENT,
        "OWNER_DEPARTMENT_NOT_ACTIVE", "部门已停用", false))
        .when(identityDirectory).activeDepartment("D".repeat(26));
    assertThatThrownBy(() -> createManualJd(command("Linux"), "inactive-department"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("OWNER_DEPARTMENT_NOT_ACTIVE"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void recruitmentTypeAndClientMustMatch() {
    var invalidType = command("Linux");
    invalidType.setRecruitmentType("PARTNER");
    assertThatThrownBy(() -> createManualJd(invalidType, "invalid-type"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RECRUITMENT_TYPE_INVALID"));

    var externalWithoutClient = command("Linux");
    externalWithoutClient.setRecruitmentType("EXTERNAL");
    assertThatThrownBy(() -> createManualJd(externalWithoutClient, "external-no-client"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CLIENT_REQUIRED"));

    var internalWithClient = command("Linux");
    internalWithClient.setClientId("C".repeat(26));
    assertThatThrownBy(() -> createManualJd(internalWithClient, "internal-client"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_INTERNAL_CLIENT_FORBIDDEN"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void activeDepartmentOutsideAssignmentScopeIsRejected() {
    var input = command("Linux");
    input.setOwnerDepartmentId("X".repeat(26));

    assertThatThrownBy(() -> createManualJd(input, "outside-department"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("OWNER_DEPARTMENT_FORBIDDEN"));
    verify(identityDirectory).activeDepartment("X".repeat(26));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void externalRecruitmentWithActiveClientIsCreated() {
    ClientEntity client = new ClientEntity();
    client.setId("C".repeat(26));
    client.setStatus("ACTIVE");
    client.setOwnerUserId("H".repeat(26));
    client.setOwnerDepartmentId("D".repeat(26));
    when(catalog.findClient(client.getId())).thenReturn(client);
    var input = command("Linux");
    input.setRecruitmentType("EXTERNAL");
    input.setClientId(client.getId());

    createManualJd(input, "external-active-client");

    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getRecruitmentType()).isEqualTo("EXTERNAL");
    assertThat(saved.getValue().getClientId()).isEqualTo(client.getId());
  }

  @Test
  void sameIdentityInDifferentDepartmentsCreatesIndependentJd() {
    CurrentActorSnapshot director = new CurrentActorSnapshot("M".repeat(26), "人力负责人",
        "director@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("HR_DIRECTOR"), Set.of("demand.manage", "demand.read"), true, false);
    when(authorization.currentActor()).thenReturn(director);
    var input = command("不同正文");
    input.setOwnerDepartmentId("X".repeat(26));

    createManualJd(input, "department-x");
    verify(jds).save(any(JdEntity.class));
  }

  @Test
  void deletedDuplicateDoesNotBlockAReplacementOrRestoreTheDeletedJd() {
    var existing = new JdEntity(); existing.setId("old"); existing.setDeleted(true);

    var replacement = createManualJd(command("Linux"), "deleted-duplicate");

    assertThat(replacement.getJdId()).isEqualTo(version.getJdId());
    assertThat(existing.isDeleted()).isTrue();
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getId()).isNotEqualTo(existing.getId());
    assertThat(saved.getValue().getSourceFingerprint()).isNull();
    assertThat(replacement.getContentFingerprint()).isNull();
  }

  @Test
  void sameIdentityWithDifferentHeadcountCreatesIndependentJd() {
    var input = command("Linux"); input.setHeadcount(2);
    createManualJd(input, "independent-Headcount");
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getHeadcount()).isEqualTo(2);
  }

  @Test
  void sameIdentityWithDifferentRecruitmentCycleCreatesIndependentJd() {
    var input = command("Linux"); input.setRecruitmentCycleDays(60);
    createManualJd(input, "independent-RecruitmentCycleDays");
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getRecruitmentCycleDays()).isEqualTo(60);
  }

  @Test
  void managerCreatesForActiveRecruiterWithTargetOwnershipAndActorAudit() {
    CurrentActorSnapshot managerActor = new CurrentActorSnapshot("M".repeat(26), "招聘经理",
        "manager@example.invalid", "D".repeat(26), Set.of(), Set.of("R".repeat(26)), Set.of(),
        Set.of("RECRUITMENT_MANAGER"), Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(managerActor);
    when(identityDirectory.activeUser("R".repeat(26)))
        .thenReturn(new UserReference("R".repeat(26), "招聘专员", "T".repeat(26)));
    when(identityDirectory.isRecruiterOwner("R".repeat(26))).thenReturn(true);
    var input = command("Linux");
    input.setOwnerUserId("R".repeat(26));

    JdVersionEntity created = createManualJd(input, "delegated-create");

    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getOwnerUserId()).isEqualTo("R".repeat(26));
    assertThat(saved.getValue().getOwnerDepartmentId()).isEqualTo("D".repeat(26));
    assertThat(created.getContentOwnerUserId()).isEqualTo("R".repeat(26));
    assertThat(created.getCreateId()).isEqualTo("M".repeat(26));
  }

  @Test
  void sameIdentityForDifferentOwnersCreatesIndependentJd() {
    CurrentActorSnapshot director = new CurrentActorSnapshot("M".repeat(26), "人力负责人",
        "director@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("HR_DIRECTOR"), Set.of("demand.manage", "demand.read"), true, false);
    when(authorization.currentActor()).thenReturn(director);
    when(identityDirectory.activeUser("A".repeat(26)))
        .thenReturn(new UserReference("A".repeat(26), "负责人A", "X".repeat(26)));
    when(identityDirectory.activeUser("B".repeat(26)))
        .thenReturn(new UserReference("B".repeat(26), "负责人B", "Y".repeat(26)));
    when(identityDirectory.isRecruiterOwner(anyString())).thenReturn(true);
    var input = command("不同正文");
    input.setOwnerUserId("B".repeat(26));

    createManualJd(input, "owner-b");
    verify(jds).save(any(JdEntity.class));
  }

  @Test
  void recruiterCannotForgeAnotherOwner() {
    var input = command("Linux");
    input.setOwnerUserId("R".repeat(26));

    assertThatThrownBy(() -> createManualJd(input, "forged-owner"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("OWNER_ASSIGNMENT_FORBIDDEN"));
    verify(identityDirectory, never()).activeUser(anyString());
    verify(identityDirectory, never()).isRecruiterOwner(anyString());
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void managerCanReadSubordinateJdButCannotEditItsContent() {
    CurrentActorSnapshot managerActor = new CurrentActorSnapshot("M".repeat(26), "招聘经理",
        "manager@example.invalid", "D".repeat(26), Set.of(), Set.of("H".repeat(26)), Set.of(),
        Set.of("RECRUITMENT_MANAGER"), Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(managerActor);
    JdEntity subordinate = ownedJd();
    when(catalog.findJd(subordinate.getId())).thenReturn(subordinate);
    when(catalog.findJdForUpdate(subordinate.getId())).thenReturn(subordinate);

    assertThat(manager.getJd(subordinate.getId())).isSameAs(subordinate);
    var update = command("经理不能直接覆盖下属正文");
    update.setVersion(subordinate.getVersion().longValue());
    assertThatThrownBy(() -> manager.updateJd(subordinate.getId(), update, "manager-edit"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_MANAGEMENT_FORBIDDEN"));
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void managerCannotExpandSubordinateJdSourcesThroughManagementScope() {
    CurrentActorSnapshot managerActor = new CurrentActorSnapshot("M".repeat(26), "招聘经理",
        "manager@example.invalid", "D".repeat(26), Set.of(), Set.of("H".repeat(26)), Set.of(),
        Set.of("RECRUITMENT_MANAGER"), Set.of("demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(managerActor);
    when(catalog.findJd(version.getJdId())).thenReturn(ownedJd());

    manager.listJdSources(version.getId(), null, 20);

    verify(sources).findPage(version.getId(), managerActor.getUserId(), false, null, 21);
  }

  @Test
  void collaboratorReadAccessDoesNotRevealOtherUsersMailSources() {
    CurrentActorSnapshot collaborator = new CurrentActorSnapshot("C".repeat(26), "协作招聘人",
        "collaborator@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(collaborator);
    when(catalog.findJd(version.getJdId())).thenReturn(ownedJd());

    manager.listJdSources(version.getId(), null, 20);

    verify(sources).findPage(version.getId(), collaborator.getUserId(), false, null, 21);
  }

  @Test
  void hrDirectorCanReadAllSourcesAcrossCompany() {
    CurrentActorSnapshot director = new CurrentActorSnapshot("D".repeat(26), "人力负责人",
        "director@example.invalid", "HQ".repeat(13), Set.of(), Set.of(), Set.of(),
        Set.of("HR_DIRECTOR"), Set.of("demand.read"), true, false);
    when(authorization.currentActor()).thenReturn(director);
    when(catalog.findJd(version.getJdId())).thenReturn(ownedJd());

    manager.listJdSources(version.getId(), null, 20);

    verify(sources).findPage(version.getId(), director.getUserId(), true, null, 21);
  }

  @Test
  void delegatedOwnerMustHaveRecruitingRole() {
    CurrentActorSnapshot director = new CurrentActorSnapshot("M".repeat(26), "人力负责人",
        "director@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("HR_DIRECTOR"), Set.of("demand.manage", "demand.read"), true, false);
    when(authorization.currentActor()).thenReturn(director);
    when(identityDirectory.activeUser("R".repeat(26)))
        .thenReturn(new UserReference("R".repeat(26), "非招聘账号", "T".repeat(26)));
    var input = command("Linux");
    input.setOwnerUserId("R".repeat(26));

    assertThatThrownBy(() -> createManualJd(input, "invalid-owner-role"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_OWNER_ROLE_INVALID"));
    verify(jds, never()).save(any(JdEntity.class));
  }

  @Test
  void sameTitleWithDifferentDistinctionCreatesIndependentJds() {
    var first = command("Linux"); first.setDistinction("北京项目");
    var second = command("Linux"); second.setDistinction("上海项目");
    createManualJd(first, "beijing"); createManualJd(second, "shanghai");

    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds, times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).extracting(JdEntity::getSourceFingerprint)
        .containsOnlyNulls();
    var savedVersions = ArgumentCaptor.forClass(JdVersionEntity.class);
    verify(versions, times(2)).save(savedVersions.capture());
    assertThat(savedVersions.getAllValues()).extracting(JdVersionEntity::getContentFingerprint)
        .containsOnlyNulls();
  }

  @Test
  void concurrentCreateIdentityCollisionReturnsDuplicateBusinessError() {
    when(jds.save(any(JdEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));

    assertThatThrownBy(() -> createManualJd(command("Linux"), "create-race"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_DUPLICATE"));
    verify(versions, never()).save(any(JdVersionEntity.class));
  }

  @Test
  void deletionRequiresOwnershipAndDoesNotDeleteVersions() {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.softDelete(eq(existing.getId()), eq(2L), eq("H".repeat(26)), any())).thenReturn(1);
    manager.deleteJd(existing.getId(), 2L);
    verify(jds).softDelete(eq(existing.getId()), eq(2L), eq("H".repeat(26)), any());
    verify(versions, never()).removeById(anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"我的简历", "智能筛选", "招聘流程"})
  void deletionIsBlockedWhileAnyJdVersionHasResumesInUse(String location) {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.softDelete(eq(existing.getId()), eq(2L), anyString(), any())).thenReturn(1);
    var allVersions = List.of("old-version", "current-version");
    when(library.hasPersonalPoolResumes(allVersions)).thenReturn("我的简历".equals(location));
    when(screening.hasActiveAdmissions(allVersions)).thenReturn("智能筛选".equals(location));
    when(applications.hasActiveApplicationsForJd(existing.getId())).thenReturn("招聘流程".equals(location));

    assertThatThrownBy(() -> manager.deleteJd(existing.getId(), 2L))
        .isInstanceOfSatisfying(PlatformException.class, error -> {
          assertThat(error.code()).isEqualTo("JD_IN_USE");
          assertThat(error.getMessage()).contains(location);
          assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
        });
    verify(jds, never()).softDelete(anyString(), anyLong(), anyString(), any());
    verifyNoInteractions((DemandAuditEventPersistenceService)
        ReflectionTestUtils.getField(manager, "demandAuditEventPersistenceService"));
  }

  @Test
  void deletionChecksAllLocationsAfterJdLockInOneSnapshot() throws NoSuchMethodException {
    var transaction = DemandCatalogApplicationManager.class
        .getMethod("deleteJd", String.class, long.class).getAnnotation(Transactional.class);
    assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    var allVersions = List.of("old-version", "current-version");
    when(library.hasPersonalPoolResumes(allVersions)).thenReturn(true);
    when(screening.hasActiveAdmissions(allVersions)).thenReturn(true);
    when(applications.hasActiveApplicationsForJd(existing.getId())).thenReturn(true);

    assertThatThrownBy(() -> manager.deleteJd(existing.getId(), 2L))
        .hasMessageContaining("我的简历、智能筛选、招聘流程");
    var order = inOrder(catalog, versions, library, screening, applications);
    order.verify(catalog).findJdForUpdate(existing.getId());
    order.verify(versions).findVersionIds(existing.getId());
    order.verify(library).hasPersonalPoolResumes(allVersions);
    order.verify(screening).hasActiveAdmissions(allVersions);
    order.verify(applications).hasActiveApplicationsForJd(existing.getId());
  }

  @Test
  void alreadyDeletedJdRemainsIdempotentWithoutUsageQueries() {
    var existing = ownedJd(); existing.setDeleted(true);
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    manager.deleteJd(existing.getId(), 0L);
    verifyNoInteractions(library, screening, applications);
    verify(jds, never()).softDelete(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void staleDeletionCannotReadUsageOrWrite() {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    assertThatThrownBy(() -> manager.deleteJd(existing.getId(), 1L))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_VERSION_CONFLICT"));
    verifyNoInteractions(library, screening, applications);
    verify(jds, never()).softDelete(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void updateAppendsVersionAndRetainsStableJdCode() {
    var existing = ownedJd(); existing.setCode("JD-stable"); existing.setCurrentVersionNo(1);
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var input = command("Linux 新增 Kubernetes"); input.setVersion(2L);

    var appended = manager.updateJd(existing.getId(), input, "jd-update");

    assertThat(appended.getVersionNo()).isEqualTo(2);
    assertThat(appended.getJdId()).isEqualTo(existing.getId());
    assertThat(appended.getRawText()).isEqualTo(input.getRawText());
    assertThat(existing.getCode()).isEqualTo("JD-stable");
    assertThat(appended.getJdCode()).isEqualTo("JD-stable");
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());
    verify(versions).save(appended);
    verify(versions, never()).updateById(any(JdVersionEntity.class));

    var fingerprint = ArgumentCaptor.forClass(String.class);
    verify(idempotency).insertIdempotency(eq("H".repeat(26)), eq("jd-update"), eq("JD_UPDATE"),
        fingerprint.capture(), eq(appended.getId()), any());
    var recorded = new IdempotencyRecordEntity();
    recorded.setActionCode("JD_UPDATE"); recorded.setRequestFingerprint(fingerprint.getValue());
    recorded.setTargetId(appended.getId());
    when(idempotency.findIdempotency("H".repeat(26), "jd-update")).thenReturn(recorded);
    when(versions.findJdVersion(appended.getId())).thenReturn(appended);
    appended.setJdCode(null);
    assertThat(manager.updateJd(existing.getId(), input, "jd-update").getJdCode()).isEqualTo("JD-stable");
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());
  }

  @Test
  void staleUpdateCannotAppendVersion() {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    existing.setCurrentVersionNo(1);
    var input = command("新正文"); input.setVersion(1L);
    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "stale-update"))
        .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo("JD_VERSION_CONFLICT"));
    verify(versions, never()).save(any(JdVersionEntity.class));
  }

  @Test
  void transferredOwnershipBlocksAnUpdateEvenWithTheNewVersionNumber() {
    var stale = ownedJd();
    var current = ownedJd(); current.setOwnerUserId("new-owner"); current.setVersion(3);
    when(catalog.findJd(stale.getId())).thenReturn(stale);
    when(catalog.findJdForUpdate(current.getId())).thenReturn(current);
    var input = command("转交后不能覆盖正文"); input.setVersion(3L);

    assertThatThrownBy(() -> manager.updateJd(current.getId(), input, "owner-race"))
        .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo("JD_MANAGEMENT_FORBIDDEN"));
    verify(jds, never()).updateCatalog(any(), anyLong());
    verify(versions, never()).save(any(JdVersionEntity.class));
  }

  @Test
  void legacyPublicFlagDoesNotChangeOwnerUpdateAuthorization() {
    var stale = ownedJd();
    var current = ownedJd(); current.setPublicVisible(true); current.setVersion(3);
    when(catalog.findJd(stale.getId())).thenReturn(stale);
    when(catalog.findJdForUpdate(current.getId())).thenReturn(current);
    var input = command("不能把公共 JD 改回私有"); input.setVersion(3L);

    when(jds.updateCatalog(any(), eq(3L))).thenReturn(1);
    manager.updateJd(current.getId(), input, "public-race");
    verify(jds).updateCatalog(any(), eq(3L));
  }

  @Test
  void transferredOwnershipBlocksDeleteEvenWithTheNewVersionNumber() {
    var stale = ownedJd();
    var current = ownedJd(); current.setOwnerUserId("new-owner"); current.setVersion(3);
    when(catalog.findJd(stale.getId())).thenReturn(stale);
    when(catalog.findJdForUpdate(current.getId())).thenReturn(current);

    assertThatThrownBy(() -> manager.deleteJd(current.getId(), 3L))
        .isInstanceOfSatisfying(PlatformException.class, e -> assertThat(e.code()).isEqualTo("JD_MANAGEMENT_FORBIDDEN"));
    verify(jds, never()).softDelete(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void legacyPublicFlagDoesNotChangeOwnerDeleteAuthorization() {
    var stale = ownedJd();
    var current = ownedJd(); current.setPublicVisible(true); current.setVersion(3);
    when(catalog.findJd(stale.getId())).thenReturn(stale);
    when(catalog.findJdForUpdate(current.getId())).thenReturn(current);

    Instant now = Instant.parse("2026-09-07T10:00:00Z");
    when(jds.softDelete(current.getId(), 3L, "H".repeat(26), now)).thenReturn(1);
    manager.deleteJd(current.getId(), 3L);
    verify(jds).softDelete(current.getId(), 3L, "H".repeat(26), now);
  }

  @Test
  void sameIdempotencyRequestReplaysTheOriginalVersionWithoutWritingAgain() {
    var original = createManualJd(command("初始原文"), "same-create");
    var requestFingerprint = ArgumentCaptor.forClass(String.class);
    verify(idempotency).insertIdempotency(eq("H".repeat(26)), eq("same-create"),
        eq("JD_CREATE"), requestFingerprint.capture(), eq(original.getId()), any());
    var recorded = new IdempotencyRecordEntity();
    recorded.setActionCode("JD_CREATE");
    recorded.setRequestFingerprint(requestFingerprint.getValue());
    recorded.setTargetId(original.getId());
    when(idempotency.findIdempotency("H".repeat(26), "same-create")).thenReturn(recorded);
    when(versions.findJdVersion(original.getId())).thenReturn(original);
    when(catalog.findJdForUpdate(original.getJdId())).thenReturn(ownedJd());

    original.setJdCode(null);
    assertThat(createManualJd(command("初始原文"), "same-create")).isSameAs(original);
    assertThat(original.getJdCode()).isEqualTo("JD-000001");
    var changedRegion = command("初始原文"); changedRegion.setWorkRegionName("上海");
    assertThatThrownBy(() -> manager.createJd(changedRegion, "same-create"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    var changedSharing = command("初始原文"); changedSharing.setWorkRegionName("北京");
    changedSharing.setContentShared(true);
    assertThatThrownBy(() -> manager.createJd(changedSharing, "same-create"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    verify(jds, times(1)).nextCode("01", "001", "001");
    verify(codeOptions, times(2)).findCodeOptionByName(anyString(), anyString());
    verify(jds, times(1)).save(any(JdEntity.class));
    verify(versions, times(1)).save(any(JdVersionEntity.class));
    verify(sources, times(1)).save(any(JdSourceEntity.class));
  }

  @Test
  void appliedCreateReplayReturnsOwnCopyAfterSourceIsUnsharedAndVersionChanges() {
    var sourceVersion = new JdVersionEntity();
    sourceVersion.setTitle("IT运维工程师"); sourceVersion.setRawText("自己的招聘原文");
    sourceVersion.setId("S".repeat(26)); sourceVersion.setJdId("Q".repeat(26));
    sourceVersion.setVersionNo(1); sourceVersion.setStatus("PUBLISHED");
    var source = ownedJd(); source.setId(sourceVersion.getJdId());
    source.setStatus("ACTIVE"); source.setContentShared(true);
    when(versions.findJdVersion(sourceVersion.getId())).thenReturn(sourceVersion);
    when(catalog.findJdForUpdate(source.getId())).thenReturn(source);
    var input = command("自己的招聘原文"); input.setWorkRegionName("北京");
    input.setAppliedFromJdVersionId(sourceVersion.getId());
    var created = manager.createJd(input, "apply-replay");
    var fingerprint = ArgumentCaptor.forClass(String.class);
    verify(idempotency).insertIdempotency(eq("H".repeat(26)), eq("apply-replay"), eq("JD_CREATE"),
        fingerprint.capture(), eq(created.getId()), any());
    var recorded = new IdempotencyRecordEntity();
    recorded.setActionCode("JD_CREATE"); recorded.setRequestFingerprint(fingerprint.getValue());
    recorded.setTargetId(created.getId());
    when(idempotency.findIdempotency("H".repeat(26), "apply-replay")).thenReturn(recorded);
    when(versions.findJdVersion(created.getId())).thenReturn(created);
    when(catalog.findJdForUpdate(created.getJdId())).thenReturn(ownedJd());
    source.setContentShared(false); source.setCurrentVersionNo(2);

    assertThat(manager.createJd(input, "apply-replay")).isSameAs(created);
    verify(catalog, times(1)).findJdForUpdate(source.getId());
    verify(jds, times(1)).save(any(JdEntity.class));
    verify(versions, times(1)).save(any(JdVersionEntity.class));
  }

  @Test
  void createReplayRechecksCurrentJdAuthorizationAfterPrivateHandover() {
    var original = createManualJd(command("初始原文"), "original-create");
    var fingerprint = ArgumentCaptor.forClass(String.class);
    verify(idempotency).insertIdempotency(eq("H".repeat(26)), eq("original-create"), eq("JD_CREATE"),
        fingerprint.capture(), eq(original.getId()), any());
    var recorded = new IdempotencyRecordEntity();
    recorded.setActionCode("JD_CREATE"); recorded.setRequestFingerprint(fingerprint.getValue()); recorded.setTargetId(original.getId());
    when(idempotency.findIdempotency("H".repeat(26), "original-create")).thenReturn(recorded);
    when(versions.findJdVersion(original.getId())).thenReturn(original);
    var transferred = ownedJd(); transferred.setOwnerUserId("new-owner");
    when(catalog.findJdForUpdate(transferred.getId())).thenReturn(transferred);
    var target = new AuthorizationTarget("JD", transferred.getId(), "new-owner", "D".repeat(26), Set.of());
    doThrow(new SecurityException("已交接且无当前查看授权")).when(authorization).requireAccess("demand.read", target);

    assertThatThrownBy(() -> createManualJd(command("初始原文"), "original-create"))
        .isInstanceOf(SecurityException.class);
    verify(jds, times(1)).save(any(JdEntity.class));
    verify(sources, times(1)).save(any(JdSourceEntity.class));
  }

  @Test
  void versionReadsExposeTheLogicalJdCode() {
    var existing = ownedJd();
    when(catalog.findJd(existing.getId())).thenReturn(existing);

    assertThat(manager.getJdVersion(version.getId()).getJdCode()).isEqualTo("JD-000001");
    version.setJdCode(null);
    assertThat(manager.listJdVersions(existing.getId(), null, 20).getItems())
        .extracting(JdVersionEntity::getJdCode).containsExactly("JD-000001");
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());
  }

  @Test
  void demandVersionCreationAndReplayReturnTheExistingStableCode() {
    ReflectionTestUtils.setField(manager, "demandCollaboratorPersistenceService",
        mock(DemandCollaboratorPersistenceService.class));
    var existing = ownedJd();
    var demand = new DemandEntity();
    demand.setId("D".repeat(26)); demand.setStatus("ACTIVE");
    demand.setOwnerUserId("H".repeat(26)); demand.setOwnerDepartmentId("D".repeat(26));
    demand.setRecruitmentType("INTERNAL"); demand.setHeadcount(10);
    when(demands.findDemand(demand.getId())).thenReturn(demand);
    when(jds.findJdIdByDemand(demand.getId())).thenReturn(existing.getId());
    when(catalog.findJd(existing.getId())).thenReturn(existing);
    when(catalog.lockJdVersionNo(existing.getId())).thenReturn(1);
    version.setId("J".repeat(26));
    when(versions.findJdVersion(version.getId())).thenReturn(version);
    when(versions.updateById(version)).thenReturn(true);

    assertThat(manager.addJdVersion(demand.getId(), command("Linux"), "demand-version").getJdCode())
        .isEqualTo("JD-000001");
    var fingerprint = ArgumentCaptor.forClass(String.class);
    verify(idempotency).insertIdempotency(eq("H".repeat(26)), eq("demand-version"),
        eq("JD_VERSION_CREATE"), fingerprint.capture(), eq(version.getId()), any());
    var recorded = new IdempotencyRecordEntity();
    recorded.setActionCode("JD_VERSION_CREATE"); recorded.setRequestFingerprint(fingerprint.getValue());
    recorded.setTargetId(version.getId());
    when(idempotency.findIdempotency("H".repeat(26), "demand-version")).thenReturn(recorded);
    version.setJdCode(null);

    assertThat(manager.addJdVersion(demand.getId(), command("Linux"), "demand-version").getJdCode())
        .isEqualTo("JD-000001");
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());
  }

  @Test
  void demandCreatesFourPartCodeOnlyForItsFirstJdVersion() {
    ReflectionTestUtils.setField(manager, "demandCollaboratorPersistenceService",
        mock(DemandCollaboratorPersistenceService.class));
    var demand = new DemandEntity();
    demand.setId("D".repeat(26)); demand.setStatus("ACTIVE");
    demand.setOwnerUserId("H".repeat(26)); demand.setOwnerDepartmentId("D".repeat(26));
    demand.setRecruitmentType("INTERNAL"); demand.setHeadcount(10);
    when(demands.findDemand(demand.getId())).thenReturn(demand);
    var missing = command("Linux");
    assertThatThrownBy(() -> manager.addJdVersion(demand.getId(), missing, "demand-missing-region"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_WORK_REGION_REQUIRED"));
    verify(jds, never()).save(any(JdEntity.class));

    var input = command("Linux"); input.setWorkRegionName("北京");
    when(catalog.lockJdVersionNo("J".repeat(26))).thenReturn(0);
    when(versions.updateById(version)).thenReturn(true);
    var created = new JdEntity(); created.setId("J".repeat(26));
    created.setCode("JD-01001001001");
    when(catalog.findJd(created.getId())).thenReturn(created);
    var saved = ArgumentCaptor.forClass(JdEntity.class);

    assertThat(manager.addJdVersion(demand.getId(), input, "demand-first-version").getJdCode())
        .isEqualTo("JD-01001001001");
    verify(jds).save(saved.capture());
    assertThat(saved.getValue().getOwnerDepartmentId()).isEqualTo("D".repeat(26));
    assertThat(saved.getValue().getWorkRegionCode()).isEqualTo("001");
    verify(versions).insertJdVersion(eq("V".repeat(26)), eq("J".repeat(26)), eq(1),
        eq("IT运维工程师"), eq("Linux"), eq("MANUAL"), eq("001"), anyString(), any());
  }

  private JdVersionEntity createManualJd(CreateJdVersionDTO input, String key) {
    if (input.getWorkRegionName() == null) input.setWorkRegionName("北京");
    return manager.createJd(input, key);
  }

  private JdEntity ownedJd() {
    var existing = new JdEntity(); existing.setId(version.getJdId());
    existing.setTitle("IT运维工程师"); existing.setDistinction("默认岗位");
    existing.setCode("JD-000001");
    existing.setOwnerUserId("H".repeat(26)); existing.setOwnerDepartmentId("D".repeat(26));
    existing.setCatalogOwnerDepartmentId("D".repeat(26));
    existing.setRecruitmentType("INTERNAL");
    existing.setHeadcount(1); existing.setRecruitmentCycleDays(45);
    existing.setVersion(2); existing.setCurrentVersionNo(1); return existing;
  }

  @Test
  void updatePreservesLegacyDistinctionWhenOmitted() {
    var edited = ownedJd();
    when(catalog.findJdForUpdate(edited.getId())).thenReturn(edited);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var input = command("新正文"); input.setDistinction(null); input.setVersion(2L);
    manager.updateJd(edited.getId(), input, "preserve-legacy-note");
    assertThat(edited.getDistinction()).isEqualTo("默认岗位");
    verify(versions).save(any(JdVersionEntity.class));
  }

  @Test
  void updateAllowsAUniqueIdentityAndDoesNotPersistContentFingerprints() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot("H".repeat(26), "测试HR",
        "hr@example.invalid", "D".repeat(26), Set.of("X".repeat(26)), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    var edited = ownedJd();
    when(catalog.findJdForUpdate(edited.getId())).thenReturn(edited);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var input = command("新正文");
    input.setOwnerDepartmentId("X".repeat(26));
    input.setVersion(2L);

    JdVersionEntity appended = manager.updateJd(edited.getId(), input, "stale-department-claim");

    assertThat(appended.getContentFingerprint()).isNull();
    verify(jds).updateCatalog(any(), eq(2L));
    verify(versions).save(appended);
  }

  @Test
  void concurrentUpdateIdentityCollisionReturnsDuplicateBusinessError() {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateCatalog(any(), eq(2L))).thenThrow(new DuplicateKeyException("duplicate"));
    var input = command("新正文");
    input.setVersion(2L);

    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "update-race"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_DUPLICATE"));
    verify(versions, never()).save(any(JdVersionEntity.class));
  }

  @Test
  void changingOnlyDepartmentUpdatesMetadataAndOwnershipHistoryWithoutNewContentVersion() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot("H".repeat(26), "测试HR",
        "hr@example.invalid", "D".repeat(26), Set.of("X".repeat(26)), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    var existing = ownedJd();
    version.setTitle("IT运维工程师");
    version.setRawText("Linux");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateRecruitmentMetadata(existing.getId(), 1, 45, actor.getUserId(), "X".repeat(26), false, 2L,
        actor.getUserId(), Instant.parse("2026-09-07T10:00:00Z"))).thenReturn(1);
    var input = command("Linux");
    input.setOwnerDepartmentId("X".repeat(26));
    input.setVersion(2L);

    assertThat(manager.updateJd(existing.getId(), input, "department-only")).isSameAs(version);

    verify(identityDirectory).activeDepartment("X".repeat(26));
    verify(jds).updateRecruitmentMetadata(existing.getId(), 1, 45, actor.getUserId(), "X".repeat(26), false, 2L,
        actor.getUserId(), Instant.parse("2026-09-07T10:00:00Z"));
    verify(ownershipHistory).insertOwnershipHistory(any(), eq("JD"), eq(existing.getId()),
        eq(actor.getUserId()), eq(actor.getUserId()), eq("D".repeat(26)), eq("X".repeat(26)),
        eq("编辑 JD 归属部门"), eq(actor.getUserId()), any());
    verify(versions, never()).save(any(JdVersionEntity.class));
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void sharingOnlyChangesMetadataAndOmissionPreservesSharedState() {
    var existing = ownedJd();
    version.setTitle("IT运维工程师"); version.setRawText("Linux");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateRecruitmentMetadata(eq(existing.getId()), eq(1), eq(45), eq(existing.getOwnerUserId()),
        eq(existing.getOwnerDepartmentId()), eq(true), eq(2L), any(), any())).thenReturn(1);
    var input = command("Linux"); input.setVersion(2L); input.setContentShared(true);

    assertThat(manager.updateJd(existing.getId(), input, "share-only")).isSameAs(version);
    verify(versions, never()).save(any(JdVersionEntity.class));
    verify(jds, never()).updateCatalog(any(), anyLong());
    verify(auditEvents).insertAudit(any(), eq("JD_CONTENT_SHARING_CHANGED"), eq(existing.getOwnerUserId()),
        eq("JD"), eq(existing.getId()), eq("false -> true"), any());

    existing.setContentShared(true);
    when(jds.updateRecruitmentMetadata(eq(existing.getId()), eq(1), eq(45), eq(existing.getOwnerUserId()),
        eq(existing.getOwnerDepartmentId()), eq(true), eq(3L), any(), any())).thenReturn(1);
    var omitted = command("Linux"); omitted.setVersion(3L);
    manager.updateJd(existing.getId(), omitted, "share-omitted");
    verify(jds).updateRecruitmentMetadata(eq(existing.getId()), eq(1), eq(45),
        eq(existing.getOwnerUserId()), eq(existing.getOwnerDepartmentId()), eq(true), eq(3L), any(), any());
  }

  @Test
  void editingSharedBodyKeepsSharingAndRevocationRecordsTransition() {
    var existing = ownedJd(); existing.setContentShared(true);
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var edited = command("新正文"); edited.setVersion(2L);
    manager.updateJd(existing.getId(), edited, "shared-body");
    var saved = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).updateCatalog(saved.capture(), eq(2L));
    assertThat(saved.getValue().isContentShared()).isTrue();

    version.setTitle("IT运维工程师"); version.setRawText("Linux");
    existing.setContentShared(true);
    when(jds.updateRecruitmentMetadata(eq(existing.getId()), eq(1), eq(45), eq(existing.getOwnerUserId()),
        eq(existing.getOwnerDepartmentId()), eq(false), eq(3L), any(), any())).thenReturn(1);
    var revoke = command("Linux"); revoke.setVersion(3L); revoke.setContentShared(false);
    manager.updateJd(existing.getId(), revoke, "revoke-share");
    verify(auditEvents).insertAudit(any(), eq("JD_CONTENT_SHARING_CHANGED"), eq(existing.getOwnerUserId()),
        eq("JD"), eq(existing.getId()), eq("true -> false"), any());
  }

  @Test
  void applicationFreezesSourceTextAndRejectsModifiedTextSharedCopiesAndResharing() {
    var sourceVersion = publicSource(" Linux\r\nGPU ");
    var changed = command("Changed"); changed.setWorkRegionName("北京");
    changed.setAppliedFromJdVersionId(sourceVersion.getId());
    assertThatThrownBy(() -> manager.createJd(changed, "apply-changed"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_CONTENT_IMMUTABLE"));
    var shared = command(" Linux\nGPU "); shared.setWorkRegionName("北京");
    shared.setAppliedFromJdVersionId(sourceVersion.getId()); shared.setContentShared(true);
    assertThatThrownBy(() -> manager.createJd(shared, "apply-shared"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_SHARE_FORBIDDEN"));
    shared.setContentShared(false);
    assertThat(manager.createJd(shared, "apply-frozen").getRawText()).isEqualTo(" Linux\r\nGPU ");
    var source = ownedJd(); source.setId(sourceVersion.getJdId()); source.setContentShared(true);
    source.setAppliedFromJdVersionId("P".repeat(26));
    when(catalog.findJdForUpdate(source.getId())).thenReturn(source);
    assertThatThrownBy(() -> manager.createJd(shared, "apply-copy"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_SOURCE_NOT_FOUND"));
  }

  @Test
  void applicationAllowsOwnSupportDepartmentButCompanyScopeDoesNotBypassAffiliations() {
    var sourceVersion = publicSource("Linux");
    var actor = new CurrentActorSnapshot("H".repeat(26), "HR", "hr@example.invalid", "D".repeat(26),
        Set.of("X".repeat(26)), Set.of(), Set.of(), Set.of("HR_DIRECTOR"),
        Set.of("demand.manage", "demand.read"), true, false);
    when(authorization.currentActor()).thenReturn(actor);
    var input = command("Linux"); input.setWorkRegionName("北京");
    input.setAppliedFromJdVersionId(sourceVersion.getId()); input.setOwnerDepartmentId("X".repeat(26));
    manager.createJd(input, "apply-support");
    var saved = ArgumentCaptor.forClass(JdEntity.class); verify(jds).save(saved.capture());
    assertThat(saved.getValue().getOwnerDepartmentId()).isEqualTo("X".repeat(26));
    input.setOwnerDepartmentId("Y".repeat(26));
    assertThatThrownBy(() -> manager.createJd(input, "apply-outside"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_DEPARTMENT_FORBIDDEN"));
  }

  @Test
  void appliedCopyCanMoveToOwnSupportDepartmentAndRevokesLegacySharingWithoutReadingSource() {
    var existing = ownedJd(); existing.setAppliedFromJdVersionId("S".repeat(26)); existing.setContentShared(true);
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    var actor = new CurrentActorSnapshot("H".repeat(26), "HR", "hr@example.invalid", "D".repeat(26),
        Set.of("X".repeat(26)), Set.of(), Set.of(), Set.of("RECRUITER"),
        Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    when(jds.updateRecruitmentMetadata(any(), anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong(), any(), any())).thenReturn(1);
    var input = command("Linux"); input.setVersion(2L); input.setOwnerDepartmentId("X".repeat(26));
    manager.updateJd(existing.getId(), input, "applied-support-update");
    verify(jds).updateRecruitmentMetadata(eq(existing.getId()), eq(1), eq(45), eq(existing.getOwnerUserId()),
        eq("X".repeat(26)), eq(false), eq(2L), any(), any());
    verify(versions, never()).findJdVersion("S".repeat(26));
    verify(auditEvents).insertAudit(any(), eq("JD_CONTENT_SHARING_CHANGED"), any(), eq("JD"),
        eq(existing.getId()), eq("true -> false"), any());
  }

  @Test
  void legacyDemandVersionEndpointCannotChangeAppliedFrozenContent() {
    ReflectionTestUtils.setField(manager, "demandCollaboratorPersistenceService",
        mock(DemandCollaboratorPersistenceService.class));
    var demand = new DemandEntity(); demand.setId("D".repeat(26)); demand.setStatus("ACTIVE");
    demand.setOwnerUserId("H".repeat(26)); demand.setOwnerDepartmentId("D".repeat(26));
    when(demands.findDemand(demand.getId())).thenReturn(demand);
    var existing = ownedJd(); existing.setAppliedFromJdVersionId("S".repeat(26));
    when(jds.findJdIdByDemand(demand.getId())).thenReturn(existing.getId());
    when(catalog.findJd(existing.getId())).thenReturn(existing);
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    assertThatThrownBy(() -> manager.addJdVersion(demand.getId(), command("Changed"), "legacy-edit-copy"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_CONTENT_IMMUTABLE"));
    var renamed = command("Linux"); renamed.setTitle("另一个名称");
    assertThatThrownBy(() -> manager.addJdVersion(demand.getId(), renamed, "legacy-rename-copy"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_TITLE_IMMUTABLE"));
    verify(versions, never()).insertJdVersion(any(), any(), anyInt(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void invalidCityIsRejectedAndUnchangedInvalidHistoricalRegionDoesNotBlockMetadataEdit() {
    var input = command("Linux"); input.setWorkRegionName("海淀区");
    assertThatThrownBy(() -> manager.createJd(input, "invalid-city"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_WORK_REGION_INVALID"));
    var existing = ownedJd(); existing.setWorkRegionName("驻场");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateRecruitmentMetadata(any(), anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong(), any(), any())).thenReturn(1);
    var update = command("Linux"); update.setVersion(2L); update.setWorkRegionName("驻场");
    manager.updateJd(existing.getId(), update, "legacy-region-metadata");
    verify(versions, never()).save(any());
  }

  private JdVersionEntity publicSource(String rawText) {
    var sourceVersion = new JdVersionEntity();
    sourceVersion.setId("S".repeat(26)); sourceVersion.setJdId("Q".repeat(26));
    sourceVersion.setVersionNo(1); sourceVersion.setStatus("PUBLISHED");
    sourceVersion.setTitle("IT运维工程师"); sourceVersion.setRawText(rawText);
    var source = ownedJd(); source.setId(sourceVersion.getJdId()); source.setContentShared(true);
    source.setStatus("ACTIVE");
    when(versions.findJdVersion(sourceVersion.getId())).thenReturn(sourceVersion);
    when(catalog.findJdForUpdate(source.getId())).thenReturn(source);
    return sourceVersion;
  }

  @Test
  void appliedCopyCannotBeSharedOrHaveItsFrozenContentChanged() {
    var existing = ownedJd(); existing.setAppliedFromJdVersionId("S".repeat(26));
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    var shared = command("Linux"); shared.setVersion(2L); shared.setContentShared(true);
    assertThatThrownBy(() -> manager.updateJd(existing.getId(), shared, "share-copy"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_SHARE_FORBIDDEN"));
    var changed = command("Changed"); changed.setVersion(2L);
    assertThatThrownBy(() -> manager.updateJd(existing.getId(), changed, "edit-copy"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_CONTENT_IMMUTABLE"));
    verify(jds, never()).updateRecruitmentMetadata(any(), anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong(), any(), any());
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void companyScopeCannotChangeOtherOwnersSharingState() {
    var existing = ownedJd(); existing.setOwnerUserId("N".repeat(26));
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    CurrentActorSnapshot managerActor = new CurrentActorSnapshot("H".repeat(26), "管理者",
        "manager@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("HR_DIRECTOR"), Set.of("demand.manage", "demand.read"), true, false);
    when(authorization.currentActor()).thenReturn(managerActor);
    var input = command("Linux"); input.setContentShared(true); input.setVersion(2L);
    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "share-other"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CONTENT_SHARE_FORBIDDEN"));
  }

  @Test
  void longTitleCannotBeSharedOnUpdate() {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    var input = command("Linux"); input.setTitle("岗".repeat(81));
    input.setContentShared(true); input.setVersion(2L);
    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "long-title-share"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_SHARED_TITLE_TOO_LONG"));
  }

  @Test
  void appliedJdDepartmentOutsideAffiliationsIsLockedButSuperAdminCanTransfer() {
    var existing = ownedJd(); existing.setAppliedFromJdVersionId("S".repeat(26));
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    var input = command("Linux"); input.setOwnerDepartmentId("X".repeat(26)); input.setVersion(2L);
    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "applied-transfer-denied"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_APPLICATION_DEPARTMENT_FORBIDDEN"));
    CurrentActorSnapshot admin = new CurrentActorSnapshot("H".repeat(26), "管理员",
        "admin@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("SUPER_ADMIN"), Set.of(), true, true);
    when(authorization.currentActor()).thenReturn(admin);
    version.setTitle("IT运维工程师"); version.setRawText("Linux");
    when(jds.updateRecruitmentMetadata(eq(existing.getId()), eq(1), eq(45), eq(existing.getOwnerUserId()),
        eq("X".repeat(26)), eq(false), eq(2L), any(), any())).thenReturn(1);
    manager.updateJd(existing.getId(), input, "applied-transfer-admin");
    verify(jds).updateRecruitmentMetadata(eq(existing.getId()), eq(1), eq(45),
        eq(existing.getOwnerUserId()), eq("X".repeat(26)), eq(false), eq(2L), any(), any());
  }

  @Test
  void superAdminCanChangeOwnerAndDepartmentWithoutCreatingAContentVersion() {
    String newOwnerUserId = "N".repeat(26);
    String newDepartmentId = "X".repeat(26);
    CurrentActorSnapshot actor = new CurrentActorSnapshot("H".repeat(26), "系统管理员",
        "admin@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("SUPER_ADMIN"), Set.of(), true, true);
    when(authorization.currentActor()).thenReturn(actor);
    when(identityDirectory.activeUser(newOwnerUserId))
        .thenReturn(new UserReference(newOwnerUserId, "新所属人", newDepartmentId));
    when(identityDirectory.isRecruiterOwner(newOwnerUserId)).thenReturn(true);
    var existing = ownedJd();
    version.setTitle("IT运维工程师");
    version.setRawText("Linux");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateRecruitmentMetadata(existing.getId(), 1, 45, newOwnerUserId,
        newDepartmentId, false, 2L, actor.getUserId(), Instant.parse("2026-09-07T10:00:00Z")))
        .thenReturn(1);
    var input = command("Linux");
    input.setOwnerUserId(newOwnerUserId);
    input.setOwnerDepartmentId(newDepartmentId);
    input.setVersion(2L);

    assertThat(manager.updateJd(existing.getId(), input, "owner-and-department")).isSameAs(version);

    verify(identityDirectory).activeUser(newOwnerUserId);
    verify(identityDirectory).isRecruiterOwner(newOwnerUserId);
    verify(identityDirectory).activeDepartment(newDepartmentId);
    verify(jds).updateRecruitmentMetadata(existing.getId(), 1, 45, newOwnerUserId,
        newDepartmentId, false, 2L, actor.getUserId(), Instant.parse("2026-09-07T10:00:00Z"));
    verify(ownershipHistory).insertOwnershipHistory(any(), eq("JD"), eq(existing.getId()),
        eq("H".repeat(26)), eq(newOwnerUserId), eq("D".repeat(26)), eq(newDepartmentId),
        eq("编辑 JD 所属人和归属部门"), eq(actor.getUserId()), any());
    verify(versions, never()).save(any(JdVersionEntity.class));
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void superAdminOwnerChangeIsAppliedToNewContentVersion() {
    String newOwnerUserId = "N".repeat(26);
    CurrentActorSnapshot actor = new CurrentActorSnapshot("H".repeat(26), "系统管理员",
        "admin@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(),
        Set.of("SUPER_ADMIN"), Set.of(), true, true);
    when(authorization.currentActor()).thenReturn(actor);
    when(identityDirectory.activeUser(newOwnerUserId))
        .thenReturn(new UserReference(newOwnerUserId, "新所属人", "D".repeat(26)));
    when(identityDirectory.isRecruiterOwner(newOwnerUserId)).thenReturn(true);
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var input = command("Linux 和 Kubernetes");
    input.setOwnerUserId(newOwnerUserId);
    input.setVersion(2L);

    manager.updateJd(existing.getId(), input, "owner-and-content");

    var updatedJd = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).updateCatalog(updatedJd.capture(), eq(2L));
    assertThat(updatedJd.getValue().getOwnerUserId()).isEqualTo(newOwnerUserId);
    var savedVersion = ArgumentCaptor.forClass(JdVersionEntity.class);
    verify(versions).save(savedVersion.capture());
    assertThat(savedVersion.getValue().getContentOwnerUserId()).isEqualTo(newOwnerUserId);
    verify(ownershipHistory).insertOwnershipHistory(any(), eq("JD"), eq(existing.getId()),
        eq("H".repeat(26)), eq(newOwnerUserId), eq("D".repeat(26)), eq("D".repeat(26)),
        eq("编辑 JD 所属人"), eq(actor.getUserId()), any());
  }

  @Test
  void nonAdminCannotChangeJdOwner() {
    String newOwnerUserId = "N".repeat(26);
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    var input = command("Linux");
    input.setOwnerUserId(newOwnerUserId);
    input.setVersion(2L);

    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "owner-forbidden"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_OWNER_CHANGE_ADMIN_REQUIRED"));

    verify(identityDirectory, never()).activeUser(newOwnerUserId);
    verify(jds, never()).updateRecruitmentMetadata(
        any(), anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong(), any(), any());
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void changingContentAndDepartmentCreatesVersionAndRecordsDepartmentHistory() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot("H".repeat(26), "测试HR",
        "hr@example.invalid", "D".repeat(26), Set.of("X".repeat(26)), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    var existing = ownedJd();
    version.setTitle("IT运维工程师");
    version.setRawText("Linux");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateCatalog(any(), eq(2L))).thenReturn(1);
    var input = command("Linux 和 Kubernetes");
    input.setOwnerDepartmentId("X".repeat(26));
    input.setVersion(2L);

    manager.updateJd(existing.getId(), input, "department-and-content");

    var updated = ArgumentCaptor.forClass(JdEntity.class);
    verify(jds).updateCatalog(updated.capture(), eq(2L));
    assertThat(updated.getValue().getOwnerDepartmentId()).isEqualTo("X".repeat(26));
    verify(versions).save(any(JdVersionEntity.class));
    verify(ownershipHistory).insertOwnershipHistory(any(), eq("JD"), eq(existing.getId()),
        eq(actor.getUserId()), eq(actor.getUserId()), eq("D".repeat(26)), eq("X".repeat(26)),
        eq("编辑 JD 归属部门"), eq(actor.getUserId()), any());
  }

  @Test
  void changingDepartmentOutsideAssignmentScopeIsRejectedBeforeWrite() {
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    var input = command("Linux");
    input.setOwnerDepartmentId("X".repeat(26));
    input.setVersion(2L);

    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "forbidden-department"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("OWNER_DEPARTMENT_FORBIDDEN"));
    verify(jds, never()).updateRecruitmentMetadata(
        any(), anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong(), any(), any());
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void changingToDisabledDepartmentIsRejectedBeforeWrite() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot("H".repeat(26), "测试HR",
        "hr@example.invalid", "D".repeat(26), Set.of("X".repeat(26)), Set.of(), Set.of(),
        Set.of("RECRUITER"), Set.of("demand.manage", "demand.read"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    var existing = ownedJd();
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    doThrow(new PlatformException(HttpStatus.UNPROCESSABLE_CONTENT,
        "OWNER_DEPARTMENT_NOT_ACTIVE", "部门已停用", false))
        .when(identityDirectory).activeDepartment("X".repeat(26));
    var input = command("Linux");
    input.setOwnerDepartmentId("X".repeat(26));
    input.setVersion(2L);

    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "disabled-department"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("OWNER_DEPARTMENT_NOT_ACTIVE"));
    verify(jds, never()).updateRecruitmentMetadata(
        any(), anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong(), any(), any());
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void changingOnlyHeadcountKeepsThePublishedVersionAndHonorsOccupiedCapacity() {
    var existing = ownedJd();
    version.setTitle("IT运维工程师");
    version.setRawText("Linux");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(catalog.countOccupiedHeadcountForUpdate(existing.getId())).thenReturn(1);
    when(jds.updateRecruitmentMetadata(
        eq(existing.getId()), eq(3), eq(45), eq("H".repeat(26)), eq("D".repeat(26)), eq(false), eq(2L), any(), any()))
        .thenReturn(1);
    var input = command("Linux");
    input.setHeadcount(3);
    input.setVersion(2L);

    assertThat(manager.updateJd(existing.getId(), input, "hc-only")).isSameAs(version);
    assertThat(version.getJdCode()).isEqualTo("JD-000001");
    verify(jds, never()).nextCode(anyString(), anyString(), anyString());

    verify(jds).updateRecruitmentMetadata(
        eq(existing.getId()), eq(3), eq(45), eq("H".repeat(26)), eq("D".repeat(26)), eq(false), eq(2L), any(), any());
    verify(versions, never()).save(any(JdVersionEntity.class));
    verify(jds, never()).updateCatalog(any(), anyLong());
    verifyNoInteractions(ownershipHistory);
  }

  @Test
  void changingOnlyRecruitmentCycleKeepsThePublishedVersion() {
    var existing = ownedJd();
    version.setTitle("IT运维工程师");
    version.setRawText("Linux");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(jds.updateRecruitmentMetadata(
        eq(existing.getId()), eq(1), eq(60), eq("H".repeat(26)), eq("D".repeat(26)), eq(false), eq(2L), any(), any()))
        .thenReturn(1);
    var input = command("Linux");
    input.setRecruitmentCycleDays(60);
    input.setVersion(2L);

    assertThat(manager.updateJd(existing.getId(), input, "cycle-only")).isSameAs(version);

    verify(jds).updateRecruitmentMetadata(
        eq(existing.getId()), eq(1), eq(60), eq("H".repeat(26)), eq("D".repeat(26)), eq(false), eq(2L), any(), any());
    verify(versions, never()).save(any(JdVersionEntity.class));
    verify(jds, never()).updateCatalog(any(), anyLong());
  }

  @Test
  void editingHeadcountBelowOccupiedCapacityCannotWriteTheJd() {
    var existing = ownedJd();
    version.setTitle("IT运维工程师");
    version.setRawText("Linux");
    when(catalog.findJdForUpdate(existing.getId())).thenReturn(existing);
    when(catalog.countOccupiedHeadcountForUpdate(existing.getId())).thenReturn(2);
    var input = command("Linux");
    input.setVersion(2L);

    assertThatThrownBy(() -> manager.updateJd(existing.getId(), input, "hc-too-small"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_HEADCOUNT_BELOW_OCCUPIED"));

    verify(jds, never()).updateRecruitmentMetadata(
        any(), anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong(), any(), any());
    verify(jds, never()).updateCatalog(any(), anyLong());
    verify(versions, never()).save(any(JdVersionEntity.class));
  }

  private static CreateJdVersionDTO command(String text) {
    var command = new CreateJdVersionDTO();
    command.setTitle("IT运维工程师");
    command.setDistinction("默认岗位");
    command.setRawText(text);
    command.setHeadcount(1);
    command.setRecruitmentCycleDays(45);
    command.setOwnerDepartmentId("D".repeat(26));
    command.setRecruitmentType("INTERNAL");
    return command;
  }
}
