package com.ezaigc.hr.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.HandoverResource;
import com.ezaigc.hr.identity.api.HandoverResourceContributor;
import com.ezaigc.hr.identity.application.manager.IdentityAdministrationApplicationManager;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import com.ezaigc.hr.identity.domain.entity.RoleEntity;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.service.AccountHistoryPersistenceService;
import com.ezaigc.hr.identity.service.DepartmentAssignmentQueuePersistenceService;
import com.ezaigc.hr.identity.service.HandoverBatchPersistenceService;
import com.ezaigc.hr.identity.service.HandoverItemPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import com.ezaigc.hr.identity.service.MembershipPersistenceService;
import com.ezaigc.hr.identity.service.RolePersistenceService;
import com.ezaigc.hr.identity.service.UserRolePersistenceService;
import com.ezaigc.hr.identity.service.UserSupportDepartmentPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;
import com.ezaigc.hr.identity.application.assembler.IdentityAdministrationAssembler;
import com.ezaigc.hr.identity.domain.response.HandoverBatchDetailsResponse;
import org.mockito.ArgumentMatchers;

class IdentityAdministrationServiceTest {

  private static final String ACTOR_ID = "01JACTOR00000000000000000";
  private final IdentityAdministrationPersistenceService mapper =
      mock(IdentityAdministrationPersistenceService.class);
  private final IdentityAccountPersistenceService accounts =
      mock(IdentityAccountPersistenceService.class);
  private final DepartmentAssignmentQueuePersistenceService assignmentQueues =
      mock(DepartmentAssignmentQueuePersistenceService.class);
  private final HandoverBatchPersistenceService handoverBatches =
      mock(HandoverBatchPersistenceService.class);
  private final HandoverItemPersistenceService handoverItems =
      mock(HandoverItemPersistenceService.class);
  private final RolePersistenceService roles = mock(RolePersistenceService.class);
  private final AccountHistoryPersistenceService accountHistory =
      mock(AccountHistoryPersistenceService.class);
  private final IdentityAuditEventPersistenceService auditEvents =
      mock(IdentityAuditEventPersistenceService.class);
  private final MembershipPersistenceService memberships =
      mock(MembershipPersistenceService.class);
  private final UserRolePersistenceService userRoles = mock(UserRolePersistenceService.class);
  private final UserSupportDepartmentPersistenceService supportDepartments =
      mock(UserSupportDepartmentPersistenceService.class);

  @ParameterizedTest
  @EnumSource(value = IdentityActivationStatus.class, names = {"ACTIVE", "DISABLED"})
  @ResourceLock(Resources.LOCALE)
  void normalizesDepartmentAndUserStatusIndependentlyOfDefaultLocale(IdentityActivationStatus status) {
    Instant now = Instant.parse("2026-09-01T12:00:00Z");
    String code = status.getCode();
    String lowerCode = code.toLowerCase(Locale.ROOT);
    String input = "\u2003" + lowerCode + "\u2003";
    when(mapper.findDepartmentById("department"))
        .thenReturn(new DepartmentRecord("department", "DEP_1", "部门", null, null, null, code, 2));
    when(mapper.updateDepartment("department", "部门", null, code, 1, ACTOR_ID, now)).thenReturn(1);
    var manager = service(List.of());
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertAll(
          () -> assertThat(manager.users(null, lowerCode, 200)).isEmpty(),
          () -> assertThat(manager.updateDepartment("department", "部门", null, null, input, 1).getStatus())
              .isEqualTo(code));
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
    verify(mapper).findUsers(null, code, 200);
    verify(mapper).updateDepartment("department", "部门", null, code, 1, ACTOR_ID, now);
  }

  @Test
  void rejectsUnknownStatusAndDeletedDepartmentBeforeWriting() {
    when(mapper.findDepartmentById("department"))
        .thenReturn(new DepartmentRecord("department", "DEP_1", "部门", null, null, null, "ACTIVE", 1));
    var manager = service(List.of());

    assertThatThrownBy(() -> manager.users(null, "unknown", 200))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("ENUM_VALUE_INVALID"));
    assertThatThrownBy(() -> manager.updateDepartment("department", "部门", null, null, "deleted", 1))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("ENUM_VALUE_INVALID"));
    verify(mapper, never()).findUsers(any(), any(), eq(200));
    verify(mapper, never()).updateDepartment(any(), any(), any(), any(), eq(1L), any(), any());
    verifyNoInteractions(assignmentQueues, auditEvents);
  }

  @ParameterizedTest
  @ValueSource(strings = {"123456", "abcdef", "ABCDEF"})
  void changesToSimplePasswordAndStillInvalidatesSessions(String password) {
    when(accounts.findById(ACTOR_ID)).thenReturn(activeUser(ACTOR_ID));
    when(accounts.updateCredential(eq(ACTOR_ID), anyString(), eq(false), eq(ACTOR_ID), any()))
        .thenReturn(1);

    service(List.of()).changeOwnPassword("secret", password);

    verify(accounts).updateCredential(
        eq(ACTOR_ID),
        argThat(hash -> !hash.equals(password)
            && PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(password, hash)),
        eq(false), eq(ACTOR_ID), any());
    verify(mapper).deleteSessionsByPrincipalName("user@example.com");
    verify(accountHistory).insertAccountHistory(
        anyString(), eq(ACTOR_ID), eq("PASSWORD_CHANGED"), any(), anyString(), any(),
        eq(ACTOR_ID), any(), eq(1L));
    verify(auditEvents).insertAudit(
        anyString(), eq("PASSWORD_CHANGED"), eq(ACTOR_ID), eq("USER_ACCOUNT"),
        eq(ACTOR_ID), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsWrongCurrentPasswordAndUnchangedPasswordWithoutWriting() {
    when(accounts.findById(ACTOR_ID)).thenReturn(activeUser(ACTOR_ID));
    IdentityAdministrationApplicationManager manager = service(List.of());

    assertThatThrownBy(() -> manager.changeOwnPassword("wrong", "123456"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURRENT_PASSWORD_INVALID"));
    assertThatThrownBy(() -> manager.changeOwnPassword("secret", "secret"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("PASSWORD_REUSE_NOT_ALLOWED"));
    verify(accounts, never()).updateCredential(anyString(), anyString(), eq(false), anyString(), any());
    verifyNoInteractions(mapper, accountHistory, auditEvents);
  }

  @ParameterizedTest
  @ValueSource(strings = {"user@example.com", " USER@EXAMPLE.COM "})
  void rejectsOwnEmailAsNewPasswordWithoutWriting(String password) {
    when(accounts.findById(ACTOR_ID)).thenReturn(activeUser(ACTOR_ID));

    assertThatThrownBy(() -> service(List.of()).changeOwnPassword("secret", password))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("PASSWORD_EMAIL_NOT_ALLOWED"));
    verify(accounts, never()).updateCredential(anyString(), anyString(), eq(false), anyString(), any());
    verifyNoInteractions(mapper, accountHistory, auditEvents);
  }

  @Test
  void createDepartmentGeneratesItsBusinessCodeFromTheNewId() {
    String generatedId = "01JTEST000000000000000000";
    String generatedCode = "DEP_" + generatedId;
    when(mapper.findDepartmentById(generatedId))
        .thenReturn(
            new DepartmentRecord(
                generatedId, generatedCode, "研发部", null, null, null, "ACTIVE", 0));

    var response = service(List.of()).createDepartment("研发部", null);

    assertThat(response.getCode()).isEqualTo(generatedCode);
    verify(mapper)
        .insertDepartment(
            generatedId,
            generatedCode,
            "研发部",
            null,
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z"));
    verify(mapper, never()).countDepartmentCode(anyString());
  }

  @Test
  void deletesUnreferencedDepartmentWithVersionAndAudit() {
    DepartmentRecord department =
        new DepartmentRecord("department", "DEP_1", "待删除部门", null, null, null, "DISABLED", 3);
    when(mapper.findDepartmentById("department")).thenReturn(department);
    when(mapper.deleteDepartment("department", 3)).thenReturn(1);

    service(List.of()).deleteDepartment("department", 3, "组织清理");

    verify(mapper).deleteDepartment("department", 3);
    verify(auditEvents)
        .insertAudit(
            "01JTEST000000000000000000",
            "DEPARTMENT_DELETED",
            ACTOR_ID,
            "DEPARTMENT",
            "department",
            "组织清理",
            "{\"code\":\"DEP_1\"}",
            null,
            null,
            Instant.parse("2026-09-01T12:00:00Z"));
  }

  @Test
  void mapsDepartmentForeignKeyProtectionToStableConflict() {
    when(mapper.findDepartmentById("department"))
        .thenReturn(
            new DepartmentRecord(
                "department", "DEP_1", "被引用部门", null, null, null, "ACTIVE", 1));
    when(mapper.deleteDepartment("department", 1))
        .thenThrow(new DataIntegrityViolationException("foreign key"));

    assertThatThrownBy(() -> service(List.of()).deleteDepartment("department", 1, "组织清理"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEPARTMENT_IN_USE"));
    verifyNoInteractions(auditEvents);
  }

  @Test
  void rejectsStaleDepartmentDeleteVersion() {
    when(mapper.findDepartmentById("department"))
        .thenReturn(
            new DepartmentRecord(
                "department", "DEP_1", "部门", null, null, null, "ACTIVE", 2));
    when(mapper.deleteDepartment("department", 1)).thenReturn(0);

    assertThatThrownBy(() -> service(List.of()).deleteDepartment("department", 1, "组织清理"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEPARTMENT_VERSION_CONFLICT"));
    verifyNoInteractions(auditEvents);
  }

  @Test
  void neverRevokesTheLastActiveSuperAdmin() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(mapper.findActiveSuperAdminUserIdsForUpdate()).thenReturn(List.of("target"));

    assertThatThrownBy(() -> service(List.of()).revokeSuperAdmin("target", "组织调整"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("LAST_SUPER_ADMIN_REQUIRED"));
  }

  @Test
  void refusesDisableWhileAMandatoryBusinessResourceStillNeedsHandover() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.findRoleCodes("target")).thenReturn(List.of("RECRUITER"));
    HandoverResourceContributor contributor = mock(HandoverResourceContributor.class);
    when(contributor.resourceType()).thenReturn("APPLICATION");
    when(contributor.preview("target"))
        .thenReturn(
            List.of(new HandoverResource("APPLICATION", "app-1", "候选人申请", true, true, null)));

    assertThatThrownBy(() -> service(List.of(contributor)).disableUser("target", 0, "离职", false))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("MANDATORY_HANDOVER_REQUIRED"));
  }

  @Test
  void detectsDepartmentCyclesBeforeWriting() {
    when(mapper.findDepartmentById("child"))
        .thenReturn(new DepartmentRecord("child", "CHILD", "子部门", "root", null, null, "ACTIVE", 0));
    when(mapper.findDepartmentById("root"))
        .thenReturn(new DepartmentRecord("root", "ROOT", "总部", null, null, null, "ACTIVE", 0));

    assertThatThrownBy(
            () -> service(List.of()).updateDepartment("root", "总部", "child", null, "ACTIVE", 0))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEPARTMENT_CYCLE"));
  }

  @Test
  void keepsAtLeastOneBusinessRoleOnEveryActiveAccount() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.findRoleCodes("target")).thenReturn(List.of("RECRUITER"));

    assertThatThrownBy(() -> service(List.of()).revokeRole("target", "RECRUITER", "角色调整"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("BUSINESS_ROLE_REQUIRED"));
  }

  @Test
  void invalidatesSessionsWhenBusinessRolesChange() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.findRoleCodes("target")).thenReturn(List.of("RECRUITER", "INTERVIEWER"));
    when(roles.findActiveRoleId("INTERVIEWER")).thenReturn("role-interviewer");
    when(userRoles.deleteUserRole("target", "role-interviewer")).thenReturn(1);
    when(accounts.advanceSessionVersion("target", ACTOR_ID, Instant.parse("2026-09-01T12:00:00Z")))
        .thenReturn(1);

    service(List.of()).revokeRole("target", "INTERVIEWER", "岗位调整");

    verify(mapper).deleteSessionsByPrincipalName("user@example.com");
    verify(accounts).advanceSessionVersion("target", ACTOR_ID, Instant.parse("2026-09-01T12:00:00Z"));
  }

  @Test
  void updateUserReloadsTheUpdatedAccountWithoutScanningABoundedUserPage() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(mapper.listByIds(Set.of("department")))
        .thenReturn(
            List.of(
                new DepartmentRecord(
                    "department", "DEPARTMENT", "部门", null, null, null, "ACTIVE", 0)));
    when(accounts.updateUser(
            "target",
            "更新账号",
            "user@example.com",
            "user@example.com",
            null,
            "department",
            null,
            0,
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z")))
        .thenReturn(1);
    when(memberships.insertOrganizationMemberships(
            "target",
            "department",
            Set.of(),
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z")))
        .thenReturn(true);
    when(mapper.findUserById("target"))
        .thenReturn(
            new AdminUserRecord(
                "target",
                "更新账号",
                "user@example.com",
                null,
                "department",
                "部门",
                null,
                null,
                "ACTIVE",
                false,
                1));

    var response =
        service(List.of())
            .updateUser(
                "target",
                "更新账号",
                "user@example.com",
                null,
                "department",
                null,
                Set.of(),
                0,
                "组织调整");

    assertThat(response.getId()).isEqualTo("target");
    assertThat(response.getDisplayName()).isEqualTo("更新账号");
    verify(mapper).findUserById("target");
    verify(mapper, never()).findUsers(null, null, 200);
  }

  @Test
  void createUserValidatesAllDepartmentAssignmentsWithOneBatchQuery() {
    String homeDepartmentId = "D".repeat(26);
    Set<String> supportDepartmentIds = Set.of("E".repeat(26), "F".repeat(26), "G".repeat(26));
    Set<String> requestedDepartmentIds =
        Set.of(homeDepartmentId, "E".repeat(26), "F".repeat(26), "G".repeat(26));
    when(mapper.listByIds(requestedDepartmentIds))
        .thenReturn(
            requestedDepartmentIds.stream()
                .map(
                    id ->
                        new DepartmentRecord(
                            id, id, id, null, null, null, "ACTIVE", 0))
                .toList());
    when(roles.findActiveRolesByCodes(Set.of("RECRUITER")))
        .thenReturn(List.of(role("R".repeat(26), "RECRUITER")));
    when(memberships.insertOrganizationMemberships(
            "01JTEST000000000000000000",
            homeDepartmentId,
            supportDepartmentIds,
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z")))
        .thenReturn(true);
    when(supportDepartments.insertSupportDepartments(
            "01JTEST000000000000000000",
            supportDepartmentIds,
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z")))
        .thenReturn(true);
    when(userRoles.insertUserRoles(
            "01JTEST000000000000000000",
            Set.of("R".repeat(26)),
            ACTOR_ID,
            "创建账号",
            Instant.parse("2026-09-01T12:00:00Z")))
        .thenReturn(true);

    var response =
        service(List.of())
            .createUser(
                "批量部门账号",
                " Batch-Department@Example.COM ",
                null,
                homeDepartmentId,
                null,
                supportDepartmentIds,
                Set.of("RECRUITER"),
                "创建账号");

    assertThat(response.getUserId()).isNotBlank();
    String credential = response.getOneTimeCredential();
    assertThat(credential != null && credential.matches("[A-Za-z0-9_-]{32}"))
        .as("新建账号应返回安全随机的一次性口令").isTrue();
    assertThat(credential.equals("batch-department@example.com")).isFalse();
    assertThat(response.isMustChangePassword()).isTrue();
    verify(accounts).insertUser(
        eq("01JTEST000000000000000000"), eq("批量部门账号"),
        eq("Batch-Department@Example.COM"), eq("batch-department@example.com"),
        argThat(hash -> !hash.equals(credential)
            && PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(credential, hash)
            && !PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches("batch-department@example.com", hash)),
        eq(null), eq(homeDepartmentId), eq(null), eq(ACTOR_ID), any());
    verify(mapper).listByIds(requestedDepartmentIds);
    verify(mapper, never()).findDepartmentById(ArgumentMatchers.anyString());
    verify(memberships)
        .insertOrganizationMemberships(
            "01JTEST000000000000000000",
            homeDepartmentId,
            supportDepartmentIds,
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z"));
    verify(supportDepartments)
        .insertSupportDepartments(
            "01JTEST000000000000000000",
            supportDepartmentIds,
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z"));
    assertThat(mockingDetails(memberships).getInvocations())
        .noneMatch(invocation -> invocation.getMethod().getName().equals("insertMembership"));
    assertThat(mockingDetails(supportDepartments).getInvocations())
        .noneMatch(
            invocation -> invocation.getMethod().getName().equals("insertSupportDepartment"));
  }

  @Test
  void createUserDoesNotResolveOrWriteRolesOneRowAtATime() {
    String departmentId = "D".repeat(26);
    Set<String> assignedRoles = Set.of("RECRUITER", "RECRUITMENT_MANAGER", "INTERVIEWER");
    when(mapper.listByIds(Set.of(departmentId)))
        .thenReturn(
            List.of(
                new DepartmentRecord(
                    departmentId,
                    "DEPARTMENT",
                    "部门",
                    null,
                    null,
                    null,
                    "ACTIVE",
                    0)));
    Set<String> roleIds = Set.of("R".repeat(26), "M".repeat(26), "I".repeat(26));
    when(roles.findActiveRolesByCodes(assignedRoles))
        .thenReturn(
            List.of(
                role("R".repeat(26), "RECRUITER"),
                role("M".repeat(26), "RECRUITMENT_MANAGER"),
                role("I".repeat(26), "INTERVIEWER")));
    when(memberships.insertOrganizationMemberships(
            anyString(), anyString(), any(), anyString(), any(Instant.class)))
        .thenReturn(true);
    when(supportDepartments.insertSupportDepartments(
            anyString(), any(), anyString(), any(Instant.class)))
        .thenReturn(true);
    when(userRoles.insertUserRoles(
            anyString(), any(), anyString(), anyString(), any(Instant.class)))
        .thenReturn(true);

    service(List.of())
        .createUser(
            "批量角色账号",
            "batch-role@example.com",
            null,
            departmentId,
            null,
            Set.of(),
            assignedRoles,
            "创建账号");

    verify(roles).findActiveRolesByCodes(assignedRoles);
    verify(userRoles)
        .insertUserRoles(
            "01JTEST000000000000000000",
            roleIds,
            ACTOR_ID,
            "创建账号",
            Instant.parse("2026-09-01T12:00:00Z"));
    verify(roles, never()).findActiveRoleId(anyString());
    verify(userRoles, never())
        .insertUserRole(anyString(), anyString(), anyString(), anyString(), any(Instant.class));
  }

  @Test
  void createUserRejectsRoleCollectionsBeyondTheApplicationLimit() {
    assertThatThrownBy(
            () ->
                service(List.of())
                    .createUser(
                        "超限角色账号",
                        "too-many-roles@example.com",
                        null,
                        "D".repeat(26),
                        null,
                        Set.of(),
                        Set.of("RECRUITER", "RECRUITMENT_MANAGER", "INTERVIEWER", "SUPER_ADMIN"),
                        "创建账号"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("ROLE_LIMIT"));
    verifyNoInteractions(roles, userRoles);
  }

  @Test
  void refusesDisableWhileUserOwnsAnUnassignedWorkQueue() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.findRoleCodes("target")).thenReturn(List.of("RECRUITER"));
    when(assignmentQueues.countAssignmentQueuesOwnedBy("target")).thenReturn(1);

    assertThatThrownBy(() -> service(List.of()).disableUser("target", 0, "离职", false))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception ->
                assertThat(exception.code()).isEqualTo("ASSIGNMENT_QUEUE_REASSIGNMENT_REQUIRED"));
  }

  @Test
  void refusesDeletingTheCurrentAccount() {
    when(accounts.findById(ACTOR_ID)).thenReturn(activeUser(ACTOR_ID));

    assertThatThrownBy(() -> service(List.of()).deleteUser(ACTOR_ID, 0, "清理账号"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("SELF_DELETE_NOT_ALLOWED"));
  }

  @Test
  void refusesDeletingTheLastActiveSuperAdmin() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.findRoleCodes("target")).thenReturn(List.of("SUPER_ADMIN"));
    when(mapper.findActiveSuperAdminUserIdsForUpdate()).thenReturn(List.of("target"));

    assertThatThrownBy(() -> service(List.of()).deleteUser("target", 0, "清理账号"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("LAST_SUPER_ADMIN_REQUIRED"));
  }

  @Test
  void refusesDeletingAnAccountWithWorkWaitingForHandover() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.findRoleCodes("target")).thenReturn(List.of("RECRUITER"));
    HandoverResourceContributor contributor = mock(HandoverResourceContributor.class);
    when(contributor.resourceType()).thenReturn("APPLICATION");
    when(contributor.preview("target"))
        .thenReturn(
            List.of(new HandoverResource("APPLICATION", "app-1", "候选人申请", true, true, null)));

    assertThatThrownBy(() -> service(List.of(contributor)).deleteUser("target", 0, "清理账号"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("HANDOVER_REQUIRED_BEFORE_DELETE"));
  }

  @Test
  void logicallyDeletesAnAccountAfterHandoverIsClear() {
    UserAccountRecord target = activeUser("target");
    when(accounts.findById("target")).thenReturn(target);
    when(accounts.findRoleCodes("target")).thenReturn(List.of("RECRUITER"));
    when(accounts.deleteUser(
            "target", 0, ACTOR_ID, Instant.parse("2026-09-01T12:00:00Z")))
        .thenReturn(1);

    service(List.of()).deleteUser("target", 0, "清理账号");

    verify(accounts)
        .deleteUser("target", 0, ACTOR_ID, Instant.parse("2026-09-01T12:00:00Z"));
    verify(mapper).deleteSessionsByPrincipalName("user@example.com");
  }

  @Test
  void adminResetReturnsRandomCredentialStoresOnlyItsHashAndRevokesSessions() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.updateCredential(eq("target"), anyString(), eq(true), eq(ACTOR_ID), any()))
        .thenReturn(1);

    var result = service(List.of()).resetCredential("target", "管理员手工重置");

    String credential = result.getOneTimeCredential();
    assertThat(credential != null && credential.matches("[A-Za-z0-9_-]{32}"))
        .as("重置账号应返回安全随机的一次性口令").isTrue();
    assertThat(credential.equals("user@example.com")).isFalse();
    assertThat(result.isMustChangePassword()).isTrue();
    verify(accounts).updateCredential(eq("target"),
        argThat(hash -> !hash.equals(credential)
            && PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(credential, hash)
            && !PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches("user@example.com", hash)), eq(true), eq(ACTOR_ID), any());
    verify(mapper).deleteSessionsByPrincipalName("user@example.com");
    verify(accounts, never()).advanceSessionVersion(anyString(), anyString(), any());
    verify(accountHistory).insertAccountHistory(anyString(), eq("target"),
        eq("CREDENTIAL_RESET"), any(), anyString(), eq("管理员手工重置"),
        eq(ACTOR_ID), any(), eq(1L));
  }

  @Test
  void repeatedResetsIssueIndependentCredentials() {
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    when(accounts.updateCredential(eq("target"), anyString(), eq(true), eq(ACTOR_ID), any()))
        .thenReturn(1);
    var manager = service(List.of());

    String first = manager.resetCredential("target", "第一次重置").getOneTimeCredential();
    String second = manager.resetCredential("target", "再次重置").getOneTimeCredential();

    assertThat(first.equals(second)).as("每次重置必须生成独立口令").isFalse();
  }

  @Test
  void createUserStillRejectsMalformedEmailBeforeWriting() {
    assertThatThrownBy(() -> service(List.of()).createUser(
        "测试账号", "not-an-email", null, "D".repeat(26), null,
        Set.of(), Set.of("RECRUITER"), "创建账号"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("WORK_EMAIL_INVALID"));
    verifyNoInteractions(accounts, accountHistory, auditEvents);
  }

  @Test
  void returnsTheOriginalHandoverResultForAnIdempotentRetry() {
    when(accounts.findById("source")).thenReturn(activeUser("source"));
    when(accounts.findById("target")).thenReturn(activeUser("target"));
    HandoverResourceContributor contributor = mock(HandoverResourceContributor.class);
    HandoverBatchRecord existing =
        new HandoverBatchRecord(
            "batch-1",
            "source",
            "target",
            "ALL",
            "SUCCEEDED",
            1,
            1,
            0,
            0,
            "离职交接",
            ACTOR_ID,
            Instant.parse("2026-09-01T12:00:00Z"),
            Instant.parse("2026-09-01T12:00:01Z"),
            1,
            "handover-key");
    when(handoverBatches.findHandoverByIdempotency(ACTOR_ID, "handover-key"))
        .thenReturn(existing);
    when(handoverItems.findHandoverItems("batch-1")).thenReturn(List.of());

    HandoverBatchDetailsResponse result =
        service(List.of(contributor))
            .executeHandover("source", "target", "ALL", Set.of(), "离职交接", "handover-key");

    assertThat(result.getBatch().getId()).isEqualTo(existing.getId());
    verifyNoInteractions(contributor);
  }

  private IdentityAdministrationApplicationManager service(
      List<HandoverResourceContributor> contributors) {
    PlatformIdGenerator ids = () -> "01JTEST000000000000000000";
    CurrentActorProvider actors = () -> Optional.of(new CurrentActor(ACTOR_ID, Set.of(), true));
    IdentityAdministrationApplicationManager service =
        new IdentityAdministrationApplicationManager();
    ReflectionTestUtils.setField(service, "persistence", mapper);
    ReflectionTestUtils.setField(service, "departmentAssignmentQueuePersistenceService", assignmentQueues);
    ReflectionTestUtils.setField(service, "handoverBatchPersistenceService", handoverBatches);
    ReflectionTestUtils.setField(service, "handoverItemPersistenceService", handoverItems);
    ReflectionTestUtils.setField(service, "rolePersistenceService", roles);
    ReflectionTestUtils.setField(service, "accountHistoryPersistenceService", accountHistory);
    ReflectionTestUtils.setField(service, "identityAuditEventPersistenceService", auditEvents);
    ReflectionTestUtils.setField(service, "membershipPersistenceService", memberships);
    ReflectionTestUtils.setField(service, "userRolePersistenceService", userRoles);
    ReflectionTestUtils.setField(
        service, "userSupportDepartmentPersistenceService", supportDepartments);
    ReflectionTestUtils.setField(service, "accounts", accounts);
    ReflectionTestUtils.setField(service, "ids", ids);
    ReflectionTestUtils.setField(service, "actorProvider", actors);
    ReflectionTestUtils.setField(
        service, "passwordEncoder", PasswordEncoderFactories.createDelegatingPasswordEncoder());
    ReflectionTestUtils.setField(
        service, "clock", Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(service, "handoverContributors", contributors);
    ReflectionTestUtils.setField(
        service,
        "assembler",
        new IdentityAdministrationAssembler());
    return service;
  }

  private static UserAccountRecord activeUser(String id) {
    return new UserAccountRecord(
        id,
        "账号",
        "user@example.com",
        "user@example.com",
        "{noop}secret",
        "department",
        "ACTIVE",
        false,
        0);
  }

  private static RoleEntity role(String id, String code) {
    RoleEntity role = new RoleEntity();
    role.setId(id);
    role.setCode(code);
    return role;
  }
}
