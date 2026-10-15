package com.ezaigc.hr.bootstrap;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.recruiting.demand.application.manager.DemandCatalogApplicationManager;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** JD 版本读取必须沿用应用层对象级查看授权，并允许前端通过版本恢复 JD 上下文。 */
@NoDatabasePlatformTest
class JdVersionReadRequestTest {

  private static final String JD_VERSION_ID = "01M1KFNYEV1VMB8D7Q69S6V622";
  private static final String URL = "/api/v1/jd-versions/" + JD_VERSION_ID;

  @Resource private MockMvc mvc;
  @MockitoBean private DemandCatalogApplicationManager manager;

  @Test
  void authenticatedReaderGetsTheExistingJdVersionWithoutCsrf() throws Exception {
    JdVersionEntity version = new JdVersionEntity();
    version.setId(JD_VERSION_ID);
    version.setJdId("01M1KFNYEV1VMB8D7Q69S6V623");
    version.setVersionNo(2);
    version.setTitle("Java 后端工程师");
    version.setRawText("负责招聘平台后端开发");
    version.setStatus("PUBLISHED");
    version.setSourceType("MANUAL");
    version.setCreateId("reader-a");
    version.setCreateTime(Instant.parse("2026-09-12T00:00:00Z"));
    when(manager.getJdVersion(JD_VERSION_ID)).thenReturn(version);

    mvc.perform(get(URL).with(user("reader-a").roles("RECRUITER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(JD_VERSION_ID))
        .andExpect(jsonPath("$.jdId").value("01M1KFNYEV1VMB8D7Q69S6V623"))
        .andExpect(jsonPath("$.versionNo").value(2))
        .andExpect(jsonPath("$.title").value("Java 后端工程师"))
        .andExpect(jsonPath("$.rawText").value("负责招聘平台后端开发"))
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.sourceType").value("MANUAL"))
        .andExpect(jsonPath("$.createId").value("reader-a"))
        .andExpect(jsonPath("$.createTime").value("2026-09-12T00:00:00Z"));

    verify(manager).getJdVersion(JD_VERSION_ID);
  }

  @Test
  void anonymousRequestIsRejectedBeforeManager() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isUnauthorized());

    verifyNoInteractions(manager);
  }

  @Test
  void objectLevelDemandReadDenialIsReturnedAsForbidden() throws Exception {
    doThrow(PlatformExceptions.forbidden("PERMISSION_REQUIRED", "缺少操作权限：demand.read"))
        .when(manager)
        .getJdVersion(eq(JD_VERSION_ID));

    mvc.perform(get(URL).with(user("unprivileged").roles("RECRUITER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));

    verify(manager).getJdVersion(JD_VERSION_ID);
  }

  @Test
  void malformedVersionIdIsRejectedBeforeManager() throws Exception {
    mvc.perform(get("/api/v1/jd-versions/invalid id").with(user("reader-a").roles("RECRUITER")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(manager);
  }
}
