package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.domain.dto.UpdateDemandDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.PositionPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DemandCollaboratorUpdateTest {
  private static final String DEMAND = "D".repeat(26);
  private static final String OWNER = "A".repeat(26);
  private static final String DEPARTMENT = "B".repeat(26);
  private static final String CLIENT = "C".repeat(26);
  private static final String POSITION = "P".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
  @Mock private AuthorizationService authorization;
  @Mock private DemandCatalogPersistenceService persistence;
  @Mock private DemandEntityPersistenceService demandEntityPersistenceService;
  @Mock private DemandCollaboratorPersistenceService demandCollaboratorPersistenceService;
  @Mock private DemandAuditEventPersistenceService demandAuditEventPersistenceService;
  @Mock private PositionPersistenceService positionPersistenceService;
  @Mock private PlatformIdGenerator ids;
  @Mock private Clock clock;
  @InjectMocks private DemandCatalogApplicationManager manager;
  private UpdateDemandDTO command;
  private AutoCloseable mocks;

  @BeforeEach
  void arrangeExistingDemand() {
    mocks = MockitoAnnotations.openMocks(this);
    var actor = new CurrentActorSnapshot();
    actor.setUserId(OWNER);
    actor.setSuperAdmin(true);
    when(authorization.currentActor()).thenReturn(actor);
    var demand = new DemandEntity();
    demand.setId(DEMAND);
    demand.setOwnerUserId(OWNER);
    demand.setOwnerDepartmentId(DEPARTMENT);
    when(demandEntityPersistenceService.findDemand(DEMAND)).thenReturn(demand);
    when(demandCollaboratorPersistenceService.findCollaboratorIds(DEMAND))
        .thenReturn(List.of("U".repeat(26)));
    var client = new ClientEntity();
    client.setId(CLIENT);
    client.setStatus("ACTIVE");
    when(persistence.findClient(CLIENT)).thenReturn(client);
    var position = new PositionEntity();
    position.setId(POSITION);
    position.setClientId(CLIENT);
    position.setStatus("ACTIVE");
    when(positionPersistenceService.findPosition(POSITION)).thenReturn(position);
    when(clock.instant()).thenReturn(NOW);
    when(demandEntityPersistenceService.updateDemand(
        DEMAND, "仅修改名称", CLIENT, null, POSITION, "INTERNAL", 1, null,
        OWNER, DEPARTMENT, 0, OWNER, NOW)).thenReturn(1);
    command = new UpdateDemandDTO();
    command.setTitle("仅修改名称");
    command.setClientId(CLIENT);
    command.setPositionId(POSITION);
    command.setRecruitmentType("INTERNAL");
    command.setHeadcount(1);
    when(demandCollaboratorPersistenceService.insertCollaborators(
        anyString(), anyList(), anyString(), any())).thenReturn(true);
  }

  @AfterEach
  void closeMocks() throws Exception { mocks.close(); }

  @Test
  void omittedCollaboratorsPreserveExistingMembership() {
    assertThat(manager.updateDemand(DEMAND, command)).isNotNull();
    verify(demandCollaboratorPersistenceService, never()).deleteCollaborators(anyString());
    verify(demandCollaboratorPersistenceService, never())
        .insertCollaborators(anyString(), anyList(), anyString(), any());
  }

  @Test
  void explicitEmptyListStillClearsCollaborators() {
    command.setCollaboratorUserIds(List.of());
    when(demandCollaboratorPersistenceService.insertCollaborators(
        eq(DEMAND), eq(List.of()), eq(OWNER), eq(NOW))).thenReturn(true);
    manager.updateDemand(DEMAND, command);
    verify(demandCollaboratorPersistenceService).deleteCollaborators(DEMAND);
    verify(demandCollaboratorPersistenceService).insertCollaborators(DEMAND, List.of(), OWNER, NOW);
  }
}
