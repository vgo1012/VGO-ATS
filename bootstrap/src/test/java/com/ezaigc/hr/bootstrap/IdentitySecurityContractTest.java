package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.identity.domain.entity.IdentityAuditEvent;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.mapper.IdentityAuditEventMapper;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.ScopeGrantPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationMapper;
import jakarta.servlet.http.HttpSession;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

@NoDatabasePlatformTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class IdentitySecurityContractTest {

  private final MockMvc mockMvc;
  private final PasswordEncoder passwordEncoder;
  private final IdentityAuditEventMapper auditEventMapper;
  private final ApplicationMapper applicationMapper;

  @MockitoBean IdentityAccountPersistenceService accountMapper;
  @MockitoBean ScopeGrantPersistenceService scopeGrants;
  @MockitoBean UserSupportDepartmentPersistenceService supportDepartments;

  @Test
  void taskCenterRequiresSuperAdminAndIncludesDisabledSchedulesWithoutExecutingThem() throws Exception {
    mockMvc.perform(get("/api/v1/admin/task-center")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/admin/task-center").with(user("hr").roles("HR")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/admin/task-center/history?jobCode=RESUME_PARSE")
            .with(user("director").roles("HR_DIRECTOR")))
        .andExpect(status().isForbidden());
    MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workEmail":"admin@example.com","password":"Admin-Password-123"}
                """))
        .andExpect(status().isOk()).andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    MvcResult overview = mockMvc.perform(get("/api/v1/admin/task-center").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instanceId").isString())
        .andExpect(jsonPath("$.observedSince").isString())
        .andReturn();
    Map<String, JsonNode> entries = new HashMap<>();
    for (JsonNode entry : new ObjectMapper().readTree(overview.getResponse().getContentAsString()).get("tasks")) {
      entries.put(entry.get("code").stringValue(), entry);
    }
    assertThat(entries.get("MATCH_RUN").get("enabled").booleanValue()).isFalse();
    assertThat(entries.get("MATCH_RUN").get("persistentBacklogStatus").stringValue())
        .isEqualTo("UNSUPPORTED");
    assertThat(entries.get("MATCH_RUN").get("persistentReadyCount").isNull()).isTrue();
    assertThat(entries.get("MATCH_RUN").get("oldestReadyAt").isNull()).isTrue();
    assertThat(entries.get("resume-lifecycle-daily").get("zone").stringValue()).isEqualTo("Asia/Shanghai");
    assertThat(entries.get("monthly-report-cleanup").get("scheduleType").stringValue()).isEqualTo("FIXED_DELAY");
    assertThat(entries.get("wecom-daily-sync").get("enabled").booleanValue()).isFalse();
    assertThat(entries.get("wecom-daily-sync").get("zone").stringValue()).isEqualTo("Asia/Shanghai");
    assertThat(entries.get("wecom-sync-recovery").get("enabled").booleanValue()).isFalse();
    mockMvc.perform(get("/api/v1/admin/task-center/history?jobCode=not-registered").session(session))
        .andExpect(status().isNotFound());
  }

  @Test
  void taskCenterOpenApiPublishesItsReadOnlyContract() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/admin/task-center'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/task-center/history'].get").exists())
        .andReturn();
    String destination = System.getProperty("task.center.openapi-output");
    if (destination != null) {
      Files.writeString(Path.of(destination), result.getResponse().getContentAsString());
    }
  }

  @Test
  void runtimeFileLoggingHasSizeAgeAndArchiveBudgetLimits() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    var appender = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FILE");
    assertThat(appender).isInstanceOf(RollingFileAppender.class);
    var policy = ((RollingFileAppender<?>) appender).getRollingPolicy();
    assertThat(policy).isInstanceOf(SizeAndTimeBasedRollingPolicy.class);
    var rolling = (SizeAndTimeBasedRollingPolicy<?>) policy;
    assertThat(rolling.getMaxHistory()).isEqualTo(14);
    assertThat(rolling.isCleanHistoryOnStart()).isTrue();
    assertThat(((FileSize) ReflectionTestUtils.getField(rolling, "maxFileSize")).getSize())
        .isEqualTo(FileSize.valueOf("20MB").getSize());
    assertThat(((FileSize) ReflectionTestUtils.getField(rolling, "totalSizeCap")).getSize())
        .isEqualTo(FileSize.valueOf("1GB").getSize());
  }

  @Test
  void wecomPreviewRequiresAdminSessionAndCsrfAndDefaultsToNoExternalCalls() throws Exception {
    mockMvc.perform(get("/api/v1/admin/wecom/directory/status"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/admin/wecom/directory/preview").with(csrf())
            .with(user("ordinary").roles("HR")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/admin/wecom/directory/status")
            .with(user("ordinary").roles("HR_DIRECTOR")))
        .andExpect(status().isForbidden());
    MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workEmail":"admin@example.com","password":"Admin-Password-123"}
                """))
        .andExpect(status().isOk()).andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    mockMvc.perform(get("/api/v1/admin/wecom/directory/status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"))
        .andExpect(jsonPath("$.members").isEmpty());
    mockMvc.perform(post("/api/v1/admin/wecom/directory/preview").session(session))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/admin/wecom/directory/preview").session(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"))
        .andExpect(jsonPath("$.departments").isEmpty());
    mockMvc.perform(post("/api/v1/admin/wecom/directory/sync").session(session)
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"synthetic-request-001\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/admin/wecom/directory/sync").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"synthetic-request-001\"}"))
        .andExpect(status().isConflict());
    mockMvc.perform(post("/api/v1/admin/wecom/directory/sync").session(session).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"requestKey\":\"\"}"))
        .andExpect(status().isBadRequest());
    for (String endpoint : List.of("stored", "batches", "batches/unknown")) {
      mockMvc.perform(get("/api/v1/admin/wecom/directory/" + endpoint).with(user("ordinary").roles("HR")))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void publishedCalendarRequiresLoginAndAllowsOrdinaryAccount() throws Exception {
    mockMvc.perform(get("/api/v1/work-calendars/2026/published"))
        .andExpect(status().isUnauthorized());
    when(accountMapper.findRoleCodes("01JADMIN000000000000000000"))
        .thenReturn(List.of("RECRUITMENT_MANAGER"));
    MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workEmail":"admin@example.com","password":"Admin-Password-123"}
                """))
        .andExpect(status().isOk()).andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    mockMvc.perform(get("/api/v1/work-calendars/2026/published").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.year").value(2026))
        .andExpect(jsonPath("$.days").isEmpty())
        .andExpect(jsonPath("$.draft").doesNotExist());
    mockMvc.perform(get("/api/v1/work-calendars/1999/published").session(session))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/v1/admin/work-calendars/2026").session(session))
        .andExpect(status().isForbidden());
  }

  @BeforeEach
  void prepareAccounts() {
    when(auditEventMapper.insert(any(IdentityAuditEvent.class))).thenReturn(1);
    when(accountMapper.findByNormalizedEmail("admin@example.com"))
        .thenReturn(
            new UserAccountRecord(
                "01JADMIN000000000000000000",
                "管理员",
                "admin@example.com",
                "admin@example.com",
                passwordEncoder.encode("Admin-Password-123"),
                "01JDEPARTMENT0000000000000",
                "ACTIVE",
                false,
                2));
    when(accountMapper.findRoleCodes("01JADMIN000000000000000000"))
        .thenReturn(List.of("SUPER_ADMIN"));
    when(accountMapper.findPermissionCodes("01JADMIN000000000000000000")).thenReturn(List.of());

    when(accountMapper.findByNormalizedEmail("new@example.com"))
        .thenReturn(
            new UserAccountRecord(
                "01JNEWUSER0000000000000000",
                "新员工",
                "new@example.com",
                "new@example.com",
                passwordEncoder.encode("New-Password-123"),
                "01JDEPARTMENT0000000000000",
                "ACTIVE",
                true,
                0));
    when(accountMapper.lockLoginAccount("admin@example.com"))
        .thenAnswer(call -> accountMapper.findByNormalizedEmail("admin@example.com"));
    when(accountMapper.lockLoginAccount("new@example.com"))
        .thenAnswer(call -> accountMapper.findByNormalizedEmail("new@example.com"));
    when(accountMapper.updateLoginFailure(any(), anyInt(), any(), any())).thenReturn(1);
    when(accountMapper.clearLoginFailures(any())).thenReturn(1);
    when(accountMapper.findRoleCodes("01JNEWUSER0000000000000000"))
        .thenReturn(List.of("RECRUITER"));
    when(accountMapper.findPermissionCodes("01JNEWUSER0000000000000000"))
        .thenReturn(List.of("talent.read"));
    when(accountMapper.findById("01JADMIN000000000000000000"))
        .thenAnswer(call -> accountMapper.findByNormalizedEmail("admin@example.com"));
    when(accountMapper.findById("01JNEWUSER0000000000000000"))
        .thenAnswer(call -> accountMapper.findByNormalizedEmail("new@example.com"));
  }

  @Test
  void loginAndMeExposePersistedSupportDepartmentsForOwnJdAssignment() throws Exception {
    String supportId = "S".repeat(26);
    when(supportDepartments.findSupportDepartmentIds("01JADMIN000000000000000000"))
        .thenReturn(List.of(supportId));
    MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workEmail":"admin@example.com","password":"Admin-Password-123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportDepartmentIds[0]").value(supportId)).andReturn();
    mockMvc.perform(get("/api/v1/me").session((MockHttpSession) login.getRequest().getSession(false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportDepartmentIds[0]").value(supportId));
  }

  @Test
  void hrDirectorCannotCallAdministratorConfigurationEndpoints() throws Exception {
    mockMvc.perform(get("/api/v1/admin/users").with(user("director").roles("HR_DIRECTOR")))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/admin/users").with(user("director").roles("HR_DIRECTOR"))
        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void retiredFilterTemplatesAreNotAccessibleToAdministrators() throws Exception {
    var login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"workEmail\":\"admin@example.com\",\"password\":\"Admin-Password-123\"}"))
        .andExpect(status().isOk()).andReturn();
    var session = (MockHttpSession) login.getRequest().getSession(false);
    mockMvc.perform(get("/api/v1/filter-templates").session(session))
        .andExpect(status().isNotFound());
    mockMvc.perform(post("/api/v1/filter-templates").session(session).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void exposesCsrfAndReturnsStableCsrfFailure() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
        .andExpect(jsonPath("$.token").isNotEmpty());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"workEmail":"admin@example.com","password":"Admin-Password-123"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
  }

  @Test
  void logsInWithSessionAndPublishesCurrentActor() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"workEmail":" ADMIN@example.com ","password":"Admin-Password-123"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.superAdmin").value(true))
            .andExpect(jsonPath("$.version").value("2"))
            .andReturn();

    HttpSession session = login.getRequest().getSession(false);
    verify(auditEventMapper)
        .insert(
            argThat(
                (IdentityAuditEvent event) ->
                    "LOGIN_SUCCEEDED".equals(event.getEventType())
                        && "01JADMIN000000000000000000".equals(event.getActorUserId())
                        && "01JADMIN000000000000000000".equals(event.getTargetId())
                        && event.getBeforeSnapshot() == null
                        && event.getAfterSnapshot() == null));
    mockMvc
        .perform(get("/api/v1/me").session((MockHttpSession) session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workEmail").value("admin@example.com"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void workbenchRequiresCurrentRecruitmentPermission(boolean permitted) throws Exception {
    when(accountMapper.findRoleCodes("01JADMIN000000000000000000"))
        .thenReturn(List.of("RECRUITMENT_MANAGER"));
    when(accountMapper.findPermissionCodes("01JADMIN000000000000000000"))
        .thenReturn(permitted ? List.of("recruitment.manage") : List.of("talent.read"));
    MvcResult login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"workEmail":"admin@example.com","password":"Admin-Password-123"}
                """))
        .andExpect(status().isOk()).andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

    for (String path : List.of("/api/v1/workbench", "/api/v1/management/workbench")) {
      var response = mockMvc.perform(get(path).session(session));
      if (permitted) {
        response.andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
      } else {
        response.andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
      }
    }
    verify(applicationMapper, times(permitted ? 2 : 0))
        .findWorkItems(any(), any(Boolean.class), any(), any(Integer.class));
  }

  @Test
  void requiresPasswordChangeBeforeBusinessAccess() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"workEmail":"new@example.com","password":"New-Password-123"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn();

    mockMvc
        .perform(
            get("/api/v1/source-channels")
                .session(
                    (MockHttpSession)
                        login.getRequest().getSession(false)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
  }

  @Test
  void rejectsWrongCredentialsWithStableProblem() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"workEmail":"admin@example.com","password":"wrong-password"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

    verify(auditEventMapper)
        .insert(
            argThat(
                (IdentityAuditEvent event) ->
                    "LOGIN_FAILED".equals(event.getEventType())
                        && "00000000000000000000000000".equals(event.getActorUserId())
                        && !"admin@example.com".equals(event.getTargetId())
                        && event.getBeforeSnapshot() == null
                        && event.getAfterSnapshot() == null));
  }

  @Test
  void locksAccountAfterRepeatedFailuresEvenWhenCorrectPasswordArrives() throws Exception {
    UserAccountRecord account = accountMapper.findByNormalizedEmail("new@example.com");
    when(accountMapper.updateLoginFailure(eq(account.getId()), anyInt(), any(), any()))
        .thenAnswer(call -> {
          account.setFailedLoginCount(call.getArgument(1));
          account.setFailedLoginWindowStartedAt(call.getArgument(2));
          account.setLoginLockedUntil(call.getArgument(3));
          return 1;
        });
    for (int attempt = 0; attempt < 5; attempt++) {
      mockMvc.perform(post("/api/v1/auth/login").with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"workEmail\":\"new@example.com\",\"password\":\"wrong-password\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    mockMvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"workEmail\":\"new@example.com\",\"password\":\"New-Password-123\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
  }
}
