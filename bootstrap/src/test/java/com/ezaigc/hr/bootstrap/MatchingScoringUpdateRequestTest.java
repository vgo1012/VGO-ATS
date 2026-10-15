package com.ezaigc.hr.bootstrap;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.intelligence.matching.application.manager.MatchingConfigurationApplicationManager;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** PATCH 评分草稿在 HTTP 边界必须保留幂等与乐观锁输入。 */
@NoDatabasePlatformTest
class MatchingScoringUpdateRequestTest {

  private static final String CONFIG_ID = "01M1KFNYEV1VMB8D7Q69S6V622";
  private static final String URL = "/api/v1/admin/jd-scoring-configs/" + CONFIG_ID;
  private static final String KEY = "scoring-draft-save-7";
  private static final String BODY = """
      {"name":"Java 后端工程师评分草稿","professionalDimensions":{"skills":true,
      "roleExperience":true,"relatedYears":true,"responsibilityMatch":true,
      "industryAndProjects":true},"criteria":[],"version":"7"}
      """;

  @Resource private MockMvc mvc;
  @MockitoBean private MatchingConfigurationApplicationManager manager;

  @ParameterizedTest
  @ValueSource(strings = {
      "skills", "roleExperience", "relatedYears", "responsibilityMatch", "industryAndProjects"
  })
  void disablingAnyProfessionalDimensionIsRejectedBeforeManager(String dimension) throws Exception {
    mvc.perform(
            patch(URL)
                .with(user("super-admin").roles("SUPER_ADMIN"))
                .with(csrf())
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"" + dimension + "\":true", "\"" + dimension + "\":false")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(manager);
  }

  @Test
  void superAdminPatchForwardsStableKeyRequestAndExpectedVersion() throws Exception {
    mvc.perform(
            patch(URL)
                .with(user("super-admin").roles("SUPER_ADMIN"))
                .with(csrf())
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk());

    verify(manager)
        .updateMatchPlanResponse(
            eq(CONFIG_ID),
            argThat(
                value ->
                    "Java 后端工程师评分草稿".equals(value.getName())
                        && value.isSkillsEnabled()
                        && value.isRoleEnabled()
                        && value.isYearsEnabled()
                        && value.isResponsibilityEnabled()
                        && value.isIndustryProjectEnabled()
                        && value.getCriteria().isEmpty()
                        && value.getVersion() == 7),
            eq(KEY));
  }

  @Test
  void missingIdempotencyKeyIsRejectedBeforeManager() throws Exception {
    mvc.perform(
            patch(URL)
                .with(user("super-admin").roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(manager);
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid key", "非法键"})
  void malformedIdempotencyKeyIsRejectedBeforeManager(String key) throws Exception {
    mvc.perform(
            patch(URL)
                .with(user("super-admin").roles("SUPER_ADMIN"))
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(manager);
  }

  @Test
  void realSecurityChainRequiresAuthenticationRoleAndCsrfBeforeManager() throws Exception {
    mvc.perform(
            patch(URL)
                .with(csrf())
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            patch(URL)
                .with(user("recruiter").roles("RECRUITER"))
                .with(csrf())
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch(URL)
                .with(user("super-admin").roles("SUPER_ADMIN"))
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isForbidden());

    verifyNoInteractions(manager);
  }
}
