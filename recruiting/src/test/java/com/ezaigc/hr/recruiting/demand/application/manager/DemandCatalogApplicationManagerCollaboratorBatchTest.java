package com.ezaigc.hr.recruiting.demand.application.manager;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_COLLABORATORS_PER_DEMAND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DemandCatalogApplicationManagerCollaboratorBatchTest {

  private static final String DEMAND_ID = "D".repeat(26);
  private static final String ACTOR_ID = "A".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void replacesCollaboratorsWithoutScalarPersistenceCalls() {
    DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
    DemandCollaboratorPersistenceService collaborators =
        mock(DemandCollaboratorPersistenceService.class);
    ReflectionTestUtils.setField(
        manager, "demandCollaboratorPersistenceService", collaborators);
    List<String> userIds = List.of("U".repeat(26), "V".repeat(26));
    when(collaborators.insertCollaborators(DEMAND_ID, userIds, ACTOR_ID, NOW))
        .thenReturn(true);

    ReflectionTestUtils.invokeMethod(
        manager,
        "replaceCollaborators",
        DEMAND_ID,
        new LinkedHashSet<>(userIds),
        ACTOR_ID,
        NOW);

    verify(collaborators).insertCollaborators(DEMAND_ID, userIds, ACTOR_ID, NOW);
    assertThat(mockingDetails(collaborators).getInvocations())
        .noneMatch(invocation -> invocation.getMethod().getName().equals("insertCollaborator"));
  }

  @Test
  void failsClosedWhenCollaboratorBatchWriteReportsFailure() {
    DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
    DemandCollaboratorPersistenceService collaborators =
        mock(DemandCollaboratorPersistenceService.class);
    ReflectionTestUtils.setField(
        manager, "demandCollaboratorPersistenceService", collaborators);
    List<String> userIds = List.of("U".repeat(26));

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    manager,
                    "replaceCollaborators",
                    DEMAND_ID,
                    new LinkedHashSet<>(userIds),
                    ACTOR_ID,
                    NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("招聘需求协作人批量写入失败");
  }

  @Test
  void rejectsRawCollaboratorCollectionsBeyondTheDeclaredLimit() {
    List<String> oversized =
        IntStream.rangeClosed(0, MAX_COLLABORATORS_PER_DEMAND)
            .mapToObj(index -> String.format("%026d", index))
            .toList();

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    new DemandCatalogApplicationManager(),
                    "collaboratorIds",
                    oversized,
                    ACTOR_ID))
        .hasMessage("协作人最多 50 人");
  }
}
