package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.dto.CreateMatchRunDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.PersonalPreferencePersistenceService;
import com.ezaigc.hr.intelligence.matching.service.WeightProfileVersionPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IntakeAutomaticScoringTest {
  private final MatchRunApplicationManager manager = spy(new MatchRunApplicationManager());
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final JdFactQueryService jdFacts = mock(JdFactQueryService.class);
  private final ResumeFactQueryService resumeFacts = mock(ResumeFactQueryService.class);
  private final MatchPlanVersionPersistenceService plans = mock(MatchPlanVersionPersistenceService.class);
  private final PersonalPreferencePersistenceService preferences = mock(PersonalPreferencePersistenceService.class);
  private final WeightProfileVersionPersistenceService weights = mock(WeightProfileVersionPersistenceService.class);
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();
  private final JdFactSnapshotData jd = new JdFactSnapshotData();
  private final ResumeFactSnapshotData resume = new ResumeFactSnapshotData();
  private final MatchPlanVersion plan = new MatchPlanVersion();
  private final List<String> keys = new ArrayList<>();
  private final List<CreateMatchRunDTO> commands = new ArrayList<>();

  IntakeAutomaticScoringTest() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "jdFacts", jdFacts);
    ReflectionTestUtils.setField(manager, "resumeFacts", resumeFacts);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    ReflectionTestUtils.setField(manager, "personalPreferencePersistenceService", preferences);
    ReflectionTestUtils.setField(manager, "weightProfileVersionPersistenceService", weights);
    actor.setUserId("hr");
    when(authorization.currentActor()).thenReturn(actor);
    jd.setSnapshotId("jd-facts-1");
    resume.setSnapshotId("resume-facts-1");
    plan.setId("plan-1");
    when(jdFacts.currentFacts(anyString())).thenReturn(jd);
    when(resumeFacts.currentFactBatch(any())).thenReturn(id -> resume);
    when(plans.findActiveMatchPlanVersion(anyString())).thenReturn(plan);
    var weight = new WeightProfileVersion("weights", "system", 1, 20, 20, 20, 20, 20);
    weight.setId(DefaultScoringConstants.WEIGHT_VERSION_ID);
    when(weights.findWeightProfileVersion(weight.getId())).thenReturn(weight);
    doAnswer(invocation -> {
      commands.add(invocation.getArgument(0));
      keys.add(invocation.getArgument(1));
      return new MatchRun();
    }).when(manager).create(any(), anyString());
  }

  @Test
  void reusesIdenticalInputsButSeparatesJobsFactsRulesAndOwners() {
    manager.createForIntake("jd-1", "resume-1");
    manager.createForIntake("jd-1", "resume-1");
    assertThat(keys.get(1)).isEqualTo(keys.getFirst());
    manager.createForIntake("jd-2", "resume-1");
    resume.setSnapshotId("resume-facts-2");
    manager.createForIntake("jd-1", "resume-1");
    jd.setSnapshotId("jd-facts-2");
    manager.createForIntake("jd-1", "resume-1");
    plan.setId("plan-2");
    manager.createForIntake("jd-1", "resume-1");
    actor.setUserId("another-hr");
    manager.createForIntake("jd-1", "resume-1");
    assertThat(keys.subList(1, keys.size())).doesNotHaveDuplicates();
    assertThat(commands.getFirst().getResumeVersionIds()).containsExactly("resume-1");
    assertThat(commands.getFirst().getWeightProfileVersionId()).isEqualTo(DefaultScoringConstants.WEIGHT_VERSION_ID);
  }

  @Test
  void automaticRequestIgnoresPersonalPreferenceAndUsesCanonicalCompanyDefault() {
    manager.createForIntake("jd-1", "resume-1");
    assertThat(commands.getFirst().getPersonalPreferenceVersionId()).isNull();
    assertThat(commands.getFirst().getWeightProfileVersionId())
        .isEqualTo(DefaultScoringConstants.WEIGHT_VERSION_ID);
    assertThat(commands.getFirst().getSelectedCriterionIds()).isEmpty();
    assertThat(commands.getFirst().getPurpose().getCode()).isEqualTo("COMPANY_DEFAULT");
    verify(preferences, never()).findLatestPersonalPreference(anyString(), anyString());
  }

  @Test
  void unavailableJdOrRevokedAccessCannotCreateOrReplayScore() {
    when(jdFacts.currentFacts("jd-1")).thenThrow(
        PlatformExceptions.notFound("JD_FACTS_NOT_READY", "JD 解析尚未完成"));
    assertThatThrownBy(() -> manager.createForIntake("jd-1", "resume-1"))
        .hasMessage("JD 解析尚未完成");
    verify(manager, never()).create(any(), anyString());
  }

  @Test
  void missingConfigurationDoesNotCreateTask() {
    when(plans.findActiveMatchPlanVersion("jd-1")).thenReturn(null);
    assertThatThrownBy(() -> manager.createForIntake("jd-1", "resume-1"))
        .hasMessage("公司正式评分配置尚未就绪");
    verify(manager, never()).create(any(), anyString());
  }
}
