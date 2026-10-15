package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ezaigc.hr.common.web.pagination.CursorCodec;

class IndependentScoringPlansTest {
  @Test
  void listsReusablePlansWithoutRequiringAJob() {
    var manager = new MatchingConfigurationApplicationManager();
    var auth = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.isSuperAdmin()).thenReturn(true);
    when(actor.getUserId()).thenReturn("admin");
    when(auth.currentActor()).thenReturn(actor);
    var demand = mock(DemandQueryService.class);
    var plans = mock(MatchPlanVersionPersistenceService.class);
    when(plans.findMatchPlanVersions(null, null, null, 11)).thenReturn(List.of());
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "demandQuery", demand);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
    assertThat(manager.listMatchPlans(null, null, 10).getItems()).isEmpty();
    verifyNoInteractions(demand);
  }
}
