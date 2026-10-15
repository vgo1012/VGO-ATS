package com.ezaigc.hr.recruiting.demand.service;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_COLLABORATORS_PER_DEMAND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.recruiting.demand.domain.entity.DemandCollaboratorEntity;
import com.ezaigc.hr.recruiting.demand.service.impl.DemandCollaboratorPersistenceServiceImpl;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DemandCollaboratorPersistenceServiceImplTest {

  private static final String DEMAND_ID = "D".repeat(26);
  private static final String ACTOR_ID = "A".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void mapsAllCollaboratorsIntoOneBoundedJdbcBatch() {
    DemandCollaboratorPersistenceServiceImpl service =
        spy(new DemandCollaboratorPersistenceServiceImpl());
    List<String> userIds = List.of("U".repeat(26), "V".repeat(26));
    doReturn(true)
        .when(service)
        .saveBatch(anyCollection(), eq(MAX_COLLABORATORS_PER_DEMAND));

    assertThat(service.insertCollaborators(DEMAND_ID, userIds, ACTOR_ID, NOW)).isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<DemandCollaboratorEntity>> batch =
        ArgumentCaptor.forClass(Collection.class);
    verify(service).saveBatch(batch.capture(), eq(MAX_COLLABORATORS_PER_DEMAND));
    assertThat(batch.getValue())
        .hasSize(2)
        .allSatisfy(
            collaborator -> {
              assertThat(collaborator.getDemandId()).isEqualTo(DEMAND_ID);
              assertThat(collaborator.getUserId()).isIn(userIds);
              assertThat(collaborator.getAddedByUserId()).isEqualTo(ACTOR_ID);
              assertThat(collaborator.getAddedAt()).isEqualTo(NOW);
              assertThat(collaborator.getVersion()).isZero();
            });
  }

  @Test
  void acceptsEmptyAndRejectsMalformedOrOversizedBatches() {
    DemandCollaboratorPersistenceServiceImpl service =
        new DemandCollaboratorPersistenceServiceImpl();
    List<String> oversized =
        IntStream.rangeClosed(0, MAX_COLLABORATORS_PER_DEMAND)
            .mapToObj(index -> String.format("%026d", index))
            .toList();

    assertThat(service.insertCollaborators(DEMAND_ID, List.of(), ACTOR_ID, NOW)).isTrue();
    assertThatThrownBy(
            () -> service.insertCollaborators(DEMAND_ID, List.of("short"), ACTOR_ID, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("demand collaborator batch is invalid");
    assertThatThrownBy(
            () -> service.insertCollaborators(DEMAND_ID, oversized, ACTOR_ID, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("demand collaborator batch is invalid");
  }
}
