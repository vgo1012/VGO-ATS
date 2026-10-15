package com.ezaigc.hr.identity.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAuditEventPersistenceService;
import com.ezaigc.hr.identity.service.RolePersistenceService;
import com.ezaigc.hr.identity.service.UserRolePersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 使用真实 Spring 事务代理与内存持久化替身验证原子边界，不代表真实 MySQL 实测。 */
class IdentityRoleSessionVersionTest {

  private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
  private final IdentityAccountPersistenceService accounts = mock(IdentityAccountPersistenceService.class);
  private final IdentityAdministrationPersistenceService persistence =
      mock(IdentityAdministrationPersistenceService.class);
  private final UserRolePersistenceService userRoles = mock(UserRolePersistenceService.class);
  private final Set<String> storedRoles = new LinkedHashSet<>();
  private final UserAccountRecord account = new UserAccountRecord("target", "员工", "user@example.com",
      "user@example.com", "{noop}secret", "department", "ACTIVE", false, 2);
  private final MemoryTransactionManager transactions = new MemoryTransactionManager();

  @ParameterizedTest
  @ValueSource(strings = {"ASSIGN", "REVOKE", "GRANT_ADMIN", "REVOKE_ADMIN"})
  void roleAndAccountVersionChangeInTheSameTransaction(String operation) {
    prepareRoles(operation, true);
    IdentityAdministrationApplicationManager manager = manager();

    changeRole(manager, operation);

    assertThat(account.getVersion()).isEqualTo(3);
    assertThat(storedRoles.contains(role(operation))).isEqualTo(isGrant(operation));
    assertThat(transactions.committed).isTrue();
    verify(accounts).advanceSessionVersion("target", "actor", NOW);
    verify(persistence).deleteSessionsByPrincipalName("user@example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ASSIGN", "REVOKE", "GRANT_ADMIN", "REVOKE_ADMIN"})
  void unchangedRoleDoesNotAdvanceAccountVersion(String operation) {
    prepareRoles(operation, false);

    changeRole(manager(), operation);

    assertThat(account.getVersion()).isEqualTo(2);
    verify(accounts, never()).advanceSessionVersion(anyString(), anyString(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ASSIGN", "REVOKE", "GRANT_ADMIN", "REVOKE_ADMIN"})
  void cannotCommitRoleChangeWhenAccountVersionCannotAdvance(String operation) {
    prepareRoles(operation, true);
    IdentityAdministrationApplicationManager manager = manager();
    Set<String> before = Set.copyOf(storedRoles);
    doReturn(0).when(accounts).advanceSessionVersion("target", "actor", NOW);

    assertThatThrownBy(() -> changeRole(manager, operation))
        .isInstanceOf(PlatformException.class);

    assertThat(storedRoles).containsExactlyInAnyOrderElementsOf(before);
    assertThat(account.getVersion()).isEqualTo(2);
    assertThat(transactions.rolledBack).isTrue();
    assertThat(transactions.committed).isFalse();
    verify(persistence, never()).deleteSessionsByPrincipalName(anyString());
  }

  private void prepareRoles(String operation, boolean changed) {
    storedRoles.add("RECRUITER");
    if (isGrant(operation) != changed) {
      storedRoles.add(role(operation));
    }
  }

  private IdentityAdministrationApplicationManager manager() {
    when(accounts.findById("target")).thenReturn(account);
    when(accounts.findRoleCodes("target")).thenAnswer(call -> List.copyOf(storedRoles));
    when(persistence.findActiveSuperAdminUserIdsForUpdate()).thenReturn(List.of("actor", "target"));
    when(accounts.advanceSessionVersion("target", "actor", NOW)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      account.setVersion(account.getVersion() + 1);
      return 1;
    });
    when(userRoles.insertUserRole(anyString(), anyString(), anyString(), anyString(), any()))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          return storedRoles.add(call.getArgument(1)) ? 1 : 0;
        });
    when(userRoles.deleteUserRole(anyString(), anyString())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return storedRoles.remove(call.getArgument(1)) ? 1 : 0;
    });
    RolePersistenceService roles = mock(RolePersistenceService.class);
    when(roles.findActiveRoleId(anyString())).thenAnswer(call -> call.getArgument(0));
    IdentityAdministrationApplicationManager target = new IdentityAdministrationApplicationManager();
    ReflectionTestUtils.setField(target, "accounts", accounts);
    ReflectionTestUtils.setField(target, "persistence", persistence);
    ReflectionTestUtils.setField(target, "userRolePersistenceService", userRoles);
    ReflectionTestUtils.setField(target, "rolePersistenceService", roles);
    ReflectionTestUtils.setField(target, "identityAuditEventPersistenceService",
        mock(IdentityAuditEventPersistenceService.class));
    CurrentActorProvider actors = () -> Optional.of(new CurrentActor("actor", Set.of(), true));
    ReflectionTestUtils.setField(target, "actorProvider", actors);
    PlatformIdGenerator ids = () -> "01JTEST000000000000000000";
    ReflectionTestUtils.setField(target, "ids", ids);
    ReflectionTestUtils.setField(target, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
    return (IdentityAdministrationApplicationManager) factory.getProxy();
  }

  private static void changeRole(IdentityAdministrationApplicationManager manager, String operation) {
    switch (operation) {
      case "ASSIGN" -> manager.assignRole("target", "INTERVIEWER", "岗位调整");
      case "REVOKE" -> manager.revokeRole("target", "INTERVIEWER", "岗位调整");
      case "GRANT_ADMIN" -> manager.grantSuperAdmin("target", "管理员授权");
      case "REVOKE_ADMIN" -> manager.revokeSuperAdmin("target", "管理员撤权");
      default -> throw new IllegalArgumentException(operation);
    }
  }

  private static String role(String operation) {
    return operation.endsWith("ADMIN") ? "SUPER_ADMIN" : "INTERVIEWER";
  }

  private static boolean isGrant(String operation) {
    return operation.equals("ASSIGN") || operation.equals("GRANT_ADMIN");
  }

  private final class MemoryTransactionManager extends AbstractPlatformTransactionManager {
    private Set<String> previousRoles;
    private long previousVersion;
    private boolean committed;
    private boolean rolledBack;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      previousRoles = Set.copyOf(storedRoles);
      previousVersion = account.getVersion();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      committed = true;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      storedRoles.clear();
      storedRoles.addAll(previousRoles);
      account.setVersion(previousVersion);
      rolledBack = true;
    }
  }
}
