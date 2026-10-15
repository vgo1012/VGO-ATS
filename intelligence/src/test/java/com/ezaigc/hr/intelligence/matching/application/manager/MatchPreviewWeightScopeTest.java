package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.dto.PreviewMatchDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterionPlan;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfile;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.ControlledCriterionPlanPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfilePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class MatchPreviewWeightScopeTest {
  @ParameterizedTest
  @ValueSource(strings = {"default", "own", "peer", "zero", "negative"})
  void previewUsesDefaultOrOwnWeightsAndRejectsPeer(String kind) {
    var manager = new MatchRunApplicationManager();
    var auth = mock(AuthorizationService.class);
    var actor = new CurrentActorSnapshot(); actor.setUserId("hr-a");
    when(auth.currentActor()).thenReturn(actor);
    var plans = mock(MatchPlanVersionPersistenceService.class);
    var plan = new MatchPlanVersion(); plan.setControlledCriterionPlanVersionId("criteria");
    when(plans.findActiveMatchPlanVersion("jd")).thenReturn(plan);
    var versions = mock(WeightProfileVersionPersistenceService.class);
    var weights = new WeightProfileVersion("weights", "profile", 1, 20, 20, 20, 20, 20);
    if (kind.equals("zero") || kind.equals("negative")) {
      weights.setYearsWeight(kind.equals("zero") ? 0 : -1);
      weights.setSkillsWeight(kind.equals("zero") ? 40 : 41);
    }
    String id = kind.equals("default") ? DefaultScoringConstants.WEIGHT_VERSION_ID : "weights";
    weights.setId(id); when(versions.findWeightProfileVersion(id)).thenReturn(weights);
    var profiles = mock(WeightProfilePersistenceService.class);
    var profile = new WeightProfile(); profile.setOwnerUserId(kind.equals("peer") ? "hr-b" : "hr-a");
    when(profiles.findWeightProfile("profile")).thenReturn(profile);
    var criteria = mock(ControlledCriterionPersistenceService.class);
    when(criteria.findCriteria("criteria")).thenReturn(List.of());
    var criterionPlans = mock(ControlledCriterionPlanPersistenceService.class);
    when(criterionPlans.findCriterionPlan("criteria")).thenReturn(new ControlledCriterionPlan());
    var engine = mock(AiMatchingEvaluationManager.class);
    ReflectionTestUtils.setField(manager,"authorization",auth);
    ReflectionTestUtils.setField(manager,"matchPlanVersionPersistenceService",plans);
    ReflectionTestUtils.setField(manager,"weightProfileVersionPersistenceService",versions);
    ReflectionTestUtils.setField(manager,"weightProfilePersistenceService",profiles);
    ReflectionTestUtils.setField(manager,"controlledCriterionPersistenceService",criteria);
    ReflectionTestUtils.setField(manager,"controlledCriterionPlanPersistenceService",criterionPlans);
    ReflectionTestUtils.setField(manager,"engine",engine);
    ReflectionTestUtils.setField(manager,"jdFacts",mock(JdFactQueryService.class));
    ReflectionTestUtils.setField(manager,"resumeFacts",mock(ResumeFactQueryService.class));
    var command = new PreviewMatchDTO("jd","resume",kind.equals("default") ? null : id,List.of());
    if (kind.equals("peer")) assertThatThrownBy(() -> manager.preview(command)).hasMessageContaining("本人");
    else if (kind.equals("negative")) {
      assertThatThrownBy(() -> manager.preview(command))
          .isInstanceOfSatisfying(PlatformException.class,
              error -> assertThat(error.code()).isEqualTo("WEIGHT_PROFILE_INVALID"));
      verifyNoInteractions(engine);
    } else {
      manager.preview(command);
      verify(engine).preview(any(), any(), eq(plan), eq(weights), any(), any(), anyInt());
    }
  }
}
