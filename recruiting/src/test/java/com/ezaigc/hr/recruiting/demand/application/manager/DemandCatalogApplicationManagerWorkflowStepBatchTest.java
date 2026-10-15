package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.recruiting.demand.domain.dto.ValidatedWorkflowStepDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.demand.enums.WorkflowStepCode;
import com.ezaigc.hr.recruiting.demand.service.DemandWorkflowStepPersistenceService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.assertj.core.groups.Tuple;

class DemandCatalogApplicationManagerWorkflowStepBatchTest {

  @Test
  void insertsWorkflowStepsWithoutScalarPersistenceCalls() {
    DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    DemandWorkflowStepPersistenceService persistence =
        mock(DemandWorkflowStepPersistenceService.class);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "demandWorkflowStepPersistenceService", persistence);
    when(ids.newId()).thenReturn("S".repeat(26), "T".repeat(26));
    when(persistence.insertWorkflowSteps(anyList())).thenReturn(true);
    List<ValidatedWorkflowStepDTO> steps =
        List.of(
            new ValidatedWorkflowStepDTO(
                WorkflowStepCode.CANDIDATE_COMMUNICATION, 1, true, "PHONE"),
            new ValidatedWorkflowStepDTO(WorkflowStepCode.DECISION, 2, false, null));

    ReflectionTestUtils.invokeMethod(
        manager,
        "insertSteps",
        "W".repeat(26),
        steps,
        Instant.parse("2026-09-05T00:00:00Z"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<WorkflowStepEntity>> batch = ArgumentCaptor.forClass(List.class);
    verify(persistence).insertWorkflowSteps(batch.capture());
    assertThat(batch.getValue())
        .extracting(
            WorkflowStepEntity::getId,
            WorkflowStepEntity::getTemplateVersionId,
            WorkflowStepEntity::getStepCode,
            WorkflowStepEntity::getSequenceNo,
            WorkflowStepEntity::getCandidateOptional,
            WorkflowStepEntity::getDefaultFollowUpChannel,
            WorkflowStepEntity::getCreateTime)
        .containsExactly(
            Tuple.tuple(
                "S".repeat(26),
                "W".repeat(26),
                "CANDIDATE_COMMUNICATION",
                1,
                true,
                "PHONE",
                Instant.parse("2026-09-05T00:00:00Z")),
            Tuple.tuple(
                "T".repeat(26),
                "W".repeat(26),
                "DECISION",
                2,
                false,
                null,
                Instant.parse("2026-09-05T00:00:00Z")));
    assertThat(mockingDetails(persistence).getInvocations())
        .noneMatch(invocation -> invocation.getMethod().getName().equals("insertWorkflowStep"));
  }

  @Test
  void failsClosedWhenWorkflowStepBatchWriteReportsFailure() {
    DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    DemandWorkflowStepPersistenceService persistence =
        mock(DemandWorkflowStepPersistenceService.class);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "demandWorkflowStepPersistenceService", persistence);
    when(ids.newId()).thenReturn("S".repeat(26));
    List<ValidatedWorkflowStepDTO> steps =
        List.of(new ValidatedWorkflowStepDTO(WorkflowStepCode.DECISION, 1, false, null));

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    manager,
                    "insertSteps",
                    "W".repeat(26),
                    steps,
                    Instant.parse("2026-09-05T00:00:00Z")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("招聘流程步骤批量写入失败");
  }
}
