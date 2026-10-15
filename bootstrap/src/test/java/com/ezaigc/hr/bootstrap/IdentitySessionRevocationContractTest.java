package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.ezaigc.hr.identity.application.manager.IdentityAuthenticationApplicationManager;
import com.ezaigc.hr.identity.domain.entity.IdentityAuditEvent;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.mapper.IdentityAuditEventMapper;
import com.ezaigc.hr.identity.mapper.IdentityAdministrationMapper;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** 用确定性时序覆盖认证返回后才保存的会话，不访问真实数据库。 */
@NoDatabasePlatformTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class IdentitySessionRevocationContractTest {

  private static final String USER_ID = "01JADMIN000000000000000000";
  private static final String EMAIL = "admin@example.com";
  private final MockMvc mockMvc;
  private final PasswordEncoder passwordEncoder;
  private final IdentityAuditEventMapper auditEvents;
  private final IdentityAdministrationMapper administrationMapper;
  @MockitoBean private IdentityAccountPersistenceService accounts;
  @MockitoBean private UserSupportDepartmentPersistenceService supportDepartments;
  @MockitoSpyBean private IdentityAuthenticationApplicationManager authenticationManager;
  private UserAccountRecord account;

  @BeforeEach
  void prepareAccount() {
    account = new UserAccountRecord(USER_ID, "管理员", EMAIL, EMAIL,
        passwordEncoder.encode("Admin-Password-123"), "01JDEPARTMENT0000000000000",
        "ACTIVE", false, 2);
    when(accounts.findByNormalizedEmail(EMAIL)).thenReturn(account);
    when(accounts.lockLoginAccount(EMAIL)).thenReturn(account);
    when(accounts.findById(USER_ID)).thenReturn(account);
    when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of("SUPER_ADMIN"));
    when(accounts.findPermissionCodes(USER_ID)).thenReturn(List.of());
    when(supportDepartments.findSupportDepartmentIds(USER_ID)).thenReturn(List.of());
    when(accounts.clearLoginFailures(USER_ID)).thenReturn(1);
    when(auditEvents.insert(any(IdentityAuditEvent.class))).thenReturn(1);
    when(administrationMapper.findDepartments()).thenReturn(List.of());
  }

  @ParameterizedTest
  @CsvSource({
      "ROLE_REVOKED,/api/v1/me", "ROLE_REVOKED,/api/v1/admin/departments",
      "PASSWORD_RESET,/api/v1/me", "PASSWORD_RESET,/api/v1/admin/departments",
      "DISABLED,/api/v1/me", "DISABLED,/api/v1/admin/departments"
  })
  void rejectsSessionAfterAccountSecurityChange(String change, String path) throws Exception {
    MockHttpSession session = login();
    mockMvc.perform(get(path).session(session)).andExpect(status().isOk());
    changeAccount(change);

    mockMvc.perform(get(path).session(session)).andExpect(status().isUnauthorized());

    assertThat(session.isInvalid()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
      "ROLE_REVOKED,/api/v1/me", "ROLE_REVOKED,/api/v1/admin/departments",
      "PASSWORD_RESET,/api/v1/me", "PASSWORD_RESET,/api/v1/admin/departments",
      "DISABLED,/api/v1/me", "DISABLED,/api/v1/admin/departments"
  })
  void rejectsLateSessionSavedAfterAccountSecurityChange(String change, String path) throws Exception {
    mockMvc.perform(get(path).session(login())).andExpect(status().isOk());
    doAnswer(call -> {
      Authentication result = (Authentication) call.callRealMethod();
      // 原认证已完成，Controller 尚未创建会话；模拟另一事务此时完成变更。
      changeAccount(change);
      return result;
    }).when(authenticationManager).authenticate(anyString(), anyString());
    MockHttpSession lateSession = login();

    mockMvc.perform(get(path).session(lateSession)).andExpect(status().isUnauthorized());

    assertThat(lateSession.isInvalid()).isTrue();
  }

  @Test
  void doesNotReviveLateSessionWhenRoleGrantedAgain() throws Exception {
    mockMvc.perform(get("/api/v1/admin/departments").session(login())).andExpect(status().isOk());
    doAnswer(call -> {
      Authentication result = (Authentication) call.callRealMethod();
      changeAccount("ROLE_REVOKED");
      account.setVersion(account.getVersion() + 1);
      when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of("SUPER_ADMIN"));
      return result;
    }).when(authenticationManager).authenticate(anyString(), anyString());
    MockHttpSession lateSession = login();

    mockMvc.perform(get("/api/v1/admin/departments").session(lateSession))
        .andExpect(status().isUnauthorized());

    assertThat(lateSession.isInvalid()).isTrue();
  }

  @Test
  void invalidatedSessionCanFetchNewCsrfAndLogInAgain() throws Exception {
    MockHttpSession oldSession = login();
    changeAccount("ROLE_REVOKED");

    MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(oldSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString()).andReturn();
    MockHttpSession anonymousSession = (MockHttpSession) csrfResult.getRequest().getSession(false);
    String token = new ObjectMapper().readTree(csrfResult.getResponse().getContentAsString())
        .get("token").stringValue();

    assertThat(oldSession.isInvalid()).isTrue();
    assertThat(anonymousSession).isNotSameAs(oldSession);
    mockMvc.perform(post("/api/v1/auth/login").session(anonymousSession)
            .header("X-CSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"workEmail\":\"admin@example.com\",\"password\":\"Admin-Password-123\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.version").value("3"));
  }

  @Test
  void rejectsSessionForMissingAccount() throws Exception {
    MockHttpSession session = login();
    when(accounts.findById(USER_ID)).thenReturn(null);

    mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());

    assertThat(session.isInvalid()).isTrue();
  }

  private MockHttpSession login() throws Exception {
    return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"workEmail\":\"admin@example.com\",\"password\":\"Admin-Password-123\"}"))
        .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
  }

  private void changeAccount(String change) {
    account.setVersion(account.getVersion() + 1);
    switch (change) {
      case "ROLE_REVOKED" -> when(accounts.findRoleCodes(USER_ID)).thenReturn(List.of("RECRUITER"));
      case "PASSWORD_RESET" -> {
        account.setCredentialHash(passwordEncoder.encode("Reset-Password-123"));
        account.setMustChangePassword(true);
      }
      case "DISABLED" -> account.setStatus("DISABLED");
      default -> throw new IllegalArgumentException("unknown test change: " + change);
    }
  }
}
