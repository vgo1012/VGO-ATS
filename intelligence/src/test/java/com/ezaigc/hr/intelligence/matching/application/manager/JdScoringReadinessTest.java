package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.intelligence.matching.application.assembler.MatchingConfigurationAssembler;
import com.ezaigc.hr.intelligence.matching.domain.dto.ActiveScoringConfigurationDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingConfigurationPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class JdScoringReadinessTest {
  @ParameterizedTest
  @CsvSource({"false,false,true,true", "true,false,true,true", "true,true,true,false", "false,false,false,false"})
  void readinessDependsOnJdWorkflowAndDeletionAndAlwaysChecksObjectAccess(
      boolean publicVisible, boolean deleted, boolean activeWorkflow, boolean ready) {
    var authorization = mock(AuthorizationService.class);
    var demands = mock(DemandQueryService.class);
    var versions = mock(MatchPlanVersionPersistenceService.class);
    var assembler = mock(MatchingConfigurationAssembler.class);
    var manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "demandQuery", demands);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", versions);
    ReflectionTestUtils.setField(manager, "assembler", assembler);
    ReflectionTestUtils.setField(manager, "persistence", mock(MatchingConfigurationPersistenceService.class));
    ReflectionTestUtils.setField(manager, "controlledCriterionPlanPersistenceService", mock(ControlledCriterionPlanPersistenceService.class));
    ReflectionTestUtils.setField(manager, "controlledCriterionPersistenceService", mock(ControlledCriterionPersistenceService.class));
    var jd = new JdVersionSnapshot("version", "jd", null, "INTERNAL", "owner", "department", Set.of());
    jd.setPublicVisible(publicVisible);
    jd.setDeleted(deleted);
    when(demands.requireJdVersion("version")).thenReturn(jd);
    when(demands.hasActiveWorkflow("version")).thenReturn(activeWorkflow);
    when(versions.findActiveMatchPlanVersion("version")).thenReturn(new MatchPlanVersion());

    manager.activeForJdResponse("version");

    var result = ArgumentCaptor.forClass(ActiveScoringConfigurationDTO.class);
    verify(assembler).toResponse(result.capture());
    assertThat(result.getValue().isReadyForApplication()).isEqualTo(ready);
    verify(authorization).requireAccess(eq("demand.read"), any());
  }
}
