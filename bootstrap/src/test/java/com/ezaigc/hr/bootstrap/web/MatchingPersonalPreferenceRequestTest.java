package com.ezaigc.hr.bootstrap.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.application.assembler.MatchingConfigurationAssembler;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchingConfigurationApplicationManager;
import com.ezaigc.hr.intelligence.matching.controller.MatchingConfigurationController;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.PersonalPreference;
import com.ezaigc.hr.intelligence.matching.service.MatchPlanVersionPersistenceService;
import com.ezaigc.hr.intelligence.matching.service.PersonalPreferencePersistenceService;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MatchingPersonalPreferenceRequestTest {

  private static final String CONFIG_ID = "01M1KFNYEV1VMB8D7Q69S6V622";
  private static final String URL = "/api/v1/me/jd-scoring-configs/" + CONFIG_ID + "/preferences";
  private static final String USER_ID = "user-a";

  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final MatchPlanVersionPersistenceService plans =
      mock(MatchPlanVersionPersistenceService.class);
  private final PersonalPreferencePersistenceService preferences =
      mock(PersonalPreferencePersistenceService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    CurrentActorSnapshot actor = new CurrentActorSnapshot();
    actor.setUserId(USER_ID);
    when(authorization.currentActor()).thenReturn(actor);
    MatchPlanVersion plan = new MatchPlanVersion();
    plan.setId(CONFIG_ID);
    plan.setJdVersionId("jd-version");
    when(plans.findMatchPlanVersion(CONFIG_ID)).thenReturn(plan);
    DemandQueryService demands = mock(DemandQueryService.class);
    when(demands.requireJdVersion("jd-version"))
        .thenReturn(
            new JdVersionSnapshot(
                "jd-version", "jd", "demand", "INTERNAL", USER_ID, "department", Set.of()));

    MatchingConfigurationAssembler assembler = new MatchingConfigurationAssembler();
    MatchingConfigurationApplicationManager manager = new MatchingConfigurationApplicationManager();
    ReflectionTestUtils.setField(manager, "assembler", assembler);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "matchPlanVersionPersistenceService", plans);
    ReflectionTestUtils.setField(manager, "personalPreferencePersistenceService", preferences);
    ReflectionTestUtils.setField(manager, "demandQuery", demands);
    MatchingConfigurationController controller = new MatchingConfigurationController();
    ReflectionTestUtils.setField(controller, "manager", manager);
    ReflectionTestUtils.setField(controller, "assembler", assembler);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void unsavedPreferenceReturnsSuccessfulEmptyResponseWithoutCreatingASelection() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().string(""));

    verify(preferences).findLatestPersonalPreference(USER_ID, CONFIG_ID);
    verifyNoMoreInteractions(preferences);
  }

  @ParameterizedTest
  @ValueSource(strings = {"[]", "[\"criterion-a\"]"})
  void savedPreferenceKeepsItsVersionWeightsAndExplicitSelection(String selectedIds)
      throws Exception {
    PersonalPreference saved =
        new PersonalPreference("preference-id", USER_ID, CONFIG_ID, "weights-v2", 3, selectedIds);
    saved.setCreateTime(Instant.parse("2026-09-07T00:00:00Z"));
    when(preferences.findLatestPersonalPreference(USER_ID, CONFIG_ID)).thenReturn(saved);

    mvc.perform(get(URL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("preference-id"))
        .andExpect(jsonPath("$.ownerUserId").value(USER_ID))
        .andExpect(jsonPath("$.planVersionId").value(CONFIG_ID))
        .andExpect(jsonPath("$.weightProfileVersionId").value("weights-v2"))
        .andExpect(jsonPath("$.versionNo").value(3))
        .andExpect(jsonPath("$.selectedDefinitionIdsJson").value(selectedIds))
        .andExpect(jsonPath("$.createTime").value("2026-09-07T00:00:00Z"));
  }

  @Test
  void unknownScoringConfigurationStillReturnsNotFound() throws Exception {
    when(plans.findMatchPlanVersion(CONFIG_ID)).thenReturn(null);

    mvc.perform(get(URL))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MATCH_PLAN_NOT_FOUND"));
    verifyNoInteractions(preferences);
  }

  @Test
  void deniedDemandReadStillReturnsForbiddenWithoutReadingPreferences() throws Exception {
    var denied = PlatformExceptions.forbidden("PERMISSION_DENIED", "没有查看权限");
    doThrow(denied).when(authorization).requirePermission("demand.read");
    doThrow(denied).when(authorization).requireAccess(eq("demand.read"), any());

    mvc.perform(get(URL))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    verifyNoInteractions(preferences);
  }
}
