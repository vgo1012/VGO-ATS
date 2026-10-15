package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ScoringBindingPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchingAuditEventPersistenceService;
import java.time.Clock;
import org.springframework.test.util.ReflectionTestUtils;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import org.junit.jupiter.api.Test;

class ScoringBindingTest {
  @Test
  void samePublishedVersionCanBeBoundToDifferentJobsWithoutChangingTheVersion() {
    var bindings = mock(ScoringBindingPersistenceService.class);
    var plans = mock(MatchPlanVersionPersistenceService.class);
    var auth = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.isSuperAdmin()).thenReturn(true);
    when(actor.getUserId()).thenReturn("admin");
    when(auth.currentActor()).thenReturn(actor);
    var version = new MatchPlanVersion(); version.setId("shared-v1"); version.setStatus("ACTIVE");
    when(plans.findMatchPlanVersion("shared-v1")).thenReturn(version);
    when(bindings.insertBinding(anyString(), eq("shared-v1"), eq("admin"), any())).thenReturn(1);
    var manager = new ScoringBindingApplicationManager();
    ReflectionTestUtils.setField(manager, "authorization", auth);
    ReflectionTestUtils.setField(manager, "demands", mock(DemandQueryService.class));
    ReflectionTestUtils.setField(manager, "plans", plans);
    ReflectionTestUtils.setField(manager, "bindings", bindings);
    ReflectionTestUtils.setField(manager, "audit", mock(MatchingAuditEventPersistenceService.class));
    ReflectionTestUtils.setField(manager, "ids", (PlatformIdGenerator) () -> "audit");
    ReflectionTestUtils.setField(manager, "clock", Clock.systemUTC());
    assertThat(manager.bind("jd-a", "shared-v1", 0).getPlanVersionId()).isEqualTo("shared-v1");
    assertThat(manager.bind("jd-b", "shared-v1", 0).getPlanVersionId()).isEqualTo("shared-v1");
    assertThat(version.getStatus()).isEqualTo("ACTIVE");
    assertThat(version.getJdVersionId()).isNull();
    verify(plans, times(2)).findMatchPlanVersion("shared-v1");
    verifyNoMoreInteractions(plans);
    assertThatThrownBy(() -> manager.bind("jd-a", "shared-v1", 8)).hasMessageContaining("已被修改");
    version.setStatus("DRAFT");
    assertThatThrownBy(() -> manager.bind("jd-c", "shared-v1", 0)).hasMessageContaining("已发布");
    when(actor.isSuperAdmin()).thenReturn(false);
    assertThatThrownBy(() -> manager.bind("jd-c", null, 0)).hasMessageContaining("管理员");
  }
}
