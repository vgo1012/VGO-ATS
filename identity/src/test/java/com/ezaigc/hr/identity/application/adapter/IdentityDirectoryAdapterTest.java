package com.ezaigc.hr.identity.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityDirectoryAdapterTest {

  private static final String FIRST_USER_ID = "A".repeat(26);
  private static final String SECOND_USER_ID = "B".repeat(26);

  private final IdentityAccountPersistenceService accounts =
      mock(IdentityAccountPersistenceService.class);
  private final IdentityDirectoryAdapter directory = new IdentityDirectoryAdapter();
  private final IdentityAdministrationPersistenceService administration =
      mock(IdentityAdministrationPersistenceService.class);

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(directory, "accounts", accounts);
    ReflectionTestUtils.setField(directory, "administration", administration);
  }

  @Test
  void activeDepartmentRejectsMissingOrDisabledMasterData() {
    DepartmentRecord disabled = new DepartmentRecord();
    disabled.setId("D".repeat(26));
    disabled.setStatus("DISABLED");
    when(administration.findDepartmentById(disabled.getId())).thenReturn(disabled);

    assertThatThrownBy(() -> directory.activeDepartment(disabled.getId()))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("OWNER_DEPARTMENT_NOT_ACTIVE"));
    assertThatThrownBy(() -> directory.activeDepartment("X".repeat(26)))
        .isInstanceOfSatisfying(PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("OWNER_DEPARTMENT_NOT_ACTIVE"));
  }

  @Test
  void onlyActiveRecruitmentAccountsCanOwnJds() {
    when(accounts.findById(FIRST_USER_ID)).thenReturn(activeUser(FIRST_USER_ID));
    for (String role : List.of("RECRUITER", "RECRUITMENT_MANAGER", "HR_DIRECTOR", "SUPER_ADMIN")) {
      when(accounts.findRoleCodes(FIRST_USER_ID)).thenReturn(List.of(role));
      assertThat(directory.isRecruiterOwner(FIRST_USER_ID)).isTrue();
    }
    when(accounts.findRoleCodes(FIRST_USER_ID)).thenReturn(List.of("INTERVIEWER"));
    assertThat(directory.isRecruiterOwner(FIRST_USER_ID)).isFalse();
    when(accounts.findById(FIRST_USER_ID)).thenReturn(inactiveUser(FIRST_USER_ID));
    when(accounts.findRoleCodes(FIRST_USER_ID)).thenReturn(List.of("RECRUITER"));
    assertThat(directory.isRecruiterOwner(FIRST_USER_ID)).isFalse();
    assertThat(directory.isRecruiterOwner(SECOND_USER_ID)).isFalse();
  }

  @Test
  void loadsMultipleActiveUsersWithOneBatchQuery() {
    when(accounts.listByIds(anyCollection()))
        .thenReturn(List.of(activeUser(FIRST_USER_ID), activeUser(SECOND_USER_ID)));

    var users = directory.activeUsers(Set.of(FIRST_USER_ID, SECOND_USER_ID));

    assertThat(users).containsOnlyKeys(FIRST_USER_ID, SECOND_USER_ID);
    verify(accounts)
        .listByIds(
            argThat(
                ids ->
                    ids.size() == 2
                        && ids.contains(FIRST_USER_ID)
                        && ids.contains(SECOND_USER_ID)));
    verify(accounts, never()).findById(anyString());
  }

  @Test
  void rejectsTheWholeBatchWhenAnyRequestedUserIsNotActive() {
    when(accounts.listByIds(anyCollection()))
        .thenReturn(List.of(activeUser(FIRST_USER_ID), inactiveUser(SECOND_USER_ID)));

    assertThatThrownBy(() -> directory.activeUsers(Set.of(FIRST_USER_ID, SECOND_USER_ID)))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("TARGET_USER_NOT_ACTIVE"));
  }

  @Test
  void rejectsNullBatchBeforePersistenceAccess() {
    assertThatThrownBy(() -> directory.activeUsers(null))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("TARGET_USERS_REQUIRED"));

    verifyNoInteractions(accounts);
  }

  @Test
  void rejectsOversizedBatchBeforePersistenceAccess() {
    Set<String> oversizedBatch =
        IntStream.rangeClosed(0, IdentityDirectoryService.MAX_ACTIVE_USER_BATCH_SIZE)
            .mapToObj(value -> "%026d".formatted(value))
            .collect(Collectors.toSet());

    assertThatThrownBy(() -> directory.activeUsers(oversizedBatch))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("TARGET_USERS_LIMIT_EXCEEDED"));

    verifyNoInteractions(accounts);
  }

  @Test
  void displayNamesIncludeDisabledOwnersAndDepartmentsWithoutRequiringAssignmentEligibility() {
    when(accounts.listByIds(anyCollection())).thenReturn(List.of(inactiveUser(FIRST_USER_ID)));
    DepartmentRecord department = new DepartmentRecord();
    department.setId("D".repeat(26));
    department.setName("历史归属部门");
    department.setStatus("DISABLED");
    when(administration.listByIds(anyCollection())).thenReturn(List.of(department));

    assertThat(directory.userNames(Set.of(FIRST_USER_ID, SECOND_USER_ID)))
        .containsOnlyKeys(FIRST_USER_ID).containsEntry(FIRST_USER_ID, "测试用户");
    assertThat(directory.departmentNames(Set.of(department.getId(), "X".repeat(26))))
        .containsOnlyKeys(department.getId()).containsEntry(department.getId(), "历史归属部门");
    verify(accounts, never()).findById(anyString());
    verify(administration, never()).findDepartmentById(anyString());
  }

  @Test
  void emptyNameLookupsDoNotQueryAndOversizedLookupsFailBeforePersistence() {
    assertThat(directory.userNames(Set.of())).isEmpty();
    assertThat(directory.departmentNames(Set.of())).isEmpty();
    Set<String> tooMany = IntStream.rangeClosed(0, IdentityDirectoryService.MAX_ACTIVE_USER_BATCH_SIZE)
        .mapToObj(value -> "%026d".formatted(value)).collect(Collectors.toSet());
    assertThatThrownBy(() -> directory.userNames(tooMany)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> directory.departmentNames(tooMany)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> directory.userNames(null)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> directory.departmentNames(Set.of("invalid"))).isInstanceOf(PlatformException.class);
    verifyNoInteractions(accounts, administration);
  }

  private static UserAccountRecord activeUser(String id) {
    return user(id, "ACTIVE");
  }

  private static UserAccountRecord inactiveUser(String id) {
    return user(id, "DISABLED");
  }

  private static UserAccountRecord user(String id, String status) {
    return new UserAccountRecord(
        id,
        "测试用户",
        "user@example.com",
        "user@example.com",
        "{noop}secret",
        "D".repeat(26),
        status,
        false,
        0);
  }
}
