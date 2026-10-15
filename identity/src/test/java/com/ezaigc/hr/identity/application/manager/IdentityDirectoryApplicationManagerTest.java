package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
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

class IdentityDirectoryApplicationManagerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
  private final CursorCodec cursors = mock(CursorCodec.class);
  private final IdentityAdministrationPersistenceService administration =
      mock(IdentityAdministrationPersistenceService.class);
  private final IdentityDirectoryApplicationManager manager =
      new IdentityDirectoryApplicationManager();
  private final CurrentActorSnapshot actor = new CurrentActorSnapshot();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "accounts", accounts);
    ReflectionTestUtils.setField(manager, "cursors", cursors);
    ReflectionTestUtils.setField(manager, "administration", administration);
    actor.setUserId("A".repeat(26));
    actor.setSupportDepartmentIds(Set.of());
    actor.setManagedUserIds(Set.of());
    actor.setExplicitlyGrantedDepartmentIds(Set.of());
    actor.setRoles(Set.of());
    actor.setPermissions(Set.of("talent.read"));
    when(authorization.currentActor()).thenReturn(actor);
  }

  @Test
  void ordinaryHrResolvesDisabledHistoricalMemberWithoutAdminPermission() {
    var disabled = user("B".repeat(26), "陈佳", "DISABLED");
    when(accounts.resolveDirectoryUsers(Set.of(disabled.getId()))).thenReturn(List.of(disabled));
    var result = manager.resolve(Set.of(disabled.getId()));
    assertThat(result).singleElement().satisfies(value -> {
      assertThat(value.getDisplayName()).isEqualTo("陈佳");
      assertThat(value.getStatus()).isEqualTo("DISABLED");
    });
    verify(authorization, never()).requirePermission("identity.admin");
  }

  @Test
  void searchContinuesBeyondFirstPageWithScopeBoundCursor() {
    var first = user("B".repeat(26), "陈佳", "ACTIVE");
    var second = user("C".repeat(26), "陈安", "ACTIVE");
    String scope = CursorScopes.scope("identity.directory", actor.getUserId(), true, "陈");
    when(accounts.findDirectoryUsers("陈", null, 2)).thenReturn(List.of(first, second));
    when(cursors.encode(scope, List.of(first.getId()))).thenReturn("next");
    var page = manager.users(" 陈 ", null, 1);
    assertThat(page.isHasMore()).isTrue();
    assertThat(page.getItems()).hasSize(1);
    when(cursors.decode("next", scope, 1)).thenReturn(List.of(first.getId()));
    when(accounts.findDirectoryUsers("陈", first.getId(), 2)).thenReturn(List.of(second));
    assertThat(manager.users("陈", page.getNextCursor(), 1).getItems().getFirst().getDisplayName())
        .isEqualTo("陈安");
  }

  @Test
  void noBusinessPermissionCannotEnumerateDirectory() {
    actor.setPermissions(Set.of());
    assertThatThrownBy(() -> manager.users(null, null, 20)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.resolve(Set.of("B".repeat(26)))).isInstanceOf(PlatformException.class);
    verifyNoInteractions(accounts);
  }

  @Test
  void demandManagerListsOnlyActiveAssignableDepartments() {
    actor.setHomeDepartmentId("D".repeat(26));
    actor.setSupportDepartmentIds(Set.of("S".repeat(26)));
    actor.setPermissions(Set.of("demand.manage"));
    DepartmentRecord home = department("D".repeat(26), "ACTIVE");
    DepartmentRecord support = department("S".repeat(26), "ACTIVE");
    DepartmentRecord outside = department("X".repeat(26), "ACTIVE");
    DepartmentRecord disabled = department("Z".repeat(26), "DISABLED");
    when(administration.findDepartments()).thenReturn(List.of(home, support, outside, disabled));

    assertThat(manager.departments()).extracting(value -> value.getId())
        .containsExactly(home.getId(), support.getId());
    verify(authorization).requirePermission("demand.manage");
    verify(authorization, never()).requirePermission("identity.admin");

    actor.setCompanyScope(true);
    assertThat(manager.departments()).extracting(value -> value.getId())
        .containsExactly(home.getId(), support.getId(), outside.getId());
  }

  @Test
  void departmentDirectoryRejectsAccountsWithoutDemandManage() {
    doThrow(PlatformExceptions.forbidden("PERMISSION_DENIED", "无权限"))
        .when(authorization).requirePermission("demand.manage");
    assertThatThrownBy(manager::departments).isInstanceOf(PlatformException.class);
    verifyNoInteractions(administration);
  }

  @Test
  void signedCursorCannotBeReusedAcrossSearchOrAccountChanges() {
    ReflectionTestUtils.setField(manager, "cursors", new SignedCursorCodec("test-only-directory-signing-key-32-bytes"));
    when(accounts.findDirectoryUsers(null, null, 2)).thenReturn(List.of(
        user("B".repeat(26), "陈佳", "ACTIVE"), user("C".repeat(26), "陈安", "ACTIVE")));
    String cursor = manager.users(null, null, 1).getNextCursor();
    clearInvocations(accounts);
    assertThatThrownBy(() -> manager.users("null", cursor, 1)).isInstanceOf(PlatformException.class);
    actor.setUserId("Z".repeat(26));
    assertThatThrownBy(() -> manager.users(null, cursor, 1)).isInstanceOf(PlatformException.class);
    verifyNoInteractions(accounts);
  }

  @Test
  void rejectsInvalidOrUnboundedRequestsBeforeDatabaseRead() {
    assertThatThrownBy(() -> manager.users(null, null, 101)).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.resolve(Set.of("bad-id"))).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.resolve(IntStream.range(0, 101).mapToObj(i -> "%026d".formatted(i))
        .collect(Collectors.toSet()))).isInstanceOf(PlatformException.class);
    verifyNoInteractions(accounts);
  }

  private UserAccountRecord user(String id, String name, String status) {
    return new UserAccountRecord(id, name, "private@example.com", "private@example.com",
        "secret-hash", "D".repeat(26), status, false, 0);
  }

  private DepartmentRecord department(String id, String status) {
    DepartmentRecord department = new DepartmentRecord();
    department.setId(id);
    department.setCode("HR");
    department.setName("招聘部");
    department.setStatus(status);
    return department;
  }
}
