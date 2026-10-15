package com.ezaigc.hr.identity.service.impl;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ASSIGNABLE_ROLES_PER_USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.identity.domain.entity.UserRole;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdentityRoleAssignmentBatchValidationTest {

  private static final String USER_ID = "U".repeat(26);
  private static final String ACTOR_ID = "A".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void mapsAllRoleAssignmentsIntoOneBoundedJdbcBatch() {
    UserRolePersistenceServiceImpl service = spy(new UserRolePersistenceServiceImpl());
    Set<String> roleIds = Set.of("R".repeat(26), "M".repeat(26), "I".repeat(26));
    doReturn(true)
        .when(service)
        .saveBatch(anyCollection(), eq(MAX_ASSIGNABLE_ROLES_PER_USER));

    assertThat(service.insertUserRoles(USER_ID, roleIds, ACTOR_ID, "创建账号", NOW)).isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<UserRole>> batch = ArgumentCaptor.forClass(Collection.class);
    verify(service).saveBatch(batch.capture(), eq(MAX_ASSIGNABLE_ROLES_PER_USER));
    assertThat(batch.getValue())
        .hasSize(3)
        .allSatisfy(
            userRole -> {
              assertThat(userRole.getUserId()).isEqualTo(USER_ID);
              assertThat(userRole.getRoleId()).isIn(roleIds);
              assertThat(userRole.getAssignedByUserId()).isEqualTo(ACTOR_ID);
              assertThat(userRole.getAssignedAt()).isEqualTo(NOW);
              assertThat(userRole.getReason()).isEqualTo("创建账号");
              assertThat(userRole.getVersion()).isZero();
            });
  }

  @Test
  void rejectsEmptyAndOversizedRoleBatchesBeforePersistence() {
    UserRolePersistenceServiceImpl service = new UserRolePersistenceServiceImpl();

    assertThatThrownBy(
            () -> service.insertUserRoles(USER_ID, Set.of(), ACTOR_ID, "创建账号", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("identity user role batch is invalid");
    assertThatThrownBy(
            () ->
                service.insertUserRoles(
                    USER_ID,
                    Set.of("1", "2", "3", "4"),
                    ACTOR_ID,
                    "创建账号",
                    NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("identity user role batch is invalid");
  }
}
